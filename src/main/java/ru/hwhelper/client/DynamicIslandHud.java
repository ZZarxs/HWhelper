package ru.hwhelper.client;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.fabricmc.fabric.api.client.screen.v1.*;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
final class DynamicIslandHud {
    private volatile JsonObject data=new JsonObject();
    private final Identifier coverId=Identifier.fromNamespaceAndPath("hwhelper","current_cover");
    private volatile boolean coverReady;
    private volatile String coverKey="";
    private volatile long coverRetryAt;
    private boolean dragging,resizing;
    private double dx,dy,startX;
    private int startWidth;
    private volatile long receivedAt;
    private String value(String key,String fallback){try{return data.get(key).getAsString();}catch(Exception e){return fallback;}}
    private double number(String key){try{return data.get(key).getAsDouble();}catch(Exception e){return 0;}}
    private boolean playing(){return value("playing","false").equals("true");}
    void accept(LocalMediaService.Update update){
        data=update.data();receivedAt=System.nanoTime();
        String key=value("coverKey","");
        if(!key.equals(coverKey)){coverKey=key;coverReady=false;coverRetryAt=0;}
        if(key.isBlank()||coverReady||update.cover().length==0||System.nanoTime()<coverRetryAt)return;
        coverRetryAt=System.nanoTime()+3_000_000_000L;
        byte[] bytes=update.cover();
        Minecraft.getInstance().execute(()->{
            if(!key.equals(coverKey))return;
            try{
                NativeImage cover=NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                roundCover(cover);
                var texture=new DynamicTexture(()->"HWhelper cover",cover);
                Minecraft.getInstance().getTextureManager().release(coverId);
                Minecraft.getInstance().getTextureManager().register(coverId,texture);
                texture.upload();coverReady=true;
            }catch(Exception exception){coverReady=false;HWhelperClient.LOGGER.warn("Cannot load music cover",exception);}
        });
    }
    private boolean visible;
    JsonObject mediaData(){return data;}
    double mediaPosition(){
        double sampled=number("sampledAt");
        double elapsed=sampled>0?Math.max(0,System.currentTimeMillis()/1000.0-sampled):Math.max(0,(System.nanoTime()-receivedAt)/1_000_000_000.0);
        double position=number("position")+(playing()?Math.min(1.5,elapsed)*Math.max(0,number("playbackRate")):0);
        return number("duration")>0?Math.min(number("duration"),position):position;
    }
    private float drawnWidth=160;
    private int drawnX,drawnY,drawnHeight=30;
    private float drawnScale=1;
    private String lastLine="";
    private long lineChangedAt;
    private long lastFrame;
    private float progress;
    private float scale(HwConfig c,int screenWidth){return Math.min(c.musicScale,(screenWidth-8f)/Math.max(140,c.musicWidth));}
    boolean contains(HwConfig c,double x,double y){
        return visible&&c.musicEnabled&&x>=drawnX&&x<drawnX+drawnWidth*drawnScale&&y>=drawnY&&y<drawnY+drawnHeight*drawnScale;
    }
    void register(HwConfig c){
        ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            if(!(screen instanceof ChatScreen)&&!(screen instanceof HudEditorScreen))return;
            ScreenMouseEvents.afterMouseClick(screen).register((s,e,used)->{
                if(!used&&e.button()==0&&contains(c,e.x(),e.y())){
                    c.islandCentered=false;c.musicX=drawnX;c.musicY=drawnY;
                    if(e.x()>drawnX+(drawnWidth-10)*drawnScale){resizing=true;startX=e.x();startWidth=c.musicWidth;}
                    else{dragging=true;dx=e.x()-drawnX;dy=e.y()-drawnY;}
                }return false;
            });
            ScreenMouseEvents.afterMouseDrag(screen).register((s,e,x,y,used)->{
                if(dragging){c.musicX=Math.max(0,(int)(e.x()-dx));c.musicY=Math.max(0,(int)(e.y()-dy));if(c.musicSnapToGrid){c.musicX=c.musicX/8*8;c.musicY=c.musicY/8*8;}}
                if(resizing)c.musicWidth=Math.max(140,Math.min(420,startWidth+(int)((e.x()-startX)/drawnScale)));return false;
            });
            ScreenMouseEvents.afterMouseRelease(screen).register((s,e,used)->{if(e.button()==0){dragging=false;resizing=false;c.save();}return false;});
            ScreenEvents.remove(screen).register(s->{dragging=false;resizing=false;c.save();});
        });
    }
    static float targetWidth(float textWidth,int height,int maximum,boolean cover,boolean equalizer) {
        float overhead=20+(cover?height-8+6:0)+(equalizer?22:0);
        return Math.max(140,Math.min(maximum,overhead+textWidth+4));
    }
    static double lyricProgress(double position,double start,double end) {
        return end>start?Math.max(0,Math.min(1,(position-start)/(end-start))):1;
    }
    private static void roundCover(NativeImage image) {
        int width=image.getWidth(),height=image.getHeight();float radius=Math.min(width,height)*.2f;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            float qx=Math.abs(x+.5f-width/2f)-(width/2f-radius),qy=Math.abs(y+.5f-height/2f)-(height/2f-radius);
            float distance=(float)Math.hypot(Math.max(0,qx),Math.max(0,qy))+Math.min(Math.max(qx,qy),0)-radius;
            float coverage=Math.max(0,Math.min(1,.5f-distance));int pixel=image.getPixel(x,y);
            if(coverage<1)image.setPixel(x,y,((int)((pixel>>>24)*coverage)<<24)|(pixel&0xFFFFFF));
        }
    }
    void render(GuiGraphics g,HwConfig c,boolean preview){
        visible=false;
        if(!c.musicEnabled)return;
        String title=value("title",""),artist=value("artist","");
        boolean present=!title.isBlank();
        if(!present&&!preview&&!c.islandAlwaysShow)return;
        if(c.musicHideIdle&&!playing()&&!preview)return;
        visible=true;
        boolean lyric=c.musicLyrics&&!value("lyric","").isBlank();
        String line=lyric?value("lyric",""):present?title+(c.musicArtist&&!artist.isBlank()?" — "+artist:""):"Dynamic Island";
        int height=c.islandHeight,coverSize=height-8;
        var font=Minecraft.getInstance().font;
        int textSize=9,total=font.width(UiStyle.text(line,textSize));
        float target=targetWidth(total,height,c.musicWidth,c.musicCover,c.islandEqualizer);
        long now=System.nanoTime();float dt=lastFrame==0?1:Math.min(.1f,(now-lastFrame)/1_000_000_000f);lastFrame=now;
        float blend=c.animations?1-(float)Math.exp(-dt*12):1;
        drawnWidth+=(target-drawnWidth)*blend;
        drawnScale=scale(c,g.guiWidth());drawnHeight=height;
        drawnX=c.islandCentered?(int)((g.guiWidth()-drawnWidth*drawnScale)/2):Math.max(0,Math.min((int)(g.guiWidth()-drawnWidth*drawnScale),c.musicX));
        drawnY=Math.max(0,Math.min((int)(g.guiHeight()-height*drawnScale),c.musicY));
        if(!c.islandCentered)c.musicX=drawnX;c.musicY=drawnY;
        int w=Math.round(drawnWidth);
        g.pose().pushMatrix();
        g.pose().translate(drawnX,drawnY);g.pose().scale(drawnScale,drawnScale);
        if(c.islandStatus){
            String clock=c.islandTrackClock?String.format("%02d:%02d",(int)number("position")/60,(int)number("position")%60):java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            int tw=font.width(UiStyle.text(clock,9));
            if(drawnX>(tw+17)*drawnScale){UiStyle.surface(g,-tw-17,(height-22)/2,tw+12,22,6,0xF018161A);UiStyle.label(g,clock,-tw-11,(height-9)/2,9,UiStyle.TEXT);}
            if(drawnX+(w+21)*drawnScale<g.guiWidth())g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    Identifier.fromNamespaceAndPath("hwhelper","textures/gui/island_wifi.png"),w+8,height/2-5,0f,0f,12,10,48,40,48,40);
        }
        UiStyle.pill(g,0,0,w,height,c.accent(),c.musicOpacity*255/100);
        int left=10;
        if(c.musicCover){
            UiStyle.shape(g,4,4,coverSize,coverSize,6,0xFF3B2933);
            if(coverReady)g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,coverId,4,4,0f,0f,coverSize,coverSize,256,256,256,256);
            else UiStyle.icon(g,"music",7,7,Math.max(10,coverSize-6),c.accent());
            left=coverSize+10;
        }
        int right=w-10-(c.islandEqualizer?20:0),room=Math.max(12,right-left);
        if(!line.equals(lastLine)){lastLine=line;lineChangedAt=now;}
        float transition=c.animations?Math.min(1,(now-lineChangedAt)/220_000_000f):1;
        int textY=c.musicProgress?Math.max(3,height/2-9):height/2-5;
        int y=textY+(int)((1-transition)*3);
        double position=mediaPosition();
        float ratio=(float)lyricProgress(position,number("lyricStart"),number("lyricEnd"));
        float sung=lyric?ratio*total:0;
        int scroll=lyric?(int)Math.max(0,Math.min(total-room,sung-room*.35)):
                c.animations?(int)((.5-.5*Math.cos(now/1_000_000_000.0))*Math.max(0,total-room)):0;
        int alpha=(int)(255*(.45+.55*transition));
        g.enableScissor(left,1,right,height-2);
        try {
            g.drawString(font,UiStyle.text(line,9),left-scroll,y,(alpha<<24)|(lyric&&c.musicGlow?0xB1A8AF:(UiStyle.WHITE&0xFFFFFF)),true);
            int filled=Math.min(right,left-scroll+(int)sung);
            if(lyric&&c.musicGlow&&filled>left){
                g.enableScissor(left,1,filled,height-2);
                try{g.drawString(font,UiStyle.text(line,9),left-scroll,y,(alpha<<24)|(c.accent()&0xFFFFFF),true);}
                finally{g.disableScissor();}
            }
        }finally{g.disableScissor();}
        if(c.musicProgress){
            float targetProgress=(float)Math.max(0,Math.min(1,number("duration")>0?position/number("duration"):0));
            progress+= (targetProgress-progress)*blend;
            UiStyle.shape(g,left,height-7,room,2,1,0xFF44353E);
            if(progress*room>=1)UiStyle.shape(g,left,height-7,Math.max(1,(int)(progress*room)),2,1,c.accent());
        }
        if(c.islandEqualizer){
            for(int i=0;i<4;i++){
                double phase=now/1_000_000_000.0*(3.3+i*.75)+i*1.6;
                int bar=playing()&&c.animations?3+(int)((.5+.5*Math.sin(phase))*9):3;
                UiStyle.shape(g,w-22+i*3,height/2-bar/2,2,bar,1,c.accent());
            }
        }
        g.pose().popMatrix();
    }
}
