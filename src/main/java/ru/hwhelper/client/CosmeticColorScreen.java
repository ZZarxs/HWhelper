package ru.hwhelper.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;

final class CosmeticColorScreen extends Screen {
    private final HwConfig c;private final Screen parent;private final IntConsumer accept;private final String label;
    private String value,error="";private EditBox input;private int x,y,w;
    CosmeticColorScreen(HwConfig c,Screen parent,String label,int color,IntConsumer accept){super(Component.literal(label));this.c=c;this.parent=parent;this.accept=accept;this.label=label;value=String.format("%08X",color);}
    static int parse(String value){String text=value.strip().replaceFirst("^#","");if(!text.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}"))throw new IllegalArgumentException("Введите RRGGBB или AARRGGBB");return (int)Long.parseLong(text.length()==6?"FF"+text:text,16);}
    private void button(int x,int y,int w,String text,Runnable action){addRenderableWidget(new UiStyle.Action(x,y,w,23,text,c,b->action.run()));}
    protected void init(){
        w=Math.min(340,width-24);x=(width-w)/2;y=(height-190)/2;
        input=new EditBox(font,x+18,y+60,w-36,20,Component.literal("AARRGGBB"));input.setMaxLength(9);input.setValue(value);input.addFormatter((text,pos)->UiStyle.text(text,11).getVisualOrderText());
        input.setResponder(text->{value=text;error="";});addRenderableWidget(input);
        int[] colors={0xFFFFFFFF,0xC8FFC864,0xC8FF96FF,0xC89696FF,0xFFFF8FB9,0xFF69E6D2};int bw=(w-41)/6;
        for(int i=0;i<colors.length;i++){final int color=colors[i];button(x+18+i*(bw+1),y+94,bw,"●",()->input.setValue(String.format("%08X",color)));}
        button(x+18,y+153,(w-42)/2,"Отмена",this::onClose);
        button(x+w/2+3,y+153,(w-42)/2,"Применить",()->{try{accept.accept(parse(input.getValue()));c.save();onClose();}catch(IllegalArgumentException e){error=e.getMessage();}});
    }
    public void renderBackground(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xD90C0B0F);}
    public void render(GuiGraphics g,int mx,int my,float dt){
        UiStyle.surface(g,x,y,w,190,10,UiStyle.PANEL);UiStyle.label(g,label,x+18,y+16,14,UiStyle.WHITE);
        UiStyle.label(g,"HEX: AARRGGBB · AA — прозрачность",x+18,y+39,9,UiStyle.MUTED);
        try{UiStyle.shape(g,x+18,y+124,w-36,12,5,parse(input.getValue()));}catch(IllegalArgumentException ignored){}
        if(!error.isBlank())UiStyle.fitted(g,error,x+18,y+139,w-36,8,0xFFFF879D);
        super.render(g,mx,my,dt);
        int[] colors={0xFFFFFFFF,0xC8FFC864,0xC8FF96FF,0xC89696FF,0xFFFF8FB9,0xFF69E6D2};int bw=(w-41)/6;
        for(int i=0;i<colors.length;i++)UiStyle.shape(g,x+18+i*(bw+1)+bw/2-5,y+100,10,10,3,colors[i]);
    }
    public void onClose(){minecraft.setScreen(parent);}
    public boolean isPauseScreen(){return false;}
}
