package ru.hwhelper.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class UiStyle {
    static final int WHITE=0xFFF7F4EC,TEXT=0xFFD9D5C9,MUTED=0xFF93918E;
    static final int PANEL=0xF70F0F10,CARD=0xFF181819,EDGE=0xFF2B2A2D,INK=0xFF291923;
    private static java.util.function.BooleanSupplier liquidEnabled=()->false;
    private static java.util.function.IntSupplier accentColor=()->0xFFFF8FB9;
    static void configure(HwConfig settings) { configure(()->settings.liquidGlass,settings::accent); }
    static void configure(java.util.function.BooleanSupplier enabled,java.util.function.IntSupplier accent) {
        liquidEnabled=enabled;accentColor=accent;
    }
    static boolean liquid() { return liquidEnabled.getAsBoolean(); }
    static int accent() { return accentColor.getAsInt(); }
    static int alpha(int color,float opacity) {
        return (Math.max(0,Math.min(255,Math.round((color>>>24)*opacity)))<<24)|(color&0xFFFFFF);
    }
    static int mix(int from,int to,float value) {
        float t=Math.max(0,Math.min(1,value));int result=0;
        for(int shift:new int[]{0,8,16,24})result|=Math.round(((from>>>shift)&255)*(1-t)+((to>>>shift)&255)*t)<<shift;
        return result;
    }
    static void frame(GuiGraphics g,int x,int y,int w,int h,int radius,int fill,int border) {
        shape(g,x,y,w,h,radius,border);
        shape(g,x+1,y+1,w-2,h-2,Math.max(0,radius-1),fill);
    }
    static void icon(GuiGraphics g,String name,int x,int y,int size,int color) {
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            net.minecraft.resources.Identifier.fromNamespaceAndPath("hwhelper","textures/gui/cs/"+name+".png"),
            x,y,0f,0f,size,size,32,32,32,32,color);
    }
    static void card(GuiGraphics g,int x,int y,int w,int h,int color) {
        surface(g,x,y,w,h,10,color);
    }
    static net.minecraft.network.chat.Component text(String value) { return text(net.minecraft.network.chat.Component.literal(value)); }
    static net.minecraft.network.chat.Component text(net.minecraft.network.chat.Component value) {
        return value.copy().withStyle(style -> style.withFont(new net.minecraft.network.chat.FontDescription.Resource(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("hwhelper","ui"))));
    }
    static net.minecraft.network.chat.Component text(String value,int size) {
        return net.minecraft.network.chat.Component.literal(value).withStyle(style -> style.withFont(
            new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("hwhelper","ui"+Math.max(7,Math.min(32,size))))));
    }
    static net.minecraft.network.chat.Component hudText(String value) {
        return net.minecraft.network.chat.Component.literal(value).withStyle(style -> style.withFont(
            new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("hwhelper","hud"))));
    }
    static void surface(GuiGraphics g,int x,int y,int w,int h,int radius,int color) {
        if(liquid()) glass(g,x,y,w,h,accentColor.getAsInt(),0,radius>=10,(color>>>24));
        else frame(g,x,y,w,h,radius,color,alpha(EDGE,(color>>>24)/255f));
    }
    static void glass(GuiGraphics g,int x,int y,int w,int h,int accent,float hover,boolean panel) {
        glass(g,x,y,w,h,accent,hover,panel,panel?220:110);
    }
    static void glass(GuiGraphics g,int x,int y,int w,int h,int accent,float hover,boolean panel,int alpha) {
        if(!liquid()) { frame(g,x,y,w,h,panel?10:6,((Math.max(0,Math.min(255,alpha)))<<24)|(panel?0x0F0F10:0x19191B),alpha(EDGE,alpha/255f));return; }
        var id=net.minecraft.resources.Identifier.fromNamespaceAndPath("hwhelper","textures/gui/"+(panel?"liquid_panel":"liquid_row")+".png");
        // Nine-slice: preserve the radius instead of stretching pixel-sized scanlines.
        int radius=Math.min(12,Math.min(w,h)/2);
        int[] destX={x,x+radius,x+w-radius}, destY={y,y+radius,y+h-radius};
        int[] dw={radius,w-radius*2,radius}, dh={radius,h-radius*2,radius};
        int[] uv={0,12,52}, region={12,40,12};
        int tint=(Math.max(0,Math.min(255,alpha))<<24)|0xFFFFFF;
        for(int iy=0;iy<3;iy++)for(int ix=0;ix<3;ix++)if(dw[ix]>0&&dh[iy]>0)
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,destX[ix],destY[iy],
                (float)uv[ix],(float)uv[iy],dw[ix],dh[iy],region[ix],region[iy],64,64,tint);
    }
    static void pill(GuiGraphics g,int x,int y,int w,int h,int accent,int alpha) {
        if(!liquid()){frame(g,x,y,w,h,h/2,(Math.max(0,Math.min(255,alpha))<<24)|0x101011,alpha(EDGE,alpha/255f));return;}
        var id=net.minecraft.resources.Identifier.fromNamespaceAndPath("hwhelper","textures/gui/island_glass.png");
        int cap=h/2,center=Math.max(0,w-h),tint=(Math.max(0,Math.min(255,alpha))<<24)|0xFFFFFF;
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,x,y,0f,0f,cap,h,32,64,64,64,tint);
        if(center>0)g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,x+cap,y,31f,0f,center,h,2,64,64,64,tint);
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,x+w-cap,y,32f,0f,cap,h,32,64,64,64,tint);
    }
    static void glint(GuiGraphics g,int x,int y,int w,int mouseX,int accent,float strength) {
        if(!liquid())return;
        int center=Math.max(x+14,Math.min(x+w-14,mouseX));
        int radius=Math.min(36,w/4);
        for(int dx=-radius;dx<=radius;dx++) {
            int a=(int)(strength*150*(1-Math.abs(dx)/(float)radius));
            int px=center+dx;
            if(px>x+10 && px<x+w-10)g.fill(px,y,px+1,y+1,a<<24|(accent&0xFFFFFF));
        }
    }
    static void shape(GuiGraphics g,int x,int y,int w,int h,int radius,int color) {
        if(w<=0||h<=0)return;
        int r=Math.min(radius,Math.min(w,h)/2);
        int[] xx={x,x+r,x+w-r},yy={y,y+r,y+h-r},ww={r,w-2*r,r},hh={r,h-2*r,r};
        int[] uv={0,16,48},region={16,32,16};
        var id=net.minecraft.resources.Identifier.fromNamespaceAndPath("hwhelper","textures/gui/shape.png");
        for(int iy=0;iy<3;iy++)for(int ix=0;ix<3;ix++)if(ww[ix]>0&&hh[iy]>0)
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,xx[ix],yy[iy],(float)uv[ix],(float)uv[iy],ww[ix],hh[iy],region[ix],region[iy],64,64,color);
    }
    static void label(GuiGraphics g,String value,float x,float y,float size,int color) {
        // Bake each size in its own font provider. No fractional transform of a glyph atlas.
        g.drawString(Minecraft.getInstance().font,text(value,Math.round(size)),Math.round(x),Math.round(y),color,false);
    }
    static void fitted(GuiGraphics g,String value,int x,int y,int width,float size,int color) {
        var font=Minecraft.getInstance().font;int actual=Math.max(7,Math.min(18,Math.round(size)));
        while(actual>8 && font.width(text(value,actual))>width)actual--;
        String display=value;
        if(font.width(text(display,actual))>width){
            while(!display.isEmpty() && font.width(text(display+"…",actual))>width)display=display.substring(0,display.length()-1);
            display=display.isEmpty()?"":display+"…";
        }
        label(g,display,x,y,actual,color);
    }
    static void centered(GuiGraphics g,String value,int x,int y,int w,int h,int size,int color) {
        var font=Minecraft.getInstance().font;int actual=size;
        while(actual>8 && font.width(text(value,actual))>w-12)actual--;
        int tw=font.width(text(value,actual));
        if(tw>w-8){fitted(g,value,x+4,y+(h-actual)/2,w-8,actual,color);return;}
        label(g,value,x+(w-tw)/2,y+(h-actual)/2,actual,color);
    }
    static final class Action extends Button {
        private final HwConfig config;
        private float hover;
        private long last = System.nanoTime();
        Action(int x,int y,int w,int h,String text,HwConfig c,OnPress action) {
            super(x,y,w,h,Component.literal(text),action,DEFAULT_NARRATION);
            config=c;
        }
        @Override protected void renderContents(GuiGraphics g,int mx,int my,float dt) {
            long now=System.nanoTime();
            float t=Math.min(1f,(now-last)/90_000_000f); last=now;
            float target=isHoveredOrFocused()?1:0;
            hover=config.animations?hover+(target-hover)*t:target;
            glass(g,getX(),getY(),getWidth(),getHeight(),config.accent(),hover,false);
            if(hover>.01f) glint(g,getX(),getY()+1,getWidth(),config.animations?mx:getX()+getWidth()/2,config.accent(),hover);
            if(getMessage().getString().startsWith("●"))
                g.fill(getX()+14,getY()+getHeight()-3,getX()+getWidth()-14,getY()+getHeight()-2,config.accent());
            if(hover>.01f)shape(g,getX()+1,getY()+1,getWidth()-2,getHeight()-2,5,alpha(config.accent(),hover*.10f));
            centered(g,getMessage().getString(),getX(),getY(),getWidth(),getHeight(),10,active?WHITE:MUTED);
        }
    }
}
