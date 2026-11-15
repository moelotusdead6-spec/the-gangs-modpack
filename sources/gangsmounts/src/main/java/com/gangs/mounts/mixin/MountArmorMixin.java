package com.gangs.mounts.mixin;

import com.gangs.mounts.MountService;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.yahoo.chirpycricket.mythicmounts.entity.MountEntity", remap = false)
public abstract class MountArmorMixin {
	@Inject(method = {
			"readCustomDataFromNbt(Lnet/minecraft/nbt/NbtCompound;)V",
			"method_5749(Lnet/minecraft/class_2487;)V"
	}, at = @At("TAIL"))
	private void restoreNativeArmor(NbtCompound nbt, CallbackInfo ci) {
		Entity entity = (Entity) (Object) this;
		if (!MountService.managed(entity) || !nbt.contains("ArmorItem", NbtCompound.COMPOUND_TYPE)) return;
		// Mythic accepts chestplates, but HorseEntity's NBT reader only restores HorseArmorItem.
		((Inventory) this).setStack(1, ItemStack.fromNbt(nbt.getCompound("ArmorItem")));
	}
}
