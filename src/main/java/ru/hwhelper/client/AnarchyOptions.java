package ru.hwhelper.client;

import java.util.ArrayList;
import java.util.List;

/** Preferences only; no server commands or connection state are serialized. */
public final class AnarchyOptions {
    public boolean enabled=true,solo=true,duo=true,trio=true,clan=true;
    public boolean favoritesOnly=false,wrap=true,feedback=true;
    public int delayMs=1200,lastSelected=0;
    public HubSwapRanges ranges=new HubSwapRanges();
    public List<String> aliases=new ArrayList<>(List.of("an","lt"));
    public boolean hubSwapImported=false;
    public List<Integer> favorites=new ArrayList<>();
    void sanitize(){
        delayMs=Math.max(800,Math.min(5000,delayMs));
        if(ranges==null)ranges=new HubSwapRanges();try{ranges.validate();}catch(IllegalArgumentException e){ranges=new HubSwapRanges();}
        try{aliases=HubSwapRanges.aliases(aliases==null?List.of("an","lt"):aliases);}catch(IllegalArgumentException e){aliases=new ArrayList<>(List.of("an","lt"));}
        if(lastSelected<0||lastSelected>ranges.total)lastSelected=0;
        if(favorites==null)favorites=new ArrayList<>();
        favorites=new ArrayList<>(favorites.stream().filter(n->n!=null&&n>=1&&n<=ranges.total).distinct().sorted().toList());
    }
    boolean allows(int number){return switch(ranges.group(number)){case 0->solo;case 1->duo;case 2->trio;case 3->clan;default->false;};}
}
