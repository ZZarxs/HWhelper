package ru.hwhelper.client;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import ru.hwhelper.client.standalone.TelegramService;

/** All authentication stays in Minecraft; secrets never enter the shared mod configuration. */
final class TelegramSettingsScreen extends Screen {
    private final HwConfig config;private final HWhelperClient mod;private final Screen parent;private final TelegramService service;
    private final Identifier qrId=Identifier.fromNamespaceAndPath("hwhelper","telegram_login_qr");
    private TelegramService.Phase phase;private EditBox first,second;private String qrLink="",validation="";private boolean qrReady;
    private int x,y,w,h;
    TelegramSettingsScreen(HwConfig config,HWhelperClient mod,Screen parent){super(Component.literal("HWhelper / Telegram"));this.config=config;this.mod=mod;this.parent=parent;service=mod.telegram();}
    private void button(int bx,int by,int bw,String text,Runnable action){addRenderableWidget(new MenuControl(bx,by,bw,22,text,"",MenuControl.ACTION,config,null,null,b->action.run()));}
    private EditBox field(int bx,int by,int bw,String hint,boolean secret){
        EditBox box=new EditBox(font,bx,by,bw,22,Component.literal(hint));box.setHint(UiStyle.text(hint,10));box.setMaxLength(secret?256:128);
        box.addFormatter((text,cursor)->UiStyle.text(secret?"•".repeat(text.length()):text,10).getVisualOrderText());addRenderableWidget(box);return box;
    }
    protected void init(){
        w=Math.min(470,width-20);h=Math.min(292,height-20);x=(width-w)/2;y=(height-h)/2;
        first=second=null;phase=service.status().phase();validation="";int left=x+14,wide=w-28,body=y+79;
        switch(phase){
            case CONFIG,ERROR,STOPPED->{
                first=field(left,body,wide,"API ID",false);second=field(left,body+30,wide,"API Hash (32 символа)",true);
                int bw=(wide-6)/2;
                button(left,body+60,bw,"Подключить",()->{
                    try{int id=Integer.parseInt(first.getValue().strip());String hash=second.getValue().strip();
                        if(id<=0||!hash.matches("[a-fA-F0-9]{32}"))throw new IllegalArgumentException();
                        config.telegramAutoConnect=true;config.save();service.connect(id,hash);second.setValue("");
                    }catch(IllegalArgumentException e){validation="API ID — число; API Hash — 32 символа с my.telegram.org/apps";}
                });
                button(left+bw+6,body+60,bw,"Сохранённая сессия",()->{config.telegramAutoConnect=true;config.save();service.connectSaved();});
                button(left,body+87,wide,"Получить API ID / Hash ↗",()->Util.getPlatform().openUri("https://my.telegram.org/apps"));
            }
            case PHONE->{
                first=field(left,body,wide,"Телефон: +380…",false);
                button(left,body+30,wide,"Отправить номер",()->service.phone(first.getValue()));
                button(left,body+60,wide,"Войти по QR-коду",service::qr);
            }
            case QR->{
                int qrSize=Math.min(112,h-128),side=left+qrSize+14;
                first=field(side,body,w-42-qrSize,"Телефон: +380…",false);
                button(side,body+30,w-42-qrSize,"Войти по номеру",()->service.phone(first.getValue()));
                button(side,body+60,w-42-qrSize,"Обновить QR",service::qr);
                updateQr();
            }
            case CODE,PASSWORD,EMAIL,EMAIL_CODE->{
                String hint=switch(phase){case PASSWORD->"Пароль двухэтапной проверки";case EMAIL->"Email";case EMAIL_CODE->"Код из письма";default->"Код из Telegram";};
                first=field(left,body,wide,hint,phase==TelegramService.Phase.PASSWORD);
                button(left,body+30,wide,"Продолжить",()->{String value=first.getValue();first.setValue("");switch(phase){case PASSWORD->service.password(value);case EMAIL->service.email(value);case EMAIL_CODE->service.emailCode(value);default->service.code(value);}});
            }
            case READY->{
                button(left,body,wide,"Отключить, сохранив сессию",()->{config.telegramAutoConnect=false;config.save();service.disconnect();});
                button(left,body+30,wide,"Выйти из аккаунта HWhelper",()->{config.telegramAutoConnect=false;config.save();service.logout();});
                button(left,body+60,wide,"Автоподключение: "+(config.telegramAutoConnect?"ВКЛ":"ВЫКЛ"),()->{config.telegramAutoConnect=!config.telegramAutoConnect;config.save();rebuild();});
            }
            default->button(left,body,wide,"Остановить подключение",()->{config.telegramAutoConnect=false;config.save();service.disconnect();});
        }
        button(left,y+h-27,78,"Назад",this::onClose);
        if(first!=null)setInitialFocus(first);
    }
    private void rebuild(){clearWidgets();init();}
    public void tick(){if(phase!=service.status().phase())rebuild();if(phase==TelegramService.Phase.QR)updateQr();}
    private void updateQr(){
        String link=service.status().qrLink();if(link.equals(qrLink))return;qrLink=link;qrReady=false;
        if(link.isBlank())return;
        try{
            var matrix=new QRCodeWriter().encode(link,BarcodeFormat.QR_CODE,256,256);NativeImage image=new NativeImage(256,256,false);
            for(int py=0;py<256;py++)for(int px=0;px<256;px++)image.setPixel(px,py,matrix.get(px,py)?0xFF000000:0xFFFFFFFF);
            var texture=new DynamicTexture(()->"Telegram login QR",image);minecraft.getTextureManager().release(qrId);minecraft.getTextureManager().register(qrId,texture);texture.upload();qrReady=true;
        }catch(Exception e){validation="Не удалось построить QR. Используйте номер телефона.";}
    }
    public void renderBackground(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xE00C0B10);}
    public void render(GuiGraphics g,int mx,int my,float dt){
        UiStyle.glass(g,x,y,w,h,config.accent(),0,true,250);
        UiStyle.label(g,"Telegram",x+14,y+13,16,UiStyle.WHITE);UiStyle.fitted(g,"Встроенная связь • @liteeventbot",x+14,y+35,w-28,9,config.accent());
        UiStyle.fitted(g,validation.isBlank()?service.status().message():validation,x+14,y+57,w-28,9,validation.isBlank()?UiStyle.TEXT:0xFFFF90AA);
        if(phase==TelegramService.Phase.QR&&qrReady){int size=Math.min(112,h-128);g.blit(RenderPipelines.GUI_TEXTURED,qrId,x+14,y+79,0f,0f,size,size,256,256,256,256);}
        if(phase==TelegramService.Phase.PASSWORD&&!service.status().hint().isBlank())UiStyle.fitted(g,"Подсказка: "+service.status().hint(),x+14,y+139,w-28,8,UiStyle.MUTED);
        UiStyle.fitted(g,mod.mediaStatus(),x+102,y+h-21,w-116,8,UiStyle.MUTED);
        super.render(g,mx,my,dt);
    }
    public void onClose(){minecraft.setScreen(parent);}
    public void removed(){if(first!=null)first.setValue("");if(second!=null)second.setValue("");minecraft.getTextureManager().release(qrId);qrReady=false;qrLink="";}
    public boolean isPauseScreen(){return false;}
}
