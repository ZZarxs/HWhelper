package ru.hwhelper.client;

import com.google.gson.*;
import java.util.*;

/** SmartSplit from zzz, with chunk times kept strictly inside their original LRC interval. */
final class LyricTimeline {
    record Line(double start,double end,String text){}
    static List<Line> read(JsonObject media,boolean split){
        List<Line> result=new ArrayList<>();JsonArray rows=media.has("lyrics")&&media.get("lyrics").isJsonArray()?media.getAsJsonArray("lyrics"):new JsonArray();
        for(int i=0;i<rows.size();i++)try{
            JsonObject row=rows.get(i).getAsJsonObject();double start=row.get("time").getAsDouble();
            double end=i+1<rows.size()?rows.get(i+1).getAsJsonObject().get("time").getAsDouble():number(media,"duration",start+4);
            String text=row.get("text").getAsString().strip();
            if(!text.isBlank()&&Double.isFinite(start)&&end>start)result.add(new Line(start,end,text));
        }catch(RuntimeException ignored){}
        if(result.isEmpty()){
            String text=string(media,"lyric");double start=number(media,"lyricStart",0),end=number(media,"lyricEnd",start+4);
            if(!text.isBlank()&&end>start)result.add(new Line(start,end,text));
        }
        result.sort(Comparator.comparingDouble(Line::start));
        return split?split(result):List.copyOf(result);
    }
    static List<Line> split(List<Line> source){
        List<Line> result=new ArrayList<>();
        for(Line line:source){String[] words=line.text().strip().split("\\s+");
            if(words.length<=4){result.add(line);continue;}
            int per=words.length>7?3:2;List<String> chunks=new ArrayList<>();
            for(int i=0;i<words.length;i+=per){int end=Math.min(words.length,i+per);if(words.length-end==1)end++;chunks.add(String.join(" ",Arrays.copyOfRange(words,i,end)));if(end>i+per)i++;}
            double step=(line.end()-line.start())/chunks.size();
            for(int i=0;i<chunks.size();i++)result.add(new Line(line.start()+i*step,line.start()+(i+1)*step,chunks.get(i)));
        }return List.copyOf(result);
    }
    static String string(JsonObject value,String key){try{return value.get(key).getAsString();}catch(RuntimeException e){return "";}}
    static double number(JsonObject value,String key,double fallback){try{double n=value.get(key).getAsDouble();return Double.isFinite(n)?n:fallback;}catch(RuntimeException e){return fallback;}}
}
