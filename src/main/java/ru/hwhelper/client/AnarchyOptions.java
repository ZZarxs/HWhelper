package ru.hwhelper.client;

import java.util.ArrayList;
import java.util.List;

/** Preferences only; no server commands or connection state are serialized. */
public final class AnarchyOptions {
    public boolean enabled=true,solo=true,duo=true,trio=true,clan=true;
    public boolean favoritesOnly=false,wrap=true,feedback=true;
    public int delayMs=1200,lastSelected=0;
    public List<Integer> favorites=new ArrayList<>();
    void sanitize(){
        delayMs=Math.max(800,Math.min(5000,delayMs));
        if(lastSelected<0||lastSelected>36)lastSelected=0;
        if(favorites==null)favorites=new ArrayList<>();
        favorites=new ArrayList<>(favorites.stream().filter(n->n!=null&&n>=1&&n<=36).distinct().sorted().toList());
    }
    boolean allows(int number){return switch(AnarchyNavigation.group(number)){case 0->solo;case 1->duo;case 2->trio;case 3->clan;default->false;};}
}
