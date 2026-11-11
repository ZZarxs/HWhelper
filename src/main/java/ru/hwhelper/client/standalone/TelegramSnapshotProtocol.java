package ru.hwhelper.client.standalone;

import com.google.gson.JsonObject;
import static ru.hwhelper.client.standalone.TelegramService.*;

/** Accept only fresh incoming text from the configured bot, with Telegram's original clock. */
public final class TelegramSnapshotProtocol {
    public record Snapshot(String text,long timestamp,String signature){}
    public static Snapshot read(JsonObject message,long chat,long bot,long now){
        if(chat==0||bot==0||!type(message).equals("message")||number(message,"chat_id")!=chat)return null;
        JsonObject sender=message.has("sender_id")&&message.get("sender_id").isJsonObject()?message.getAsJsonObject("sender_id"):null;
        if(sender==null||!type(sender).equals("messageSenderUser")||number(sender,"user_id")!=bot)return null;
        JsonObject content=message.has("content")&&message.get("content").isJsonObject()?message.getAsJsonObject("content"):null;
        if(content==null||!type(content).equals("messageText"))return null;
        String raw=string(content.getAsJsonObject("text"),"text");long stamp=number(message,"edit_date");if(stamp==0)stamp=number(message,"date");
        if(stamp<=0||stamp>now/1000+5||now/1000-stamp>30)return null;
        return new Snapshot(raw,Math.min(now,stamp*1000),number(message,"id")+"/"+stamp+"/"+raw);
    }
}
