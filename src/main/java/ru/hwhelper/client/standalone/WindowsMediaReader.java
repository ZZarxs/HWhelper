package ru.hwhelper.client.standalone;

import com.sun.jna.*;
import com.sun.jna.ptr.*;
import java.util.*;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import java.io.*;
import javax.imageio.ImageIO;
import static ru.hwhelper.client.standalone.WindowsNative.*;

/** Windows SMTC metadata, clock and cover read in-process, on one MTA worker thread. */
public final class WindowsMediaReader implements AutoCloseable {
    public record Session(String title,String artist,String source,boolean playing,double position,double duration,double rate,byte[] cover,String coverKey,String status){}
    private record Candidate(Ref session,Ref properties,String title,String artist,String source,boolean playing){}
    private Ref manager;private boolean apartment;
    private String imageKey="";private byte[] image=new byte[0];private long coverRetry;
    private void open()throws Exception {
        if(manager!=null)return;if(!supported())throw new UnsupportedOperationException("Нужна 64-битная Windows 10/11");
        initialize();apartment=true;
        try(Ref factory=factory("Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager","2050c4ee-11a0-57de-aed7-c97c70338245")){
            manager=await(object(factory.value,6),4000);
        }catch(Exception e){close();throw e;}
    }
    public Session poll()throws Exception {
        open();List<Ref> sessions=new ArrayList<>();List<Candidate> candidates=new ArrayList<>();
        try{
            Ref current=object(manager.value,6);if(!current.empty())sessions.add(current);else current.close();
            try(Ref list=object(manager.value,7)){
                int size=Math.min(16,integer32(list.value,7));
                for(int i=0;i<size;i++){PointerByReference out=new PointerByReference();check(call(list.value,6,i,out));sessions.add(new Ref(out.getValue()));}
            }
            Set<String> seen=new HashSet<>();
            for(Ref session:sessions){
                Ref properties=null;
                try{
                    properties=await(object(session.value,7),2000);String title=string(properties.value,6),artist=string(properties.value,9),source=string(session.value,6);
                    boolean playing;try(Ref info=object(session.value,9)){playing=integer32(info.value,7)==4;}
                    if(title.isBlank()||!seen.add(source+"\n"+title)){properties.close();continue;}
                    candidates.add(new Candidate(session,properties,title,artist,source,playing));
                    if(playing)break;
                }catch(InterruptedException e){if(properties!=null)properties.close();throw e;}
                catch(RuntimeException e){if(properties!=null)properties.close();}
            }
            Candidate selected=candidates.stream().filter(Candidate::playing).findFirst().orElse(candidates.isEmpty()?null:candidates.getFirst());
            if(selected==null){imageKey="";image=new byte[0];return new Session("","","",false,0,0,1,image,"","no_session");}
            String key=selected.source()+"\n"+selected.title()+"\n"+selected.artist();
            if(!key.equals(imageKey)){imageKey=key;image=new byte[0];coverRetry=0;}
            if(image.length==0&&System.nanoTime()>coverRetry){
                coverRetry=System.nanoTime()+5_000_000_000L;
                try{Ref thumbnail=object(selected.properties().value,15);if(!thumbnail.empty())image=png(stream(thumbnail));else thumbnail.close();}catch(InterruptedException e){throw e;}catch(Exception ignored){}
            }
            double start,end,position,rate=1;long updated;
            try(Ref timeline=object(selected.session().value,8)){
                start=integer64(timeline.value,6)/10_000_000.0;end=integer64(timeline.value,7)/10_000_000.0;
                position=integer64(timeline.value,10)/10_000_000.0-start;updated=integer64(timeline.value,11)/10000-11644473600000L;
            }
            try(Ref info=object(selected.session().value,9);Ref playbackRate=object(info.value,10)){
                if(!playbackRate.empty()){DoubleByReference value=new DoubleByReference();check(call(playbackRate.value,6,value));if(value.getValue()>0&&value.getValue()<5)rate=value.getValue();}
            }catch(RuntimeException ignored){}
            double duration=Math.max(0,end-start),elapsed=(System.currentTimeMillis()-updated)/1000.0;
            if(selected.playing()&&elapsed>=0&&elapsed<86400)position+=elapsed*rate;
            position=Math.max(0,Math.min(duration>0?duration:1_000_000,position));
            return new Session(selected.title(),selected.artist(),selected.source(),selected.playing(),position,duration,rate,image,key,"ok");
        }finally{for(Candidate c:candidates)c.properties().close();for(Ref ref:sessions)ref.close();}
    }
    private static byte[] png(byte[] source)throws IOException {
        if(source.length==0)return source;
        try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(source))){
            var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())return new byte[0];var reader=readers.next();
            try{
                reader.setInput(stream);if((long)reader.getWidth(0)*reader.getHeight(0)>4096*4096)return new byte[0];
                BufferedImage input=reader.read(0),output=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);var g=output.createGraphics();
                try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);double scale=Math.max(256.0/input.getWidth(),256.0/input.getHeight());int w=(int)(input.getWidth()*scale),h=(int)(input.getHeight()*scale);g.drawImage(input,(256-w)/2,(256-h)/2,w,h,null);}finally{g.dispose();}
                var bytes=new ByteArrayOutputStream();ImageIO.write(output,"PNG",bytes);return bytes.toByteArray();
            }finally{reader.dispose();}
        }
    }
    public void close(){if(manager!=null){manager.close();manager=null;}if(apartment){uninitialize();apartment=false;}}
}
