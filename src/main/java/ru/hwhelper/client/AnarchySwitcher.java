package ru.hwhelper.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.*;
import org.lwjgl.glfw.GLFW;

/** One shared, cancellable server-menu route for local commands, cards and keyboard shortcuts. */
final class AnarchySwitcher {
    private final HwConfig config;private final AnarchyTransferFlow flow=new AnarchyTransferFlow();
    private final AnarchyMenuDriver driver=new AnarchyMenuDriver();
    private Connection routeConnection;private Object initialScreen;private long requestedAt;private boolean reported=true;
    private KeyMapping open,previous,next;private Object connection;private int current,ticks;
    private String notice="";private long noticeUntil,noticeBegan;
    private final UiTransition entrance=new UiTransition(0,170);
    AnarchySwitcher(HwConfig config){this.config=config;}
    void register(KeyMapping.Category category){
        open=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hwhelper.anarchy_menu",GLFW.GLFW_KEY_G,category));
        previous=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hwhelper.anarchy_previous",GLFW.GLFW_KEY_PAGE_UP,category));
        next=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hwhelper.anarchy_next",GLFW.GLFW_KEY_PAGE_DOWN,category));
        AnarchyClientCommands.install(this);
        ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            ScreenKeyboardEvents.beforeKeyPress(screen).register((s,event)->{
                if(event.key()==GLFW.GLFW_KEY_ESCAPE||s instanceof AbstractContainerScreen<?>)cancel("Переход отменён вручную");
            });
            ScreenMouseEvents.beforeMouseClick(screen).register((s,event)->cancel("Переход отменён ручным действием"));
            ScreenMouseEvents.beforeMouseScroll(screen).register((s,x,y,dx,dy)->cancel("Переход отменён ручным действием"));
            ScreenMouseEvents.beforeMouseDrag(screen).register((s,event,dx,dy)->cancel("Переход отменён ручным действием"));
        });
    }
    private static long now(){return System.nanoTime()/1_000_000;}
    int current(){return current;}
    int pending(){return flow.active()?flow.target():0;}
    String status(){return pending()>0?switch(flow.stage()){
        case LOBBY->"Переход в лобби…";case MODES->"Ожидаю меню компаса…";case LITE->"Выбираю Лайт-анархию…";
        case GROUP->"Выбираю группу анархий…";default->"Подключение: "+AnarchyNavigation.name(pending());
    }:"Текущая: "+AnarchyNavigation.name(current);}
    String shortcuts(){return previous.getTranslatedKeyMessage().getString()+" / "+next.getTranslatedKeyMessage().getString();}
    void tick(Minecraft client){
        boolean menu=false;int direction=0;
        while(open.consumeClick())menu=true;
        while(previous.consumeClick())direction=-1;
        while(next.consumeClick())direction=1;
        Object activeConnection=client.getConnection();
        if(connection!=activeConnection){connection=activeConnection;current=0;ticks=0;}
        boolean ready=client.player!=null&&client.level!=null&&client.getConnection()!=null;
        if(ready&&(++ticks%5==0||flow.active()))current=readCurrent(client);
        if(!config.anarchy.enabled)cancel("Переход отменён: модуль выключен");
        if(flow.active()){
            boolean initial=client.screen!=null&&client.screen==initialScreen&&now()-requestedAt<500;
            boolean foreign=client.screen!=null&&!(client.screen instanceof AbstractContainerScreen<?>)&&!(client.screen instanceof LevelLoadingScreen)&&!initial;
            var snapshot=AnarchyMenuDriver.snapshot(client);
            var observation=new AnarchyTransferFlow.Observation(routeConnection!=null&&routeConnection.isConnected(),ready&&!initial,current,
                    ready&&current==0&&AnarchyMenuDriver.compass(client)>=0,foreign,snapshot);
            var action=flow.poll(observation,now());
            if(action!=null)try{driver.apply(client,action);}catch(RuntimeException exception){flow.fail("Переход остановлен: "+exception.getMessage());}
        }
        driver.restore(client,!flow.active());finish(client);
        if(!config.anarchy.enabled||!ready)return;
        // Consume and discard bindings while typing or using any other screen.
        if(client.screen!=null)return;
        if(menu){client.setScreen(new AnarchySwitchScreen(config,this,null));return;}
        if(direction!=0)step(direction);
    }
    private static int readCurrent(Minecraft client){
        Scoreboard scoreboard=client.level.getScoreboard();Objective objective=null;
        PlayerTeam team=scoreboard.getPlayersTeam(client.player.getScoreboardName());
        if(team!=null){DisplaySlot slot=DisplaySlot.teamColorToSlot(team.getColor());if(slot!=null)objective=scoreboard.getDisplayObjective(slot);}
        if(objective==null)objective=scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if(objective==null)return 0;
        int header=AnarchyNavigation.parseCurrent(objective.getDisplayName().getString());if(header>0)return header;
        for(var row:scoreboard.listPlayerScores(objective)){
            if(row.isHidden())continue;
            String text=PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(row.owner()),row.ownerName()).getString();
            int number=AnarchyNavigation.parseCurrent(text);if(number>0)return number;
        }return 0;
    }
    void step(int direction){
        var numbers=AnarchyNavigation.available(config.anarchy);
        int anchor=pending()>0?pending():current>0?current:config.anarchy.lastSelected;
        int target=AnarchyNavigation.step(numbers,anchor,direction,config.anarchy.wrap);
        if(target==0){notify(numbers.isEmpty()?"Нет анархий в выбранном списке":"Достигнут край списка");return;}
        join(target);
    }
    boolean join(int number){
        Minecraft client=Minecraft.getInstance();long now=now();
        if(!config.anarchy.enabled){notify("Включите модуль «Быстрый переход»");return false;}
        if(client.player==null||client.level==null||client.getConnection()==null||client.hasSingleplayerServer()){notify("Доступно при подключении к серверу");return false;}
        current=readCurrent(client);
        if(!flow.begin(number,current,now,config.anarchy.delayMs)){
            notify(flow.error());return false;
        }
        routeConnection=client.getConnection().getConnection();initialScreen=client.screen;requestedAt=now;reported=false;
        config.anarchy.lastSelected=number;config.save();notify("Переход: "+AnarchyNavigation.name(number));return true;
    }
    private void cancel(String message){flow.cancel(message);driver.restore(Minecraft.getInstance(),!flow.active());finish(Minecraft.getInstance());}
    private void finish(Minecraft client){
        if(flow.active()||reported)return;reported=true;
        String message=flow.stage()==AnarchyTransferFlow.Stage.DONE?"Подключено: "+AnarchyNavigation.name(flow.target()):flow.error();notify(message);
        if(flow.stage()!=AnarchyTransferFlow.Stage.DONE&&client.player!=null)client.player.displayClientMessage(Component.literal("HWhelper: "+message),false);
        routeConnection=null;initialScreen=null;
    }
    private void notify(String text){notice=text;noticeBegan=now();noticeUntil=noticeBegan+2800;entrance.reset(0);}
    String notice(){return now()<noticeUntil?notice:"";}
    void render(GuiGraphics g){
        if(!config.anarchy.enabled||!config.anarchy.feedback)return;
        long now=now();float alpha=entrance.update(flow.active()||now<noticeUntil?1:0,System.nanoTime(),config.animations);
        if(alpha<.01)return;
        int w=Math.min(286,g.guiWidth()-16),h=38,x=(g.guiWidth()-w)/2,y=g.guiHeight()-88+(int)((1-alpha)*7);
        UiStyle.glass(g,x,y,w,h,config.accent(),0,true,(int)(238*alpha));
        UiStyle.shape(g,x+10,y+11,3,15,1,UiStyle.alpha(config.accent(),alpha));
        UiStyle.fitted(g,flow.active()?status():notice,x+21,y+9,w-32,10,UiStyle.alpha(UiStyle.WHITE,alpha));
        UiStyle.fitted(g,shortcuts()+" · "+open.getTranslatedKeyMessage().getString()+" — выбор анархии",x+21,y+24,w-32,8,UiStyle.alpha(UiStyle.MUTED,alpha));
    }
}
