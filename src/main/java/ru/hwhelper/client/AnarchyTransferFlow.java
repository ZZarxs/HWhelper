package ru.hwhelper.client;

/** Pure transition engine. Every returned action is single-use and must be applied to its exact snapshot. */
final class AnarchyTransferFlow {
    enum Stage { IDLE,LOBBY,MODES,LITE,GROUP,CONFIRM,DONE,FAILED,CANCELLED }
    enum Kind { HUB,COMPASS,CLICK }
    record Action(Kind kind,AnarchyMenu menu,int slot){}
    record Observation(boolean connected,boolean ready,int current,boolean lobby,boolean foreignScreen,AnarchyMenu menu){}
    private Stage stage=Stage.IDLE;private int target;private long deadline,stageDeadline,allowedAt;private boolean hubSent;
    private String clickedLayout="",error="";
    boolean active(){return switch(stage){case LOBBY,MODES,LITE,GROUP,CONFIRM->true;default->false;};}
    Stage stage(){return stage;}int target(){return target;}String error(){return error;}
    long remaining(long now){return Math.max(0,allowedAt-now);}
    boolean begin(int number,int current,long now,int delay){
        if(active()){error="Сначала дождитесь перехода или отмените его через Escape";return false;}
        if(AnarchyNavigation.group(number)<0){error="Допустимы анархии 1–36";return false;}
        if(number==current){error="Вы уже на "+AnarchyNavigation.name(number);return false;}
        if(now<allowedAt){error="Слишком частые переходы: подождите";return false;}
        target=number;deadline=now+30000;allowedAt=now+Math.max(800,delay);hubSent=false;clickedLayout="";error="";enter(Stage.LOBBY,now,10000);return true;
    }
    void cancel(String reason){if(active()){error=reason;stage=Stage.CANCELLED;}}
    void fail(String reason){if(active()){error=reason;stage=Stage.FAILED;}}
    private void enter(Stage next,long now,long timeout){stage=next;stageDeadline=Math.min(deadline,now+timeout);}
    Action poll(Observation observation,long now){
        if(!active())return null;
        if(!observation.connected()){cancel("Соединение с сервером закрыто");return null;}
        if(now>=deadline||now>=stageDeadline){fail(switch(stage){case LOBBY->"Не найден компас выбора режима в лобби";case CONFIRM->"Сервер не подтвердил переход";default->"Не получено ожидаемое меню или нужная кнопка";});return null;}
        if(observation.foreignScreen()){cancel("Переход отменён: открыт другой экран");return null;}
        if(!observation.ready())return null;
        AnarchyMenu menu=observation.menu();
        if(stage==Stage.LOBBY){
            if(menu!=null){fail("Неожиданное меню при ожидании лобби");return null;}
            if(observation.lobby()){enter(Stage.MODES,now,5000);return new Action(Kind.COMPASS,null,-1);}
            if(!hubSent){hubSent=true;return new Action(Kind.HUB,null,-1);}return null;
        }
        if(stage==Stage.CONFIRM){
            if(observation.current()==target){stage=Stage.DONE;return null;}
            if(menu!=null&&!menu.lite()){fail("Переход прерван другим серверным меню");}return null;
        }
        if(menu==null)return null;
        if(stage==Stage.MODES){
            if(menu.lite()){enter(Stage.LITE,now,5000);return chooseLite(menu,now);}
            if(!menu.modes()){fail("Не распознано меню выбора режимов");return null;}
            int slot=menu.modeButton();if(slot==-2){fail("Кнопка Лайт-анархии неоднозначна");return null;}
            if(slot<0)return null;clickedLayout=menu.layout();enter(Stage.LITE,now,5000);return new Action(Kind.CLICK,menu,slot);
        }
        if(stage==Stage.LITE){
            if(menu.layout().equals(clickedLayout))return null;
            if(!menu.lite()){if(!menu.modes())fail("Не распознано меню Лайт-анархий");return null;}
            return chooseLite(menu,now);
        }
        if(stage==Stage.GROUP){
            if(!menu.lite()){fail("Меню группы анархий изменилось");return null;}
            if(menu.layout().equals(clickedLayout)&&menu.selectedGroup()!=AnarchyNavigation.group(target))return null;
            int selected=menu.selectedGroup();
            if(selected>=0&&selected!=AnarchyNavigation.group(target))return null; // Old tab contents may arrive before the new inventory.
            // A new container ID is not proof of a category: require a selected-tab marker/title
            // for generic item names, or an explicit group in the server item's name/lore.
            return chooseTarget(menu,selected,now);
        }return null;
    }
    private Action chooseLite(AnarchyMenu menu,long now){
        int selected=menu.selectedGroup();int direct=menu.targetButton(target,selected);
        if(direct==-2){fail("Найдено несколько кнопок нужной анархии");return null;}
        if(direct>=0)return finalClick(menu,direct,now);
        if(selected==AnarchyNavigation.group(target))return null; // Wait for delayed item contents.
        int slot=menu.groupButton(AnarchyNavigation.group(target));
        if(slot==-2){fail("Кнопка группы анархий неоднозначна");return null;}
        if(slot<0)return null;
        clickedLayout=menu.layout();enter(Stage.GROUP,now,5000);return new Action(Kind.CLICK,menu,slot);
    }
    private Action chooseTarget(AnarchyMenu menu,int selected,long now){
        int slot=menu.targetButton(target,selected);
        if(slot==-2){fail("Найдено несколько кнопок нужной анархии");return null;}
        return slot<0?null:finalClick(menu,slot,now);
    }
    private Action finalClick(AnarchyMenu menu,int slot,long now){enter(Stage.CONFIRM,now,8000);return new Action(Kind.CLICK,menu,slot);}
}
