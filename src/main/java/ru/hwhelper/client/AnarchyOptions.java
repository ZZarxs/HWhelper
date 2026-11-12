package ru.hwhelper.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Preferences only; no server commands or connection state are serialized. */
public final class AnarchyOptions {
    public boolean enabled=true,solo=true,duo=true,trio=true,clan=true;
    public boolean favoritesOnly=false,wrap=true,feedback=true;
    public int delayMs=1200,lastSelected=0;
    public HubSwapRanges ranges=new HubSwapRanges();
    public List<String> aliases=new ArrayList<>(List.of("an","lt"));
    public boolean hubSwapImported=false;
    public Map<Integer,String> detectedCategories=new HashMap<>();
    public List<Integer> favorites=new ArrayList<>();
    void sanitize(){
        delayMs=Math.max(800,Math.min(5000,delayMs));
        if(ranges==null)ranges=new HubSwapRanges();try{ranges.validate();}catch(IllegalArgumentException e){ranges=new HubSwapRanges();}
        try{aliases=HubSwapRanges.aliases(aliases==null?List.of("an","lt"):aliases);}catch(IllegalArgumentException e){aliases=new ArrayList<>(List.of("an","lt"));}
        if(lastSelected<0||lastSelected>ranges.total)lastSelected=0;
        if(favorites==null)favorites=new ArrayList<>();
        favorites=new ArrayList<>(favorites.stream().filter(n->n!=null&&n>=1&&n<=ranges.total).distinct().sorted().toList());
        if(detectedCategories==null)detectedCategories=new HashMap<>();
        detectedCategories.entrySet().removeIf(e->e.getKey()==null||ranges.group(e.getKey())<0||e.getValue()==null||!Arrays.asList(HubSwapRanges.KEYS).contains(e.getValue()));
    }
    int group(int number){
        String key=detectedCategories.get(number);
        return key==null?ranges.group(number):Arrays.asList(HubSwapRanges.KEYS).indexOf(key);
    }
    String category(int number){int g=group(number);return g<0?"":HubSwapRanges.KEYS[g];}
    String name(int number){int g=group(number);return g<0?"Не определена":HubSwapRanges.NAMES[g]+" #"+number;}
    boolean remember(int number,String key){
        if(ranges.group(number)<0||key==null||!Arrays.asList(HubSwapRanges.KEYS).contains(key))return false;
        return !key.equals(detectedCategories.put(number,key));
    }
    boolean allows(int number){return switch(group(number)){case 0->solo;case 1->duo;case 2->trio;case 3->clan;default->false;};}
}
