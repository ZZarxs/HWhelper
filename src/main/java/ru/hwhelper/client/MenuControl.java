package ru.hwhelper.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
final class MenuControl extends Button {
    static final int ROW=0,NAV=1,ACTION=2,PRIMARY=3,STEP=4,CHIP=5;
    private final HwConfig config;
    private final String title,description;
    private final int kind;
    private final BooleanSupplier selected;
    private final IntSupplier color;
    private float hover;
    private long last=System.nanoTime();
    MenuControl(int x,int y,int w,int h,String title,String description,int kind,HwConfig config,
                BooleanSupplier selected,IntSupplier color,OnPress action){
        super(x,y,w,h,Component.literal(title),action,DEFAULT_NARRATION);
        this.title=title;this.description=description;this.kind=kind;this.config=config;this.selected=selected;this.color=color;
    }
    protected void renderContents(GuiGraphics g,int mx,int my,float dt){
        long now=System.nanoTime();float blend=Math.min(1,(now-last)/100_000_000f);last=now;
        float target=isHoveredOrFocused()?1:0;hover=config.animations?hover+(target-hover)*blend:target;
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();boolean on=selected!=null&&selected.getAsBoolean();
        int accent=color==null?config.accent():color.getAsInt();
        int fg=UiStyle.WHITE,muted=UiStyle.MUTED;
        if(kind==ROW){
            UiStyle.surface(g,x,y,w,h,6,UiStyle.mix(0xFF19191B,0xFF242125,hover));
            int ty=description.isBlank()||h<30?y+(h-10)/2:y+6;
            UiStyle.fitted(g,title,x+11,ty,w-60,10,fg);
            if(!description.isBlank()&&h>=30)UiStyle.fitted(g,description,x+11,y+20,w-60,8,muted);
            if(selected!=null){
                int sx=x+w-27,sy=y+(h-12)/2;
                UiStyle.shape(g,sx,sy,12,12,3,on?accent:0xFF383338);
                if(on)UiStyle.icon(g,"check",sx+1,sy+1,10,UiStyle.INK);
            }else UiStyle.label(g,"›",x+w-25,y+(h-12)/2,12,muted);
        }else if(kind==NAV){
            UiStyle.shape(g,x,y,w,h,5,on?accent:UiStyle.mix(0xFF1D1B1F,0xFF2B252D,hover));
            UiStyle.centered(g,title,x,y,w,h,9,on?UiStyle.INK:UiStyle.TEXT);
        }else if(kind==CHIP && color!=null){
            UiStyle.shape(g,x,y,w,h,5,on?UiStyle.WHITE:UiStyle.EDGE);
            UiStyle.shape(g,x+2,y+2,w-4,h-4,4,accent);
            if(on)UiStyle.shape(g,x+w/2-2,y+h/2-2,4,4,2,UiStyle.INK);
        }else{
            int bg=kind==PRIMARY?accent:kind==CHIP&&on?0xFF382832:0xFF242125;
            if(kind==PRIMARY)UiStyle.shape(g,x,y,w,h,6,bg);else UiStyle.surface(g,x,y,w,h,6,bg);
            if(hover>.1)UiStyle.shape(g,x,y,w,h,6,((int)(hover*16)<<24)|0xFFFFFF);
            UiStyle.centered(g,title,x,y,w,h,kind==STEP?12:10,kind==PRIMARY?UiStyle.INK:on?accent:fg);
        }
    }
}
