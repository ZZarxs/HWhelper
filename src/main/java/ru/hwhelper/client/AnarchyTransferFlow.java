package ru.hwhelper.client;

/** One cancellable /hub → /lite → PDC category → PDC server route, adapted to native 1.21.11 APIs. */
final class AnarchyTransferFlow {
    enum Stage { IDLE,LOBBY,CATEGORY,SERVER,CONFIRM,DONE,FAILED,CANCELLED }
    enum Kind { HUB,LITE,CLICK }
    record Action(Kind kind,AnarchyMenu menu,int slot){}
    record Observation(boolean connected,boolean ready,int current,boolean lobby,boolean foreignScreen,AnarchyMenu menu){}
    private Stage stage=Stage.IDLE;private int target;private long deadline,stageDeadline,allowedAt;private boolean hubSent;
    private String category="",server="",error="",clickedCategory="";
    boolean active(){return switch(stage){case LOBBY,CATEGORY,SERVER,CONFIRM->true;default->false;};}
    Stage stage(){return stage;}int target(){return target;}String error(){return error;}
    long remaining(long now){return Math.max(0,allowedAt-now);}
    boolean begin(int number,int current,long now,int delay){return begin(number,current,now,delay,new HubSwapRanges());}
    boolean begin(int number,int current,long now,int delay,HubSwapRanges ranges){
        if(active()){error="Сначала дождитесь перехода или отмените его через Escape";return false;}
        if(ranges.group(number)<0){error="Допустимы анархии 1–"+ranges.total;return false;}
        if(number==current){error="Вы уже на "+ranges.name(number);return false;}
        if(now<allowedAt){error="Слишком частые переходы: подождите";return false;}
        target=number;category=ranges.category(number);server=ranges.server(number);deadline=now+30000;
        allowedAt=now+Math.max(800,delay);hubSent=false;error="";clickedCategory="";enter(Stage.LOBBY,now,10000);return true;
    }
    void cancel(String reason){if(active()){error=reason;stage=Stage.CANCELLED;}}
    void fail(String reason){if(active()){error=reason;stage=Stage.FAILED;}}
    private void enter(Stage next,long now,long timeout){stage=next;stageDeadline=Math.min(deadline,now+timeout);}
    Action poll(Observation observation,long now){
        if(!active())return null;
        if(!observation.connected()){cancel("Соединение с сервером закрыто");return null;}
        if(now>=deadline||now>=stageDeadline){fail(switch(stage){case LOBBY->"Не получено подтверждение входа в лобби";case CONFIRM->"Сервер не подтвердил переход";default->"Не найдена нужная NBT-метка в меню выбора анархий";});return null;}
        if(observation.foreignScreen()){cancel("Переход отменён: открыт другой экран");return null;}
        if(!observation.ready())return null;
        AnarchyMenu menu=observation.menu();
        if(stage==Stage.LOBBY){
            if(menu!=null){fail("Неожиданное меню при ожидании лобби");return null;}
            if(!hubSent){hubSent=true;return new Action(Kind.HUB,null,-1);}
            if(observation.lobby()){enter(Stage.CATEGORY,now,10000);return new Action(Kind.LITE,null,-1);}return null;
        }
        if(stage==Stage.CONFIRM){if(observation.current()==target)stage=Stage.DONE;return null;}
        if(menu==null)return null;
        if(stage==Stage.CATEGORY){
            int slot=menu.categoryButton(category);
            if(slot==-2){fail("Несколько предметов имеют метку нужной группы");return null;}
            if(slot>=0){clickedCategory=menu.routingLayout();enter(Stage.SERVER,now,10000);return new Action(Kind.CLICK,menu,slot);}
        }else if(stage==Stage.SERVER&&!menu.routingLayout().equals(clickedCategory)){
            int slot=menu.serverButton(server,category);
            if(slot==-2){fail("Несколько предметов имеют метку нужного сервера");return null;}
            if(slot>=0){enter(Stage.CONFIRM,now,8000);return new Action(Kind.CLICK,menu,slot);}
        }return null;
    }
}
