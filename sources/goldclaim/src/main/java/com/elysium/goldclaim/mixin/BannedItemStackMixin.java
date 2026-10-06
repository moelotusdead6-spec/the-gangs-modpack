package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.BannedContent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class BannedItemStackMixin {
    @Shadow @Final private Item item;
    @Shadow private int count;
    @Shadow private NbtCompound nbt;

    @Inject(method = {
        "<init>(Lnet/minecraft/item/ItemConvertible;I)V",
        "<init>(Lnet/minecraft/item/ItemConvertible;ILjava/util/Optional;)V",
        "<init>(Lnet/minecraft/nbt/NbtCompound;)V"
    }, at = @At("RETURN"))
    private void goldclaim$removeBinStacks(CallbackInfo ci) {
        if (this.item != null && BannedContent.isBanned(Registries.ITEM.getId(this.item))) {
            if (this.count > 0) {
                BannedContent.report("item creation/load (inventories, loot, entities and creative packets)");
            }
            this.count = 0;
            this.nbt = null;
        } else if (this.nbt != null && BannedContent.sanitize(this.nbt) > 0) {
            BannedContent.report("nested container item load");
        }
    }

    @ModifyVariable(method = "setCount", at = @At("HEAD"), argsOnly = true)
    private int goldclaim$preventBinRefills(int count) {
        if (this.item != null && BannedContent.isBanned(Registries.ITEM.getId(this.item))) {
            if (count > 0) {
                BannedContent.report("item count mutation");
            }
            return 0;
        }
        return count;
    }

    @Inject(method = "setNbt", at = @At("HEAD"))
    private void goldclaim$removeNestedBins(NbtCompound nbt, CallbackInfo ci) {
        if (nbt != null && BannedContent.sanitize(nbt) > 0) {
            BannedContent.report("nested container item update");
        }
    }
}
