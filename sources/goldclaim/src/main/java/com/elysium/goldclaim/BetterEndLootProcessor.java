package com.elysium.goldclaim;

import net.minecraft.block.ChestBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate.StructureBlockInfo;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.betterx.betterend.util.LootTableUtil;

public final class BetterEndLootProcessor extends StructureProcessor {
    @Override
    public StructureBlockInfo process(WorldView world, BlockPos origin, BlockPos pivot,
            StructureBlockInfo original, StructureBlockInfo current, StructurePlacementData placement) {
        if (!(current.state().getBlock() instanceof ChestBlock)) return current;
        NbtCompound nbt = current.nbt() == null ? new NbtCompound() : current.nbt().copy();
        if (!nbt.contains("LootTable")) {
            nbt.remove("Items");
            nbt.putString("LootTable", LootTableUtil.getTable(world.getBiome(current.pos())).toString());
        }
        if (!nbt.contains("LootTableSeed")) {
            nbt.putLong("LootTableSeed", placement.getRandom(current.pos()).nextLong());
        }
        // Template placement reads this NBT after creating the final block entity.
        return new StructureBlockInfo(current.pos(), current.state(), nbt);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.BLOCK_IGNORE;
    }
}
