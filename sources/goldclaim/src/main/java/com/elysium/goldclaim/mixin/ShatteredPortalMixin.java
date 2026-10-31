package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.BorderClamp;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.alexthe666.alexsmobs.entity.EntityVoidPortal", remap = false)
public abstract class ShatteredPortalMixin {
    @Shadow public RegistryKey<World> exitDimension;
    @Shadow public abstract boolean isShattered();

    @Inject(method = "getDestination", at = @At("RETURN"), cancellable = true)
    private void goldclaim$stopAtBorder(CallbackInfoReturnable<BlockPos> cir) {
        BlockPos destination = cir.getReturnValue();
        if (destination == null || !isShattered()
                || !(((Entity)(Object)this).getWorld() instanceof ServerWorld source)) return;
        ServerWorld target = exitDimension == null ? source : source.getServer().getWorld(exitDimension);
        if (target == null) return;
        var border = target.getWorldBorder();
        double[] inside = BorderClamp.inside(destination.getX() + 0.5, destination.getZ() + 0.5,
            border.getBoundWest(), border.getBoundEast(), border.getBoundNorth(), border.getBoundSouth(),
            BorderClamp.MARGIN);
        // Resolve on every read so saved portals and shrinking borders cannot retain an out-of-bounds exit.
        cir.setReturnValue(BlockPos.ofFloored(inside[0], destination.getY(), inside[1]));
    }
}
