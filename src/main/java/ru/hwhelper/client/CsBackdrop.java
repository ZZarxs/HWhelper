package ru.hwhelper.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import java.util.ArrayDeque;

/** Cursor ripples adapted from the supplied CsShapeWaves reference, using GUI textures only. */
final class CsBackdrop {
    private record Wave(float x,float y,long started) {}
    private final ArrayDeque<Wave> waves=new ArrayDeque<>();
    private float lastX=Float.NaN,lastY;
    private long lastRipple;
    void render(GuiGraphics g,int width,int height,int mx,int my,float alpha,boolean animate,int accent) {
        long now=System.nanoTime();
        if(animate && (Float.isNaN(lastX)||Math.hypot(mx-lastX,my-lastY)>12) && now-lastRipple>110_000_000L){
            waves.addLast(new Wave(mx,my,now));
            while(waves.size()>4)waves.removeFirst();
            lastX=mx;lastY=my;lastRipple=now;
        }
        waves.removeIf(wave->now-wave.started()>1_200_000_000L);
        g.fill(0,0,width,height,UiStyle.alpha(0xE90C0B0F,alpha));
        int cell=Math.max(18,(int)Math.sqrt(width*(double)height/430));
        for(int y=cell/2;y<height;y+=cell)for(int x=cell/2;x<width;x+=cell){
            float charge=0;
            if(animate)for(Wave wave:waves){
                float age=(now-wave.started())/1_000_000_000f;
                float distance=(float)Math.hypot(x-wave.x(),y-wave.y());
                charge+=Math.max(0,1-Math.abs(distance-age*190)/36)*(1-age/1.2f);
            }
            int seed=(x/cell*73+y/cell*137)&255;
            float edge=Math.max(0,1-(float)Math.hypot((x-width/2f)/width,(y-height/2f)/height)*1.35f);
            float opacity=(.025f+seed/255f*.035f)*edge+Math.min(.24f,charge*.2f);
            int size=Math.max(3,(int)(cell*(.22f+Math.min(.07f,charge*.07f))));
            String shape=switch(seed%3){case 0->"circle";case 1->"triangle";default->"square";};
            g.blit(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("hwhelper","textures/gui/cs/"+shape+".png"),
                    x-size/2,y-size/2,0f,0f,size,size,32,32,32,32,
                    UiStyle.alpha((charge>.15f?accent:0xFFB9B3BE),opacity*alpha));
        }
    }
}
