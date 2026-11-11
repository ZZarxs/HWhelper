package ru.hwhelper.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;
final class DynamicIslandSettingsScreen extends Screen {
    private final HwConfig c;private final Screen parent;
    private int page,x,y,w,h,body,step;
    private final String[] counterTitles=new String[5],counterValues=new String[5];
    DynamicIslandSettingsScreen(HwConfig c,Screen parent){super(Component.literal("Dynamic Island"));this.c=c;this.parent=parent;}
    private void button(int bx,int by,int bw,int bh,String title,int kind,BooleanSupplier selected,Runnable task){
        addRenderableWidget(new MenuControl(bx,by,bw,bh,title,"",kind,c,selected,null,b->{task.run();c.save();clearWidgets();init();}));
    }
    private void toggle(int row,String title,BooleanSupplier selected,Runnable task){button(x+16,body+row*step,w-32,step-3,title,MenuControl.ROW,selected,task);}
    private void counter(int row,String title,String value,Runnable minus,Runnable plus){
        counterTitles[row]=title;counterValues[row]=value;
        int ry=body+row*step;
        button(x+w-109,ry+2,24,step-7,"−",MenuControl.STEP,null,minus);
        button(x+w-43,ry+2,24,step-7,"+",MenuControl.STEP,null,plus);
    }
    protected void init(){
        java.util.Arrays.fill(counterTitles,null);
        w=Math.min(420,width-20);h=Math.min(278,height-16);x=(width-w)/2;y=(height-h)/2;
        body=y+76;step=Math.min(32,(h-110)/5);
        String[] tabs={"Размер","Состав","Поведение","Часы"};int tw=(w-44)/4;
        for(int i=0;i<tabs.length;i++){final int target=i;button(x+16+i*(tw+4),y+43,tw,23,tabs[i],MenuControl.NAV,()->page==target,()->page=target);}
        if(page==0){
            counter(0,"Макс. ширина",c.musicWidth+" px",()->c.musicWidth=Math.max(140,c.musicWidth-10),()->c.musicWidth=Math.min(420,c.musicWidth+10));
            counter(1,"Высота",c.islandHeight+" px",()->c.islandHeight=Math.max(22,c.islandHeight-2),()->c.islandHeight=Math.min(44,c.islandHeight+2));
            counter(2,"Масштаб",Math.round(c.musicScale*100)+"%",()->c.musicScale=Math.max(.65f,c.musicScale-.05f),()->c.musicScale=Math.min(1.6f,c.musicScale+.05f));
            counter(3,"Плотность фона",c.musicOpacity+"%",()->c.musicOpacity=Math.max(30,c.musicOpacity-5),()->c.musicOpacity=Math.min(100,c.musicOpacity+5));
            button(x+16,body+4*step,w-32,step-3,"Сбросить размер и положение",MenuControl.ROW,null,()->{c.islandCentered=true;c.musicY=6;c.musicWidth=250;c.islandHeight=30;c.musicScale=1;});
        }else if(page==1){
            toggle(0,"Dynamic Island",()->c.musicEnabled,()->c.musicEnabled=!c.musicEnabled);
            toggle(1,"Обложка альбома",()->c.musicCover,()->c.musicCover=!c.musicCover);
            toggle(2,"Декоративный эквалайзер",()->c.islandEqualizer,()->c.islandEqualizer=!c.islandEqualizer);
            toggle(3,"Строка песни",()->c.musicLyrics,()->c.musicLyrics=!c.musicLyrics);
            toggle(4,"Прогресс трека",()->c.musicProgress,()->c.musicProgress=!c.musicProgress);
        }else if(page==2){
            toggle(0,"Караоке-подсветка",()->c.musicGlow,()->c.musicGlow=!c.musicGlow);
            toggle(1,"Часы и индикатор сети",()->c.islandStatus,()->c.islandStatus=!c.islandStatus);
            toggle(2,"Привязка музыки к сетке",()->c.musicSnapToGrid,()->c.musicSnapToGrid=!c.musicSnapToGrid);
            toggle(3,"По центру сверху",()->c.islandCentered,()->{c.islandCentered=!c.islandCentered;c.musicY=6;});
            toggle(4,"Всегда показывать",()->c.islandAlwaysShow,()->c.islandAlwaysShow=!c.islandAlwaysShow);
        }
        if(page==3){
            toggle(0,"Часы и индикатор сети",()->c.islandStatus,()->c.islandStatus=!c.islandStatus);
            toggle(1,"Время трека вместо часов",()->c.islandTrackClock,()->c.islandTrackClock=!c.islandTrackClock);
            toggle(2,"Скрывать на паузе",()->c.musicHideIdle,()->c.musicHideIdle=!c.musicHideIdle);
            toggle(3,"Исполнитель в названии",()->c.musicArtist,()->c.musicArtist=!c.musicArtist);
        }
        button(x+16,y+h-27,86,20,"Назад",MenuControl.ACTION,null,this::onClose);
    }
    public void renderBackground(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xD90C0B0F);}
    public void render(GuiGraphics g,int mx,int my,float dt){
        UiStyle.surface(g,x,y,w,h,10,UiStyle.PANEL);
        UiStyle.icon(g,"music",x+16,y+14,18,c.accent());
        UiStyle.label(g,"Dynamic Island",x+43,y+13,14,UiStyle.WHITE);
        UiStyle.label(g,"МЕДИА / HWhelper",x+43,y+30,7,UiStyle.MUTED);
        for(int row=0;row<5;row++)if(counterTitles[row]!=null){
            int ry=body+row*step;
            UiStyle.surface(g,x+16,ry,w-32,step-3,6,UiStyle.CARD);
            UiStyle.fitted(g,counterTitles[row],x+27,ry+(step-13)/2,w-160,10,UiStyle.TEXT);
            UiStyle.fitted(g,counterValues[row],x+w-80,ry+(step-13)/2,36,9.5f,c.accent());
        }
        super.render(g,mx,my,dt);
    }
    public void onClose(){c.save();minecraft.setScreen(parent);}
    public boolean isPauseScreen(){return false;}
}
