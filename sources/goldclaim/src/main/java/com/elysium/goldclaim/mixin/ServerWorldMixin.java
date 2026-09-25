/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.server.world.ServerWorld
 *  net.minecraft.fluid.Fluid
 *  net.minecraft.fluid.Fluids
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.world.timer.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ServerWorld.class})
public abstract class ServerWorldMixin {
    @Inject(method={"tickFluid"}, at={@At(value="HEAD")}, cancellable=true)
    private void goldclaim$freezeHubFluids(BlockPos pos, Fluid fluid, CallbackInfo callbackInfo) {
        if (GoldClaimMod.shouldFreezeHubFluids((ServerWorld)(Object)this) && (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER)) {
            callbackInfo.cancel();
        }
    }

    @Redirect(method={"tickTime"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/timer/Timer;processEvents(Ljava/lang/Object;J)V"))
    private void goldclaim$skipSharedScheduledEvents(Timer<MinecraftServer> timer, Object server, long time) {
        if (!GoldClaimMod.isTestingWorld((ServerWorld)(Object)this)) {
            timer.processEvents((MinecraftServer)server, time);
        }
    }
}
