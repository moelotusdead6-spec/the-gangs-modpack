package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import net.mehvahdjukaar.supplementaries.common.misc.explosion.BombExplosion;
import net.mehvahdjukaar.supplementaries.common.misc.explosion.CannonBallExplosion;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin({BombExplosion.class, CannonBallExplosion.class})
public abstract class SupplementariesExplosionMixin extends Explosion {
    protected SupplementariesExplosionMixin(World world, Entity entity, double x, double y, double z,
            float power, boolean createFire, DestructionType destructionType) {
        super(world, entity, x, y, z, power, createFire, destructionType);
    }

    @Inject(method = "affectWorld", at = @At("HEAD"))
    private void goldclaim$protectClaimedBlocks(boolean particles, CallbackInfo ci) {
        if (((ExplosionAccessor)this).goldclaim$getWorld() instanceof ServerWorld serverWorld) {
            if (GoldClaimMod.isPvpWorld(serverWorld)) {
                this.clearAffectedBlocks();
            } else {
                // These subclasses override vanilla block destruction, so protect their final block lists.
                this.getAffectedBlocks().removeIf(pos -> GoldClaimMod.getInstance().hasClaimAt(serverWorld, pos));
            }
        }
    }
}
