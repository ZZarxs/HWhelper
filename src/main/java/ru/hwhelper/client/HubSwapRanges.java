package ru.hwhelper.client;

import java.util.*;

/** Configurable contiguous category ranges; preset matches the supplied krsHubSwap 1.0. */
public final class HubSwapRanges {
    public static final int LIMIT=999;
    public int total=66,soloEnd=15,duoEnd=31,trioEnd=47;
    static final String[] KEYS={"solo","duo","trio","clans"};
    static final String[] NAMES={"СолоЛайт","ДуоЛайт","ТриоЛайт","КланЛайт"};
    public HubSwapRanges(){}
    HubSwapRanges(int total,int solo,int duo,int trio){this.total=total;soloEnd=solo;duoEnd=duo;trioEnd=trio;validate();}
    static HubSwapRanges legacy(){return new HubSwapRanges(36,9,19,29);}
    void validate(){if(total<4||total>LIMIT||soloEnd<1||duoEnd<=soloEnd||trioEnd<=duoEnd||total<=trioEnd)throw new IllegalArgumentException("Диапазоны должны покрывать 1–"+total+" подряд без пересечений; максимум "+LIMIT);}
    HubSwapRanges copy(){return new HubSwapRanges(total,soloEnd,duoEnd,trioEnd);}
    int group(int n){return n<1||n>total?-1:n<=soloEnd?0:n<=duoEnd?1:n<=trioEnd?2:3;}
    String category(int n){int g=group(n);if(g<0)throw new IllegalArgumentException("Номер вне диапазона 1–"+total);return KEYS[g];}
    String server(int n){category(n);return n==1?"lanarchy":"lanarchy"+n;}
    String name(int n){int g=group(n);return g<0?"Не определена":NAMES[g]+" #"+n;}
    String range(int group){int[] ends={0,soloEnd,duoEnd,trioEnd,total};return (ends[group]+1)+"–"+ends[group+1];}
    static HubSwapRanges parse(int total,List<String> ranges){
        if(ranges.size()!=4)throw new IllegalArgumentException("Нужны четыре диапазона");int start=1;int[] ends=new int[4];
        for(int i=0;i<4;i++){
            String[] parts=ranges.get(i).strip().split("\\s*[-–—]\\s*");
            if(parts.length!=2)throw new IllegalArgumentException("Формат диапазона: 1-15");
            int min=Integer.parseInt(parts[0]),max=Integer.parseInt(parts[1]);
            if(min!=start||max<min)throw new IllegalArgumentException("Диапазоны должны идти подряд с 1, без пропусков и пересечений");
            ends[i]=max;start=max+1;
        }
        if(ends[3]!=total)throw new IllegalArgumentException("Последний диапазон должен заканчиваться на "+total);
        return new HubSwapRanges(total,ends[0],ends[1],ends[2]);
    }
    static List<String> aliases(Collection<String> input){
        LinkedHashSet<String> names=new LinkedHashSet<>();names.add("an");
        for(String raw:input){if(raw==null)continue;String name=raw.strip().replaceFirst("^/+","").toLowerCase(Locale.ROOT);
            if(!name.matches("[a-z][a-z0-9_]{0,23}"))throw new IllegalArgumentException("Алиасы: латинские буквы, цифры и _, без пробелов");
            if(name.matches("an[0-9].*"))throw new IllegalArgumentException("Алиасы anN зарезервированы для коротких команд");
            if(Set.of("hub","lite","help","msg","login","register").contains(name))throw new IllegalArgumentException("Нельзя перекрывать серверную команду /"+name);
            names.add(name);
        }if(names.size()>12)throw new IllegalArgumentException("Не более 12 алиасов");return new ArrayList<>(names);
    }
}
