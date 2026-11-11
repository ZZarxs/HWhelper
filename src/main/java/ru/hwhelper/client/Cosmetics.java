package ru.hwhelper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Cosmetic V2 adapted to native world render submissions. Cosmetic entities are never added to a level. */
public final class Cosmetics {
    private record Quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int ca,int cb,int cc,int cd){}
    private record Surface(CosmeticGeometry.Mesh mesh,Vec3 center,float major,float minor,int color,boolean halo){}
    private record Sprite(Vec3 position,float size,int color,float rotation){}
    private record Model(EntityRenderState state,Vec3 position,float scale){}
    private record Bird(ParrotRenderState state,Vec3 position,float yaw,float scale,int color){}
    private record Frame(List<Surface> cores,List<Surface> rings,List<Surface> lights,List<Quad> trails,List<Quad> trailGlow,List<Sprite> bloom,List<Sprite> smoke,List<Model> models,List<Bird> birds){
        static Frame empty(){return new Frame(List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());}
    }
    private record Sample(Vec3 position,double time){}
    private static final class History {
        final ArrayDeque<Sample> samples=new ArrayDeque<>();Vec3 smoothed,lastRaw;double lastTime,lastEmit,born;
        List<Sample> update(Vec3 raw,double time,double step,int limit,boolean smooth){
            double dt=Math.max(0,Math.min(.08,time-lastTime));lastTime=time;
            if(smoothed==null||smoothed.distanceToSqr(raw)>16){samples.clear();smoothed=raw;lastRaw=raw;lastEmit=time;}
            smoothed=smooth?smoothed.lerp(raw,1-Math.exp(-dt/.115)):raw;
            int count=Math.min(6,(int)((time-lastEmit)/step));
            for(int i=1;i<=count;i++){
                Vec3 value=lastRaw.lerp(smoothed,i/(double)count);double at=lastEmit+i*step;
                if(samples.isEmpty()||samples.getLast().position().distanceToSqr(value)>1.0e-8){if(samples.isEmpty())born=at;samples.addLast(new Sample(value,at));}
            }
            if(count>0){lastEmit=time;lastRaw=smoothed;}
            while(samples.size()>limit||(!samples.isEmpty()&&time-samples.getFirst().time()>limit*step))samples.removeFirst();
            return List.copyOf(samples);
        }
    }
    private volatile Frame frame=Frame.empty();
    private final Map<Long,History> paths=new HashMap<>();
    private Vec3 petPosition;private float petYaw;private long lastExtract;private double animationTime;
    private ParrotModel ravenModel;
    private static volatile Map<Integer,Integer> cloneColors=Map.of();
    public static int cloneColor(int id){return cloneColors.getOrDefault(id,0);}
    private Object world;private Entity pet;private int petType=-1;
    private final List<RemotePlayer> clones=new ArrayList<>();
    private long clonesAt,ravenAt;
    private KeyMapping cloneKey,ravenKey;
    private static volatile int runningId=Integer.MIN_VALUE,actingId=Integer.MIN_VALUE;
    private static volatile float seals;
    public static boolean running(int id){return runningId==id;}
    public static boolean acting(int id){return actingId==id;}
    public static float signs(int id){return actingId==id?seals:0;}
    void register(HwConfig c,KeyMapping.Category category){
        CosmeticLayers.initialize();
        cloneKey=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hwhelper.clones",74,category));
        ravenKey=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hwhelper.ravens",75,category));
        WorldRenderEvents.END_EXTRACTION.register(context->extract(c,context.worldState().cameraRenderState.pos));
        WorldRenderEvents.BEFORE_ENTITIES.register(this::render);
    }
    void triggerClones(Minecraft client,HwConfig config){
        if(client.player==null||client.level==null)return;
        config.cosmetics.enabled=true;config.cosmetics.clones=true;clonesAt=System.nanoTime();clones.clear();
        for(int i=0;i<4;i++){var clone=new RemotePlayer(client.level,client.player.getGameProfile());clone.setId(-2_000_000_000+i);clone.setSilent(true);clone.noPhysics=true;clones.add(clone);}
        client.player.playSound(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("hwhelper","shadow_clones")),.65f,1);
    }
    void triggerRavens(Minecraft client,HwConfig config){if(client.player!=null){config.cosmetics.enabled=true;ravenAt=System.nanoTime();}}
    void tick(Minecraft client,HwConfig config){
        CosmeticOptions c=config.cosmetics;
        while(cloneKey.consumeClick())if(client.screen==null&&c.enabled&&c.clones)triggerClones(client,config);
        while(ravenKey.consumeClick())if(client.screen==null&&c.enabled)triggerRavens(client,config);
        if(client.player==null||client.level==null||!c.enabled){reset();return;}
        if(world!=client.level){reset();world=client.level;}
        if(c.pet&&(pet==null||petType!=c.petType)){
            EntityType<?> type=switch(c.petType){case 0->EntityType.BAT;case 2->EntityType.PARROT;case 3->EntityType.ALLAY;case 4->EntityType.BEE;case 5->EntityType.VEX;case 6->EntityType.FOX;case 7->EntityType.PIG;case 8->EntityType.FROG;case 9->EntityType.PUFFERFISH;case 10->EntityType.SLIME;default->EntityType.PARROT;};
            pet=type.create(client.level,EntitySpawnReason.COMMAND);petType=c.petType;petPosition=null;
            if(pet!=null){pet.setSilent(true);pet.noPhysics=true;pet.setNoGravity(true);pet.setInvulnerable(true);if(pet instanceof Mob mob)mob.setNoAi(true);}
        }
        if(!c.pet){pet=null;petType=-1;}
        if(pet!=null){if(petPosition==null)pet.setPos(client.player.position().add(0,c.petHeight,0));pet.setDeltaMovement(Vec3.ZERO);pet.tick();}
        if(System.nanoTime()-clonesAt>3_460_000_000L||!c.clones){clones.clear();clonesAt=0;}
    }
    private boolean allowed(Minecraft client,Player player,CosmeticOptions c){
        boolean self=player==client.player;
        return (self?c.self:c.others&&!player.isInvisible())&&player.isAlive()&&player.distanceToSqr(client.player)<=c.distance*c.distance;
    }
    private void extract(HwConfig config,Vec3 camera){
        Minecraft client=Minecraft.getInstance();CosmeticOptions c=config.cosmetics;
        runningId=actingId=Integer.MIN_VALUE;seals=0;cloneColors=Map.of();
        if(client.player==null||client.level==null||!c.enabled||client.options.hideGui||(client.screen!=null&&!(client.screen instanceof ChatScreen))){frame=Frame.empty();lastExtract=0;paths.clear();return;}
        long now=System.nanoTime();double time=now/1_000_000_000.0,dt=lastExtract==0?1/60.0:Math.min(.05,(now-lastExtract)/1_000_000_000.0);lastExtract=now;
        if(config.animations)animationTime+=dt;
        double animation=animationTime;
        if(c.naruto&&client.player.isSprinting()&&!client.player.isUsingItem()&&!client.player.isFallFlying())runningId=client.player.getId();
        double cloneAge=clonesAt==0?-1:(now-clonesAt)/1_000_000_000.0;
        if(cloneAge>=0&&cloneAge<1.85){actingId=client.player.getId();seals=cloneAge<.98?CosmeticGeometry.smooth((float)(cloneAge/.98)):1-CosmeticGeometry.smooth((float)((cloneAge-1.33)/.52));}
        float partial=client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Surface> cores=new ArrayList<>(),rings=new ArrayList<>(),lights=new ArrayList<>();
        List<Quad> ribbons=new ArrayList<>(),ribbonGlow=new ArrayList<>();List<Sprite> bloom=new ArrayList<>(),smoke=new ArrayList<>();
        List<Model> models=new ArrayList<>();List<Bird> birds=new ArrayList<>();Set<Long> usedPaths=new HashSet<>();int count=0;
        for(Player player:client.level.players())if(allowed(client,player,c)&&count++<24){
            if(player==client.player&&client.options.getCameraType().isFirstPerson()&&!c.firstPerson)continue;
            Vec3 pos=player.getPosition(partial);double yaw=Math.toRadians(net.minecraft.util.Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot));
            int quality=player.distanceToSqr(client.player)>1024?0:c.quality;
            if(c.halo){
                double bob=config.animations&&c.haloAnimate?Math.sin(animation*2.8)*Math.min(.12,c.haloThickness*.8):0;
                Vec3 center=pos.add(0,player.getBbHeight()+.12+c.haloHeight+bob,0);
                int color=c.themeColors?config.accent():c.haloColor;float tube=Math.max(.01f,c.haloThickness*.5f);var mesh=CosmeticGeometry.torus(quality);
                rings.add(new Surface(mesh,center,c.haloRadius,tube,color,true));
                float[] widths={1.55f,2.35f,3.25f},alphas={.34f,.16f,.08f};int passes=quality==0?1:quality==1?2:3;
                for(int pass=0;pass<passes;pass++)lights.add(new Surface(mesh,center,c.haloRadius,tube*widths[pass],UiStyle.alpha(color,alphas[pass]),true));
            }
            if(c.spheres)for(int i=0;i<(c.sphereMode==3?6:3);i++){
                int orbIndex=i%3;var orb=CosmeticGeometry.orb(pos,player.getBbHeight(),yaw+(i>=3?Math.PI:0),animation+(i>=3?.6:0),orbIndex,c);
                int color=UiStyle.alpha(c.sphereColor(orbIndex,config.accent()),orb.opacity());
                if(orb.opacity()>.0025&&(color>>>24)>0){
                    ((color>>>24)<16?rings:cores).add(new Surface(player.distanceToSqr(client.player)>576?CosmeticGeometry.DISTANT_SPHERE:CosmeticGeometry.SPHERE,orb.position(),0,c.sphereSize,color,false));
                    bloom.add(new Sprite(orb.position(),c.sphereSize*6,UiStyle.alpha(color,.62f*c.glow),0));
                }
                if(c.sphereEffect!=0){
                    long id=((long)player.getId()<<4)|i;usedPaths.add(id);
                    List<Sample> samples=paths.computeIfAbsent(id,key->new History()).update(orb.position(),time,1/60.0,c.sphereTrailPoints,false);
                    for(int k=0;k<samples.size();k++){
                        float fade=(k+1f)/samples.size();fade*=fade;int trailColor=c.sphereEffect==2?UiStyle.mix(color,0xFFFFC752,.22f+.78f*fade):color;
                        bloom.add(new Sprite(samples.get(k).position(),c.sphereSize*4.8f*(.55f+.70f*fade),UiStyle.alpha(trailColor,.60f*fade*c.glow*(c.sphereEffect==2?1.18f:1)),0));
                    }
                }
            }
            if(c.trail){
                Vec3 raw=pos.add(Math.sin(yaw)*.34,player.getBbHeight()*.62,-Math.cos(yaw)*.34);long id=((long)player.getId()<<4)|15;usedPaths.add(id);
                History path=paths.computeIfAbsent(id,key->new History());List<Sample> samples=path.update(raw,time,c.trailStepMs/1000.0,c.trailPoints,true);
                List<Vec3> smooth=spline(samples);
                int colorA=c.themeColors?config.accent():c.trailColor,colorB=c.themeColors?UiStyle.mix(config.accent(),0xFFFFFFFF,.4f):c.trailColorB;
                float entrance=(float)Math.min(1,Math.max(0,(time-path.born)/.24));entrance*=entrance;
                ribbon(ribbons,smooth,c.trailWidth,c.trailOpacity*.9f*entrance,c.trailGradient,colorA,colorB);
                ribbon(ribbonGlow,smooth,c.trailWidth*1.55f,c.trailOpacity*.55f*.85f*entrance,c.trailGradient,colorA,colorB);
            }
        }
        paths.keySet().retainAll(usedPaths);
        Vec3 own=client.player.getPosition(partial);double ownYaw=Math.toRadians(client.player.yBodyRot);
        if(c.pet&&pet!=null&&(c.firstPerson||!client.options.getCameraType().isFirstPerson())){
            double angle=animation*c.petSpeed;boolean hold=client.player.isSprinting()||client.player.getDeltaMovement().horizontalDistance()>.12;
            boolean walker=c.petType==6||c.petType==7||c.petType==8||c.petType==10;
            Vec3 target=hold?own.add(-Math.cos(ownYaw)*.48-Math.sin(ownYaw)*.12,client.player.getEyeHeight()*.82+c.petHeight-.95, -Math.sin(ownYaw)*.48+Math.cos(ownYaw)*.12):own.add(Math.cos(angle)*c.petRadius,c.petHeight+Math.sin(animation*2.8)*.06,Math.sin(angle)*c.petRadius);
            if(walker){
                var hit=client.level.clip(new ClipContext(target.add(0,2,0),target.add(0,-4,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));
                if(hit.getType()==HitResult.Type.BLOCK)target=new Vec3(target.x,hit.getLocation().y+.015+((c.petType==8||c.petType==10)?Math.max(0,Math.sin(animation*4))*.18:0),target.z);
            }
            Vec3 old=petPosition;petPosition=petPosition==null||petPosition.distanceToSqr(target)>64?target:petPosition.lerp(target,config.animations?1-Math.exp(-dt/.12):1);
            Vec3 velocity=old==null?Vec3.ZERO:petPosition.subtract(old);float wanted=velocity.horizontalDistanceSqr()>1e-8?(float)Math.toDegrees(Math.atan2(-velocity.x,velocity.z)):client.player.getYRot();
            petYaw=net.minecraft.util.Mth.rotLerp((float)(1-Math.exp(-dt/.16)),petYaw,wanted);
            pet.setPos(petPosition);pet.setYRot(petYaw);pet.setXRot(0);pet.setOnGround(walker);pet.setOldPosAndRot();
            if(pet instanceof LivingEntity living){living.yBodyRot=petYaw;living.yHeadRot=petYaw;}
            if(c.petType==2)birds.add(bird(petPosition,petYaw,c.petScale*1.28f,animation*1.15,0xFFFFFFFF));
            else{
                var state=client.getEntityRenderDispatcher().extractEntity(pet,0);state.nameTag=null;state.shadowRadius=0;state.shadowPieces.clear();state.lightCoords=LightTexture.FULL_BRIGHT;
                if(state instanceof LivingEntityRenderState living){living.bodyRot=petYaw;living.walkAnimationPos=(float)animation*6;living.walkAnimationSpeed=walker?(float)Math.min(.8,velocity.horizontalDistance()/Math.max(.001,dt)*1.2):0;}
                if(state instanceof ParrotRenderState parrot){parrot.pose=ParrotModel.Pose.FLYING;parrot.flapAngle=(float)(1+Math.sin(animation*5.25))*1.1f;}
                models.add(new Model(state,petPosition,c.petScale));
            }
        }
        Map<Integer,Integer> cloneTints=new HashMap<>();
        if(cloneAge>=0){
            float alpha=CosmeticGeometry.cloneAlpha(cloneAge);
            for(int i=0;i<clones.size();i++){
                double angle=i*Math.PI/2+ownYaw,radius=1.15+(i%2)*.22;Vec3 pos=own.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
                if(alpha>.005f){
                    var clone=clones.get(i);clone.setPos(pos);clone.setYRot((float)Math.toDegrees(angle)+180);clone.setXRot(0);clone.yBodyRot=clone.yHeadRot=clone.getYRot();clone.tickCount=client.player.tickCount;clone.setOldPosAndRot();
                    var state=client.getEntityRenderDispatcher().extractEntity(clone,0);state.nameTag=null;state.shadowRadius=0;state.shadowPieces.clear();if(state instanceof AvatarRenderState avatar)avatar.showCape=false;
                    models.add(new Model(state,pos,1));cloneTints.put(clone.getId(),UiStyle.alpha(0xFFFFFFFF,alpha*.95f));
                }
                if(cloneAge>=.98&&cloneAge<1.8)smokeColumn(smoke,pos,cloneAge-.98, .82, i*29+7);
                if(cloneAge>=2.84&&cloneAge<3.65)smokeColumn(smoke,pos,cloneAge-2.84,.81,i*29+71);
            }
            if(cloneAge>=.98&&cloneAge<1.7)smokeColumn(smoke,own,cloneAge-.98,.72,13);
        }
        cloneColors=Map.copyOf(cloneTints);
        double ravenAge=ravenAt==0?-1:(now-ravenAt)/1_000_000_000.0;
        if(ravenAge>=0&&ravenAge<17.6){
            double disperse=Math.max(0,(ravenAge-11.8)/5.8);float alpha=1-CosmeticGeometry.smooth((float)((ravenAge-14.6)/3));
            for(int i=0;i<42;i++){
                double phase=i*Math.PI*2/42+animation*(.85+(i%5)*.18),radius=.48+(i%4)*.13+disperse*5;
                double y=.15+(i*.618033%1)*1.65+Math.sin(phase*1.7)*.12+disperse*(i%3)*.8;
                Vec3 pos=own.add(Math.cos(phase)*radius,y,Math.sin(phase)*radius);
                birds.add(bird(pos,(float)Math.toDegrees(-phase),c.petScale*(.8f+(i%4)*.08f),animation*(1.75+(i%3)*.3)+i,UiStyle.alpha(0xFFFFFFFF,alpha*.83f)));
                if(i%3==0)smoke.add(new Sprite(pos,.5f,UiStyle.alpha(0xFF302D38,.22f*alpha),(float)phase));
            }
        }
        frame=new Frame(List.copyOf(cores),List.copyOf(rings),List.copyOf(lights),List.copyOf(ribbons),List.copyOf(ribbonGlow),List.copyOf(bloom),List.copyOf(smoke),List.copyOf(models),List.copyOf(birds));
    }
    private Bird bird(Vec3 position,float yaw,float scale,double phase,int color){
        ParrotRenderState state=new ParrotRenderState();state.pose=ParrotModel.Pose.FLYING;state.variant=Parrot.Variant.GRAY;state.ageInTicks=(float)phase*20;state.flapAngle=(float)(1+Math.sin(phase))*1.2f;state.lightCoords=LightTexture.FULL_BRIGHT;
        return new Bird(state,position,yaw,scale,color);
    }
    private static void smokeColumn(List<Sprite> sprites,Vec3 origin,double age,double duration,int seed){
        float life=(float)Math.max(0,Math.min(1,age/duration));float opacity=(1-life)*(1-life)*Math.min(1,life/.12f);
        for(int i=0;i<18;i++){
            double angle=(i*2.399963+seed),radius=.18+(i%5)*.10+life*.23;
            Vec3 pos=origin.add(Math.cos(angle)*radius,.15+(i%6)*.27+life*.35,Math.sin(angle)*radius);
            sprites.add(new Sprite(pos,.45f+(i%4)*.15f+life*.35f,UiStyle.alpha(0xFFE7E8EE,opacity*.75f),(float)(angle+life*.35)));
        }
    }
    private void render(WorldRenderContext context){
        Frame captured=frame;Vec3 camera=context.worldState().cameraRenderState.pos;var matrices=context.matrices();
        surfaces(context,captured.cores(),CosmeticLayers.CORE,camera,0);surfaces(context,captured.rings(),CosmeticLayers.SURFACE,camera,0);
        quads(context,captured.trails(),CosmeticLayers.SURFACE,camera,0);
        for(Model model:captured.models()){
            matrices.pushPose();try{
                Vec3 pos=model.position().subtract(camera);matrices.translate(pos.x,pos.y,pos.z);matrices.scale(model.scale(),model.scale(),model.scale());
                Minecraft.getInstance().getEntityRenderDispatcher().submit(model.state(),context.worldState().cameraRenderState,0,0,0,matrices,context.commandQueue());
            }finally{matrices.popPose();}
        }
        if(!captured.birds().isEmpty()){
            if(ravenModel==null){var root=Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PARROT);root.getChild("head").getChild("feather").visible=false;ravenModel=new ParrotModel(root);}
            for(Bird bird:captured.birds()){
                matrices.pushPose();try{
                    Vec3 pos=bird.position().subtract(camera);matrices.translate(pos.x,pos.y,pos.z);matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-bird.yaw()));
                    matrices.scale(-bird.scale(),-bird.scale(),bird.scale());matrices.translate(0,-1.501,0);
                    context.commandQueue().submitModel(ravenModel,bird.state(),matrices,RenderTypes.entityTranslucent(Identifier.fromNamespaceAndPath("hwhelper","textures/world/raven.png")),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,bird.color(),null,0,null);
                }finally{matrices.popPose();}
            }
        }
        surfaces(context,captured.lights(),CosmeticLayers.LIGHT,camera,1);quads(context,captured.trailGlow(),CosmeticLayers.LIGHT,camera,1);
        sprites(context,captured.bloom(),CosmeticLayers.BLOOM,camera,1);sprites(context,captured.smoke(),CosmeticLayers.SMOKE,camera,2);
    }
    private static void surfaces(WorldRenderContext context,List<Surface> surfaces,net.minecraft.client.renderer.rendertype.RenderType layer,Vec3 camera,int order){
        if(surfaces.isEmpty())return;
        context.commandQueue().order(order).submitCustomGeometry(context.matrices(),layer,(pose,v)->{
            for(Surface surface:surfaces){float[] centers=surface.mesh().centers(),normals=surface.mesh().normals();Vec3 view=camera.subtract(surface.center()).normalize(),pos=surface.center().subtract(camera);
                for(int k=0;k<normals.length;k+=3){
                    float nx=normals[k],ny=normals[k+1],nz=normals[k+2];int color=CosmeticGeometry.shade(surface.color(),nx,ny,nz,view,surface.halo());
                    v.addVertex(pose,(float)(pos.x+centers[k]*surface.major()+nx*surface.minor()),(float)(pos.y+centers[k+1]*surface.major()+ny*surface.minor()),(float)(pos.z+centers[k+2]*surface.major()+nz*surface.minor())).setColor(color).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose,nx,ny,nz);
                }
            }
        });
    }
    private static void quads(WorldRenderContext context,List<Quad> quads,net.minecraft.client.renderer.rendertype.RenderType layer,Vec3 camera,int order){
        if(quads.isEmpty())return;
        context.commandQueue().order(order).submitCustomGeometry(context.matrices(),layer,(pose,v)->{for(Quad q:quads){vertex(v,pose,q.a(),camera,q.ca(),0,0);vertex(v,pose,q.b(),camera,q.cb(),0,1);vertex(v,pose,q.c(),camera,q.cc(),1,1);vertex(v,pose,q.d(),camera,q.cd(),1,0);}});
    }
    private static void sprites(WorldRenderContext context,List<Sprite> sprites,net.minecraft.client.renderer.rendertype.RenderType layer,Vec3 camera,int order){
        if(sprites.isEmpty())return;
        var rotation=context.worldState().cameraRenderState.orientation;
        Vec3 right=new Vec3(new org.joml.Vector3f(1,0,0).rotate(rotation)),up=new Vec3(new org.joml.Vector3f(0,1,0).rotate(rotation));
        context.commandQueue().order(order).submitCustomGeometry(context.matrices(),layer,(pose,v)->{
            for(Sprite sprite:sprites){
                Vec3 r=right.scale(Math.cos(sprite.rotation())).add(up.scale(Math.sin(sprite.rotation()))).scale(sprite.size()*.5),u=up.scale(Math.cos(sprite.rotation())).subtract(right.scale(Math.sin(sprite.rotation()))).scale(sprite.size()*.5),p=sprite.position();
                vertex(v,pose,p.subtract(r).add(u),camera,sprite.color(),0,0);vertex(v,pose,p.subtract(r).subtract(u),camera,sprite.color(),0,1);vertex(v,pose,p.add(r).subtract(u),camera,sprite.color(),1,1);vertex(v,pose,p.add(r).add(u),camera,sprite.color(),1,0);
            }
        });
    }
    private static void vertex(VertexConsumer v,PoseStack.Pose matrix,Vec3 p,Vec3 camera,int color,float u,float vv){v.addVertex(matrix,(float)(p.x-camera.x),(float)(p.y-camera.y),(float)(p.z-camera.z)).setColor(color).setUv(u,vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(matrix,0,1,0);}
    private static List<Vec3> spline(List<Sample> samples){
        if(samples.size()<2)return List.of();List<Vec3> result=new ArrayList<>();
        for(int i=0;i<samples.size()-1;i++){
            Vec3 a=samples.get(Math.max(0,i-1)).position(),b=samples.get(i).position(),c=samples.get(i+1).position(),d=samples.get(Math.min(samples.size()-1,i+2)).position();
            int steps=Math.max(3,Math.min(9,(int)Math.ceil(b.distanceTo(c)/.06)));if(i==0)result.add(b);
            for(int j=1;j<=steps;j++)result.add(CosmeticGeometry.catmull(a,b,c,d,j/(double)steps));
        }return result;
    }
    private static void ribbon(List<Quad> output,List<Vec3> points,float width,float opacity,boolean gradient,int colorA,int colorB){
        for(int i=1;i<points.size();i++){
            float t0=(i-1f)/(points.size()-1),t1=i/(points.size()-1f),f0=t0*t0,f1=t1*t1;
            int c0=UiStyle.alpha(gradient?UiStyle.mix(colorB,colorA,t0):colorA,opacity*f0),c1=UiStyle.alpha(gradient?UiStyle.mix(colorB,colorA,t1):colorA,opacity*f1);
            double w0=width*.5*(.55+.75*f0),w1=width*.5*(.55+.75*f1);Vec3 a=points.get(i-1),b=points.get(i);
            output.add(new Quad(a.add(0,w0,0),a.add(0,-w0,0),b.add(0,-w1,0),b.add(0,w1,0),c0,c0,c1,c1));
        }
    }
    private void reset(){world=null;pet=null;petType=-1;petPosition=null;clones.clear();clonesAt=ravenAt=lastExtract=0;paths.clear();runningId=actingId=Integer.MIN_VALUE;cloneColors=Map.of();frame=Frame.empty();}
}
