package ru.hwhelper.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Compact native selector; left click joins, right click saves a favorite. No container packets. */
final class AnarchySwitchScreen extends Screen {
    private final HwConfig config;private final AnarchySwitcher switcher;private final Screen parent;
    private final List<Card> cards=new ArrayList<>();private final UiTransition opening=new UiTransition(0,190);
    private record Card(int x,int y,int w,int h,int number){boolean contains(double px,double py){return px>=x&&px<x+w&&py>=y&&py<y+h;}}
    private int x,y,w,h,group=-1,page,pages=1;private String query="";private EditBox search;
    AnarchySwitchScreen(HwConfig config,AnarchySwitcher switcher,Screen parent){super(Component.literal("HWhelper / Быстрый переход"));this.config=config;this.switcher=switcher;this.parent=parent;}
    private void button(int x,int y,int w,String label,Runnable action){addRenderableWidget(new MenuControl(x,y,w,22,label,"",MenuControl.ACTION,config,null,null,b->action.run()));}
    protected void init(){
        w=Math.min(620,width-24);h=Math.min(400,height-24);x=(width-w)/2;y=(height-h)/2;
        search=new EditBox(font,x+13,y+53,w-26,21,Component.literal("Номер или название анархии"));
        search.setHint(UiStyle.text("Поиск: номер, /an7 или СолоЛайт",9));search.setMaxLength(48);
        search.addFormatter((text,cursor)->UiStyle.text(text,10).getVisualOrderText());search.setValue(query);
        search.setResponder(value->{query=value;page=0;});addRenderableWidget(search);
        int slot=(w-26-16)/5;String[] groups={"Все","Соло","Дуо","Трио","Клан"};
        for(int i=0;i<5;i++){int selected=i-1;addRenderableWidget(new MenuControl(x+13+i*(slot+4),y+81,slot,22,groups[i],"",MenuControl.NAV,config,()->group==selected,null,b->{group=selected;page=0;}));}
        button(x+13,y+h-30,64,"Назад",this::onClose);
        button(x+82,y+h-30,24,"‹",()->page=Math.max(0,page-1));
        button(x+110,y+h-30,24,"›",()->page=Math.min(pages-1,page+1));
        int favWidth=Math.min(140,w-162);
        button(x+w-13-favWidth,y+h-30,favWidth,config.anarchy.favoritesOnly?"★ Избранное":"Все серверы",()->{config.anarchy.favoritesOnly=!config.anarchy.favoritesOnly;config.save();page=0;clearWidgets();init();});
        setInitialFocus(search);
    }
    private List<Integer> filtered(){return AnarchyNavigation.available(config.anarchy).stream().filter(n->(group<0||AnarchyNavigation.group(n)==group)&&AnarchyNavigation.matches(n,query)).toList();}
    public void renderBackground(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xB00B0C12);}
    public void render(GuiGraphics g,int mx,int my,float dt){
        float alpha=opening.update(1,System.nanoTime(),config.animations);cards.clear();
        UiStyle.glass(g,x,y,w,h,config.accent(),0,true,(int)(250*alpha));
        UiStyle.fitted(g,"Быстрый переход",x+14,y+13,w-28,17,UiStyle.WHITE);
        UiStyle.fitted(g,config.anarchy.enabled?switcher.status():"Модуль выключен: M → Связь → Быстрый переход",x+14,y+36,w-28,9,config.accent());
        int columns=w>=480?6:4,gap=6,cardWidth=(w-26-(columns-1)*gap)/columns;
        int rows=Math.max(1,(h-165)/39),perPage=columns*rows;var numbers=filtered();
        pages=Math.max(1,(numbers.size()+perPage-1)/perPage);page=Math.max(0,Math.min(pages-1,page));
        if(numbers.isEmpty()){
            UiStyle.fitted(g,config.anarchy.favoritesOnly?"Нет избранных. Выключи фильтр и нажми ПКМ по серверу.":"Серверы не найдены. Проверь поиск и группы в настройках.",x+14,y+125,w-28,9,UiStyle.MUTED);
        }
        for(int index=page*perPage;index<Math.min(numbers.size(),(page+1)*perPage);index++){
            int n=numbers.get(index),local=index-page*perPage,cx=x+13+local%columns*(cardWidth+gap),cy=y+113+local/columns*39;
            Card card=new Card(cx,cy,cardWidth,33,n);cards.add(card);boolean hover=card.contains(mx,my),current=n==switcher.current(),waiting=n==switcher.pending();
            int tint=current?0xFF94DEB2:config.accent();
            UiStyle.glass(g,cx,cy,cardWidth,33,tint,current||waiting?1:0,false,hover?255:225);
            UiStyle.label(g,"#"+n,cx+8,cy+6,13,current?tint:UiStyle.WHITE);
            String label=new String[]{"Соло","Дуо","Трио","Клан"}[AnarchyNavigation.group(n)];
            UiStyle.label(g,waiting?"Переход…":current?"Вы здесь":label,cx+8,cy+23,7,current?tint:UiStyle.MUTED);
            boolean favorite=config.anarchy.favorites.contains(n);
            UiStyle.icon(g,"star",cx+cardWidth-18,cy+7,10,favorite?config.accent():UiStyle.EDGE);
        }
        String note=switcher.notice();
        if(note.isBlank())note="ЛКМ — перейти · ПКМ — избранное · "+switcher.shortcuts()+" — сменить в игре";
        UiStyle.fitted(g,note,x+14,y+h-51,w-28,8,UiStyle.MUTED);
        UiStyle.fitted(g,(page+1)+" / "+pages,x+140,y+h-23,Math.max(8,w-310),8,UiStyle.MUTED);
        super.render(g,mx,my,dt);
    }
    public boolean mouseClicked(MouseButtonEvent event,boolean doubled){
        if(super.mouseClicked(event,doubled))return true;
        for(Card card:cards)if(card.contains(event.x(),event.y())){
            if(event.button()==1){Integer n=card.number();if(!config.anarchy.favorites.remove(n))config.anarchy.favorites.add(n);config.anarchy.sanitize();config.save();return true;}
            if(event.button()==0){if(switcher.join(card.number()))minecraft.setScreen(null);return true;}
        }return false;
    }
    public boolean keyPressed(KeyEvent event){
        if(event.key()==GLFW.GLFW_KEY_ENTER||event.key()==GLFW.GLFW_KEY_KP_ENTER){var numbers=filtered();if(numbers.size()==1&&switcher.join(numbers.getFirst()))minecraft.setScreen(null);return true;}
        if(event.key()==GLFW.GLFW_KEY_PAGE_UP){page=Math.max(0,page-1);return true;}
        if(event.key()==GLFW.GLFW_KEY_PAGE_DOWN){page=Math.min(pages-1,page+1);return true;}
        return super.keyPressed(event);
    }
    public void onClose(){config.save();minecraft.setScreen(parent);}
    public boolean isPauseScreen(){return false;}
}
