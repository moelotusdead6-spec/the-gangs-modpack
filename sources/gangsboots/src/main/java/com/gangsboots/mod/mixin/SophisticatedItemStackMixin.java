package com.gangsboots.mod.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.extensions.item.SophisticatedItemStack", remap = false)
public interface SophisticatedItemStackMixin {
    @Shadow(remap = false)
    ItemStack self();

    @Inject(method = "onArmorTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void gangsboots$skipEmptyArmorTick(World world, PlayerEntity player, CallbackInfo ci) {
        if (self().isEmpty()) {
            ci.cancel();
        }
    }
}