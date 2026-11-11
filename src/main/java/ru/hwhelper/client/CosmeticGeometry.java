package ru.hwhelper.client;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Cached topology and the lighting/trajectory equations from Cosmetic V2. */
final class CosmeticGeometry {
    record Mesh(float[] centers,float[] normals){int vertices(){return normals.length/3;}}
    record Orb(Vec3 position,float opacity){}
    private static final Map<Long,Mesh> TORI=new HashMap<>();
    static final Mesh SPHERE=sphere(18,28);
    static final Mesh DISTANT_SPHERE=sphere(10,16);
    static synchronized Mesh torus(int quality){
        int[] majors={64,96,128,160},minors={18,24,30,36};int q=Math.max(0,Math.min(3,quality));
        int major=majors[q],minor=minors[q];long key=((long)major<<32)|minor;
        return TORI.computeIfAbsent(key,ignored->{
            float[] centers=new float[major*minor*12],normals=new float[centers.length];int index=0;
            for(int i=0;i<major;i++)for(int j=0;j<minor;j++)for(int[] corner:new int[][]{{i,j},{i+1,j},{i+1,j+1},{i,j+1}}){
                double a=corner[0]*Math.PI*2/major,b=corner[1]*Math.PI*2/minor;
                centers[index]=(float)Math.cos(a);normals[index++]=(float)(Math.cos(b)*Math.cos(a));
                centers[index]=0;normals[index++]=(float)Math.sin(b);
                centers[index]=(float)Math.sin(a);normals[index++]=(float)(Math.cos(b)*Math.sin(a));
            }return new Mesh(centers,normals);
        });
    }
    private static Mesh sphere(int stacks,int slices){
        float[] normals=new float[stacks*slices*12];int index=0;
        for(int i=0;i<stacks;i++)for(int j=0;j<slices;j++)for(int[] corner:new int[][]{{i,j},{i+1,j},{i+1,j+1},{i,j+1}}){
            double lat=corner[0]*Math.PI/stacks-Math.PI/2,lon=corner[1]*Math.PI*2/slices;
            normals[index++]=(float)(Math.cos(lat)*Math.cos(lon));normals[index++]=(float)Math.sin(lat);normals[index++]=(float)(Math.cos(lat)*Math.sin(lon));
        }return new Mesh(new float[normals.length],normals);
    }
    static int shade(int color,float nx,float ny,float nz,Vec3 view,boolean halo){
        double dot=Math.max(0,Math.min(1,nx*view.x+ny*view.y+nz*view.z)),rim=1-dot,rim2=rim*rim;
        double light;
        if(halo){double diffuse=Math.max(0,Math.min(1,((nx*.35+ny+nz*.25)/Math.sqrt(1.185)+1)*.5));double d2=dot*dot,d4=d2*d2;
            light=Math.min(1,.22+.68*diffuse+rim2*.30+d4*d4*d2*.20);
        }else light=Math.min(1,.40+.60*dot+.22*rim2*rim2);
        return (color&0xFF000000)|((int)(((color>>>16)&255)*light)<<16)|((int)(((color>>>8)&255)*light)<<8)|(int)((color&255)*light);
    }
    static Orb orb(Vec3 p,double height,double yaw,double time,int index,CosmeticOptions c){
        double phase=index*Math.PI*2/3,radius=c.sphereRadius*(1+Math.sin(time*2.1+phase)*c.breathe),angle,y;
        double bob=Math.sin(time*2.8+phase)*Math.min(.18,c.sphereSize*.85),base=p.y+height*.5+c.sphereHeight,head=p.y+height+c.sphereHeight;
        float opacity=1;
        switch(c.sphereMode){
            case 1->{angle=time*c.speed+phase+yaw;y=base+bob;}
            case 2,3->{
                double progress=frac(time*(c.sphereMode==2?.18:.22)*c.speed+index/3.0),top=head+c.spiralHeight,bottom=p.y+c.sphereHeight-c.spiralHeight,span=top-bottom;
                angle=time*(c.sphereMode==2?2.10:2.65)*c.speed+phase*(c.sphereMode==2?1:1.15)+yaw;
                if(c.sphereMode==3)radius*=.80+.20*Math.sin(time*1.7+phase);
                y=top-progress*span+(c.sphereMode==3?Math.sin(time*1.35+phase)*span*.04:0);
                double f=Math.max(0,Math.min(1,(y-bottom)/span));opacity=(float)Math.min(1,Math.min(f/.12,(1-f)/.12));
            }
            case 4->{angle=time*c.speed+phase+yaw;y=head+Math.sin(angle*c.waveFrequency+time*1.2)*c.waveAmplitude+bob;}
            default->{angle=yaw+Math.toRadians(new double[]{180,-90,360}[index]);y=base+bob;}
        }
        double lr=c.offsetLR[index],fb=c.offsetFB[index];
        return new Orb(new Vec3(p.x+Math.cos(angle)*radius+Math.cos(yaw)*lr-Math.sin(yaw)*fb,y,p.z+Math.sin(angle)*radius+Math.sin(yaw)*lr+Math.cos(yaw)*fb),opacity);
    }
    static double frac(double value){return value-Math.floor(value);}
    static Vec3 catmull(Vec3 a,Vec3 b,Vec3 c,Vec3 d,double t){
        double t2=t*t,t3=t2*t;
        return b.scale(2).add(c.subtract(a).scale(t)).add(a.scale(2).subtract(b.scale(5)).add(c.scale(4)).subtract(d).scale(t2)).add(b.scale(3).subtract(a).subtract(c.scale(3)).add(d).scale(t3)).scale(.5);
    }
    static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    static float cloneAlpha(double age){return smooth((float)((age-1.22)/.22))*(1-smooth((float)((age-2.84)/.62)));}
}
