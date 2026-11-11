package ru.hwhelper.client;

/** Monotonic clock and the visible scoreboard acknowledge a transfer; sending is never treated as success. */
final class AnarchyTransferGate {
    enum Result { IDLE,WAITING,CONFIRMED,TIMED_OUT }
    private int target;private long requestedAt,allowedAt;private Result result=Result.IDLE;
    boolean begin(int number,int current,long now,int delay){
        if(AnarchyNavigation.group(number)<0||number==current||now<allowedAt||result==Result.WAITING&&number==target)return false;
        target=number;requestedAt=now;allowedAt=now+Math.max(800,delay);result=Result.WAITING;return true;
    }
    Result observe(int current,long now){
        if(result==Result.WAITING){if(current==target)result=Result.CONFIRMED;else if(now-requestedAt>=8000)result=Result.TIMED_OUT;}
        return result;
    }
    int pending(){return result==Result.WAITING?target:0;}
    Result result(){return result;}
    int target(){return target;}
    long remaining(long now){return Math.max(0,allowedAt-now);}
    void clear(){target=0;result=Result.IDLE;}
}
