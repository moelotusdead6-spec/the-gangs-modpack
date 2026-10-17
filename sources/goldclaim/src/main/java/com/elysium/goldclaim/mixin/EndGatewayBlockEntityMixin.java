package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EndGatewayBlockEntity.class)
public abstract class EndGatewayBlockEntityMixin {
    @ModifyExpressionValue(method = "tryTeleportingEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"))
    private static RegistryKey<World> goldclaim$allowRswGatewayDestination(RegistryKey<World> dimension) {
        // Only the lazy destination-generation check needs vanilla End semantics.
        return GoldClaimMod.RSW_THE_END_DIMENSION.equals(dimension.getValue().toString())
            ? World.END : dimension;
    }
}
