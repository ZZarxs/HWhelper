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
        return new Routing(values.getStringOr(TYPE_KEY,""),values.getStringOr(SERVER_KEY,""));
    }
    private int unique(Predicate<Entry> test){
        var rows=entries.stream().filter(e->containerId>0&&!e.playerSlot()&&e.count()>0&&test.test(e)).toList();
        return rows.isEmpty()?-1:rows.size()==1?rows.getFirst().slot():-2;
    }
    int categoryButton(String category){return unique(e->e.category().equals(category));}
    int serverButton(String key,String category){return unique(e->e.server().equals(key)&&(e.category().isBlank()||e.category().equals(category)));}
    String routingLayout(){StringBuilder result=new StringBuilder(Integer.toString(containerId));for(Entry e:entries)if(!e.playerSlot())result.append('|').append(e.slot()).append(':').append(e.category()).append(':').append(e.server());return result.toString();}
}
