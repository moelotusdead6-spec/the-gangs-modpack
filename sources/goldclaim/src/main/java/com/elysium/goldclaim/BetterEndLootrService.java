package com.elysium.goldclaim;

import com.elysium.goldclaim.mixin.ChestInventoryAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.state.property.Property;
import net.zestyblaze.lootr.init.ModBlocks;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.zestyblaze.lootr.api.blockentity.ILootBlockEntity;
import net.zestyblaze.lootr.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BetterEndLootrService {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/Lootr");

    public static boolean convertBeforeOpening(ServerWorld world, BlockPos pos, BlockState state) {
        if (!GoldClaimMod.RSW_THE_END_DIMENSION.equals(world.getRegistryKey().getValue().toString())
                || !Registries.BLOCK.getId(state.getBlock()).getNamespace().equals("betterend")
                || ConfigManager.get().conversion.disable
                || ConfigManager.isDimensionBlacklisted(world.getRegistryKey())
                || (ConfigManager.get().conversion.world_border && !world.getWorldBorder().contains(pos))
                || !(world.getBlockEntity(pos) instanceof ChestBlockEntity original)
                || original instanceof ILootBlockEntity) {
            return false;
        }
        var nbt = original.createNbt();
        if (!nbt.contains("LootTable", NbtElement.STRING_TYPE)
                || ((ChestInventoryAccessor)original).goldclaim$inventory().stream().anyMatch(stack -> !stack.isEmpty())
                || !nbt.getString("Lock").isEmpty()) return false;
        Identifier table = Identifier.tryParse(nbt.getString("LootTable"));
        if (table == null) {
            throw new IllegalStateException("Invalid natural chest loot table at " + pos);
        }
        if (ConfigManager.isBlacklisted(table)) return false;
        BlockState replacement = ConfigManager.replacement(state);
        if (replacement == null) {
            // The lootr:convert/chests tag can be shadowed by another datapack; plain chests still map safely.
            replacement = fallbackReplacement(state);
            if (replacement == null) {
                LOGGER.warn("BetterEnd container at {} ({}) has no Lootr replacement; opening it normally",
                    pos, Registries.BLOCK.getId(state.getBlock()));
                return false;
            }
        }
        // Lootr's background converter waits for 5x5 FULL chunks. Opening must not consume the table first.
        original.setLootTable(null, 0);
        try {
            if (!world.setBlockState(pos, replacement, Block.NOTIFY_ALL)
                    || !(world.getBlockEntity(pos) instanceof LootableContainerBlockEntity converted)
                    || !(converted instanceof ILootBlockEntity)) {
                throw new IllegalStateException("Lootr chest replacement failed at " + pos);
            }
            converted.setLootTable(table, nbt.getLong("LootTableSeed"));
            converted.setCustomName(original.getCustomName());
            converted.markDirty();
        } catch (RuntimeException failure) {
            LOGGER.error("Lootr conversion failed at {}; restoring the original chest", pos, failure);
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
            if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity restored) {
                restored.readNbt(nbt);
                restored.markDirty();
            } else {
                failure.addSuppressed(new IllegalStateException("Could not restore original chest at " + pos));
            }
            throw failure;
        }
        return true;
    }

    static BlockState fallbackReplacement(BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock) || state.getBlock() instanceof TrappedChestBlock) return null;
        BlockState replacement = ModBlocks.CHEST.getDefaultState();
        for (Property<?> property : state.getProperties()) {
            if (replacement.contains(property)) replacement = copy(replacement, state, property);
        }
        return replacement.contains(ChestBlock.CHEST_TYPE)
            ? replacement.with(ChestBlock.CHEST_TYPE, ChestType.SINGLE) : replacement;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState target, BlockState source, Property<T> property) {
        return target.with(property, source.get(property));
    }
}
