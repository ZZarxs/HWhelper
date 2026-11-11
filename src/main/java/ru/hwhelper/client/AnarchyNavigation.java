package ru.hwhelper.client;

import java.util.*;
import java.util.regex.*;

/** HollyWorld Lite numbers are global: Solo 1–9, Duo 10–19, Trio 20–29, Clan 30–36. */
final class AnarchyNavigation {
    private static final String[] NAMES={"СолоЛайт","ДуоЛайт","ТриоЛайт","КланЛайт"};
    private static final Pattern CURRENT=Pattern.compile("(?:СолоЛайт|ДуоЛайт|ТриоЛайт|КланЛайт)\\s*#\\s*(\\d+)(?!\\d)",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    static int group(int number){return number<1||number>36?-1:number<10?0:number<20?1:number<30?2:3;}
    static String name(int number){int group=group(number);return group<0?"Не определена":NAMES[group]+" #"+number;}
    static int parseCurrent(String text){
        if(text==null)return 0;
        Matcher matcher=CURRENT.matcher(text.replaceAll("§.","").replace('\u00A0',' '));
        if(!matcher.find())return 0;
        try{int number=Integer.parseInt(matcher.group(1));return group(number)<0?0:number;}catch(NumberFormatException e){return 0;}
    }
    static List<Integer> available(AnarchyOptions options){
        List<Integer> result=new ArrayList<>();
        for(int number=1;number<=36;number++)if(options.allows(number)&&(!options.favoritesOnly||options.favorites.contains(number)))result.add(number);
        return List.copyOf(result);
    }
    static int step(List<Integer> numbers,int from,int direction,boolean wrap){
        if(numbers.isEmpty()||direction==0)return 0;
        if(from==0)return direction>0?numbers.getFirst():numbers.getLast();
        int index=Collections.binarySearch(numbers,from);
        int next=index>=0?index+(direction>0?1:-1):direction>0?-index-1:-index-2;
        if(next<0||next>=numbers.size()){if(!wrap)return 0;next=next<0?numbers.size()-1:0;}
        int value=numbers.get(next);return value==from?0:value;
    }
    static boolean matches(int number,String query){
        String text=query.toLowerCase(Locale.ROOT).strip();
        if(text.isBlank())return true;
        if(text.matches("(?:/?an)?\\d+"))try{return number==Integer.parseInt(text.replaceFirst("^/?an",""));}catch(NumberFormatException e){return false;}
        return name(number).toLowerCase(Locale.ROOT).contains(text);
    }
    static String command(int number){if(group(number)<0)throw new IllegalArgumentException("Unsupported Lite number");return "an"+number;}
}
