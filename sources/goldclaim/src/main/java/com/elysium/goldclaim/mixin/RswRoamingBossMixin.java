package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.RswRoamingBosses;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class RswRoamingBossMixin {
    @Inject(method = "initialize", at = @At("RETURN"))
    private void goldclaim$roamingBoss(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason,
            EntityData data, NbtCompound nbt, CallbackInfoReturnable<EntityData> cir) {
        if (FabricLoader.getInstance().isModLoaded("brutalbosses")) {
            RswRoamingBosses.initialize((MobEntity)(Object)this, reason);
        }
    }
}
