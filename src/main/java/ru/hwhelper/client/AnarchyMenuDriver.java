package ru.hwhelper.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Main-thread native actions. No mouse coordinates, synthetic inventory swaps or raw packets. */
final class AnarchyMenuDriver {
    static AnarchyMenu snapshot(Minecraft client){
        if(!(client.screen instanceof AbstractContainerScreen<?> screen)||client.player==null)return null;
        var menu=screen.getMenu();List<AnarchyMenu.Entry> entries=new ArrayList<>();
        if(menu instanceof ChestMenu&&menu==client.player.containerMenu&&menu.containerId>0){
            for(var slot:menu.slots){
                if(slot.container==client.player.getInventory())continue;
                var item=slot.getItem();if(item.isEmpty())continue;
                var lore=item.get(DataComponents.LORE);
                var custom=item.get(DataComponents.CUSTOM_DATA);
                var routing=AnarchyMenu.routing(custom==null?new CompoundTag():custom.copyTag());
                entries.add(new AnarchyMenu.Entry(slot.index,BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),item.getHoverName().getString(),lore==null?List.of():lore.lines().stream().map(line->line.getString()).toList(),item.getCount(),false,routing.category(),routing.server()));
            }
        }
        return new AnarchyMenu(menu.containerId,menu.getStateId(),screen.getTitle().getString(),entries);
    }
    void apply(Minecraft client,AnarchyTransferFlow.Action action){
        if(client.player==null||client.gameMode==null||client.getConnection()==null)throw new IllegalStateException("Мир ещё не готов");
        switch(action.kind()){
            case HUB->client.getConnection().sendCommand("hub");
            case LITE->client.getConnection().sendCommand("lite");
            case CLICK->{
                AnarchyMenu current=snapshot(client);
                if(!action.menu().equals(current))throw new IllegalStateException("Содержимое меню изменилось до клика");
                var slot=client.player.containerMenu.slots.stream().filter(s->s.index==action.slot()).findFirst().orElseThrow();
                if(slot.container==client.player.getInventory()||!client.player.containerMenu.getCarried().isEmpty())throw new IllegalStateException("Инвентарь занят ручным действием");
                client.gameMode.handleInventoryMouseClick(current.containerId(),action.slot(),0,ClickType.PICKUP,client.player);
            }
        }
    }
}
