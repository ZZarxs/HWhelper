package ru.hwhelper.client;

import com.mojang.brigadier.CommandDispatcher;
import java.util.*;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.nbt.CompoundTag;
import static ru.hwhelper.client.AnarchyTransferFlow.*;

/** Compatibility fixtures use the exact PublicBukkitValues keys and values found in the supplied reference JAR. */
public final class AnarchyTransferRegression {
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static void invalid(Runnable run,String label){boolean failed=false;try{run.run();}catch(IllegalArgumentException e){failed=true;}require(failed,label);}
    private static AnarchyMenu.Entry entry(int slot,String type,String server){return new AnarchyMenu.Entry(slot,"minecraft:stone","Название не важно",List.of("Любой язык"),1,false,type,server);}
    private static AnarchyMenu menu(int id,int state,AnarchyMenu.Entry... entries){return new AnarchyMenu(id,state,"Независимый заголовок",List.of(entries));}
    private static final AnarchyMenu CATEGORIES=menu(1,1,entry(0,"solo",""),entry(1,"duo",""),entry(2,"trio",""),entry(3,"clans",""));
    private static final AnarchyMenu SERVERS=menu(2,1,entry(18,"","lanarchy16"),entry(19,"","lanarchy17"));
    private static Observation game(int current){return new Observation(true,true,current,false,ScreenState.WORLD,null);}
    private static Observation view(AnarchyMenu menu){return new Observation(true,true,0,false,ScreenState.SELECTOR,menu);}
    private static Observation lobby(ScreenState screen){return new Observation(true,true,0,true,screen,null);}
    private static AnarchyTransferFlow toCategories(){return toCategories(17,"");}
    private static AnarchyTransferFlow toCategories(int target,String preferred){
        var flow=new AnarchyTransferFlow();
        require(flow.begin(target,7,0,1200,new HubSwapRanges(),preferred),"start requested target");
        require(flow.poll(game(7),1).kind()==Kind.HUB,"send supported hub command once");
        require(flow.poll(game(7),2)==null,"do not repeat hub while waiting");
        require(flow.poll(new Observation(true,false,0,false,ScreenState.WAIT,null),50)==null&&flow.active(),"survive world loading with live connection");
        require(flow.poll(lobby(ScreenState.WORLD),100).kind()==Kind.LITE,"world-change/already-in-hub acknowledgement opens /lite");
        require(flow.poll(game(0),110)==null,"do not repeat lite while waiting");return flow;
    }
    private static void checkReconfiguration(){
        require(AnarchySwitcher.isTransferLoadingScreen(ServerReconfigScreen.class),"recognize native reconfiguration screen");
        require(AnarchySwitcher.isTransferLoadingScreen(LevelLoadingScreen.class),"recognize native world loading screen");
        require(!AnarchySwitcher.isTransferLoadingScreen(GenericMessageScreen.class),"reject unrelated screen");

        var flow=new AnarchyTransferFlow();
        require(flow.begin(17,7,0,1200),"start route from another anarchy");
        require(flow.poll(game(7),1).kind()==Kind.HUB,"send hub before reconfiguration");
        require(flow.poll(new Observation(true,false,0,false,ScreenState.WAIT,null),100)==null&&flow.stage()==Stage.LOBBY,"keep route during server reconfiguration");
        require(flow.poll(lobby(ScreenState.WAIT),200)==null&&flow.stage()==Stage.LOBBY,"wait even if hub world already exists");
        require(flow.poll(lobby(ScreenState.WORLD),300).kind()==Kind.LITE,"open selector after hub loading ends");
        require(flow.poll(view(CATEGORIES),400).slot()==1,"select category after loading");
        require(flow.poll(view(SERVERS),500).slot()==19,"select destination after loading");
        require(flow.poll(new Observation(true,false,0,false,ScreenState.WAIT,null),600)==null&&flow.stage()==Stage.CONFIRM,"keep route while destination reconfigures");
        require(flow.poll(new Observation(true,true,17,false,ScreenState.WAIT,null),700)==null&&flow.stage()==Stage.CONFIRM,"wait for playable destination");
        flow.poll(game(17),800);
        require(flow.stage()==Stage.DONE,"confirm the destination after its loading screen closes");
    }
    public static void main(String[] args)throws Exception {
        checkReconfiguration();
        var profile=new HubSwapRanges();
        require(profile.total==66&&profile.category(1).equals("solo")&&profile.category(15).equals("solo"),"original Solo range");
        require(profile.category(16).equals("duo")&&profile.category(31).equals("duo"),"original Duo range");
        require(profile.category(32).equals("trio")&&profile.category(47).equals("trio"),"original Trio range");
        require(profile.category(48).equals("clans")&&profile.category(66).equals("clans"),"original clans protocol value");
        require(profile.server(1).equals("lanarchy")&&profile.server(2).equals("lanarchy2")&&profile.server(66).equals("lanarchy66"),"special first-server key has no suffix");
        var legacy=HubSwapRanges.legacy();require(legacy.category(14).equals("duo")&&legacy.category(30).equals("clans"),"optional old HWhelper profile");
        var imported=HubSwapLegacyImport.parseRanges("# krsHubSwap\ntotal=66\nsolo=1-15\nduo=16-31\ntrio=32-47\nclans=48-66\n");
        require(imported.server(1).equals("lanarchy")&&imported.group(40)==2,"original plain-text config import");
        invalid(()->HubSwapRanges.parse(66,List.of("1-15","15-31","32-47","48-66")),"reject overlapping ranges");
        invalid(()->HubSwapRanges.parse(66,List.of("1-15","17-31","32-47","48-66")),"reject gaps");
        invalid(()->HubSwapRanges.parse(66,List.of("1-15","16-31","32-47","48-65")),"reject wrong total");
        invalid(()->new HubSwapRanges(1000,15,31,47),"bound malformed command-tree size");
        invalid(()->HubSwapRanges.aliases(List.of("hub")),"aliases must not consume real /hub");
        invalid(()->HubSwapRanges.aliases(List.of("lite")),"aliases must not consume real /lite");
        invalid(()->HubSwapRanges.aliases(List.of("an7")),"reserve compact syntax");
        require(HubSwapRanges.aliases(List.of("/lt","an","custom","custom")).equals(List.of("an","lt","custom")),"aliases sanitize and deduplicate");

        CompoundTag values=new CompoundTag();values.putString(AnarchyMenu.TYPE_KEY,"duo");values.putString(AnarchyMenu.SERVER_KEY,"lanarchy17");
        CompoundTag custom=new CompoundTag();custom.put("PublicBukkitValues",values);
        var routing=AnarchyMenu.routing(custom);require(routing.category().equals("duo")&&routing.server().equals("lanarchy17"),"read actual 1.21.11 custom-data NBT");
        require(AnarchyMenu.routing(new CompoundTag()).server().isBlank(),"missing PDC is not a match");
        CompoundTag invalidType=new CompoundTag();invalidType.putString("PublicBukkitValues","not a compound");require(AnarchyMenu.routing(invalidType).category().isBlank(),"malformed NBT is harmless");
        var fake=new AnarchyMenu.Entry(70,"minecraft:stone","fake",List.of(),1,true,"duo","lanarchy17");
        require(menu(1,1,fake).categoryButton("duo")==-1&&menu(1,1,fake).serverButton("lanarchy17")==-1,"never click player-inventory slots");
        require(menu(0,1,entry(0,"duo","")).categoryButton("duo")==-1,"never click player container");
        require(menu(1,1,entry(1,"solo","lanarchy17")).serverButton("lanarchy17")==1,"exact global server key wins stale group hint");
        values.putString(AnarchyMenu.TYPE_KEY," Clan ");
        require(AnarchyMenu.routing(custom).category().equals("clans"),"normalize singular category tag");

        var options=new AnarchyOptions();options.sanitize();List<Integer> requests=new ArrayList<>();int[] help={0};
        var dispatcher=new CommandDispatcher<List<Integer>>();AnarchyClientCommands.register(dispatcher,options,(source,n)->{source.add(n);return 1;},source->help[0]++);
        dispatcher.execute("an 17",requests);dispatcher.execute("lt 17",requests);dispatcher.execute("an17",requests);
        require(requests.equals(List.of(17,17,17)),"original aliases and existing compact commands");
        dispatcher.execute("an",requests);dispatcher.execute("an17 extra",requests);dispatcher.execute("an 67",requests);
        require(help[0]==3&&requests.size()==3,"help and invalid ranges are local");
        require(dispatcher.getCompletionSuggestions(dispatcher.parse("lt ",requests)).get().getList().stream().anyMatch(s->s.getText().equals("66")),"argument completion reaches profile maximum");
        require(dispatcher.getCompletionSuggestions(dispatcher.parse("an",requests)).get().getList().stream().anyMatch(s->s.getText().equals("an66")),"compact completion preserved");
        for(String bad:List.of("an0","an67","an007","an-1","an+7","an7.5","an7typo","AN7","an 999999999999999999","lt 0")){
            var parsed=AnarchyClientCommands.parse(bad,options);require(parsed.owned()&&parsed.target()==null&&!parsed.error().isBlank(),"invalid local syntax never leaks: "+bad);
        }
        require(!AnarchyClientCommands.parse("hub",options).owned()&&!AnarchyClientCommands.parse("lite",options).owned(),"real hub/lite remain server commands");
        options.aliases=HubSwapRanges.aliases(List.of("custom"));require(AnarchyClientCommands.parse("custom 17",options).target()==17,"new alias executes immediately without tree refresh");
        options.enabled=false;require(AnarchyClientCommands.parse("an17",options).owned(),"disabled module still owns its commands");

        var flow=toCategories();require(flow.poll(view(menu(1,0)),200)==null,"wait for delayed item packet");
        var group=flow.poll(view(CATEGORIES),250);require(group.slot()==1&&flow.stage()==Stage.SERVER,"select category by PDC");
        require(flow.poll(view(CATEGORIES),260)==null,"no repeated category click on stale menu");
        require(!flow.begin(2,0,2000,1200)&&flow.target()==17,"one request in flight");
        var target=flow.poll(view(SERVERS),2100);require(target.slot()==19&&flow.stage()==Stage.CONFIRM,"select exact server key");
        require(flow.poll(view(SERVERS),2200)==null&&flow.poll(game(16),2300)==null,"no repeat click or false success");
        flow.poll(game(17),2400);require(flow.stage()==Stage.DONE,"confirm actual destination through sidebar");
        require(flow.begin(1,17,2500,1200),"new request after completion");flow.cancel("Escape");require(flow.poll(view(SERVERS),2600)==null,"cancel prevents delayed actions");
        require(!flow.begin(1,17,2700,1200),"cooldown survives cancellation");

        flow=toCategories(31,"");
        require(flow.poll(view(CATEGORIES),200).slot()==1,"profile 66 tries Duo first for 31");
        var duoList=menu(1,2,entry(0,"solo",""),entry(1,"duo",""),entry(2,"trio",""),entry(3,"clans",""),entry(18,"duo","lanarchy16"));
        require(flow.poll(view(duoList),300)==null&&flow.poll(view(duoList),1399)==null,"wait for delayed entries");
        var fullDuoList=menu(1,3,entry(0,"solo",""),entry(1,"duo",""),entry(2,"trio",""),entry(3,"clans",""),entry(18,"duo","lanarchy16"),entry(19,"duo","lanarchy19"));
        require(flow.poll(view(fullDuoList),1400)==null&&flow.poll(view(fullDuoList),2199)==null,"slot update restarts settling time");
        require(flow.poll(view(fullDuoList),2200).slot()==3,"31 falls back to Clan in the same menu");
        require(flow.poll(view(fullDuoList),2210)==null,"do not reuse stale Duo list");
        var clanList=menu(1,4,entry(3,"clans",""),entry(18,"duo","lanarchy31"));
        require(flow.poll(view(clanList),2350).slot()==18&&flow.stage()==Stage.CONFIRM,"choose exact 31 despite stale card type");
        require(flow.poll(game(30),2400)==null&&flow.active(),"nearby server does not confirm 31");
        flow.poll(game(31),2450);
        require(flow.stage()==Stage.DONE,"confirm 31 on the sidebar");
        var detected=AnarchyNavigation.readCurrent("§dКланЛайт #31",profile);
        require(detected.number()==31&&options.remember(31,detected.category())&&options.group(31)==3,"remember confirmed Clan group");
        flow=toCategories(31,options.category(31));
        require(flow.poll(view(CATEGORIES),200).slot()==3,"next route uses confirmed group first");

        flow=toCategories(31,"");
        flow.poll(view(CATEGORIES),200);
        var separateList=menu(2,1,entry(19,"clans","lanarchy17"));
        require(flow.poll(view(separateList),300)==null&&flow.poll(view(separateList),1400).kind()==Kind.LITE,"reopen separate list instead of treating a server card as a tab");
        require(flow.poll(view(separateList),1410)==null,"wait for reopened menu");
        require(flow.poll(view(CATEGORIES),1500).slot()==3,"reopened selector skips the visited group");

        flow=toCategories(31,"");
        require(flow.poll(view(CATEGORIES),200).slot()==1,"start with Duo on an unchanged selector");
        require(flow.poll(view(CATEGORIES),4200).slot()==3,"try Clan after the unchanged selector times out");
        require(flow.poll(view(CATEGORIES),8200).slot()==0,"try Solo once");
        require(flow.poll(view(CATEGORIES),12200).slot()==2,"try Trio once");
        require(flow.poll(view(CATEGORIES),16200)==null&&flow.stage()==Stage.FAILED,"stop after trying all four groups");

        flow=toCategories(17,"");
        var alreadyVisible=menu(1,1,entry(1,"duo",""),entry(19,"","lanarchy17"));
        require(flow.poll(view(alreadyVisible),200).slot()==1,"select tab when the destination list is already visible");
        require(flow.poll(view(alreadyVisible),250).slot()==19,"select exact server in unchanged list");

        flow=toCategories();require(flow.poll(view(menu(1,1,entry(1,"duo",""),entry(2,"duo",""))),200)==null&&flow.stage()==Stage.FAILED,"ambiguous category stops safely");
        flow=toCategories();flow.poll(view(CATEGORIES),200);require(flow.poll(view(menu(2,1,entry(1,"","lanarchy17"),entry(2,"","lanarchy17"))),300)==null&&flow.stage()==Stage.FAILED,"ambiguous server stops safely");
        flow=toCategories();var dualTag=menu(2,1,entry(1,"duo","lanarchy17"));
        require(flow.poll(view(dualTag),200).slot()==1&&flow.stage()==Stage.SERVER,"category stage remains distinct even if item has both tags");
        require(flow.poll(view(dualTag),210)==null,"old category item is never mistaken for destination");
        require(flow.poll(view(SERVERS),250).slot()==19&&flow.stage()==Stage.CONFIRM,"destination is selected only after category inventory changes");
        flow=new AnarchyTransferFlow();flow.begin(1,7,0,1200);flow.poll(game(7),1);flow.poll(lobby(ScreenState.WORLD),100);
        flow.poll(view(CATEGORIES),150);
        require(flow.poll(view(menu(2,1,entry(4,"","lanarchy1"))),200)==null,"first server must not use lanarchy1");
        require(flow.poll(view(menu(3,1,entry(4,"","lanarchy"))),250).slot()==4,"first server uses original special value");
        flow=new AnarchyTransferFlow();flow.begin(17,7,0,1200);flow.poll(game(7),1);flow.poll(game(7),10000);require(flow.stage()==Stage.FAILED,"hub timeout");
        flow=toCategories();flow.poll(view(CATEGORIES),200);flow.poll(view(SERVERS),300);flow.poll(game(0),8299);require(flow.active(),"full confirmation window");flow.poll(game(0),8300);require(flow.stage()==Stage.FAILED,"eight-second confirmation timeout");
        flow=new AnarchyTransferFlow();flow.begin(17,7,0,1200);flow.poll(game(7),1);flow.poll(lobby(ScreenState.WORLD),9900);flow.poll(view(CATEGORIES),19800);flow.poll(view(SERVERS),29700);flow.poll(game(0),30000);require(flow.stage()==Stage.FAILED,"overall 30-second cap");
        flow=toCategories();flow.poll(new Observation(false,false,0,false,ScreenState.WAIT,null),200);require(flow.stage()==Stage.CANCELLED,"disconnect cancels even during loading");
        flow=toCategories();flow.poll(new Observation(true,true,0,false,ScreenState.FOREIGN,null),200);require(flow.stage()==Stage.CANCELLED,"foreign UI cancels");
        for(int stop=0;stop<5;stop++){
            flow=new AnarchyTransferFlow();flow.begin(17,7,0,1200);if(stop>0)flow.poll(game(7),1);if(stop>1)flow.poll(lobby(ScreenState.WORLD),100);if(stop>2)flow.poll(view(CATEGORIES),200);if(stop>3)flow.poll(view(SERVERS),300);
            flow.cancel("manual action / disabled");require(flow.poll(view(SERVERS),400)==null&&flow.stage()==Stage.CANCELLED,"cancel stage "+stop);
        }
        require(!SERVERS.equals(menu(2,2,SERVERS.entries().toArray(AnarchyMenu.Entry[]::new))),"state change invalidates captured click");
        System.out.println("krsHubSwap protocol, 1.21.11 NBT, aliases, ranges, import and cancellable transfer checks passed");
    }
}
