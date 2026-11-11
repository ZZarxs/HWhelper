package ru.hwhelper.client;

/** Interruptible cubic transition adapted from the supplied ExampleBase reference. */
final class UiTransition {
    private final long duration;
    private float value,start,target;
    private long began;
    UiTransition(float initial,long durationMillis){value=start=target=initial;duration=durationMillis*1_000_000L;}
    float update(float next,long now,boolean animate){
        if(!animate){value=start=target=next;began=now;return value;}
        if(next!=target){start=value;target=next;began=now;}
        float t=Math.max(0,Math.min(1,(now-began)/(float)duration));
        value=start+(target-start)*(1-(float)Math.pow(1-t,3));
        return value;
    }
    float value(){return value;}
    void reset(float value){this.value=start=target=value;began=0;}
}
