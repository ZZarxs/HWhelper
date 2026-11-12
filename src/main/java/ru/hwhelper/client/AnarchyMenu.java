package ru.hwhelper.client;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;

/** Selector protocol observed in the user-supplied krsHubSwap JAR; no translated-label heuristics. */
record AnarchyMenu(int containerId,int stateId,String title,List<AnarchyMenu.Entry> entries) {
    static final String TYPE_KEY="advancedserverselecter:server-type",SERVER_KEY="advancedserverselecter:server";
    record Routing(String category,String server){}
    record Entry(int slot,String item,String name,List<String> lore,int count,boolean playerSlot,String category,String server) {
        Entry{lore=List.copyOf(lore);}
    }
    AnarchyMenu{entries=List.copyOf(entries);}
    static String normalize(String text){return text.replaceAll("§.","").toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("[\\u200B-\\u200D\\uFEFF]","").replaceAll("[^\\p{L}\\p{N}#]+"," ").strip();}
    static Routing routing(CompoundTag customData){
        CompoundTag values=customData.getCompoundOrEmpty("PublicBukkitValues");
        return new Routing(categoryKey(values.getStringOr(TYPE_KEY,"")),values.getStringOr(SERVER_KEY,"").strip().toLowerCase(Locale.ROOT));
    }
    private int unique(Predicate<Entry> test){
        var rows=entries.stream().filter(e->containerId>0&&!e.playerSlot()&&e.count()>0&&test.test(e)).toList();
        return rows.isEmpty()?-1:rows.size()==1?rows.getFirst().slot():-2;
    }
    static String categoryKey(String value){
        String text=value.strip().toLowerCase(Locale.ROOT);
        return switch(text.replaceAll("[\\s_-]","")){
            case "solo","sololite","litesolo","сололайт","соло"->"solo";
            case "duo","duolite","liteduo","дуолайт","дуо"->"duo";
            case "trio","triolite","litetrio","триолайт","трио"->"trio";
            case "clan","clans","clanlite","clanslite","liteclan","кланлайт","клан"->"clans";
            default->text;
        };
    }
    int categoryTab(String category){return unique(e->e.category().equals(category)&&e.server().isBlank());}
    int categoryButton(String category){
        int tab=categoryTab(category);
        return tab==-1?unique(e->e.category().equals(category)):tab;
    }
    // The canonical lanarchyN identifier is global; a stale category hint must not hide the exact destination.
    int serverButton(String key){return unique(e->e.server().equals(key));}
    int serverOnlyButton(String key){return unique(e->e.server().equals(key)&&e.category().isBlank());}
    boolean hasServerEntries(){return entries.stream().anyMatch(e->!e.playerSlot()&&e.count()>0&&e.server().matches("lanarchy(?:[1-9][0-9]*)?"));}
    boolean hasCategoryEntries(){return Arrays.stream(HubSwapRanges.KEYS).anyMatch(key->categoryTab(key)>=0);}
    String routingLayout(){
        StringBuilder result=new StringBuilder(Integer.toString(containerId));
        for(Entry e:entries){
            if(e.playerSlot()||e.count()<=0||e.category().isBlank()&&e.server().isBlank())continue;
            result.append('|').append(e.slot()).append(':').append(e.category()).append(':').append(e.server());
        }
        return result.toString();
    }
}
