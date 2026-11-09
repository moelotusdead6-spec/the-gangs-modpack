package com.gangs.botaniacompat.mixin;

import com.gangs.botaniacompat.BotaniaCompat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "vazkii.botania.common.item.equipment.bauble.FlugelTiaraItem", remap = false)
public abstract class FlugelTiaraMixin {
    @Inject(method = "shouldPlayerHaveFlight", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gangs$denyTiaraFlight(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "onWornTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void gangs$disableTiaraMovement(ItemStack stack, LivingEntity wearer, CallbackInfo ci) {
        // Saved movement flags must not revive a glide or queued dash after the update.
        var nbt = stack.getNbt();
        if (nbt != null) {
            nbt.remove("flying");
            nbt.remove("gliding");
            nbt.remove("boostPending");
        }
        ci.cancel();
    }

    @Inject(method = "onEquipped", at = @At("TAIL"), remap = false)
    private void gangs$notifyWearer(ItemStack stack, LivingEntity wearer, CallbackInfo ci) {
        if (wearer instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.literal(BotaniaCompat.NOTICE), false);
        }
    }
}
