package ru.hwhelper.client;

final class CosmeticOptions {
    boolean enabled,others,trail,pet,naruto;
    boolean self=true,firstPerson=true,halo=true,spheres=true,haloAnimate=true,clones=true;
    int quality=1,sphereMode=1,petType=1,trailPoints=24;
    float haloRadius=.36f,haloThickness=.06f,haloHeight=.10f,distance=72;
    float sphereSize=.12f,sphereRadius=.42f,sphereHeight=.05f,speed=1.85f;
    float trailWidth=.22f,petRadius=.65f,petHeight=.95f,petScale=.55f,petSpeed=1.15f;
    boolean themeColors,trailGradient;
    int haloColor=0xFFFFFFFF,exortColor=0xC8FFC864,wexColor=0xC8FF96FF,quasColor=0xC89696FF;
    int trailColor=0xC8FFFFFF,trailColorB=0xC8FF8CFF,sphereEffect=1,sphereTrailPoints=18,trailStepMs=18,sphereEditor=3;
    float glow=1.10f,breathe=.08f,spiralHeight=1.25f,waveAmplitude=.22f,waveFrequency=2.4f,trailOpacity=1;
    float[] offsetLR=new float[3],offsetFB=new float[3];
    int visualVersion;
    void referenceStyle(){
        haloRadius=.36f;haloThickness=.06f;haloHeight=.10f;quality=1;haloAnimate=true;
        sphereMode=0;sphereSize=.12f;sphereRadius=.42f;sphereHeight=.05f;speed=1.85f;sphereEffect=1;sphereTrailPoints=18;
        haloColor=0xFFFFFFFF;exortColor=0xC8FFC864;wexColor=0xC8FF96FF;quasColor=0xC89696FF;themeColors=false;
        glow=1.1f;breathe=.08f;spiralHeight=1.25f;waveAmplitude=.22f;waveFrequency=2.4f;
        trailWidth=.22f;trailPoints=24;trailColor=0xC8FFFFFF;trailColorB=0xC8FF8CFF;trailGradient=false;trailOpacity=1;trailStepMs=18;
        petRadius=.65f;petHeight=.95f;petScale=.55f;petSpeed=1.15f;offsetLR=new float[3];offsetFB=new float[3];visualVersion=2;
    }
    int sphereColor(int i,int accent){return themeColors?accent:new int[]{exortColor,wexColor,quasColor}[i%3];}
    float offset(boolean forward){float[] values=forward?offsetFB:offsetLR;return sphereEditor==3?values[0]:values[sphereEditor];}
    void offset(boolean forward,float value){float[] values=forward?offsetFB:offsetLR;if(sphereEditor==3)java.util.Arrays.fill(values,value);else values[sphereEditor]=value;}
    void sanitize(){
        if(visualVersion<2){
            if(haloThickness==.045f)haloThickness=.06f;if(haloHeight==.12f)haloHeight=.10f;
            if(sphereRadius==.6f)sphereRadius=.42f;if(sphereHeight==1.1f)sphereHeight=.05f;
            if(speed==1.5f)speed=1.85f;if(petRadius==.8f)petRadius=.65f;if(petHeight==1.15f)petHeight=.95f;visualVersion=2;
        }
        if(offsetLR==null||offsetLR.length!=3)offsetLR=new float[3];if(offsetFB==null||offsetFB.length!=3)offsetFB=new float[3];
        for(int i=0;i<3;i++){offsetLR[i]=clamp(offsetLR[i],-1.5f,1.5f,0);offsetFB[i]=clamp(offsetFB[i],-1.5f,1.5f,0);}
        sphereEffect=Math.floorMod(sphereEffect,3);sphereEditor=Math.floorMod(sphereEditor,4);
        sphereTrailPoints=Math.max(6,Math.min(48,sphereTrailPoints));trailStepMs=Math.max(6,Math.min(80,trailStepMs));
        petSpeed=clamp(petSpeed,.05f,5,1.15f);glow=clamp(glow,.2f,2.2f,1.1f);breathe=clamp(breathe,0,.3f,.08f);
        spiralHeight=clamp(spiralHeight,.2f,6,1.25f);waveAmplitude=clamp(waveAmplitude,0,1.2f,.22f);waveFrequency=clamp(waveFrequency,.6f,8,2.4f);trailOpacity=clamp(trailOpacity,.1f,1.6f,1);
        quality=Math.floorMod(quality,4);sphereMode=Math.floorMod(sphereMode,5);petType=Math.floorMod(petType,11);
        trailPoints=Math.max(6,Math.min(80,trailPoints));
        haloRadius=clamp(haloRadius,.18f,.9f,.36f);haloThickness=clamp(haloThickness,.02f,.16f,.06f);
        haloHeight=clamp(haloHeight,-.6f,.6f,.10f);distance=clamp(distance,8,192,72);
        sphereSize=clamp(sphereSize,.05f,.22f,.12f);sphereRadius=clamp(sphereRadius,.18f,1.25f,.42f);
        sphereHeight=clamp(sphereHeight,-1.2f,3.5f,.05f);speed=clamp(speed,.2f,4,1.85f);
        trailWidth=clamp(trailWidth,.06f,.6f,.22f);petRadius=clamp(petRadius,.15f,2.5f,.65f);
        petHeight=clamp(petHeight,-.5f,3,.95f);petScale=clamp(petScale,.15f,2,.55f);
    }
    private static float clamp(float v,float min,float max,float fallback){return Float.isFinite(v)?Math.max(min,Math.min(max,v)):fallback;}
}
