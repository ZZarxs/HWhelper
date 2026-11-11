package ru.hwhelper.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class HudEditorScreen extends Screen {
    private final HwConfig config;
    private final HWhelperClient hud;
    private final Screen parent;
    HudEditorScreen(HwConfig c,HWhelperClient h,Screen parent){super(Component.literal("Редактор HUD"));config=c;hud=h;this.parent=parent;}
    @Override protected void init(){
        int w=Math.min(90,(width-24)/4);
        addRenderableWidget(new UiStyle.Action(8,8,w,22,"Готово",config,b->onClose()));
        addRenderableWidget(new UiStyle.Action(12+w,8,w,22,"Сетка: "+(config.snapToGrid?"ВКЛ":"ВЫКЛ"),config,b->{config.snapToGrid=!config.snapToGrid;config.save();clearWidgets();init();}));
        addRenderableWidget(new UiStyle.Action(16+w*2,8,w,22,"Остров",config,b->minecraft.setScreen(new DynamicIslandSettingsScreen(config,this))));
        addRenderableWidget(new UiStyle.Action(20+w*3,8,w,22,config.enabled?"Выключить":"Включить",config,b->{config.enabled=!config.enabled;config.save();clearWidgets();init();}));
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt){}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fill(0,0,width,height,0x770D0B10);
        if(config.snapToGrid){
            for(int x=0;x<width;x+=8)g.fill(x,32,x+1,height,0x137F6B77);
            for(int y=32;y<height;y+=8)g.fill(0,y,width,y+1,0x137F6B77);
        }
        hud.renderHud(g,true);
        UiStyle.glass(g, 2, 2, width-4, 32, config.accent(), 0, true);
        super.render(g,mx,my,dt);
        String hint="Тяни заголовок / угол · позиция "+config.hudX+", "+config.hudY+" · "+Math.round(config.hudScale*100)+"%";
        UiStyle.glass(g, 2, height-20, width-4, 20, config.accent(), 0, false);
        int tw=font.width(UiStyle.text(hint,9));
        g.drawString(font,UiStyle.text(hint,9),(width-tw)/2,height-14,UiStyle.TEXT,true);
    }
    @Override public void onClose(){config.save();minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
