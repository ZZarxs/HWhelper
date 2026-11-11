package ru.hwhelper.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import java.util.*;
import static ru.hwhelper.client.AnarchyTransferFlow.*;

public final class AnarchyTransferRegression {
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static AnarchyMenu.Entry entry(int slot,String name,String... lore){return new AnarchyMenu.Entry(slot,"minecraft:stone",name,List.of(lore),1,false);}
    private static AnarchyMenu menu(int id,int state,String title,AnarchyMenu.Entry... entries){return new AnarchyMenu(id,state,title,List.of(entries));}
    private static final AnarchyMenu MODES=menu(1,1,"§8Выбор режима",entry(11,"§aЛайт-анархия"),entry(12,"Классическая анархия"));
    private static final AnarchyMenu SOLO=menu(2,1,"Выбор Лайт анархий",entry(0,"СолоЛайт","Выбрано"),entry(1,"ДуоЛайт"),entry(18,"СолоЛайт #7","Онлайн: 14"));
    private static final AnarchyMenu DUO=menu(3,4,"Выбор Лайт анархий",entry(0,"СолоЛайт"),entry(1,"ДуоЛайт","Выбрано"),entry(22,"Анархия #14"));
    private static Observation game(int current){return new Observation(true,true,current,false,false,null);}
    private static Observation view(AnarchyMenu menu){return new Observation(true,true,0,false,false,menu);}
    private static AnarchyTransferFlow routeToLite(){
        var flow=new AnarchyTransferFlow();require(flow.begin(14,7,0,1200),"begin route");
        require(flow.poll(game(7),1).kind()==Kind.HUB,"one supported /hub action");
        require(flow.poll(game(7),2)==null,"never repeat /hub while waiting for lobby");
        require(flow.poll(new Observation(true,true,0,true,false,null),100).kind()==Kind.COMPASS,"use lobby compass");
        require(flow.poll(new Observation(true,true,0,true,false,null),110)==null,"never repeatedly use the compass");
        require(flow.poll(view(MODES),200).slot()==11,"choose Lite mode by item name");return flow;
    }
    public static void main(String[] args)throws Exception {
        var dispatcher=new CommandDispatcher<List<Integer>>();int[] help={0};
        AnarchyClientCommands.register(dispatcher,(sink,n)->{sink.add(n);return 1;},sink->help[0]++);
        List<Integer> targets=new ArrayList<>();
        require(dispatcher.execute("an7",targets)==1&&targets.equals(List.of(7)),"slash-stripped command executes locally once");
        dispatcher.execute("an",targets);dispatcher.execute("an 7",targets);dispatcher.execute("an7 extra",targets);
        require(help[0]==3&&targets.size()==1,"bare /an and malformed arguments show help without a transfer");
        ParseResults<List<Integer>> parsed=dispatcher.parse("an",targets);
        var suggestions=dispatcher.getCompletionSuggestions(parsed).get().getList();
        require(suggestions.stream().anyMatch(s->s.getText().equals("an7"))&&suggestions.stream().noneMatch(s->s.getText().equals("an90")),"autocomplete only valid local aliases");
        for(String input:List.of("an0","an37","an90","an007","an9999999999999999999999","an88 extra","an-1","an+7","an7.5","an7typo","AN7"))require(AnarchyClientCommands.invalidNumeric(input),"unsupported numeric command cannot fall through to server: "+input);
        require(!AnarchyClientCommands.invalidNumeric("hub")&&!AnarchyClientCommands.invalidNumeric("an7")&&!AnarchyClientCommands.invalidNumeric("anvil"),"unrelated commands are preserved");
        var disabledDispatcher=new CommandDispatcher<Boolean>();int[] handled={0};
        AnarchyClientCommands.register(disabledDispatcher,(enabled,n)->{handled[0]++;return enabled?1:0;},enabled->{});
        require(disabledDispatcher.execute("an7",false)==0&&handled[0]==1,"disabled module still owns command locally");

        var flow=routeToLite();require(flow.poll(view(MODES),210)==null,"do not click old mode screen again");
        require(flow.poll(view(menu(2,0,"Выбор Лайт анархий")),220)==null,"wait for delayed menu item contents");
        var group=flow.poll(view(SOLO),250);require(group.slot()==1&&group.menu().equals(SOLO),"pick Duo tab, not a player-inventory slot");
        require(flow.poll(view(SOLO),260)==null,"do not repeat a tab click on unchanged state");
        var onlineChange=menu(2,2,"Выбор Лайт анархий",entry(0,"СолоЛайт","Выбрано"),entry(1,"ДуоЛайт"),entry(18,"СолоЛайт #7","Онлайн: 15"));
        require(flow.poll(view(onlineChange),270)==null,"live lore counts do not acknowledge group navigation");
        var unconfirmedGroup=menu(8,7,"Выбор Лайт анархий",entry(1,"ДуоЛайт"),entry(22,"Анархия #14"));
        require(flow.poll(view(unconfirmedGroup),280)==null&&flow.stage()==Stage.GROUP,"new container with generic item name is not proof of selected group");
        require(!flow.begin(2,0,2000,1200)&&flow.target()==14,"a second request never replaces active navigation");
        var target=flow.poll(view(DUO),2100);require(target.slot()==22&&flow.stage()==Stage.CONFIRM,"click requested server in confirmed group");
        require(flow.poll(view(DUO),2150)==null&&flow.poll(game(7),2200)==null,"no repeat click and no success on stale sidebar");
        flow.poll(game(14),2300);require(flow.stage()==Stage.DONE,"only requested sidebar confirms success");
        require(flow.begin(2,14,2400,1200),"a new request works after completion");
        flow.cancel("Escape");require(flow.poll(view(DUO),2450)==null&&!flow.active(),"cancelled work cannot emit delayed clicks");
        require(!flow.begin(2,14,2500,1200),"cooldown persists after cancellation");

        flow=routeToLite();var wrong=menu(7,1,"Неизвестный сундук",entry(11,"Лайт-анархия"));
        require(flow.poll(view(wrong),300)==null&&flow.stage()==Stage.FAILED,"unexpected server container stops routing");
        flow=routeToLite();var ambiguous=menu(4,1,"Выбор Лайт анархий",entry(1,"ДуоЛайт"),entry(2,"ДуоЛайт"));
        require(flow.poll(view(ambiguous),300)==null&&flow.stage()==Stage.FAILED,"ambiguous group is never clicked");
        flow=routeToLite();flow.poll(view(SOLO),300);
        var ambiguousTarget=menu(5,1,"Выбор Лайт анархий",entry(1,"ДуоЛайт","Выбрано"),entry(22,"ДуоЛайт #14"),entry(23,"ДуоЛайт #14"));
        require(flow.poll(view(ambiguousTarget),400)==null&&flow.stage()==Stage.FAILED,"ambiguous server is never clicked");
        var inventoryCopy=new AnarchyMenu.Entry(60,"minecraft:stone","Лайт-анархия",List.of(),1,true);
        require(menu(1,1,"Выбор режима",inventoryCopy).modeButton()==-1,"player slots cannot act as menu buttons");
        require(menu(1,1,"Выбор режима",entry(1,"Лайт (1.20) анархия"),entry(2,"Лайт-анархия")).modeButton()==2,"legacy mode is excluded");
        require(menu(2,1,"Выбор Лайт анархий",entry(1,"СолоЛайт #7","Онлайн: 14")).targetButton(14,1)==-1,"incidental lore numbers never select a server");
        require(menu(2,1,"Выбор Лайт анархий",entry(1,"СолоЛайт #7")).targetButton(7,-1)==1,"explicit group and number need no extra tab click");
        require(menu(2,1,"Выбор Лайт анархий",entry(1,"Анархия #7")).targetButton(7,-1)==-1,"generic numbers require group context");
        require(!DUO.equals(menu(3,5,DUO.title(),DUO.entries().toArray(AnarchyMenu.Entry[]::new))),"changed state invalidates action snapshot");
        Object inventory=new Object();
        require(AnarchyMenuDriver.mayRestore(inventory,inventory,0,0),"restore original selection after using compass");
        require(!AnarchyMenuDriver.mayRestore(inventory,inventory,2,0),"manual hotbar change is preserved");
        require(!AnarchyMenuDriver.mayRestore(inventory,new Object(),0,0),"respawn/new inventory cancels old slot restoration");

        flow=new AnarchyTransferFlow();flow.begin(7,2,0,1200);flow.poll(game(2),1);
        require(flow.poll(game(0),9999)==null&&flow.active(),"wait for lobby and missing compass");
        flow.poll(game(0),10000);require(flow.stage()==Stage.FAILED,"missing compass times out");
        flow=routeToLite();flow.poll(view(SOLO),300);flow.poll(view(DUO),400);
        flow.poll(game(0),8399);require(flow.active(),"confirmation has its own eight-second clock");
        flow.poll(game(0),8400);require(flow.stage()==Stage.FAILED,"confirmation timeout does not report success");
        flow=new AnarchyTransferFlow();flow.begin(14,7,0,1200);flow.poll(game(7),1);
        flow.poll(new Observation(true,true,0,true,false,null),9900);flow.poll(view(MODES),14800);flow.poll(view(SOLO),19700);flow.poll(view(DUO),24600);
        flow.poll(game(0),30000);require(flow.stage()==Stage.FAILED,"overall deadline caps the final confirmation");
        flow=routeToLite();flow.poll(new Observation(false,false,0,false,false,null),300);require(flow.stage()==Stage.CANCELLED,"real disconnection cancels immediately");
        flow=routeToLite();flow.poll(new Observation(true,false,0,false,false,null),300);require(flow.active(),"world loading with live connection is not a disconnect");
        flow.poll(new Observation(true,true,0,false,true,null),350);require(flow.stage()==Stage.CANCELLED,"foreign screen cancels without further actions");
        for(int stop=0;stop<6;stop++){
            flow=new AnarchyTransferFlow();flow.begin(14,7,0,1200);
            if(stop>0)flow.poll(game(7),1);if(stop>1)flow.poll(new Observation(true,true,0,true,false,null),100);
            if(stop>2)flow.poll(view(MODES),200);if(stop>3)flow.poll(view(SOLO),300);if(stop>4)flow.poll(view(DUO),400);
            flow.cancel("manual input or module disabled");require(flow.poll(view(DUO),500)==null&&flow.stage()==Stage.CANCELLED,"cancellation at route step "+stop);
        }
        System.out.println("Local anarchy commands, completion and cancellable hub/compass/menu routing checks passed");
    }
}
