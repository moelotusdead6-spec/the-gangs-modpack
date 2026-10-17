package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.BetterEndLootProcessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.structure.StructurePlacementData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.BuildingListFeature", remap = false)
public abstract class BetterEndStructureLootMixin {
    @Inject(method = "addStructureData", at = @At("HEAD"), cancellable = true, remap = false)
    private void goldclaim$keepDeferredStructureLoot(StructurePlacementData placement, CallbackInfo ci) {
        if (FabricLoader.getInstance().isModLoaded("lootr")) {
            placement.addProcessor(new BetterEndLootProcessor());
            ci.cancel();
        }
    }
}
