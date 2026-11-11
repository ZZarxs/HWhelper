package ru.hwhelper.client.standalone;

import com.google.gson.*;

/** No network, account, or installed helper is needed for these protocol checks. */
public final class StandaloneRegression {
    public static void main(String[] args){
        TelegramPollGate gate=new TelegramPollGate();
        require(gate.request(10000),"first poll");require(!gate.request(15000),"one request in flight");
        gate.response();require(!gate.request(14999),"five-second interval after a fast reply");require(gate.request(15000),"next poll after response");
        require(!gate.request(22999),"timeout holds request");require(gate.request(23000),"eight-second timeout recovers missing reply");
        gate.backoff(24000,60);require(!gate.request(84999),"flood wait suppresses polls");require(gate.request(85000),"flood wait plus margin expires");
        JsonObject message=JsonParser.parseString("{\"@type\":\"message\",\"id\":3,\"chat_id\":7,\"date\":1000,\"sender_id\":{\"@type\":\"messageSenderUser\",\"user_id\":9},\"content\":{\"@type\":\"messageText\",\"text\":{\"text\":\"Обычная: СолоЛайт #7 62 сек.\"}}}").getAsJsonObject();
        var snapshot=TelegramSnapshotProtocol.read(message,7,9,1010000);
        require(snapshot!=null&&snapshot.timestamp()==1000000,"snapshot retains Telegram message time");
        require(snapshot.signature().equals(TelegramSnapshotProtocol.read(message,7,9,1015000).signature()),"unchanged messages deduplicate");
        require(TelegramSnapshotProtocol.read(message,8,9,1010000)==null,"ignore other chats");
        require(TelegramSnapshotProtocol.read(message,7,8,1010000)==null,"ignore outgoing/user messages");
        require(TelegramSnapshotProtocol.read(message,7,9,1031000)==null,"ignore stale history");
        require(TelegramSnapshotProtocol.read(message,7,9,900000)==null,"ignore invalid future date");
        message.addProperty("edit_date",1020);require(TelegramSnapshotProtocol.read(message,7,9,1025000).timestamp()==1020000,"edited messages retain edit clock");
        message.getAsJsonObject("content").addProperty("@type","messagePhoto");require(TelegramSnapshotProtocol.read(message,7,9,1025000)==null,"ignore attachments");
        var cues=LyricsProvider.parse("[offset:-500]\n[00:01.00]First\n[00:02.00]\n[00:03.00][00:04.00]Again\n[01:90.00]invalid");
        require(cues.size()==4&&cues.getFirst().time()==.5,"LRC timestamps and global offset");require(cues.get(1).text().isBlank(),"instrumental gaps end previous lyric");require(cues.get(3).time()==3.5,"multiple stamps per line");
        require(LyricsProvider.normalize("Song (Official Audio)").equals("song"),"safe metadata normalization");
        require(LyricsProvider.normalize("Песня [Lyrics]").equals("песня"),"Unicode track names");
        JsonArray rows=JsonParser.parseString("[{\"trackName\":\"Song\",\"artistName\":\"Artist\",\"duration\":200,\"syncedLyrics\":\"[00:01]wrong\"},{\"trackName\":\"Song\",\"artistName\":\"Other\",\"duration\":100,\"syncedLyrics\":\"[00:01]wrong\"},{\"trackName\":\"Song\",\"artistName\":\"Artist\",\"duration\":101,\"syncedLyrics\":\"[00:01]right\"}]").getAsJsonArray();
        require(LyricsProvider.select(rows,"Song","Artist",100).get("duration").getAsInt()==101,"lyrics match artist and duration");require(LyricsProvider.select(rows,"Unknown","Artist",100)==null,"do not attach unrelated lyrics");
        System.out.println("Standalone protocol and synchronized lyrics regression checks passed");
    }
    private static void require(boolean value,String name){if(!value)throw new AssertionError(name);}
}
