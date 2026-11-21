package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.AlexCavesProtection;
import com.github.alexmodguy.alexscaves.server.block.blockentity.VolcanicCoreBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In /wild the Volcanic Core never attracts or consumes an Ominous Catalyst (no Luxtructosaurus, no tephra).
 * Catalysts thrown at it are handed back to the thrower (or nearest player) with a pointer to RSW.
 */
@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.block.blockentity.VolcanicCoreBlockEntity", remap = false)
public abstract class AlexCavesVolcanicCoreMixin {
    @Unique
    private static final Identifier GOLDCLAIM$CATALYST = new Identifier("alexscaves", "ominous_catalyst");

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void goldclaim$inertInWild(World world, BlockPos pos, BlockState state,
                                             VolcanicCoreBlockEntity core, CallbackInfo ci) {
        if (world.isClient || !AlexCavesProtection.isWild(world)) return;
        ci.cancel();
        if (world.getTime() % 10L != 0L) return;
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, new Box(pos).expand(16.0),
                entity -> entity.isAlive() && GOLDCLAIM$CATALYST.equals(Registries.ITEM.getId(entity.getStack().getItem())))) {
            Entity owner = item.getOwner();
            PlayerEntity player = owner instanceof PlayerEntity thrower && thrower.getWorld() == world
                && thrower.squaredDistanceTo(item) < 64.0 * 64.0
                ? thrower : world.getClosestPlayer(item, 16.0);
            if (player == null) continue;
            player.getInventory().offerOrDrop(item.getStack().copy());
            item.discard();
            AlexCavesProtection.sendWildBossMessage(player);
        }
    }
}
