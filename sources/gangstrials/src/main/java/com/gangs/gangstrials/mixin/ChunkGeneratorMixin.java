package com.gangs.gangstrials.mixin;

import com.gangs.gangstrials.StructureDimensionRules;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "trySetStructureStart", at = @At("HEAD"), cancellable = true)
    private void gangstrials$gateStructureStart(StructureSet.WeightedEntry weightedEntry,
            StructureAccessor structureAccessor, DynamicRegistryManager dynamicRegistryManager,
            NoiseConfig noiseConfig, StructureTemplateManager structureManager, long seed, Chunk chunk,
            ChunkPos pos, ChunkSectionPos sectionPos, CallbackInfoReturnable<Boolean> cir) {
        String structureId = weightedEntry.structure().getKey()
                .map(key -> key.getValue().toString()).orElse(null);
        if (structureId == null) {
            return;
        }
        String dimensionId = gangstrials$dimensionId(
                ((StructureAccessorAccessor) structureAccessor).gangstrials$getWorld());
        if (!StructureDimensionRules.isAllowed(structureId, dimensionId)) {
            cir.setReturnValue(false);
        }
    }

    private static String gangstrials$dimensionId(WorldAccess world) {
        RegistryKey<World> key = null;
        if (world instanceof World w) {
            key = w.getRegistryKey();
        } else if (world instanceof ServerWorldAccess swa) {
            key = swa.toServerWorld().getRegistryKey();
        }
        return key == null ? null : key.getValue().toString();
    }
}
