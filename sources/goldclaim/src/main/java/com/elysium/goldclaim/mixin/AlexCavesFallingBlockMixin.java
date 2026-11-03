package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.AlexCavesProtection;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FallingBlockEntity.class)
public abstract class AlexCavesFallingBlockMixin {
    private static final Identifier GOLDCLAIM_HEART_OF_IRON = new Identifier("alexscaves", "heart_of_iron");

    @Inject(method = "spawnFromBlock", at = @At("HEAD"), cancellable = true)
    private static void goldclaim$preventMagnetronBlockPlacement(World world, BlockPos pos, BlockState state,
            CallbackInfoReturnable<FallingBlockEntity> cir) {
        Entity actor = AlexCavesProtection.protectedActor();
        if (!AlexCavesProtection.isMagnetron(actor)) return;
        if (((AlexCavesMagnetronAccessor)actor).goldclaim$hasDroppedHeart()
                && pos.equals(actor.getBlockPos())
                && GOLDCLAIM_HEART_OF_IRON.equals(Registries.BLOCK.getId(state.getBlock()))) {
            actor.dropStack(new ItemStack(state.getBlock()));
        }
        cir.setReturnValue(null);
    }
}
