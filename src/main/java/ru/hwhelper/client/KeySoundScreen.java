package ru.hwhelper.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

final class KeySoundScreen extends Screen {
    private final HwConfig c;private final Screen parent;private int x,y,w,h,page;
    KeySoundScreen(HwConfig c,Screen parent){super(Component.literal("HWhelper / Звуки уника"));this.c=c;this.parent=parent;}
    private void button(int x,int y,int w,String text,Runnable action){addRenderableWidget(new MenuControl(x,y,w,23,text,"",MenuControl.ACTION,c,null,null,b->action.run()));}
    private void refresh(){if(minecraft.screen==this){clearWidgets();init();}}
    protected void init(){
        w=Math.min(440,width-24);h=Math.min(290,height-24);x=(width-w)/2;y=(height-h)/2;
        int gap=5,bw=(w-34)/3;
        button(x+12,y+49,bw,"Добавить .ogg",()->CustomKeySound.choose(c,this::refresh));
        button(x+17+bw,y+49,bw,"Папка звуков",()->{CustomKeySound.files();Util.getPlatform().openPath(CustomKeySound.directory());});
        button(x+22+bw*2,y+49,bw,"Прослушать",()->CustomKeySound.preview(c));
        var files=CustomKeySound.files();int rows=Math.max(1,(h-142)/26),pages=Math.max(1,(files.size()+rows-1)/rows);page=Math.max(0,Math.min(page,pages-1));
        button(x+12,y+78,w-24,"Стандартный звук"+(c.keySoundFile.isBlank()?"  ✓":""),()->CustomKeySound.select(c,"",this::refresh));
        for(int i=0;i<rows&&page*rows+i<files.size();i++){
            String name=files.get(page*rows+i).getFileName().toString();
            button(x+12,y+105+i*26,w-24,(c.keySoundFile.equals(name)?"✓  ":"")+name,()->CustomKeySound.select(c,name,this::refresh));
        }
        button(x+12,y+h-28,70,"Назад",this::onClose);
        if(pages>1){button(x+w-65,y+h-28,24,"←",()->{page--;refresh();});button(x+w-36,y+h-28,24,"→",()->{page++;refresh();});}
    }
    public void renderBackground(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xDC0C0B0F);}
    public void render(GuiGraphics g,int mx,int my,float dt){UiStyle.surface(g,x,y,w,h,10,UiStyle.PANEL);UiStyle.label(g,"Звуки уника",x+14,y+13,15,UiStyle.WHITE);UiStyle.fitted(g,CustomKeySound.status,x+14,y+33,w-28,8,UiStyle.MUTED);super.render(g,mx,my,dt);}
    public void onClose(){minecraft.setScreen(parent);}
    public boolean isPauseScreen(){return false;}
}
