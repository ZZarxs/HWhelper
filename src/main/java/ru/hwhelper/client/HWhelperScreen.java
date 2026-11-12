package ru.hwhelper.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import java.util.*;
import java.util.function.*;

/** CsClickGui card layout, adapted to native Fabric GUI rendering and live HWhelper settings. */
public final class HWhelperScreen extends Screen {
    private final HwConfig c;private final HWhelperClient hud;
    private final List<Group> groups=new ArrayList<>();
    private final List<Hit> hits=new ArrayList<>();
    private final List<Control> controls=new ArrayList<>();
    private final CsBackdrop backdrop=new CsBackdrop();
    private final UiTransition categoryFade=new UiTransition(1,200);
    private final UiTransition tabLeft=new UiTransition(0,250),tabRight=new UiTransition(0,170);
    private static int rememberedCategory;
    private int categoryIndex=rememberedCategory;
    private boolean favoritesOnly,activeFirst,compactColumns;
    private float scroll,scrollTarget,maxScroll;
    private long lastFrame;
    private CsMenuLayout layout;
    private record Control(int x,int y,int w,int h,int button,Runnable action) {
        boolean contains(double mx,double my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    }
    private final UiTransition opening=new UiTransition(0,260);
    private boolean closing;private String query="";private EditBox search;private Option dragging;
    private int dragX,dragWidth,rarityDimension;
    private static final int MODULE_H=68,OPTION_H=26;
    private static final class Group {
        final String name,icon;final List<Module> modules=new ArrayList<>();
        Group(String name,String icon){this.name=name;this.icon=icon;}
    }
    private static final class Module {
        final String name;final BooleanSupplier enabled;final Runnable toggle;final List<Option> options=new ArrayList<>();
        boolean expanded;final UiTransition expand=new UiTransition(0,240),state=new UiTransition(0,180),hover=new UiTransition(0,140);
        Module(String name,BooleanSupplier enabled,Runnable toggle){this.name=name;this.enabled=enabled;this.toggle=toggle;state.reset(enabled!=null&&enabled.getAsBoolean()?1:0);}
    }
    private static final class Option {
        final String name;BooleanSupplier state;Runnable change;DoubleSupplier number;DoubleConsumer set;
        Supplier<String> value;double min,max,step;
        final UiTransition animation=new UiTransition(0,180);
        Option(String name){this.name=name;}
        int height(){return number!=null?35:OPTION_H;}
    }
    private record Hit(int x,int y,int w,int h,Module module,Option option) {
        boolean contains(double mx,double my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    }
    public HWhelperScreen(HwConfig c,HWhelperClient hud){super(Component.literal("HWhelper"));this.c=c;this.hud=hud;createModules();tabLeft.reset(categoryIndex);tabRight.reset(categoryIndex);}
    private Group group(String name,String icon){Group g=new Group(name,icon);groups.add(g);return g;}
    private Module module(Group g,String name,BooleanSupplier enabled,Runnable toggle){Module m=new Module(name,enabled,toggle);g.modules.add(m);return m;}
    private void bool(Module m,String name,BooleanSupplier state,Runnable change){Option o=new Option(name);o.state=state;o.change=change;o.animation.reset(state.getAsBoolean()?1:0);m.options.add(o);}
    private void number(Module m,String name,DoubleSupplier value,DoubleConsumer set,double min,double max,double step){Option o=new Option(name);o.number=value;o.set=set;o.min=min;o.max=max;o.step=step;o.animation.reset((float)((value.getAsDouble()-min)/(max-min)));m.options.add(o);}
    private void action(Module m,String name,Runnable run){Option o=new Option(name);o.change=run;m.options.add(o);}
    private void mode(Module m,String name,Supplier<String> value,Runnable change){Option o=new Option(name);o.value=value;o.change=change;m.options.add(o);}
    private void color(Module m,String name,IntSupplier value,IntConsumer set){mode(m,name,()->String.format("#%08X",value.getAsInt()),()->minecraft.setScreen(new CosmeticColorScreen(c,this,name,value.getAsInt(),set)));}
    private void createModules(){
        Group mines=group("Шахты","mines"),media=group("Медиа","media"),look=group("Оформление","style"),alerts=group("Уведомления","alerts"),cosmetic=group("Косметика","style"),connection=group("Связь","media");
        Module telegram=module(connection,"Telegram",()->hud.telegram().active(),()->{if(hud.telegram().active()){c.telegramAutoConnect=false;hud.telegram().disconnect();}else if(hud.telegram().credentialsSaved()){c.telegramAutoConnect=true;hud.telegram().connectSaved();}else minecraft.setScreen(new TelegramSettingsScreen(c,hud,this));});
        bool(telegram,"Подключать при запуске",()->c.telegramAutoConnect,()->c.telegramAutoConnect=!c.telegramAutoConnect);
        action(telegram,"Настроить Telegram →",()->minecraft.setScreen(new TelegramSettingsScreen(c,hud,this)));
        Module navigation=module(connection,"Быстрый переход",()->c.anarchy.enabled,()->c.anarchy.enabled=!c.anarchy.enabled);
        action(navigation,"Выбрать анархию →",()->minecraft.setScreen(new AnarchySwitchScreen(c,hud.switcher(),this)));
        action(navigation,"HubSwap: диапазоны и алиасы →",()->minecraft.setScreen(new HubSwapSettingsScreen(c,hud.switcher(),this)));
        bool(navigation,"Только избранные",()->c.anarchy.favoritesOnly,()->c.anarchy.favoritesOnly=!c.anarchy.favoritesOnly);
        bool(navigation,"СолоЛайт",()->c.anarchy.solo,()->c.anarchy.solo=!c.anarchy.solo);
        bool(navigation,"ДуоЛайт",()->c.anarchy.duo,()->c.anarchy.duo=!c.anarchy.duo);
        bool(navigation,"ТриоЛайт",()->c.anarchy.trio,()->c.anarchy.trio=!c.anarchy.trio);
        bool(navigation,"КланЛайт",()->c.anarchy.clan,()->c.anarchy.clan=!c.anarchy.clan);
        bool(navigation,"По кругу",()->c.anarchy.wrap,()->c.anarchy.wrap=!c.anarchy.wrap);
        bool(navigation,"Уведомления",()->c.anarchy.feedback,()->c.anarchy.feedback=!c.anarchy.feedback);
        number(navigation,"Пауза, мс",()->c.anarchy.delayMs,v->c.anarchy.delayMs=(int)v,800,5000,100);
        action(navigation,"Настроить клавиши →",()->minecraft.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options)));
        Module m=module(mines,"Автошахты",()->c.enabled,()->c.enabled=!c.enabled);
        number(m,"Количество",()->c.maxEntries,v->c.maxEntries=(int)v,1,40,1);
        bool(m,"Обычный мир",()->c.showNormal,()->c.showNormal=!c.showNormal);bool(m,"Ад",()->c.showNether,()->c.showNether=!c.showNether);bool(m,"Энд",()->c.showEnd,()->c.showEnd=!c.showEnd);
        bool(m,"После обновления",()->c.keepUpdated,()->c.keepUpdated=!c.keepUpdated);
        number(m,"Ещё секунд",()->c.updatedSeconds,v->c.updatedSeconds=(int)v,10,60,5);
        m=module(mines,"Фильтр редкостей",null,null);
        mode(m,"Измерение",()->new String[]{"Мир","Ад","Энд"}[rarityDimension],()->rarityDimension=(rarityDimension+1)%3);
        bool(m,"Обычная",()->rarities().common,()->rarities().common=!rarities().common);
        bool(m,"Редкая",()->rarities().rare,()->rarities().rare=!rarities().rare);
        bool(m,"Эпическая",()->rarities().epic,()->rarities().epic=!rarities().epic);
        bool(m,"Легендарная",()->rarities().legendary,()->rarities().legendary=!rarities().legendary);
        bool(m,"Без редкости",()->rarities().unknown,()->rarities().unknown=!rarities().unknown);
        m=module(mines,"HWhelper",()->c.spawnerEnabled,()->c.spawnerEnabled=!c.spawnerEnabled);
        bool(m,"Золотой спавнер",()->c.spawnerEnabled,()->c.spawnerEnabled=!c.spawnerEnabled);
        m=module(media,"Dynamic Island",()->c.musicEnabled,()->c.musicEnabled=!c.musicEnabled);
        number(m,"Макс. ширина",()->c.musicWidth,v->c.musicWidth=(int)v,140,420,10);
        number(m,"Высота",()->c.islandHeight,v->c.islandHeight=(int)v,22,44,2);
        number(m,"Масштаб",()->c.musicScale,v->c.musicScale=(float)v,.65,1.6,.05);
        number(m,"Плотность фона",()->c.musicOpacity,v->c.musicOpacity=(int)v,30,100,5);
        bool(m,"Обложка",()->c.musicCover,()->c.musicCover=!c.musicCover);bool(m,"Текст песни",()->c.musicLyrics,()->c.musicLyrics=!c.musicLyrics);
        bool(m,"Караоке",()->c.musicGlow,()->c.musicGlow=!c.musicGlow);bool(m,"Прогресс",()->c.musicProgress,()->c.musicProgress=!c.musicProgress);
        bool(m,"Эквалайзер",()->c.islandEqualizer,()->c.islandEqualizer=!c.islandEqualizer);
        bool(m,"По центру",()->c.islandCentered,()->c.islandCentered=!c.islandCentered);
        bool(m,"Часы и сеть",()->c.islandStatus,()->c.islandStatus=!c.islandStatus);bool(m,"Время трека",()->c.islandTrackClock,()->c.islandTrackClock=!c.islandTrackClock);
        bool(m,"Всегда виден",()->c.islandAlwaysShow,()->c.islandAlwaysShow=!c.islandAlwaysShow);
        bool(m,"Сетка острова",()->c.musicSnapToGrid,()->c.musicSnapToGrid=!c.musicSnapToGrid);
        action(m,"Полные настройки →",()->minecraft.setScreen(new DynamicIslandSettingsScreen(c,this)));
        m=module(media,"World Lyrics",()->c.worldLyrics.enabled,()->c.worldLyrics.enabled=!c.worldLyrics.enabled);
        mode(m,"Анимация",()->new String[]{"LyricFlow","Typewriter","PopScale","Slide","Fade"}[c.worldLyrics.animation],()->c.worldLyrics.animation=(c.worldLyrics.animation+1)%5);
        bool(m,"Умное деление строк",()->c.worldLyrics.smartSplit,()->c.worldLyrics.smartSplit=!c.worldLyrics.smartSplit);
        bool(m,"Белый цвет",()->c.worldLyrics.white,()->c.worldLyrics.white=!c.worldLyrics.white);
        number(m,"Макс. строк",()->c.worldLyrics.maxLines,v->c.worldLyrics.maxLines=(int)v,1,6,1);
        number(m,"Мин. дистанция",()->c.worldLyrics.minDistance,v->c.worldLyrics.minDistance=(float)v,1.2,4,.1);
        number(m,"Макс. дистанция",()->c.worldLyrics.maxDistance,v->c.worldLyrics.maxDistance=(float)v,2,7,.1);
        number(m,"Разброс",()->c.worldLyrics.spread,v->c.worldLyrics.spread=(float)v,20,85,5);
        number(m,"Подъём строки",()->c.worldLyrics.floatHeight,v->c.worldLyrics.floatHeight=(float)v,.1,2,.1);
        number(m,"Размер текста",()->c.worldLyrics.scale,v->c.worldLyrics.scale=(float)v,.5,2,.05);
        number(m,"Сдвиг, мс",()->c.worldLyrics.offsetMs,v->c.worldLyrics.offsetMs=(int)v,-5000,5000,50);
        m=module(look,"Liquid Glass",()->c.liquidGlass,()->c.liquidGlass=!c.liquidGlass);
        number(m,"Плотность HUD",()->c.opacity,v->c.opacity=(int)v,30,100,5);
        number(m,"Масштаб HUD",()->c.hudScale,v->c.hudScale=(float)v,.65,1.6,.05);
        mode(m,"Акцент",()->new String[]{"Сакура","Ирис","Песок","Пудра","Лёд","Шалфей"}[Math.floorMod(c.theme,6)],()->c.theme=(c.theme+1)%6);
        bool(m,"Анимации",()->c.animations,()->c.animations=!c.animations);
        bool(m,"Компактный HUD",()->c.compact,()->c.compact=!c.compact);bool(m,"Редкость в HUD",()->c.showRarity,()->c.showRarity=!c.showRarity);bool(m,"Полоса таймера",()->c.showProgress,()->c.showProgress=!c.showProgress);
        m=module(look,"Редактор HUD",null,null);action(m,"Открыть редактор →",()->minecraft.setScreen(new HudEditorScreen(c,hud,this)));bool(m,"Сетка шахт",()->c.snapToGrid,()->c.snapToGrid=!c.snapToGrid);
        m=module(alerts,"Универсальный ключ",()->c.keyAlertEnabled,()->c.keyAlertEnabled=!c.keyAlertEnabled);
        bool(m,"Надпись и звук",()->c.keyAlertEnabled,()->c.keyAlertEnabled=!c.keyAlertEnabled);
        number(m,"Громкость",()->c.keySoundVolume,v->c.keySoundVolume=(float)v,0,1,.05);
        action(m,"Свои звуки →",()->minecraft.setScreen(new KeySoundScreen(c,this)));
        action(m,"Проверить звук ♪",()->CustomKeySound.preview(c));
        m=module(cosmetic,"Cosmetic",()->c.cosmetics.enabled,()->c.cosmetics.enabled=!c.cosmetics.enabled);
        action(m,"Вернуть стиль из примера",()->c.cosmetics.referenceStyle());
        bool(m,"Цвета общей темы",()->c.cosmetics.themeColors,()->c.cosmetics.themeColors=!c.cosmetics.themeColors);
        color(m,"Цвет нимба",()->c.cosmetics.haloColor,v->c.cosmetics.haloColor=v);
        color(m,"Экзорт",()->c.cosmetics.exortColor,v->c.cosmetics.exortColor=v);
        color(m,"Векс",()->c.cosmetics.wexColor,v->c.cosmetics.wexColor=v);
        color(m,"Квас",()->c.cosmetics.quasColor,v->c.cosmetics.quasColor=v);
        bool(m,"Нимб",()->c.cosmetics.halo,()->c.cosmetics.halo=!c.cosmetics.halo);
        bool(m,"Сферы",()->c.cosmetics.spheres,()->c.cosmetics.spheres=!c.cosmetics.spheres);
        bool(m,"След тела",()->c.cosmetics.trail,()->c.cosmetics.trail=!c.cosmetics.trail);
        bool(m,"Питомец",()->c.cosmetics.pet,()->c.cosmetics.pet=!c.cosmetics.pet);
        bool(m,"Naruto-бег",()->c.cosmetics.naruto,()->c.cosmetics.naruto=!c.cosmetics.naruto);
        bool(m,"На себе",()->c.cosmetics.self,()->c.cosmetics.self=!c.cosmetics.self);
        bool(m,"На других",()->c.cosmetics.others,()->c.cosmetics.others=!c.cosmetics.others);
        bool(m,"От первого лица",()->c.cosmetics.firstPerson,()->c.cosmetics.firstPerson=!c.cosmetics.firstPerson);
        mode(m,"Движение сфер",()->new String[]{"Статично","Орбита","Спираль","Спираль V2","Волны"}[c.cosmetics.sphereMode],()->c.cosmetics.sphereMode=(c.cosmetics.sphereMode+1)%5);
        mode(m,"Эффект сфер",()->new String[]{"Нет","След","Огненный"}[c.cosmetics.sphereEffect],()->c.cosmetics.sphereEffect=(c.cosmetics.sphereEffect+1)%3);
        number(m,"След сфер: точки",()->c.cosmetics.sphereTrailPoints,v->c.cosmetics.sphereTrailPoints=(int)v,6,48,1);
        number(m,"Свечение",()->c.cosmetics.glow,v->c.cosmetics.glow=(float)v,.2,2.2,.05);
        number(m,"Пульсация орбиты",()->c.cosmetics.breathe,v->c.cosmetics.breathe=(float)v,0,.3,.01);
        number(m,"Высота спирали",()->c.cosmetics.spiralHeight,v->c.cosmetics.spiralHeight=(float)v,.2,6,.1);
        number(m,"Амплитуда волн",()->c.cosmetics.waveAmplitude,v->c.cosmetics.waveAmplitude=(float)v,0,1.2,.05);
        number(m,"Частота волн",()->c.cosmetics.waveFrequency,v->c.cosmetics.waveFrequency=(float)v,.6,8,.1);
        mode(m,"Редактор сфер",()->new String[]{"Экзорт","Векс","Квас","Все"}[c.cosmetics.sphereEditor],()->c.cosmetics.sphereEditor=(c.cosmetics.sphereEditor+1)%4);
        number(m,"Лево / право",()->c.cosmetics.offset(false),v->c.cosmetics.offset(false,(float)v),-1.5,1.5,.05);
        number(m,"Вперёд / назад",()->c.cosmetics.offset(true),v->c.cosmetics.offset(true,(float)v),-1.5,1.5,.05);
        number(m,"Размер сфер",()->c.cosmetics.sphereSize,v->c.cosmetics.sphereSize=(float)v,.05,.22,.01);
        number(m,"Орбита сфер",()->c.cosmetics.sphereRadius,v->c.cosmetics.sphereRadius=(float)v,.18,1.25,.01);
        number(m,"Высота сфер",()->c.cosmetics.sphereHeight,v->c.cosmetics.sphereHeight=(float)v,-1.2,3.5,.05);
        number(m,"Скорость",()->c.cosmetics.speed,v->c.cosmetics.speed=(float)v,.2,4,.1);
        number(m,"Радиус нимба",()->c.cosmetics.haloRadius,v->c.cosmetics.haloRadius=(float)v,.18,.9,.01);
        number(m,"Толщина нимба",()->c.cosmetics.haloThickness,v->c.cosmetics.haloThickness=(float)v,.02,.16,.01);
        number(m,"Высота нимба",()->c.cosmetics.haloHeight,v->c.cosmetics.haloHeight=(float)v,-.6,.6,.05);
        mode(m,"Качество нимба",()->new String[]{"Низкое","Среднее","Высокое","Ультра"}[c.cosmetics.quality],()->c.cosmetics.quality=(c.cosmetics.quality+1)%4);
        bool(m,"Покачивание нимба",()->c.cosmetics.haloAnimate,()->c.cosmetics.haloAnimate=!c.cosmetics.haloAnimate);
        number(m,"Дальность",()->c.cosmetics.distance,v->c.cosmetics.distance=(float)v,8,192,8);
        number(m,"Длина следа",()->c.cosmetics.trailPoints,v->c.cosmetics.trailPoints=(int)v,6,80,2);
        number(m,"Ширина следа",()->c.cosmetics.trailWidth,v->c.cosmetics.trailWidth=(float)v,.06,.6,.02);
        bool(m,"Градиент следа",()->c.cosmetics.trailGradient,()->c.cosmetics.trailGradient=!c.cosmetics.trailGradient);
        color(m,"Цвет следа",()->c.cosmetics.trailColor,v->c.cosmetics.trailColor=v);
        color(m,"Второй цвет следа",()->c.cosmetics.trailColorB,v->c.cosmetics.trailColorB=v);
        number(m,"Прозрачность следа",()->c.cosmetics.trailOpacity,v->c.cosmetics.trailOpacity=(float)v,.1,1.6,.05);
        number(m,"Шаг следа, мс",()->c.cosmetics.trailStepMs,v->c.cosmetics.trailStepMs=(int)v,6,80,2);
        mode(m,"Тип питомца",()->new String[]{"Мышь","Попугай","Ворон","Фея","Пчела","Векс","Лиса","Свинья","Лягушка","Иглобрюх","Слайм"}[c.cosmetics.petType],()->c.cosmetics.petType=(c.cosmetics.petType+1)%11);
        number(m,"Радиус питомца",()->c.cosmetics.petRadius,v->c.cosmetics.petRadius=(float)v,.15,2.5,.05);
        number(m,"Высота питомца",()->c.cosmetics.petHeight,v->c.cosmetics.petHeight=(float)v,-.5,3,.05);
        number(m,"Размер питомца",()->c.cosmetics.petScale,v->c.cosmetics.petScale=(float)v,.15,2,.05);
        number(m,"Скорость питомца",()->c.cosmetics.petSpeed,v->c.cosmetics.petSpeed=(float)v,.05,5,.05);
        bool(m,"Клоны по клавише",()->c.cosmetics.clones,()->c.cosmetics.clones=!c.cosmetics.clones);
        action(m,"Запустить теневых клонов",()->{hud.showClones();if(minecraft.player!=null)minecraft.setScreen(null);});
        action(m,"Воронья сцена",()->{hud.showRavens();if(minecraft.player!=null)minecraft.setScreen(null);});
        action(m,"Настроить клавиши →",()->minecraft.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options)));
    }
    private RarityOptions rarities(){return c.rarityOptions(new String[]{"Обычные","Ад","Энд"}[rarityDimension]);}
    @Override protected void init(){
        clearWidgets();closing=false;opening.reset(0);lastFrame=0;
        layout=CsMenuLayout.create(width,height,compactColumns);
        int sw=Math.min(154,layout.w()/3);
        search=new EditBox(font,layout.x()+layout.w()-12-sw,layout.y()+layout.header()+17,sw-24,16,Component.literal("Поиск модулей"));
        search.setBordered(false);search.setMaxLength(64);search.setHint(UiStyle.text("Поиск…",9));
        search.addFormatter((text,pos)->UiStyle.text(text,9).getVisualOrderText());search.setTextColor(UiStyle.TEXT);
        search.setValue(query);search.setResponder(value->{query=value;resetScroll();});addRenderableWidget(search);
    }
    private void resetScroll(){scroll=scrollTarget=0;}
    private void category(int index){
        if(categoryIndex==index)return;
        categoryIndex=rememberedCategory=index;dragging=null;resetScroll();categoryFade.reset(0);
    }
    private String description(Module m){return switch(m.name){
        case "Автошахты"->"События, измерения и таймеры";
        case "Telegram"->"Встроенное подключение к @liteeventbot";
        case "Быстрый переход"->"Анархии, горячие клавиши и избранное";
        case "Фильтр редкостей"->"Свой набор для каждого измерения";
        case "Dynamic Island"->"Музыка, обложка и строка песни";
        case "Liquid Glass"->"Материал, цвет и читаемость";
        case "Редактор HUD"->"Положение и размеры модулей";
        case "HWhelper"->"Данные золотого спавнера и меча";
        case "World Lyrics"->"Строки песни в 3D-пространстве";
        case "Cosmetic"->"Нимб, сферы, питомцы и анимации";
        default->"Получение ключа: надпись и звук";
    };}
    private boolean matches(Group group,Module m){String q=query.toLowerCase(Locale.ROOT).trim();return q.isEmpty()||m.name.toLowerCase(Locale.ROOT).contains(q)||description(m).toLowerCase(Locale.ROOT).contains(q)||group.name.toLowerCase(Locale.ROOT).contains(q);}
    private int alpha(int color,float value){return UiStyle.alpha(color,Math.max(.02f,value));}
    private boolean starred(Module m){return c.favoriteModules.contains(m.name);}
    private List<Module> visibleModules(Group selected){
        List<Module> visible=new ArrayList<>();
        for(Group group:groups)if(group==selected||!query.isBlank())for(Module m:group.modules)
            if(matches(group,m)&&(!favoritesOnly||starred(m)))visible.add(m);
        if(activeFirst)visible.sort((one,two)->Boolean.compare(two.enabled!=null&&two.enabled.getAsBoolean(),one.enabled!=null&&one.enabled.getAsBoolean()));
        return visible;
    }
    private void star(Module m){if(starred(m))c.favoriteModules.remove(m.name);else c.favoriteModules.add(m.name);c.save();}
    private void control(int x,int y,int w,int h,Runnable action){controls.add(new Control(x,y,w,h,0,action));}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt){backdrop.render(g,width,height,mx,my,opening.value(),c.animations,c.accent());}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        long now=System.nanoTime();float a=opening.update(closing?0:1,now,c.animations);
        float dt=lastFrame==0?0:Math.min(.05f,(now-lastFrame)/1_000_000_000f);lastFrame=now;
        hits.clear();controls.clear();
        if(closing&&a<.003){minecraft.setScreen(null);return;}
        if(a<.03)return;
        Group selected=groups.get(categoryIndex);List<Module> visible=visibleModules(selected);
        CsMenuLayout l=CsMenuLayout.create(width,height,compactColumns,visible.size());layout=l;
        int x=l.x(),y=l.y()+Math.round((1-a)*12),w=l.w(),h=l.h(),shift=y-l.y();
        for(int i=4;i>0;i--)UiStyle.shape(g,x-i*2,y+3-i*2,w+i*4,h+i*4,12,alpha(0x10000000,a));
        UiStyle.surface(g,x,y,w,h,10,alpha(UiStyle.PANEL,a));
        g.fill(x+12,y+l.header()-1,x+w-12,y+l.header(),alpha(UiStyle.EDGE,a));
        UiStyle.shape(g,x+13,y+13,26,26,6,alpha(c.accent(),a));
        UiStyle.centered(g,"HW",x+13,y+13,26,26,10,alpha(UiStyle.INK,a));
        if(l.header()>52){UiStyle.label(g,"HWhelper",x+49,y+15,11,alpha(UiStyle.WHITE,a));UiStyle.label(g,"Zarxs",x+49,y+29,7,alpha(UiStyle.MUTED,a));}
        int trackW=l.header()>52?w-24:Math.min(360,w-132),slot=trackW/groups.size();
        int tx=l.header()>52?x+12:x+52+(w-132-trackW)/2,ty=l.header()>52?y+42:y+14;
        if(w>=580){UiStyle.label(g,"HWhelper",x+47,y+18,8,alpha(UiStyle.WHITE,a));UiStyle.label(g,"Zarxs",x+47,y+30,7,alpha(UiStyle.MUTED,a));}
        UiStyle.shape(g,tx-2,ty-2,slot*groups.size()+4,28,8,alpha(0xFF1B1B1D,a));
        float left=tabLeft.update(categoryIndex,now,c.animations),right=tabRight.update(categoryIndex,now,c.animations)+1;
        int thumbX=tx+Math.round(left*slot),thumbW=Math.max(4,Math.round((right-left)*slot));
        UiStyle.shape(g,thumbX,ty,thumbW,24,6,alpha(c.accent(),a));
        for(int i=0;i<groups.size();i++){
            final int index=i;
            UiStyle.centered(g,groups.get(i).name,tx+i*slot,ty,slot,24,8,alpha(UiStyle.TEXT,a));
            control(tx+i*slot,ty,slot,24,()->category(index));
        }
        g.enableScissor(thumbX,ty,thumbX+thumbW,ty+24);
        try{for(int i=0;i<groups.size();i++)UiStyle.centered(g,groups.get(i).name,tx+i*slot,ty,slot,24,8,alpha(UiStyle.INK,a));}
        finally{g.disableScissor();}
        UiStyle.shape(g,x+w-66,y+14,25,24,6,alpha(0xFF242124,a));
        UiStyle.icon(g,"palette",x+w-60,y+19,14,alpha(c.accent(),a));
        control(x+w-66,y+14,25,24,()->{c.theme=(c.theme+1)%6;c.save();});
        UiStyle.shape(g,x+w-36,y+14,23,24,6,alpha(0xFF1E1E20,a));
        UiStyle.label(g,"×",x+w-29,y+20,12,alpha(UiStyle.MUTED,a));control(x+w-36,y+14,23,24,this::onClose);
        int searchW=Math.min(154,w/3),searchX=x+w-12-searchW,toolbarY=y+l.header()+10;
        int quickX=searchX-56;
        UiStyle.fitted(g,query.isBlank()?selected.name:"Результаты",x+13,toolbarY,quickX-x-20,13,alpha(UiStyle.WHITE,a));
        long active=selected.modules.stream().filter(m->m.enabled!=null&&m.enabled.getAsBoolean()).count();
        UiStyle.label(g,active+" включено · "+selected.modules.size()+" модуля",x+13,toolbarY+18,8,alpha(UiStyle.MUTED,a));
        quick(g,"star",quickX,toolbarY,favoritesOnly,a,()->{favoritesOnly=!favoritesOnly;resetScroll();});
        quick(g,"sort",quickX+27,toolbarY,activeFirst,a,()->{activeFirst=!activeFirst;resetScroll();});
        UiStyle.frame(g,searchX-5,toolbarY,searchW+5,25,6,alpha(0xFF19191B,a),alpha(search.isFocused()?c.accent():UiStyle.EDGE,a));
        UiStyle.icon(g,"search",searchX+searchW-18,toolbarY+6,12,alpha(UiStyle.MUTED,a));
        search.setY(toolbarY+8);
        int bodyTop=l.bodyTop()+shift,bodyBottom=l.bodyBottom()+shift;
        float fade=categoryFade.update(1,now,c.animations);
        float blend=c.animations?1-(float)Math.exp(-dt*18):1;
        scroll+=(scrollTarget-scroll)*blend;
        int[] ys=new int[l.columns()];Arrays.fill(ys,bodyTop+Math.round((1-fade)*5)-(int)scroll);
        g.enableScissor(x+10,bodyTop,x+w-10,bodyBottom);
        try{
            for(int i=0;i<visible.size();i++){
                Module m=visible.get(i);int col=i%l.columns(),cx=x+12+col*(l.cardWidth()+8),cy=ys[col];
                float expand=m.expand.update(m.expanded?1:0,now,c.animations);int settingsHeight=0;for(Option o:m.options)settingsHeight+=o.height();
                int expandedHeight=Math.round(settingsHeight*expand),cardH=MODULE_H+expandedHeight;
                float opacity=a*fade;
                boolean hovered=mx>=cx&&mx<cx+l.cardWidth()&&my>=cy&&my<cy+MODULE_H&&my>=bodyTop&&my<bodyBottom;
                float hover=m.hover.update(hovered?1:0,now,c.animations),enabled=m.state.update(m.enabled!=null&&m.enabled.getAsBoolean()?1:0,now,c.animations);
                if(cy<bodyBottom&&cy+cardH>bodyTop){
                    UiStyle.surface(g,cx,cy,l.cardWidth(),cardH,7,alpha(UiStyle.mix(0xFF181819,0xFF222023,hover),opacity));
                    if(hover>.01)UiStyle.shape(g,cx+1,cy+1,l.cardWidth()-2,cardH-2,6,UiStyle.alpha(c.accent(),hover*opacity*.05f));
                    int iconColor=UiStyle.mix(UiStyle.MUTED,c.accent(),enabled);
                    UiStyle.shape(g,cx+8,cy+9,23,23,5,alpha(0xFF2C252C,opacity));
                    UiStyle.icon(g,icon(m),cx+13,cy+14,13,alpha(iconColor,opacity));
                    UiStyle.fitted(g,m.name,cx+37,cy+12,l.cardWidth()-63,10,alpha(UiStyle.WHITE,opacity));
                    UiStyle.icon(g,"star",cx+l.cardWidth()-21,cy+12,12,alpha(starred(m)?c.accent():UiStyle.MUTED,opacity));
                    UiStyle.fitted(g,description(m),cx+10,cy+36,l.cardWidth()-20,8,alpha(UiStyle.MUTED,opacity));
                    UiStyle.label(g,m.enabled==null?"ИНСТРУМЕНТ":enabled>.5?"ВКЛЮЧЕНО":"ВЫКЛЮЧЕНО",cx+10,cy+53,7,alpha(m.enabled==null?UiStyle.MUTED:UiStyle.mix(UiStyle.MUTED,c.accent(),enabled),opacity));
                    UiStyle.label(g,m.expanded?"−":"+",cx+l.cardWidth()-48,cy+48,12,alpha(UiStyle.TEXT,opacity));
                    if(m.enabled!=null){UiStyle.shape(g,cx+l.cardWidth()-29,cy+51,19,10,5,alpha(UiStyle.mix(0xFF353336,c.accent(),enabled),opacity));UiStyle.shape(g,cx+l.cardWidth()-27+Math.round(enabled*9),cy+53,6,6,3,alpha(enabled>.5?UiStyle.INK:UiStyle.TEXT,opacity));}
                    addHit(cx,cy,l.cardWidth(),MODULE_H,m,null,bodyTop,bodyBottom);
                    clippedControl(cx+l.cardWidth()-25,cy+6,21,26,bodyTop,bodyBottom,()->star(m));
                    clippedControl(cx+l.cardWidth()-54,cy+47,23,18,bodyTop,bodyBottom,()->m.expanded=!m.expanded);
                    int settingsY=cy+MODULE_H;
                    if(expandedHeight>0&&settingsY<bodyBottom&&settingsY+expandedHeight>bodyTop){
                        g.enableScissor(cx+5,Math.max(bodyTop,settingsY),cx+l.cardWidth()-5,Math.min(bodyBottom,settingsY+expandedHeight));
                        try{
                            g.fill(cx+10,settingsY,cx+l.cardWidth()-10,settingsY+1,alpha(UiStyle.EDGE,opacity));
                            int sy=settingsY;
                            for(Option o:m.options){drawOption(g,o,cx+8,sy,l.cardWidth()-16,now,opacity*expand);if(expand>.98)addHit(cx+8,sy,l.cardWidth()-16,o.height(),null,o,bodyTop,Math.min(bodyBottom,settingsY+expandedHeight));sy+=o.height();}
                        }finally{g.disableScissor();}
                    }
                }
                ys[col]+=cardH+8;
            }
            if(visible.isEmpty())UiStyle.centered(g,favoritesOnly?"Нет избранных модулей":"Модули не найдены",x+12,bodyTop,w-24,Math.max(20,bodyBottom-bodyTop),10,alpha(UiStyle.MUTED,a));
        }finally{g.disableScissor();}
        int lastY=bodyTop;for(int value:ys)lastY=Math.max(lastY,value+(int)scroll);
        maxScroll=Math.max(0,lastY-bodyBottom-8);scrollTarget=Math.min(scrollTarget,maxScroll);scroll=Math.max(0,Math.min(scroll,maxScroll));
        if(maxScroll>0){int track=bodyBottom-bodyTop,thumb=Math.max(15,(int)(track*track/(track+maxScroll)));UiStyle.shape(g,x+w-6,bodyTop+(int)((track-thumb)*scroll/maxScroll),2,thumb,1,alpha(UiStyle.MUTED,a*.6f));}
        UiStyle.fitted(g,"ЛКМ — вкл. · ПКМ — настройки · ESC — закрыть",x+13,y+h-16,w-140,7,alpha(UiStyle.MUTED,a));
        UiStyle.icon(g,"grid",x+w-108,y+h-19,12,alpha(compactColumns?c.accent():UiStyle.MUTED,a));
        control(x+w-112,y+h-22,20,18,()->{compactColumns=!compactColumns;resetScroll();});
        UiStyle.label(g,"Zarxs / 3.2.2",x+w-85,y+h-16,8,alpha(c.accent(),a));
        if(y+h+40<height){
            String brand="HWhelper";var text=UiStyle.text(brand,18);int tw=font.width(text),bx=(width-tw)/2,by=y+h+16;
            g.drawString(font,text,bx,by,alpha(0xFFAAA3AB,a),false);
            if(c.animations){
                int shine=bx+(int)((.5+.5*Math.sin(now/1_400_000_000.0))*tw);
                g.enableScissor(shine-18,by,shine+18,by+24);
                try{g.drawString(font,text,bx,by,alpha(c.accent(),a),false);}finally{g.disableScissor();}
            }
        }
        super.render(g,mx,my,partial);
    }
    private String icon(Module m){return switch(m.name){case "Автошахты","Фильтр редкостей"->"mines";case "Dynamic Island","World Lyrics"->"music";case "HWhelper"->"mines";case "Cosmetic"->"star";case "Универсальный ключ"->"key";case "Редактор HUD"->"grid";default->"palette";};}
    private void quick(GuiGraphics g,String icon,int x,int y,boolean on,float a,Runnable action){UiStyle.frame(g,x,y,23,25,5,alpha(on?0xFF35242D:0xFF1A1A1C,a),alpha(UiStyle.EDGE,a));UiStyle.icon(g,icon,x+5,y+6,13,alpha(on?c.accent():UiStyle.MUTED,a));control(x,y,23,25,action);}
    private void clippedControl(int x,int y,int w,int h,int top,int bottom,Runnable action){int y1=Math.max(y,top),y2=Math.min(y+h,bottom);if(y2>y1)control(x,y1,w,y2-y1,action);}
    private void addHit(int x,int y,int w,int h,Module module,Option option,int top,int bottom){int y1=Math.max(y,top),y2=Math.min(y+h,bottom);if(y2>y1)hits.add(new Hit(x,y1,w,y2-y1,module,option));}
    private void drawOption(GuiGraphics g,Option o,int x,int y,int w,long now,float a){
        if(o.number!=null){
            double value=o.number.getAsDouble();String display=o.step<1?String.format(Locale.ROOT,"%.2f",value):String.valueOf((int)value);
            UiStyle.fitted(g,o.name,x+3,y+7,w-45,8,alpha(UiStyle.TEXT,a));int tw=font.width(UiStyle.text(display,8));UiStyle.label(g,display,x+w-3-tw,y+7,8,alpha(UiStyle.WHITE,a));
            float ratio=o.animation.update((float)((value-o.min)/(o.max-o.min)),now,c.animations);
            UiStyle.shape(g,x+5,y+25,w-10,3,1,alpha(0xFF343236,a));int filled=(int)((w-10)*ratio);
            if(filled>0)UiStyle.shape(g,x+5,y+25,filled,3,1,alpha(c.accent(),a));UiStyle.shape(g,x+2+filled,y+23,6,7,3,alpha(c.accent(),a));
        }else if(o.state!=null){
            UiStyle.fitted(g,o.name,x+3,y+9,w-25,8,alpha(UiStyle.TEXT,a));float on=o.animation.update(o.state.getAsBoolean()?1:0,now,c.animations);
            int sx=x+w-16,sy=y+8;UiStyle.shape(g,sx,sy,11,11,3,alpha(UiStyle.mix(0xFF353236,c.accent(),on),a));
            if(on>.05)UiStyle.icon(g,"check",sx+1,sy+1,9,alpha(UiStyle.INK,a*on));
        }else if(o.value!=null){
            UiStyle.fitted(g,o.name,x+3,y+9,w/2-4,8,alpha(UiStyle.TEXT,a));UiStyle.shape(g,x+w/2,y+5,w/2-2,19,4,alpha(0xFF282529,a));UiStyle.fitted(g,o.value.get(),x+w/2+5,y+10,w/2-12,8,alpha(c.accent(),a));
        }else{UiStyle.shape(g,x+2,y+4,w-4,20,4,alpha(0xFF252226,a));UiStyle.fitted(g,o.name,x+7,y+10,w-14,8,alpha(UiStyle.TEXT,a));}
    }
    private void slider(Option o,double mouseX){double ratio=Math.max(0,Math.min(1,(mouseX-dragX)/(double)dragWidth));double value=o.min+Math.round(ratio*(o.max-o.min)/o.step)*o.step;o.set.accept(Math.max(o.min,Math.min(o.max,value)));}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){
        if(closing||opening.value()<.2)return true;
        for(Control control:controls)if(e.button()==control.button()&&control.contains(e.x(),e.y())){control.action().run();return true;}
        if(super.mouseClicked(e,doubleClick))return true;
        for(Hit hit:hits)if(hit.contains(e.x(),e.y())){
            if(hit.module()!=null){Module m=hit.module();if(e.button()==1||e.button()==0&&m.toggle==null)m.expanded=!m.expanded;else if(e.button()==0)m.toggle.run();else return false;c.save();return true;}
            Option o=hit.option();if(e.button()!=0)return true;
            if(o.number!=null){dragging=o;dragX=hit.x()+5;dragWidth=Math.max(1,hit.w()-10);slider(o,e.x());}
            else if(o.change!=null){o.change.run();c.save();}
            return true;
        }return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(dragging!=null&&e.button()==0){slider(dragging,e.x());return true;}return super.mouseDragged(e,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent e){if(dragging!=null){dragging=null;c.save();return true;}return super.mouseReleased(e);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(layout!=null&&mx>=layout.x()&&mx<layout.x()+layout.w()&&my>=layout.bodyTop()&&my<layout.bodyBottom()){scrollTarget=Math.max(0,Math.min(maxScroll,scrollTarget-(float)vertical*32));return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
    @Override public void onClose(){dragging=null;c.save();if(!c.animations)minecraft.setScreen(null);else closing=true;}
    @Override public boolean isPauseScreen(){return false;}
}
