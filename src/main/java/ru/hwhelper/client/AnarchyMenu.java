package ru.hwhelper.client;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.*;

/** Immutable, visible server-menu data. Player-inventory slots are never candidates. */
record AnarchyMenu(int containerId,int stateId,String title,List<AnarchyMenu.Entry> entries) {
    record Entry(int slot,String item,String name,List<String> lore,int count,boolean playerSlot) {
        Entry{lore=List.copyOf(lore);}
        String text(){return normalize(name+" "+String.join(" ",lore));}
    }
    AnarchyMenu{entries=List.copyOf(entries);}
    static String normalize(String text){return text.replaceAll("§.","").toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("[\\u200B-\\u200D\\uFEFF]","").replaceAll("[^\\p{L}\\p{N}#]+"," ").strip();}
    private static boolean legacy(String text){return text.contains("1 20")||text.contains("классическ");}
    boolean lite(){String text=normalize(title);return text.contains("лайт")&&text.contains("анарх")&&!legacy(text);}
    boolean modes(){String text=normalize(title);return !lite()&&text.contains("выбор")&&(text.contains("режим")||text.contains("сервер"));}
    private List<Entry> eligible(){return containerId>0?entries.stream().filter(e->!e.playerSlot()&&e.count()>0).toList():List.of();}
    /** -1 absent, -2 ambiguous; callers must never choose the first ambiguous item. */
    private int unique(Predicate<Entry> test){var rows=eligible().stream().filter(test).toList();return rows.isEmpty()?-1:rows.size()==1?rows.getFirst().slot():-2;}
    private static int groupName(String text){
        String name=normalize(text);int found=-1;
        String[] names={"соло","дуо","трио","клан"};
        for(int i=0;i<names.length;i++)if(Pattern.compile("(?:^| )"+names[i]+"(?:лайт)?(?: |$)").matcher(name).find()){if(found>=0)return -2;found=i;}
        return found;
    }
    int modeButton(){return unique(e->{String text=e.text();return (text.contains("лайт")||text.contains("lite"))&&text.contains("анарх")&&!legacy(text)&&groupName(e.name())<0;});}
    int groupButton(int group){return unique(e->groupName(e.name())==group&&!normalize(e.name()).contains("#")&&!legacy(e.text()));}
    int selectedGroup(){
        int titleGroup=groupName(title);if(titleGroup>=0)return titleGroup;
        var selected=eligible().stream().filter(e->groupName(e.name())>=0&&!normalize(e.name()).contains("#")&&Pattern.compile("(?:^| )(?:выбрано|выбрана|выбран|текущая|текущий)(?: |$)").matcher(e.text()).find()&&!e.text().contains("не выбран")).toList();
        return selected.size()==1?groupName(selected.getFirst().name()):-1;
    }
    int targetButton(int number,int confirmedGroup){
        return unique(e->{
            String text=e.text();if(legacy(text))return false;
            int group=groupName(e.name());if(group<0)group=groupName(text);if(group==-2)return false;
            if(group>=0&&group!=AnarchyNavigation.group(number))return false;
            if(group<0&&confirmedGroup!=AnarchyNavigation.group(number))return false;
            // Number comes from the item name, never from online counts or incidental lore numbers.
            Matcher match=Pattern.compile("(?:#\\s*|(?:анархия|сервер)\\s+)(\\d+)(?!\\d)").matcher(normalize(e.name()));
            if(!match.find())return false;
            try{return Integer.parseInt(match.group(1))==number;}catch(NumberFormatException ignored){return false;}
        });
    }
    /** Ignore live online counts in lore; those are not proof that a tab has changed. */
    String layout(){StringBuilder key=new StringBuilder(containerId+"/"+normalize(title));for(Entry e:eligible())key.append('|').append(e.slot()).append(':').append(e.item()).append(':').append(normalize(e.name())).append(':').append(e.count());return key.toString();}
}
