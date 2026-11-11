package ru.hwhelper.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Native HWhelper settings for the ported range/alias editor (the original opens this on F6). */
final class HubSwapSettingsScreen extends Screen {
    private final HwConfig config;private final AnarchySwitcher switcher;private final Screen parent;
    private EditBox aliases,total;private final EditBox[] ranges=new EditBox[4];private int x,y,w,h;private String status="Команды: /an 7, /lt 7, /an7 · переход по NBT-меткам";
    HubSwapSettingsScreen(HwConfig config,AnarchySwitcher switcher,Screen parent){super(Component.literal("HWhelper / HubSwap"));this.config=config;this.switcher=switcher;this.parent=parent;}
    private EditBox field(int x,int y,int w,int max){EditBox box=new EditBox(font,x,y,w,20,Component.literal("HubSwap"));box.setMaxLength(max);box.addFormatter((text,cursor)->UiStyle.text(text,9).getVisualOrderText());addRenderableWidget(box);return box;}
    private void button(int x,int y,int w,String text,Runnable action){addRenderableWidget(new MenuControl(x,y,w,22,text,"",MenuControl.ACTION,config,null,null,b->action.run()));}
    protected void init(){
        w=Math.min(500,width-24);h=Math.min(290,height-24);x=(width-w)/2;y=(height-h)/2;
        aliases=field(x+14,y+57,w-28,256);aliases.setValue(String.join(", ",config.anarchy.aliases));
        total=field(x+77,y+83,54,3);
        button(x+w-150,y+82,64,"Профиль 66",()->fill(new HubSwapRanges()));
        button(x+w-80,y+82,66,"Профиль 36",()->fill(HubSwapRanges.legacy()));
        int column=(w-36)/2;
        for(int i=0;i<4;i++)ranges[i]=field(x+14+(i%2)*(column+8),y+120+(i/2)*37,column,16);
        fill(config.anarchy.ranges);
        button(x+14,y+h-29,74,"Назад",this::onClose);
        button(x+w-120,y+h-29,106,"Сохранить",this::save);
        setInitialFocus(aliases);
    }
    private void fill(HubSwapRanges profile){total.setValue(Integer.toString(profile.total));for(int i=0;i<4;i++)ranges[i].setValue(profile.range(i));}
    private void save(){
        try{
            var profile=HubSwapRanges.parse(Integer.parseInt(total.getValue().strip()),Arrays.stream(ranges).map(EditBox::getValue).toList());
            var names=HubSwapRanges.aliases(Arrays.asList(aliases.getValue().strip().split("[,;\\s]+")));
            switcher.cancel("Переход отменён: настройки изменены");
            config.anarchy.ranges=profile;config.anarchy.aliases=names;config.anarchy.hubSwapImported=true;config.anarchy.sanitize();config.save();
            aliases.setValue(String.join(", ",names));status="Сохранено. Подсказки новых алиасов обновятся при подключении.";
        }catch(IllegalArgumentException e){status=e instanceof NumberFormatException?"Проверь числа и формат диапазонов 1-15":e.getMessage();}
    }
    public void renderBackground(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xD00C0B10);}
    public void render(GuiGraphics g,int mx,int my,float dt){
        UiStyle.glass(g,x,y,w,h,config.accent(),0,true,250);UiStyle.label(g,"HubSwap",x+14,y+13,16,UiStyle.WHITE);
        UiStyle.fitted(g,status,x+14,y+34,w-28,8,UiStyle.MUTED);
        UiStyle.label(g,"Алиасы через запятую",x+14,y+47,8,config.accent());UiStyle.label(g,"Всего",x+14,y+89,9,UiStyle.TEXT);
        int column=(w-36)/2;String[] names={"СолоЛайт","ДуоЛайт","ТриоЛайт","КланЛайт"};
        for(int i=0;i<4;i++)UiStyle.label(g,names[i],x+14+(i%2)*(column+8),y+109+(i/2)*37,8,UiStyle.TEXT);
        if(h>=250)UiStyle.fitted(g,"Механизм krsHubSwap · автор оригинала krosov_ok",x+14,y+202,w-28,8,UiStyle.MUTED);
        super.render(g,mx,my,dt);
    }
    public void onClose(){minecraft.setScreen(parent);}
    public boolean isPauseScreen(){return false;}
}
