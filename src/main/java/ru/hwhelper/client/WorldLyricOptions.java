package ru.hwhelper.client;

final class WorldLyricOptions {
    boolean enabled,white,smartSplit=true;
    int animation,maxLines=3,offsetMs;
    float minDistance=2,maxDistance=4.5f,spread=70,floatHeight=.5f,scale=1;
    void sanitize(){
        animation=Math.floorMod(animation,5);maxLines=Math.max(1,Math.min(6,maxLines));offsetMs=Math.max(-5000,Math.min(5000,offsetMs));
        minDistance=clamp(minDistance,1.2f,4,2);maxDistance=clamp(maxDistance,2,7,4.5f);maxDistance=Math.max(minDistance+.2f,maxDistance);
        spread=clamp(spread,20,85,70);floatHeight=clamp(floatHeight,.1f,2,.5f);scale=clamp(scale,.5f,2,1);
    }
    private static float clamp(float v,float min,float max,float fallback){return Float.isFinite(v)?Math.max(min,Math.min(max,v)):fallback;}
}
