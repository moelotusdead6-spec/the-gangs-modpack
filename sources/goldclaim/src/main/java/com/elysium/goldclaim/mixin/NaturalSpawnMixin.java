package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.NaturalSpawnPolicy;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnHelper.class)
public abstract class NaturalSpawnMixin {
    @Inject(method = "isValidSpawn", at = @At("HEAD"), cancellable = true)
    private static void goldclaim$naturalSpawn(ServerWorld world, MobEntity mob, double distance,
                                              CallbackInfoReturnable<Boolean> callback) {
        if (!NaturalSpawnPolicy.allow(mob, world)) {
            callback.setReturnValue(false);
        }
    }

    @Redirect(method = "populateEntities", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/EntityType;create(Lnet/minecraft/world/World;)Lnet/minecraft/entity/Entity;"))
    private static Entity goldclaim$generationSpawn(EntityType<?> type, World world,
            ServerWorldAccess region, RegistryEntry<Biome> biome, ChunkPos pos, Random random) {
        Entity entity = type.create(world);
        return entity instanceof MobEntity mob && !NaturalSpawnPolicy.allow(mob, world, random) ? null : entity;
    }
}
