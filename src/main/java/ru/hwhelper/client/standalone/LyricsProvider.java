package ru.hwhelper.client.standalone;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Shared LRC data for the island and world lyrics. Network work never runs on the render/media thread. */
public final class LyricsProvider implements AutoCloseable {
    public record Cue(double time,String text){}
    public record Result(String status,List<Cue> cues){}
    private static final Pattern STAMP=Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]");
    private static final Pattern OFFSET=Pattern.compile("(?im)^\\[offset:([+-]?\\d+)\\]");
    private record Key(String title,String artist,long duration){}
    private static final class Entry {volatile Result value=new Result("loading",List.of());long tried;}
    private final LinkedHashMap<Key,Entry> cache=new LinkedHashMap<>(16,.75f,true);
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"hwhelper-lyrics");t.setDaemon(true);return t;});
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private volatile boolean closed;
    public synchronized Result get(String title,String artist,double duration){
        Key key=new Key(title,artist,Math.round(duration));Entry entry=cache.get(key);long now=System.nanoTime();
        if(entry==null){entry=new Entry();cache.put(key,entry);if(cache.size()>100)cache.remove(cache.keySet().iterator().next());}
        if(!closed&&(entry.tried==0||entry.value.cues().isEmpty()&&!entry.value.status().equals("instrumental")&&now-entry.tried>60_000_000_000L)){
            entry.tried=now;Entry current=entry;worker.execute(()->{try{current.value=load(key);}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();current.value=new Result("missing",List.of());}});
        }return entry.value;
    }
    private Result load(Key key)throws Exception {
        LinkedHashMap<String,String> params=new LinkedHashMap<>();params.put("track_name",key.title());params.put("artist_name",key.artist());if(key.duration()>1)params.put("duration",Long.toString(key.duration()));
        JsonObject found=null;
        try{JsonElement response=request("get",params);if(response.isJsonObject()&&matches(response.getAsJsonObject(),key.title(),key.artist(),key.duration()))found=response.getAsJsonObject();}catch(java.io.IOException ignored){}
        if(found==null){params.remove("duration");JsonElement response=request("search",params);if(response.isJsonArray())found=select(response.getAsJsonArray(),key.title(),key.artist(),key.duration());}
        if(found==null)return new Result("missing",List.of());
        List<Cue> cues=parse(string(found,"syncedLyrics"));
        return new Result(!cues.isEmpty()?"synced":found.has("instrumental")&&found.get("instrumental").getAsBoolean()?"instrumental":"missing",cues);
    }
    private JsonElement request(String endpoint,Map<String,String> params)throws Exception {
        StringJoiner query=new StringJoiner("&");for(var e:params.entrySet())query.add(URLEncoder.encode(e.getKey(),java.nio.charset.StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),java.nio.charset.StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(URI.create("https://lrclib.net/api/"+endpoint+"?"+query)).timeout(Duration.ofSeconds(6)).header("User-Agent","HWhelper/3.2.0 Minecraft music HUD").GET().build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200||response.body().length()>2_000_000)throw new java.io.IOException("Lyrics unavailable");return JsonParser.parseString(response.body());
    }
    public static JsonObject select(JsonArray rows,String title,String artist,double duration){
        JsonObject best=null;double delta=Double.POSITIVE_INFINITY;
        for(JsonElement element:rows)if(element.isJsonObject()){
            JsonObject row=element.getAsJsonObject();if(!matches(row,title,artist,duration)||string(row,"syncedLyrics").isBlank())continue;
            double distance=duration>1?Math.abs(number(row,"duration")-duration):0;if(distance<delta){best=row;delta=distance;}
        }return best;
    }
    private static boolean matches(JsonObject row,String title,String artist,double duration){return normalize(string(row,"trackName")).equals(normalize(title))&&normalize(string(row,"artistName")).equals(normalize(artist))&&(duration<=1||Math.abs(number(row,"duration")-duration)<=5);}
    public static List<Cue> parse(String text){
        if(text==null||text.isBlank())return List.of();double offset=0;Matcher offsetMatcher=OFFSET.matcher(text);if(offsetMatcher.find())try{offset=Math.max(-600,Math.min(600,Double.parseDouble(offsetMatcher.group(1))/1000));}catch(NumberFormatException ignored){}
        List<Cue> cues=new ArrayList<>();
        for(String line:text.split("\\R")){Matcher matcher=STAMP.matcher(line);String value=line.replaceAll("\\[[^]]*]","").strip();while(matcher.find()&&cues.size()<10000){double seconds=Double.parseDouble(matcher.group(2));if(seconds>=60)continue;double time=Double.parseDouble(matcher.group(1))*60+seconds+offset;if(Double.isFinite(time))cues.add(new Cue(Math.max(0,time),value));}}
        cues.sort(Comparator.comparingDouble(Cue::time));return List.copyOf(cues);
    }
    public static String normalize(String value){return value.replaceAll("(?i)\\s*[\\[(](?:official[^]\\)]*|lyrics?|audio|video)[]\\)]","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").strip();}
    private static String string(JsonObject object,String key){try{return object.get(key).isJsonNull()?"":object.get(key).getAsString();}catch(RuntimeException e){return "";}}
    private static double number(JsonObject object,String key){try{return object.get(key).getAsDouble();}catch(RuntimeException e){return 0;}}
    public void close(){closed=true;worker.shutdownNow();http.shutdownNow();}
}
