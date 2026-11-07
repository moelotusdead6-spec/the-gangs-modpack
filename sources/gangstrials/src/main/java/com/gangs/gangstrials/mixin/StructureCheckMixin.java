package com.gangs.gangstrials.mixin;

import com.gangs.gangstrials.StructureDimensionRules;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.StructureLocator;
import net.minecraft.world.StructurePresence;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps /locate, maps and other structure searches consistent with the
 * generation gate in {@link ChunkGeneratorMixin}.
 */
@Mixin(StructureLocator.class)
public abstract class StructureCheckMixin {
    @Shadow
    @Final
    private RegistryKey<World> worldKey;

    @Shadow
    @Final
    private DynamicRegistryManager registryManager;

    @Inject(method = "getStructurePresence(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/gen/structure/Structure;Z)Lnet/minecraft/world/StructurePresence;",
            at = @At("HEAD"), cancellable = true)
    private void gangstrials$gateStructurePresence(ChunkPos pos, Structure structure,
            boolean skipReferencedStructures, CallbackInfoReturnable<StructurePresence> cir) {
        gangstrials$gate(structure, cir);
    }

    @Inject(method = "getStructurePresence(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/gen/structure/Structure;ZJ)Lnet/minecraft/world/StructurePresence;",
            at = @At("HEAD"), cancellable = true)
    private void gangstrials$gateStructurePresenceSeeded(ChunkPos pos, Structure structure,
            boolean skipReferencedStructures, long seed, CallbackInfoReturnable<StructurePresence> cir) {
        gangstrials$gate(structure, cir);
    }

    private void gangstrials$gate(Structure structure, CallbackInfoReturnable<StructurePresence> cir) {
        Identifier id = this.registryManager.get(RegistryKeys.STRUCTURE).getId(structure);
        if (id == null) {
            return;
        }
        if (!StructureDimensionRules.isAllowed(id.toString(), this.worldKey.getValue().toString())) {
            cir.setReturnValue(StructurePresence.START_NOT_PRESENT);
        }
    }
}
