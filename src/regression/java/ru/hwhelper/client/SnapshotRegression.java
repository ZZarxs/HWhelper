package ru.hwhelper.client;
public final class SnapshotRegression {
    public static void main(String[] args) throws Exception {
        var mines = MineSnapshotClient.parse("Энд - Легендарная Шахта Ада и Энда: СолоЛайт #7 13 сек., КланЛайт #90 211 сек.\nАд - Эпическая Шахта Ада и Энда: ТриоЛайт #22 18 сек.\nЛегендарная Шахта Верхнего мира: ДуоЛайт #12 18 сек.");
        require(mines.size()==4,"inline entries");
        require(mines.get(0).type().equals("Энд"),"end category");
        require(mines.get(2).type().equals("Ад"),"nether category must not match End in shared heading");
        require(mines.get(3).type().equals("Обычные"),"overworld category");
        require(mines.get(0).rarity().equals("Легендарная"),"legendary rarity");
        require(mines.get(2).rarity().equals("Эпическая"),"epic rarity");
        var old = MineSnapshotClient.parse("Редкая Шахта:\nСолоЛайт #1 120 сек.");
        require(old.getFirst().rarity().equals("Редкая"),"multiline headings");
        require(new Mine("Шахты","ДуоЛайт",1,3).rarity().equals("Не указана"),"unknown rarity");
        var candidates = MineSnapshotClient.parse("Легендарная Шахта Верхнего мира: КланЛайт #88 8 сек., КланЛайт #89 9 сек., КланЛайт #90 1 сек., СолоЛайт #90 120 сек., КланЛайт #34 300 сек., КланЛайт #36 90 сек., ДуоЛайт #2 0 сек.");
        var selected = HWhelperClient.selectUpcoming(candidates, 1, 10, type -> true);
        require(selected.size()==3, "exclude ClanLight 88, 89, 90 and expired entries");
        require(selected.get(0).mine().number()==36, "nearest first");
        require(selected.get(1).mine().anarchy().equals("СолоЛайт"), "other anarchy 90 remains");
        require(selected.get(2).secondsLeft()==299, "multiple mines beyond 90 seconds remain");
        require(HWhelperClient.selectUpcoming(candidates, 1, 1, type -> true).size()==1, "configured count");
        require(HWhelperClient.selectUpcoming(candidates, 1, 10, type -> false).isEmpty(), "category filter");
        var otherNumbers = MineSnapshotClient.parse("Обычная Шахта: КланЛайт #87 120 сек., КланЛайт #91 130 сек., СолоЛайт #88 140 сек., ДуоЛайт #89 150 сек.");
        require(HWhelperClient.selectUpcoming(otherNumbers,0,10,type->true).size()==4, "keep neighboring ClanLight numbers and other anarchies 88/89");
        require(KeyAlert.matchesName("§6Универсальный ключ"), "formatted key name");
        require(KeyAlert.matchesName("  УНИВЕРСАЛЬНЫЙ  КЛЮЧ  "), "normalized key name");
        require(!KeyAlert.matchesName("Ключ от сундука"), "ignore unrelated keys");
        var glassFlag=new java.util.concurrent.atomic.AtomicBoolean(false);
        UiStyle.configure(glassFlag::get,()->0xFF69E6D2);
        require(!UiStyle.liquid(),"shared matte style");
        glassFlag.set(true);
        require(UiStyle.liquid(),"shared glass switch applies immediately");
        glassFlag.set(false);
        require(!UiStyle.liquid(),"shared glass switch can turn off again");
        String example;
        try(var stream=SnapshotRegression.class.getResourceAsStream("/snapshot-newbot.txt")) {
            example=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF","");
        }
        var newFormat=MineSnapshotClient.parse(example);
        require(newFormat.size()==80,"all 80 entries in the new bot example");
        require(newFormat.getFirst().seconds()==119,"parenthesized seconds");
        require(newFormat.getFirst().rarity().equals("Обычная"),"separate rarity heading");
        require(newFormat.stream().anyMatch(m->m.anarchy().equals("Лайт (1.20)")&&m.seconds()==450),"versioned Light entries");
        require(newFormat.stream().anyMatch(m->m.type().equals("Энд")&&m.rarity().equals("Легендарная")&&m.number()==31),"End legendary section");
        require(newFormat.stream().anyMatch(m->m.type().equals("Ад")&&m.rarity().equals("Эпическая")&&m.seconds()==199),"Nether epic section");
        var obj=new com.google.gson.JsonObject();obj.addProperty("text",example);obj.addProperty("updatedAt","2026-11-10T10:00:00+00:00");
        var aged=MineSnapshotClient.parseResponse(obj.toString(),java.time.Instant.parse("2026-11-10T10:00:10Z").toEpochMilli());
        require(aged.getFirst().seconds()==109,"re-reading an unchanged bot message does not reset timers");
        var rarityMines=new java.util.ArrayList<Mine>();int index=1;
        for(String prefix:java.util.List.of("","Ад - ","Энд - "))
            for(String rarity:java.util.List.of("Обычная","Редкая","Эпическая","Легендарная","Не указана"))
                rarityMines.add(new Mine(prefix+rarity,"СолоЛайт",index,100+index++));
        var legendaryOnly=HWhelperClient.selectUpcoming(rarityMines,0,40,t->true,r->r.equals("Легендарная"));
        require(legendaryOnly.size()==3,"rarity filter covers all dimensions");
        require(legendaryOnly.stream().map(e->e.mine().type()).distinct().count()==3,"all three dimensions retained");
        require(HWhelperClient.selectUpcoming(rarityMines,0,40,t->true,r->false).isEmpty(),"all rarities disabled");
        require(HWhelperClient.selectUpcoming(rarityMines,0,40,t->true,r->true).size()==15,"all rarities enabled");
        require(HWhelperClient.selectUpcoming(rarityMines,0,40,t->t.equals("Ад"),r->r.equals("Редкая")).size()==1,"category and rarity filters combine");
        require(HWhelperClient.selectUpcoming(rarityMines,0,1,t->true,r->r.equals("Легендарная")).getFirst().mine().number()==4,"rarity applied before list limit");
        require(HWhelperClient.selectUpcoming(rarityMines,0,40,t->true,r->r.equals("Не указана")).size()==3,"unknown rarity filter");
        require(!HWhelperClient.matchesFilters(new Mine("Энд - Эпическая","СолоЛайт",7,-5),t->true,r->r.equals("Легендарная")),"recently updated mines obey same rarity filter");
        var world=RarityOptions.fromLegacy(false,false,false,true,false);
        var nether=RarityOptions.fromLegacy(false,false,true,false,false);
        var end=RarityOptions.fromLegacy(true,false,false,false,false);
        java.util.function.Predicate<Mine> perDimension=m->switch(m.type()) {
            case "Ад" -> nether.allows(m.rarity());
            case "Энд" -> end.allows(m.rarity());
            default -> world.allows(m.rarity());
        };
        var dimensionSelected=HWhelperClient.selectUpcomingByMine(rarityMines,0,40,t->true,perDimension);
        require(dimensionSelected.size()==3,"independent dimension rarity sets");
        require(dimensionSelected.stream().anyMatch(e->e.mine().type().equals("Обычные")&&e.mine().rarity().equals("Легендарная")),"world legendary only");
        require(dimensionSelected.stream().anyMatch(e->e.mine().type().equals("Ад")&&e.mine().rarity().equals("Эпическая")),"nether epic only");
        require(dimensionSelected.stream().anyMatch(e->e.mine().type().equals("Энд")&&e.mine().rarity().equals("Обычная")),"end common only");
        world.legendary=false;
        require(HWhelperClient.selectUpcomingByMine(rarityMines,0,40,t->true,perDimension).size()==2,"disabling world rarity leaves other dimensions unchanged");
        require(!HWhelperClient.matchesMineFilters(new Mine("Ад - Легендарная","СолоЛайт",7,-5),t->true,perDimension),"recent events use dimension-specific rarity rules");
        var copy=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(nether),RarityOptions.class);
        require(copy.epic&&!copy.common&&!copy.legendary,"dimension settings serialization");
        var migratedA=RarityOptions.fromLegacy(false,true,false,true,true);
        var migratedB=RarityOptions.fromLegacy(false,true,false,true,true);
        migratedA.legendary=false;
        require(migratedB.legendary,"migration creates independent settings");
        require(DynamicIslandHud.targetWidth(20,30,250,true,true)==140,"island minimum width");
        require(DynamicIslandHud.targetWidth(1000,30,250,true,true)==250,"island max width");
        require(DynamicIslandHud.targetWidth(150,30,300,true,true)>DynamicIslandHud.targetWidth(150,30,300,false,false),"island element spacing");
        require(DynamicIslandHud.lyricProgress(12,10,14)==.5,"island karaoke timing");
        require(DynamicIslandHud.lyricProgress(9,10,14)==0,"island karaoke lower bound");
        require(DynamicIslandHud.lyricProgress(16,10,14)==1,"island karaoke upper bound");
        var tween=new UiTransition(0,200);
        require(tween.update(1,1_000_000_000L,true)==0,"opening animation starts at zero");
        float halfway=tween.update(1,1_100_000_000L,true);
        require(halfway>.5f&&halfway<1,"cubic opening easing");
        require(tween.update(1,1_200_000_000L,true)==1,"opening reaches target");
        tween.update(0,1_300_000_000L,true);
        require(tween.update(0,1_500_000_000L,true)==0,"closing animation completes");
        require(tween.update(1,1_600_000_000L,false)==1,"disabled animations apply immediately");
        for(int[] viewport:new int[][]{{320,240},{426,240},{480,270},{640,360},{960,540}}){
            var layout=CsMenuLayout.create(viewport[0],viewport[1],false);
            require(layout.x()>=0&&layout.y()>=0,"card menu stays on screen");
            require(layout.x()+layout.w()<=viewport[0]&&layout.y()+layout.h()<=viewport[1],"card menu right/bottom bounds");
            require(layout.bodyBottom()-layout.bodyTop()>=68,"a collapsed card fits the scroll viewport");
            require(layout.cardWidth()>=170,"card controls remain readable");
            require(layout.columns()*layout.cardWidth()+(layout.columns()-1)*8<=layout.w()-24,"card columns fit");
        }
        require(CsMenuLayout.create(320,240,false).columns()==1,"narrow screen uses a single card column");
        require(CsMenuLayout.create(640,360,false).columns()==3,"wide screen uses the reference card grid");
        require(CsMenuLayout.create(640,360,false,2).columns()==2,"small categories do not leave empty columns");
        require(CsMenuLayout.create(640,360,false,1).columns()==1,"single module can use full settings width");
        var split=LyricTimeline.split(java.util.List.of(new LyricTimeline.Line(10,11,"one two three four five six seven")));
        require(split.size()==3,"smart lyrics split into rhythmic chunks");
        require(split.getFirst().start()==10&&split.getLast().end()==11,"smart split preserves short original timing");
        for(int i=1;i<split.size();i++)require(split.get(i-1).end()<=split.get(i).start(),"lyrics chunks do not overlap");
        var media=com.google.gson.JsonParser.parseString("{\"duration\":20,\"lyrics\":[{\"time\":1,\"text\":\"hello\"},{\"time\":5,\"text\":\"\"},{\"time\":7,\"text\":\"world\"}]}").getAsJsonObject();
        var timeline=LyricTimeline.read(media,false);
        require(timeline.size()==2&&timeline.getFirst().end()==5,"empty LRC cues leave an instrumental gap");
        require(GoldenSpawnerInfo.roman(14).equals("XIV"),"spawner enchantment numeral");
        require(GoldenSpawnerInfo.number(-1).equals("нет данных"),"spawner missing counter stays explicit");
        require(GoldenSpawnerInfo.swordLines("not json").size()==2,"malformed server sword data does not break tooltip");
        var options=new CosmeticOptions();options.haloRadius=Float.NaN;options.quality=99;options.sanitize();
        require(Float.isFinite(options.haloRadius)&&options.quality<4,"invalid cosmetic settings are sanitized");
        options.referenceStyle();
        var sphere=CosmeticGeometry.SPHERE;
        require(sphere.vertices()==18*28*4,"reference sphere resolution");
        for(int i=0;i<sphere.normals().length;i+=3){double norm=sphere.normals()[i]*sphere.normals()[i]+sphere.normals()[i+1]*sphere.normals()[i+1]+sphere.normals()[i+2]*sphere.normals()[i+2];require(Math.abs(norm-1)<.00001,"sphere normals remain unit length");}
        require(CosmeticGeometry.torus(1).vertices()==96*24*4,"reference torus resolution");
        var orb=CosmeticGeometry.orb(net.minecraft.world.phys.Vec3.ZERO,1.8,0,0,0,options);
        require(Math.abs(orb.position().x+.42)<.00001&&Math.abs(orb.position().y-.95)<.00001,"reference static sphere placement");
        var a=new net.minecraft.world.phys.Vec3(0,1,2);var b=new net.minecraft.world.phys.Vec3(1,2,3);var c0=new net.minecraft.world.phys.Vec3(4,5,6);var d=new net.minecraft.world.phys.Vec3(8,9,10);
        require(CosmeticGeometry.catmull(a,b,c0,d,0).distanceToSqr(b)<1e-12&&CosmeticGeometry.catmull(a,b,c0,d,1).distanceToSqr(c0)<1e-12,"ribbon spline preserves endpoints");
        require(CosmeticGeometry.cloneAlpha(1)==0&&CosmeticGeometry.cloneAlpha(1.5)>.99f&&CosmeticGeometry.cloneAlpha(3.5)==0,"clone scene follows reference smoke/appear/disperse stages");
        require(CosmeticColorScreen.parse("#C8FFC864")==0xC8FFC864&&CosmeticColorScreen.parse("FFFFFF")==0xFFFFFFFF,"cosmetic color editor preserves alpha");
        var parrot=net.minecraft.client.model.animal.parrot.ParrotModel.createBodyLayer().bakeRoot();
        require(parrot.getChild("head").hasChild("feather"),"native raven model uses the reference parrot head topology");
        require(!CosmeticLayers.BLOOM.pipeline().isWriteDepth()&&!CosmeticLayers.BLOOM.pipeline().isWriteAlpha(),"bloom does not change world depth or framebuffer alpha");
        require(CosmeticLayers.BLOOM.pipeline().getBlendFunction().orElseThrow().destColor()==com.mojang.blaze3d.platform.DestFactor.ONE,"reference additive bloom blend");
        require(CosmeticLayers.CORE.pipeline().isWriteDepth(),"sphere cores retain normal occlusion");
        require(net.minecraft.client.renderer.RenderPipelines.getStaticPipelines().contains(CosmeticLayers.BLOOM.pipeline()),"cosmetic shaders participate in native resource reload/precompilation");
        var lyricOptions=new WorldLyricOptions();lyricOptions.minDistance=4;lyricOptions.maxDistance=2;lyricOptions.sanitize();
        require(lyricOptions.maxDistance>lyricOptions.minDistance,"world lyrics maintain a valid distance range");
        var badSound=java.nio.file.Files.createTempFile("hwhelper-sound-",".ogg");
        try{
            java.nio.file.Files.writeString(badSound,"not an ogg");
            boolean rejected=false;try{CustomKeySound.validate(badSound);}catch(java.io.IOException expected){rejected=true;}
            require(rejected,"custom sound import rejects renamed non-OGG files");
        }finally{java.nio.file.Files.deleteIfExists(badSound);}
        System.out.println("Snapshot regression checks passed");
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
