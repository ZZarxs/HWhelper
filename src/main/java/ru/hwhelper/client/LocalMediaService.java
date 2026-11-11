package ru.hwhelper.client;

import com.google.gson.*;
import ru.hwhelper.client.standalone.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Owns the native reader on its MTA thread and publishes immutable snapshots to the HUD. */
final class LocalMediaService implements AutoCloseable {
    record Update(JsonObject data,byte[] cover){}
    private final HwConfig config;private final Consumer<Update> sink;
    private final WindowsMediaReader media=new WindowsMediaReader();private final LyricsProvider lyrics=new LyricsProvider();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"hwhelper-windows-media");t.setDaemon(true);return t;});
    private final ScheduledFuture<?> polling;private volatile boolean closed;private volatile String status="Подключаю медиасессию Windows…";
    private long retry;
    LocalMediaService(HwConfig config,Consumer<Update> sink){this.config=config;this.sink=sink;polling=worker.scheduleWithFixedDelay(this::poll,0,350,TimeUnit.MILLISECONDS);}
    String status(){return status;}
    private void poll(){
        if(closed||!config.musicEnabled&&!config.worldLyrics.enabled||System.nanoTime()<retry)return;
        try{
            NativeBinaries.architecture();var session=media.poll();JsonObject data=new JsonObject();
            data.addProperty("title",session.title());data.addProperty("artist",session.artist());data.addProperty("source",session.source());data.addProperty("playing",session.playing());
            data.addProperty("position",session.position());data.addProperty("duration",session.duration());data.addProperty("playbackRate",session.rate());data.addProperty("sampledAt",System.currentTimeMillis()/1000.0);data.addProperty("protocolVersion",3);
            data.addProperty("status",session.status());data.addProperty("coverKey",session.coverKey());
            if(!session.title().isBlank()){
                var result=lyrics.get(session.title(),session.artist(),session.duration());JsonArray timeline=new JsonArray();String line="";double start=0,end=0;
                for(int i=0;i<result.cues().size();i++){
                    var cue=result.cues().get(i);JsonObject row=new JsonObject();row.addProperty("time",cue.time());row.addProperty("text",cue.text());timeline.add(row);
                    if(cue.time()<=session.position()){line=cue.text();start=cue.time();end=i+1<result.cues().size()?result.cues().get(i+1).time():session.duration();}
                }
                data.add("lyrics",timeline);data.addProperty("lyric",line);data.addProperty("lyricStart",start);data.addProperty("lyricEnd",Math.max(start,end));data.addProperty("lyricsAvailable",!result.cues().isEmpty());data.addProperty("lyricsStatus",result.status());
                status="Windows: "+(session.playing()?"музыка играет":"пауза");
            }else status="Windows: нет активной музыки";
            if(!closed)sink.accept(new Update(data,session.cover()));
        }catch(Exception|LinkageError e){
            media.close();retry=System.nanoTime()+3_000_000_000L;
            status=e.getMessage()!=null&&e.getMessage().contains("80070005")?"Windows запретила доступ к медиасессии":"Музыка: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
            JsonObject empty=new JsonObject();empty.addProperty("status","unavailable");if(!closed)sink.accept(new Update(empty,new byte[0]));
        }
    }
    public void close(){closed=true;polling.cancel(false);lyrics.close();worker.execute(media::close);worker.shutdown();}
}
