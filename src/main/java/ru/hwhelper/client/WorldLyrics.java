package ru.hwhelper.client;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** World-space KineticLyrics adaptation: native depth-tested text, no GL state changes. */
final class WorldLyrics {
    private record Particle(LyricTimeline.Line line,Vec3 position){}
    private record Draw(Component text,Vec3 position,float scale,float tilt,int color){}
    private final DynamicIslandHud media;
    private final List<Particle> active=new ArrayList<>();
    private volatile List<Draw> frame=List.of();
    private Object world;private String track="",lineKey="",cacheKey="";
    private List<LyricTimeline.Line> timeline=List.of();private double lastAudio=Double.NaN;
    WorldLyrics(DynamicIslandHud media){this.media=media;}
    void register(HwConfig c){
        WorldRenderEvents.END_EXTRACTION.register(context->extract(c));
        WorldRenderEvents.BEFORE_ENTITIES.register(this::draw);
    }
    void tick(Minecraft client,HwConfig c){
        if(!c.worldLyrics.enabled||client.player==null||client.level==null){clear();return;}
        if(world!=client.level){clear();world=client.level;}
        var data=media.mediaData();String nextTrack=LyricTimeline.string(data,"title")+"/"+LyricTimeline.string(data,"artist");
        if(!nextTrack.equals(track)){clear();world=client.level;track=nextTrack;}
        if(LyricTimeline.string(data,"title").isBlank()){clear();return;}
        String key=track+"/"+c.worldLyrics.smartSplit+"/"+(data.has("lyrics")?data.get("lyrics").hashCode():LyricTimeline.string(data,"lyric"));
        if(!key.equals(cacheKey)){cacheKey=key;timeline=LyricTimeline.read(data,c.worldLyrics.smartSplit);}
        double audio=media.mediaPosition()+c.worldLyrics.offsetMs/1000.0;
        if(Double.isFinite(lastAudio)&&(audio<lastAudio-.2||audio-lastAudio>2)){active.clear();lineKey="";}
        lastAudio=audio;
        active.removeIf(p->audio>p.line().end()+.7||audio<p.line().start()-.2);
        LyricTimeline.Line current=null;for(var line:timeline){if(line.start()>audio)break;if(audio<line.end())current=line;}
        if(current!=null){String id=current.start()+":"+current.text();
            if(!id.equals(lineKey)){
                lineKey=id;while(active.size()>=c.worldLyrics.maxLines)active.removeFirst();
                active.add(new Particle(current,spawn(client,c.worldLyrics)));
            }
        }
    }
    private Vec3 spawn(Minecraft client,WorldLyricOptions c){
        Vec3 origin=client.player.getEyePosition();double yaw=client.player.getYRot();Vec3 best=origin;double score=-1;
        for(int attempt=0;attempt<16;attempt++){
            double angle=Math.toRadians(yaw+(Math.random()*2-1)*c.spread),distance=c.minDistance+Math.random()*Math.max(.2,c.maxDistance-c.minDistance);
            Vec3 candidate=origin.add(-Math.sin(angle)*distance,-.25+Math.random()*.65,Math.cos(angle)*distance);
            double nearest=active.stream().mapToDouble(p->p.position().distanceToSqr(candidate)).min().orElse(100);
            if(nearest>score){best=candidate;score=nearest;}if(nearest>1.8)return candidate;
        }return best;
    }
    private void extract(HwConfig config){
        Minecraft client=Minecraft.getInstance();if(client.level==null||client.options.hideGui||!config.worldLyrics.enabled||(client.screen!=null&&!(client.screen instanceof ChatScreen))){frame=List.of();return;}
        WorldLyricOptions c=config.worldLyrics;double audio=media.mediaPosition()+c.offsetMs/1000.0;List<Draw> draws=new ArrayList<>();
        for(Particle p:List.copyOf(active)){
            double length=Math.max(.25,p.line().end()-p.line().start()+.7),progress=Math.max(0,Math.min(1,(audio-p.line().start())/length));
            float opacity=(float)Math.min(1,Math.min(progress/.15,(1-progress)/.22));if(opacity<.02)continue;
            String text=p.line().text();float scale=.025f*c.scale,tilt=0;double rise=progress*c.floatHeight*.2;
            if(config.animations)switch(c.animation){
                case 1->{int count=text.codePointCount(0,text.length());int visible=Math.max(1,Math.min(count,(int)Math.ceil(count*Math.min(1,progress/.3))));text=text.substring(0,text.offsetByCodePoints(0,visible));}
                case 2->scale*=.4f+.6f*(float)(1-Math.pow(1-Math.min(1,progress/.18),3));
                case 3->{rise-=.15*(1-Math.min(1,progress/.2));tilt=(float)(1-Math.min(1,progress/.2))*.07f;}
                case 4->{}
                default->scale*=.93f+.07f*(float)Math.min(1,progress/.15);
            }
            int color=UiStyle.alpha(c.white?UiStyle.WHITE:config.accent(),opacity);
            draws.add(new Draw(UiStyle.hudText(text),p.position().add(0,rise,0),scale,tilt,color));
        }frame=List.copyOf(draws);
    }
    private void draw(WorldRenderContext context){
        var camera=context.worldState().cameraRenderState;var matrices=context.matrices();var font=Minecraft.getInstance().font;
        for(Draw draw:frame){
            matrices.pushPose();
            try{
                Vec3 relative=draw.position().subtract(camera.pos);matrices.translate(relative.x,relative.y,relative.z);matrices.mulPose(camera.orientation);
                if(draw.tilt()!=0)matrices.mulPose(com.mojang.math.Axis.ZP.rotation(draw.tilt()));
                matrices.scale(draw.scale(),-draw.scale(),draw.scale());
                context.commandQueue().submitText(matrices,-font.width(draw.text())/2f,-5,draw.text().getVisualOrderText(),true,Font.DisplayMode.NORMAL,LightTexture.FULL_BRIGHT,draw.color(),0,0);
            }finally{matrices.popPose();}
        }
    }
    private void clear(){active.clear();frame=List.of();world=null;track=lineKey=cacheKey="";timeline=List.of();lastAudio=Double.NaN;}
}
