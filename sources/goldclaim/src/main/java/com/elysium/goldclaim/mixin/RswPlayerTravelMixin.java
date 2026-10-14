package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import com.elysium.goldclaim.LegacyDimensionPolicy;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockLocating;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.NetherPortal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The outer wrapper must validate/redirect before Phantasm starts its vanilla-End cutscene.
@Mixin(value = ServerPlayerEntity.class, priority = 2000)
public abstract class RswPlayerTravelMixin {

    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void goldclaim$rememberBeforeTeleport(ServerWorld target, double x, double y, double z,
                                                 float yaw, float pitch, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        if (LegacyDimensionPolicy.deny(player, target)) {
            ci.cancel();
            return;
        }
        if (target != player.getServerWorld()) {
            GoldClaimMod.rememberRswLocation(player);
        }
    }

    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FF)Z",
        at = @At("HEAD"), cancellable = true)
    private void goldclaim$denyLegacyCommandTeleport(ServerWorld target, double x, double y, double z,
            Set<PositionFlag> flags, float yaw, float pitch, CallbackInfoReturnable<Boolean> cir) {
        if (LegacyDimensionPolicy.deny((ServerPlayerEntity)(Object)this, target)) cir.setReturnValue(false);
    }

    @Inject(method = "setSpawnPoint", at = @At("HEAD"), cancellable = true)
    private void goldclaim$denyLegacySpawn(RegistryKey<World> dimension, BlockPos pos, float angle,
            boolean forced, boolean sendMessage, CallbackInfo ci) {
        if (pos != null && LegacyDimensionPolicy.isClosed(dimension.getValue().toString())) {
            ((ServerPlayerEntity)(Object)this).sendMessage(
                net.minecraft.text.Text.literal(LegacyDimensionPolicy.MESSAGE), false);
            ci.cancel();
        }
    }

    @WrapMethod(method = "moveToWorld")
    private Entity goldclaim$travelWithPolicy(ServerWorld target, Operation<Entity> original) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        ServerWorld redirected = goldclaim$redirectRswDestination(target);
        if (LegacyDimensionPolicy.deny(player, redirected)) {
            return null;
        }
        GoldClaimMod.rememberRswLocation(player);
        return original.call(redirected);
    }

    private ServerWorld goldclaim$redirectRswDestination(ServerWorld destination) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        ServerWorld currentWorld = player.getServerWorld();
        String currentDim = currentWorld.getRegistryKey().getValue().toString();
        MinecraftServer server = currentWorld.getServer();

        if (GoldClaimMod.RSW_DIMENSION.equals(currentDim)) {
            if (destination.getRegistryKey() == World.NETHER) {
                ServerWorld rswNether = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier("multiworld", "rsw_nether")));
                if (rswNether != null) return rswNether;
            } else if (destination.getRegistryKey() == World.END) {
                ServerWorld rswEnd = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier("multiworld", "rsw_the_end")));
                if (rswEnd != null) return rswEnd;
            }
        } else if (GoldClaimMod.RSW_NETHER_DIMENSION.equals(currentDim)) {
            ServerWorld rsw = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier("multiworld", "rsw")));
            if (rsw != null) return rsw;
        } else if (GoldClaimMod.RSW_THE_END_DIMENSION.equals(currentDim)) {
            ServerWorld rsw = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier("multiworld", "rsw")));
            if (rsw != null) return rsw;
        }
        return destination;
    }

    @Inject(method = "getTeleportTarget", at = @At("HEAD"), cancellable = true)
    private void goldclaim$rswTeleportTarget(ServerWorld destination, CallbackInfoReturnable<TeleportTarget> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        ServerWorld currentWorld = player.getServerWorld();
        String currentDim = currentWorld.getRegistryKey().getValue().toString();
        String destDim = destination.getRegistryKey().getValue().toString();

        if (GoldClaimMod.RSW_DIMENSION.equals(currentDim) && GoldClaimMod.RSW_NETHER_DIMENSION.equals(destDim)) {
            cir.setReturnValue(goldclaim$calculateNetherTeleportTarget(player, currentWorld, destination, true));
        } else if (GoldClaimMod.RSW_NETHER_DIMENSION.equals(currentDim) && GoldClaimMod.RSW_DIMENSION.equals(destDim)) {
            cir.setReturnValue(goldclaim$calculateNetherTeleportTarget(player, currentWorld, destination, false));
        } else if (GoldClaimMod.RSW_DIMENSION.equals(currentDim) && GoldClaimMod.RSW_THE_END_DIMENSION.equals(destDim)) {
            ServerWorld.createEndSpawnPlatform(destination);
            BlockPos spawn = ServerWorld.END_SPAWN_POS;
            cir.setReturnValue(new TeleportTarget(new Vec3d(spawn.getX() + 0.5, spawn.getY() - 1.0, spawn.getZ() + 0.5), Vec3d.ZERO, 90.0f, 0.0f));
        } else if (GoldClaimMod.RSW_THE_END_DIMENSION.equals(currentDim) && GoldClaimMod.RSW_DIMENSION.equals(destDim)) {
            BlockPos spawn = destination.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, destination.getSpawnPos());
            cir.setReturnValue(new TeleportTarget(new Vec3d(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5), player.getVelocity(), player.getYaw(), player.getPitch()));
        }
    }

    private TeleportTarget goldclaim$calculateNetherTeleportTarget(ServerPlayerEntity player, ServerWorld currentWorld, ServerWorld destination, boolean isEnteringNether) {
        WorldBorder worldBorder = destination.getWorldBorder();
        double scale = 1.0;
        BlockPos blockPos = worldBorder.clamp(player.getX() * scale, player.getY(), player.getZ() * scale);

        BlockPos portalPos = player.getBlockPos();
        BlockState blockState = currentWorld.getBlockState(portalPos);
        Direction.Axis axis = blockState.contains(Properties.HORIZONTAL_AXIS) ? blockState.get(Properties.HORIZONTAL_AXIS) : Direction.Axis.X;

        Optional<BlockLocating.Rectangle> portalRect = destination.getPortalForcer().getPortalRect(blockPos, isEnteringNether, worldBorder);
        if (portalRect.isEmpty()) {
            portalRect = destination.getPortalForcer().createPortal(blockPos, axis);
        }

        return portalRect.map(rect -> {
            Vec3d vec3d;
            if (blockState.contains(Properties.HORIZONTAL_AXIS)) {
                BlockLocating.Rectangle rectangle = BlockLocating.getLargestRectangle(
                    portalPos, axis, 21, Direction.Axis.Y, 21, pos -> currentWorld.getBlockState(pos) == blockState);
                vec3d = NetherPortal.entityPosInPortal(rectangle, axis, player.getPos(), player.getDimensions(player.getPose()));
            } else {
                vec3d = new Vec3d(0.5, 0.0, 0.0);
            }
            return NetherPortal.getNetherTeleportTarget(destination, rect, axis, vec3d, player, player.getVelocity(), player.getYaw(), player.getPitch());
        }).orElse(null);
    }
}
