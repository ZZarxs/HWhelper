package ru.hwhelper.client;

import java.util.LinkedHashSet;
import java.util.Set;

/** One cancellable /hub → /lite → PDC category → PDC server route, adapted to native 1.21.11 APIs. */
final class AnarchyTransferFlow {
    enum Stage { IDLE,LOBBY,CATEGORY,SERVER,CONFIRM,DONE,FAILED,CANCELLED }
    enum Kind { HUB,LITE,CLICK }
    enum ScreenState { WORLD,SELECTOR,WAIT,FOREIGN }
    record Action(Kind kind,AnarchyMenu menu,int slot){}
    record Observation(boolean connected,boolean ready,int current,boolean lobby,ScreenState screen,AnarchyMenu menu){}
    private Stage stage=Stage.IDLE;private int target;private long deadline,stageDeadline,allowedAt;private boolean hubSent;
    private String category="",server="",error="",clickedCategory="";
    private final Set<String> categoryOrder=new LinkedHashSet<>();
    private final Set<String> visited=new LinkedHashSet<>();
    private String serverView="",reopenedFrom="";
    private long categoryClickedAt,serverViewSince;
    boolean active(){return switch(stage){case LOBBY,CATEGORY,SERVER,CONFIRM->true;default->false;};}
    Stage stage(){return stage;}int target(){return target;}String error(){return error;}
    String category(){return category;}
    long remaining(long now){return Math.max(0,allowedAt-now);}
    boolean begin(int number,int current,long now,int delay){return begin(number,current,now,delay,new HubSwapRanges());}
    boolean begin(int number,int current,long now,int delay,HubSwapRanges ranges){
        return begin(number,current,now,delay,ranges,"");
    }
    boolean begin(int number,int current,long now,int delay,HubSwapRanges ranges,String preferred){
        if(active()){error="Сначала дождитесь перехода или отмените его через Escape";return false;}
        if(ranges.group(number)<0){error="Допустимы анархии 1–"+ranges.total;return false;}
        if(number==current){error="Вы уже на "+ranges.name(number);return false;}
        if(now<allowedAt){error="Слишком частые переходы: подождите";return false;}
        target=number;category=ranges.category(number);server=ranges.server(number);deadline=now+30000;
        categoryOrder.clear();
        for(String key:HubSwapRanges.KEYS)if(key.equals(preferred))categoryOrder.add(key);
        categoryOrder.add(category);
        HubSwapRanges previousProfile=HubSwapRanges.legacy();
        if(previousProfile.group(number)>=0)categoryOrder.add(previousProfile.category(number));
        for(String key:HubSwapRanges.KEYS)categoryOrder.add(key);
        visited.clear();
        allowedAt=now+Math.max(800,delay);hubSent=false;error="";clickedCategory="";reopenedFrom="";
        enter(Stage.LOBBY,now,10000);
        return true;
    }
    void cancel(String reason){if(active()){error=reason;stage=Stage.CANCELLED;}}
    void fail(String reason){if(active()){error=reason;stage=Stage.FAILED;}}
    private void enter(Stage next,long now,long timeout){stage=next;stageDeadline=Math.min(deadline,now+timeout);}
    Action poll(Observation observation,long now){
        if(!active())return null;
        if(!observation.connected()){cancel("Соединение с сервером закрыто");return null;}
        if(now>=deadline||now>=stageDeadline){fail(switch(stage){case LOBBY->"Не получено подтверждение входа в лобби";case CONFIRM->"Сервер не подтвердил переход";case CATEGORY->"Сервер не показал доступные группы анархий";default->"Анархия №"+target+" не появилась в меню группы "+category;});return null;}
        if(observation.screen()==ScreenState.FOREIGN){cancel("Переход отменён: открыт другой экран");return null;}
        if(observation.screen()==ScreenState.WAIT||!observation.ready())return null;
        AnarchyMenu menu=observation.menu();
        if(stage==Stage.LOBBY){
            if(menu!=null){fail("Неожиданное меню при ожидании лобби");return null;}
            if(!hubSent){hubSent=true;return new Action(Kind.HUB,null,-1);}
            if(observation.lobby()){enter(Stage.CATEGORY,now,10000);return new Action(Kind.LITE,null,-1);}return null;
        }
        if(stage==Stage.CONFIRM){if(observation.current()==target)stage=Stage.DONE;return null;}
        if(menu==null)return null;
        String layout=menu.routingLayout();
        if(stage==Stage.CATEGORY)return layout.equals(reopenedFrom)?null:selectCategory(menu,now,false);

        boolean newList=!layout.equals(clickedCategory)&&menu.hasServerEntries();
        int slot=menu.serverButton(server);
        // A tab click can leave an already open list unchanged. A dual-tag tab alone is not a server list.
        if(newList||menu.serverOnlyButton(server)!=-1){
            if(slot==-2){fail("Несколько предметов имеют метку нужного сервера");return null;}
            if(slot>=0){
                enter(Stage.CONFIRM,now,8000);
                return new Action(Kind.CLICK,menu,slot);
            }
        }
        if(newList){
            if(!layout.equals(serverView)){
                serverView=layout;
                serverViewSince=now;
            }
        }else{
            serverView="";
        }
        // Let the slot updates settle before deciding that the destination is absent.
        boolean settled=newList&&now-serverViewSince>=800&&now-categoryClickedAt>=1200;
        boolean unchangedTabs=!newList&&menu.hasCategoryEntries()&&now-categoryClickedAt>=4000;
        if(!settled&&!unchangedTabs)return null;
        if(visited.size()==categoryOrder.size()){
            fail("Анархия №"+target+" не найдена в доступных группах");
            return null;
        }
        Action next=selectCategory(menu,now,true);
        if(next!=null||!active())return next;
        // Some selectors show a separate server list with no group tabs.
        reopenedFrom=layout;
        enter(Stage.CATEGORY,now,10000);
        return new Action(Kind.LITE,null,-1);
    }

    private Action selectCategory(AnarchyMenu menu,long now,boolean tabsOnly){
        for(String candidate:categoryOrder){
            if(visited.contains(candidate))continue;
            int slot=tabsOnly?menu.categoryTab(candidate):menu.categoryButton(candidate);
            if(slot==-2){
                fail("Несколько предметов имеют метку группы "+candidate);
                return null;
            }
            if(slot<0)continue;
            category=candidate;
            visited.add(candidate);
            clickedCategory=menu.routingLayout();
            categoryClickedAt=now;
            serverView="";
            enter(Stage.SERVER,now,10000);
            return new Action(Kind.CLICK,menu,slot);
        }
        return null;
    }
}
