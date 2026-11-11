package ru.hwhelper.client;

import java.util.*;

/** User journeys with synthetic clocks; never connects to Minecraft or sends commands. */
public final class AnarchyRegression {
    public static void main(String[] args){
        var options=new AnarchyOptions();options.sanitize();
        require(AnarchyNavigation.available(options).size()==36,"all known Lite servers");
        options.favorites=new ArrayList<>(Arrays.asList(7,2,4,4,null,0,88,89,90,37));options.sanitize();
        require(options.favorites.equals(List.of(2,4,7)),"favorites discard duplicates and nonexistent numbers");
        options.favoritesOnly=true;var favorites=AnarchyNavigation.available(options);
        require(favorites.equals(List.of(2,4,7)),"favorite-only sequence");
        require(AnarchyNavigation.step(favorites,2,1,true)==4,"next favorite");
        require(AnarchyNavigation.step(favorites,4,-1,true)==2,"previous favorite");
        require(AnarchyNavigation.step(favorites,7,1,true)==2,"wrap forward");
        require(AnarchyNavigation.step(favorites,2,-1,true)==7,"wrap backward");
        require(AnarchyNavigation.step(favorites,7,1,false)==0,"stop at end without wrapping");
        require(AnarchyNavigation.step(favorites,2,-1,false)==0,"stop at start without wrapping");
        require(AnarchyNavigation.step(favorites,3,1,true)==4&&AnarchyNavigation.step(favorites,3,-1,true)==2,"navigate from a server outside favorites");
        require(AnarchyNavigation.step(favorites,0,-1,false)==7,"unknown current server starts at last entry");
        require(AnarchyNavigation.step(List.of(4),4,1,true)==0,"single favorite never reconnects to itself");
        options.solo=false;require(AnarchyNavigation.available(options).isEmpty(),"group switches also filter favorites");
        require(AnarchyNavigation.step(List.of(),4,1,true)==0,"empty list sends nothing");
        options.favoritesOnly=false;options.duo=false;options.trio=false;require(AnarchyNavigation.available(options).equals(List.of(30,31,32,33,34,35,36)),"Clan-only range");
        for(int n:List.of(1,9,10,19,20,29,30,36))require(AnarchyNavigation.parseCurrent("§6→ "+AnarchyNavigation.name(n)+" §f←")==n,"read formatted sidebar "+n);
        require(AnarchyNavigation.parseCurrent("СолоЛайт\u00a0#2")==2,"nonbreaking space in sidebar");
        require(AnarchyNavigation.parseCurrent("КланЛайт #90")==0,"do not select nonexistent ClanLight 90");
        require(AnarchyNavigation.parseCurrent("Лайт (1.20) #2")==0,"do not confuse legacy Light with Solo");
        require(AnarchyNavigation.parseCurrent("КланЛайт #999999999999999999999")==0,"malformed numbers do not crash");
        require(AnarchyNavigation.parseCurrent("Монеток: 12345")==0,"unrelated scoreboard row");
        require(AnarchyNavigation.matches(7,"/an7")&&AnarchyNavigation.matches(7,"7")&&!AnarchyNavigation.matches(17,"7"),"exact number and command search");
        require(AnarchyNavigation.matches(7,"соло")&&!AnarchyNavigation.matches(17,"соло"),"group-name search");
        require(AnarchyNavigation.command(7).equals("an7"),"ordinary server command without chat prefix");
        for(int invalid:List.of(-1,0,37,88,90,Integer.MAX_VALUE)){
            boolean rejected=false;try{AnarchyNavigation.command(invalid);}catch(IllegalArgumentException expected){rejected=true;}require(rejected,"invalid target cannot produce command");
        }
        var gate=new AnarchyTransferGate();
        require(!gate.begin(2,2,10000,1200),"do not reconnect to current server");
        require(gate.begin(4,2,10000,1200),"manual request accepted");
        require(gate.pending()==4&&gate.result()==AnarchyTransferGate.Result.WAITING,"request is not assumed successful");
        require(gate.observe(2,10500)==AnarchyTransferGate.Result.WAITING,"old sidebar cannot acknowledge transfer");
        require(!gate.begin(7,2,11199,1200),"cooldown blocks extra inputs");
        require(!gate.begin(4,2,11200,1200),"duplicate pending target is suppressed");
        require(gate.begin(7,2,11200,1200),"new manual choice can replace pending request after cooldown");
        require(gate.observe(4,11500)==AnarchyTransferGate.Result.WAITING,"late acknowledgement of old choice is ignored");
        require(gate.observe(7,12000)==AnarchyTransferGate.Result.CONFIRMED&&gate.pending()==0,"only requested sidebar confirms connection");
        require(gate.begin(2,7,12400,1200),"another manual transfer after acknowledgement");
        require(gate.observe(0,20399)==AnarchyTransferGate.Result.WAITING,"temporary missing sidebar retains request");
        require(gate.observe(0,20400)==AnarchyTransferGate.Result.TIMED_OUT,"missing confirmation times out");
        require(gate.begin(2,7,20401,1200),"manual retry after timeout");
        gate.clear();require(gate.pending()==0&&gate.result()==AnarchyTransferGate.Result.IDLE,"disabling clears pending feedback");
        options.favorites=null;options.delayMs=-1;options.lastSelected=90;options.sanitize();require(options.delayMs==800&&options.lastSelected==0&&options.favorites.isEmpty(),"sanitize old/null preferences");
        options.delayMs=Integer.MAX_VALUE;options.sanitize();require(options.delayMs==5000,"cap corrupted cooldown");
        System.out.println("Anarchy navigation, favorites, sidebar parsing and transfer-state regression checks passed");
    }
    private static void require(boolean value,String name){if(!value)throw new AssertionError(name);}
}
