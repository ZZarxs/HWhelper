package ru.hwhelper.client.standalone;

/** One bot request in flight, at least five seconds apart, with flood-wait backoff. */
public final class TelegramPollGate {
    private long allowedAt,waitingUntil;private boolean waiting;
    public boolean request(long now){if(now<allowedAt||(waiting&&now<waitingUntil))return false;waiting=true;waitingUntil=now+8000;allowedAt=now+5000;return true;}
    public void response(){waiting=false;}
    public void backoff(long now,int seconds){waiting=false;allowedAt=Math.max(allowedAt,now+Math.max(5,seconds+1)*1000L);}
}
