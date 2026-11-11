package ru.hwhelper.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import java.util.*;

/** Main-thread native actions. No mouse coordinates, synthetic inventory swaps or raw packets. */
final class AnarchyMenuDriver {
    private Inventory inventory;private int savedSlot,compassSlot;
    static int compass(Minecraft client){
        if(client.player==null)return -1;
        for(int slot=0;slot<9;slot++){
            var item=client.player.getInventory().getItem(slot);String name=AnarchyMenu.normalize(item.getHoverName().getString());
            if(item.is(Items.COMPASS)&&(name.contains("выбор режима")||name.contains("выбор сервера")||name.contains("выбор игр")))return slot;
        }return -1;
    }
    static AnarchyMenu snapshot(Minecraft client){
        if(!(client.screen instanceof AbstractContainerScreen<?> screen)||client.player==null)return null;
        var menu=screen.getMenu();List<AnarchyMenu.Entry> entries=new ArrayList<>();
        if(menu instanceof ChestMenu&&menu==client.player.containerMenu&&menu.containerId>0){
            for(var slot:menu.slots){
                if(slot.container==client.player.getInventory())continue;
                var item=slot.getItem();if(item.isEmpty())continue;
                var lore=item.get(DataComponents.LORE);
                entries.add(new AnarchyMenu.Entry(slot.index,BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),item.getHoverName().getString(),lore==null?List.of():lore.lines().stream().map(line->line.getString()).toList(),item.getCount(),false));
            }
        }
        return new AnarchyMenu(menu.containerId,menu.getStateId(),screen.getTitle().getString(),entries);
    }
    void apply(Minecraft client,AnarchyTransferFlow.Action action){
        if(client.player==null||client.gameMode==null||client.getConnection()==null)throw new IllegalStateException("Мир ещё не готов");
        switch(action.kind()){
            case HUB->client.getConnection().sendCommand("hub");
            case COMPASS->{
                int slot=compass(client);if(slot<0)throw new IllegalStateException("Компас выбора режима не найден");
                if(client.screen!=null)throw new IllegalStateException("Другой экран перекрыл использование компаса");
                inventory=client.player.getInventory();savedSlot=inventory.getSelectedSlot();compassSlot=slot;
                inventory.setSelectedSlot(slot);client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
            }
            case CLICK->{
                AnarchyMenu current=snapshot(client);
                if(!action.menu().equals(current))throw new IllegalStateException("Содержимое меню изменилось до клика");
                var slot=client.player.containerMenu.slots.stream().filter(s->s.index==action.slot()).findFirst().orElseThrow();
                if(slot.container==client.player.getInventory()||!client.player.containerMenu.getCarried().isEmpty())throw new IllegalStateException("Инвентарь занят ручным действием");
                client.gameMode.handleInventoryMouseClick(current.containerId(),action.slot(),0,ClickType.PICKUP,client.player);
            }
        }
    }
    void restore(Minecraft client,boolean finish){
        if(inventory==null)return;
        if(client.player==null||!mayRestore(inventory,client.player.getInventory(),inventory.getSelectedSlot(),compassSlot)){inventory=null;return;}
        if(finish||client.screen instanceof AbstractContainerScreen<?>){inventory.setSelectedSlot(savedSlot);inventory=null;}
    }
    static boolean mayRestore(Object savedInventory,Object currentInventory,int selected,int compass){return savedInventory==currentInventory&&selected==compass;}
}
