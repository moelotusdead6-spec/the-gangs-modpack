package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.AlexCavesProtection;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Refuses boss spawn eggs in /wild before the egg is consumed (also covers re-typing spawners). */
@Mixin(SpawnEggItem.class)
public abstract class SpawnEggWildBossMixin {
    @Shadow
    public abstract EntityType<?> getEntityType(NbtCompound nbt);

    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void goldclaim$denyWildBossEggOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        World world = context.getWorld();
        if (!world.isClient && AlexCavesProtection.blocksWildBossType(this.getEntityType(context.getStack().getNbt()), world)) {
            if (context.getPlayer() != null) AlexCavesProtection.sendWildBossMessage(context.getPlayer());
            cir.setReturnValue(ActionResult.FAIL);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void goldclaim$denyWildBossEgg(World world, PlayerEntity user, Hand hand,
                                          CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && AlexCavesProtection.blocksWildBossType(this.getEntityType(stack.getNbt()), world)) {
            AlexCavesProtection.sendWildBossMessage(user);
            cir.setReturnValue(TypedActionResult.fail(stack));
        }
    }
}
