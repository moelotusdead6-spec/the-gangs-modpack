/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.entity.player.PlayerEntity
 *  net.minecraft.entity.player.PlayerInventory
 *  net.minecraft.inventory.Inventory
 *  net.minecraft.item.ItemStack
 *  net.minecraft.screen.GenericContainerScreenHandler
 *  net.minecraft.screen.ScreenHandlerType
 *  net.minecraft.screen.slot.SlotActionType
 *  net.minecraft.server.network.ServerPlayerEntity
 */
package com.gangs.gangshop.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

public class GangShopScreenHandler
extends GenericContainerScreenHandler {
    private final Inventory inventory;
    private SlotClickHandler clickHandler;

    public GangShopScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, SlotClickHandler clickHandler) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
        this.inventory = inventory;
        this.clickHandler = clickHandler;
    }

    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;
        if (slotIndex >= 0 && slotIndex < this.inventory.size()) {
            this.clickHandler.onClick(serverPlayer, slotIndex, actionType, button);
            return;
        }
    }

    public void refresh(Inventory source, SlotClickHandler clickHandler) {
        if (source.size() != this.inventory.size()) {
            throw new IllegalArgumentException("Gang Shop inventory size cannot change during refresh");
        }
        this.clickHandler = clickHandler;
        for (int slot = 0; slot < this.inventory.size(); ++slot) {
            this.inventory.setStack(slot, source.getStack(slot));
        }
        this.sendContentUpdates();
    }

    @FunctionalInterface
    public static interface SlotClickHandler {
        public void onClick(ServerPlayerEntity var1, int var2, SlotActionType var3, int var4);
    }
}

