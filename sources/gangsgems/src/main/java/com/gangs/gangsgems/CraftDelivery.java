package com.gangs.gangsgems;

import com.gangs.gangsgems.mixin.PlayerManagerInvoker;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import net.conczin.immersive_furniture.data.FurnitureData;
import net.conczin.immersive_furniture.item.FurnitureItem;
import net.conczin.immersive_furniture.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CraftDelivery {
    private static final Logger LOGGER = LoggerFactory.getLogger("GangGems/Craft");
    private static final String RECEIPT_PREFIX = "gangsgems_craft:";

    private CraftDelivery() {}

    public static void craft(ServerPlayerEntity player, FurnitureData data, boolean shift) {
        WalletStore wallet = GangGems.wallet();
        if (wallet == null) {
            player.sendMessage(Text.literal("[Gang Gems] Wallet unavailable; contact staff."), false);
            return;
        }
        if (data == null) {
            player.sendMessage(Text.literal("[Gang Gems] Invalid furniture design."), false);
            return;
        }
        int cost = data.getCost();
        if (cost <= 0) {
            player.sendMessage(Text.literal("[Gang Gems] This design has an invalid crafting cost."), false);
            return;
        }
        long balance = wallet.balance(player.getUuid());
        int count = shift ? (int) Math.min(64L, balance / cost) : 1;
        if (count < 1 || balance < (long) cost * count) {
            player.sendMessage(Text.literal("[Gang Gems] Not enough Gems. Cost: " + cost + "."), false);
            return;
        }
        ItemStack stack = new ItemStack(Items.FURNITURE, count);
        if (count > stack.getMaxCount()) {
            player.sendMessage(Text.literal("[Gang Gems] Furniture stack is too large."), false);
            return;
        }
        FurnitureItem.setData(stack, data);
        try {
            wallet.reserveCraft(player.getUuid(), (long) cost * count, stack.writeNbt(new NbtCompound()).toString());
            deliverPending(player);
        } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
            LOGGER.error("Could not reserve furniture for {}", player.getUuid(), failure);
            player.sendMessage(Text.literal("[Gang Gems] Furniture could not be reserved; contact staff."), false);
        }
    }

    public static void deliverPending(ServerPlayerEntity player) {
        WalletStore wallet = GangGems.wallet();
        if (wallet == null) return;
        for (WalletStore.Craft craft : wallet.pendingCrafts(player.getUuid())) {
            try {
                deliver(player, wallet, craft);
            } catch (IOException | com.mojang.brigadier.exceptions.CommandSyntaxException | IllegalArgumentException failure) {
                LOGGER.error("Could not deliver reserved furniture {} for {}", craft.id(), player.getUuid(), failure);
                player.sendMessage(Text.literal("[Gang Gems] Furniture delivery failed; contact staff."), false);
                return;
            }
        }
        removeOldReceipts(player, wallet);
    }

    private static void deliver(ServerPlayerEntity player, WalletStore wallet, WalletStore.Craft craft)
            throws IOException, com.mojang.brigadier.exceptions.CommandSyntaxException {
        String receipt = RECEIPT_PREFIX + craft.id();
        if (!player.getCommandTags().contains(receipt)) {
            ItemStack stack = ItemStack.fromNbt(StringNbtReader.parse(craft.itemNbt()));
            if (stack.isEmpty() || stack.getCount() > stack.getMaxCount()) throw new IOException("Invalid furniture item");
            if (!fits(player, stack)) {
                player.sendMessage(Text.literal("[Gang Gems] Make room and use /ganggems claim for your furniture."), false);
                return;
            }
            if (!player.addCommandTag(receipt)) throw new IOException("Could not create delivery receipt");
            if (!player.getInventory().insertStack(stack)) throw new IOException("Could not insert reserved furniture");
            player.getInventory().markDirty();
        }
        ((PlayerManagerInvoker) player.getServer().getPlayerManager()).gangsgems$savePlayerData(player);
        Path saved = player.getServer().getSavePath(WorldSavePath.PLAYERDATA).resolve(player.getUuidAsString() + ".dat");
        NbtCompound nbt = NbtIo.readCompressed(saved.toFile());
        if (nbt.getList("Tags", NbtElement.STRING_TYPE).stream().noneMatch(tag -> receipt.equals(tag.asString()))) {
            throw new IOException("Furniture receipt was not saved");
        }
        wallet.completeCraft(craft.id(), player.getUuid());
        player.getCommandTags().remove(receipt);
        player.sendMessage(Text.literal("[Gang Gems] Your furniture has arrived."), false);
    }

    private static boolean fits(ServerPlayerEntity player, ItemStack stack) {
        int remaining = stack.getCount();
        for (ItemStack slot : player.getInventory().main) {
            if (slot.isEmpty()) remaining -= Math.min(stack.getMaxCount(), player.getInventory().getMaxCountPerStack());
            else if (ItemStack.canCombine(slot, stack)) remaining -= Math.max(0,
                Math.min(slot.getMaxCount(), player.getInventory().getMaxCountPerStack()) - slot.getCount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static void removeOldReceipts(ServerPlayerEntity player, WalletStore wallet) {
        var pending = wallet.pendingCrafts(player.getUuid()).stream().map(WalletStore.Craft::id).toList();
        player.getCommandTags().removeIf(tag -> {
            if (!tag.startsWith(RECEIPT_PREFIX)) return false;
            try {
                return !pending.contains(UUID.fromString(tag.substring(RECEIPT_PREFIX.length())));
            } catch (IllegalArgumentException failure) {
                LOGGER.warn("Invalid Gang Gems receipt on player {}", player.getUuid());
                return false;
            }
        });
    }
}
