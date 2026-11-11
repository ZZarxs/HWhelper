package ru.hwhelper.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Locale;

final class KeyAlert {
    private static final net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId KEY_TOAST =
            new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId(4500);
    private static final Identifier KEY_SOUND_ID=Identifier.fromNamespaceAndPath("hwhelper","unique_key");
    private static final SoundEvent KEY_SOUND=SoundEvent.createVariableRangeEvent(KEY_SOUND_ID);
    private boolean wasEnabled=true;
    private Object player;
    private int warmup, previous, candidate, stable;
    private long started;
    static boolean matchesName(String name) {
        return name.replaceAll("§.", "").replaceAll("[\\p{Cf}]", "")
                .replace('\u00a0', ' ').strip().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ").equals("универсальный ключ");
    }
    private int keys(ItemStack stack) {
        return stack.is(Items.TRIPWIRE_HOOK) && matchesName(stack.getHoverName().getString()) ? stack.getCount() : 0;
    }
    void tick(Minecraft client,HwConfig config) {
        boolean enabled=config.keyAlertEnabled;
        if(!enabled && wasEnabled) {
            started=0;
            client.getSoundManager().stop(KEY_SOUND_ID,net.minecraft.sounds.SoundSource.PLAYERS);
            net.minecraft.client.gui.components.toasts.SystemToast.forceHide(client.getToastManager(),KEY_TOAST);
        }
        wasEnabled=enabled;
        if(client.player==null || client.level==null) { player=null; started=0; return; }
        if(player!=client.player) {player=client.player;warmup=20;previous=0;candidate=0;stable=0;started=0;}
        int count=0;
        var inventory=client.player.getInventory();
        for(int i=0;i<inventory.getContainerSize();i++) count+=keys(inventory.getItem(i));
        // Include the carried stack so moving a key through the cursor does not count as receiving it.
        count+=keys(client.player.containerMenu.getCarried());
        if(!enabled) { previous=count;candidate=count;stable=0;return; }
        if(warmup>0){warmup--;previous=count;candidate=count;return;}
        if(count!=candidate){candidate=count;stable=0;return;}
        if(++stable<3)return;
        if(count>previous){
            if(client.screen!=null && !(client.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
                started=0;
                net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(client.getToastManager(),KEY_TOAST,
                    UiStyle.text("УНИК!",12).copy().withStyle(style->style.withColor(config.accent()&0xFFFFFF)),
                    net.minecraft.network.chat.Component.literal("Универсальный ключ получен"));
            } else started=System.nanoTime();
            client.player.playSound(KEY_SOUND,config.keySoundVolume,1f);
        }
        previous=count;
    }
    void render(GuiGraphics g,HwConfig config) {
        if(started==0)return;
        double age=(System.nanoTime()-started)/1_000_000_000.0;
        if(age>4.2){started=0;return;}
        float opacity=(float)Math.min(1,Math.min(age/.2,(4.2-age)/.7));
        if(opacity<.02f)return;
        var font=Minecraft.getInstance().font;
        float entrance=config.animations?(float)(1-Math.pow(1-Math.min(1,age/.4),3)):1;
        int w=Math.min(276,g.guiWidth()-24),x=(g.guiWidth()-w)/2,y=(int)(g.guiHeight()*.24+(1-entrance)*12);
        UiStyle.surface(g,x,y,w,71,10,UiStyle.alpha(0xFA111012,opacity));
        UiStyle.shape(g,x+13,y+15,39,39,10,UiStyle.alpha(0xFF3B2531,opacity));
        UiStyle.icon(g,"key",x+22,y+24,21,UiStyle.alpha(config.accent(),opacity));
        g.drawString(font,UiStyle.text("УНИК!",18),x+66,y+14,UiStyle.alpha(UiStyle.WHITE,opacity),true);
        g.drawString(font,UiStyle.text("Универсальный ключ получен",9),x+66,y+39,UiStyle.alpha(UiStyle.TEXT,opacity),true);
        UiStyle.shape(g,x+13,y+63,w-26,2,1,UiStyle.alpha(0xFF393137,opacity));
        UiStyle.shape(g,x+13,y+63,Math.max(1,(int)((w-26)*(1-age/4.2))),2,1,UiStyle.alpha(config.accent(),opacity));
    }
}
