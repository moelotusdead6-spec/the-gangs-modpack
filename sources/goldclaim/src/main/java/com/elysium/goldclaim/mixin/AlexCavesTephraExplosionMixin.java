package com.elysium.goldclaim.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.entity.util.TephraExplosion", remap = false)
public abstract class AlexCavesTephraExplosionMixin {
    @Shadow public abstract Entity getDirectSourceEntity();
    @Shadow public abstract void clearToBlow();

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void goldclaim$preventEruptionTerrainDamage(boolean particles, CallbackInfo ci) {
        Entity source = getDirectSourceEntity();
        if (source != null && new Identifier("alexscaves", "tephra").equals(
                Registries.ENTITY_TYPE.getId(source.getType()))) {
            clearToBlow();
        }
    }
}
