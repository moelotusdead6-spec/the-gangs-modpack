package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.BeachSurfacePolicy;
import com.mojang.serialization.JsonOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.gen.surfacebuilder.MaterialRules$ConditionMaterialRule")
public abstract class BeachSurfaceRuleMixin {
    @Shadow @Final private MaterialRules.MaterialCondition ifTrue;
    @Shadow @Final @Mutable private MaterialRules.MaterialRule thenRun;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void goldclaim$preserveCoastalTerrain(CallbackInfo ci) {
        if (!(thenRun instanceof SurfaceBlockRuleAccessor block) || !block.goldclaim$resultState().isAir()) return;
        var condition = MaterialRules.MaterialCondition.CODEC.encodeStart(JsonOps.INSTANCE, ifTrue)
            .getOrThrow(false, message -> LoggerFactory.getLogger("GoldClaim/Beaches").error(message));
        if (!BeachSurfacePolicy.isCoastalAirCut(condition)) return;
        var beaches = MaterialRules.biome(
            RegistryKey.of(RegistryKeys.BIOME, new Identifier("wythers:coastal_mangroves")),
            RegistryKey.of(RegistryKeys.BIOME, new Identifier("wythers:black_beach")),
            RegistryKey.of(RegistryKeys.BIOME, new Identifier("wythers:tropical_beach")),
            RegistryKey.of(RegistryKeys.BIOME, new Identifier("wythers:gravelly_beach")),
            RegistryKey.of(RegistryKeys.BIOME, new Identifier("minecraft:beach")),
            RegistryKey.of(RegistryKeys.BIOME, new Identifier("minecraft:snowy_beach")));
        thenRun = MaterialRules.condition(MaterialRules.not(beaches), thenRun);
        LoggerFactory.getLogger("GoldClaim/Beaches").info(
            "Protected six coastal biomes from Expanded Ecosphere's sea-level air cut; river/ocean/lake rules retained.");
    }
}
