package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.GoldClaimMod;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class RswPlayerTravelMixin {

    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V", at = @At("HEAD"))
    private void goldclaim$rememberBeforeTeleport(ServerWorld target, double x, double y, double z,
                                                 float yaw, float pitch, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        if (target != player.getServerWorld()) {
            GoldClaimMod.rememberRswLocation(player);
        }
    }

    @Inject(method = "moveToWorld", at = @At("HEAD"))
    private void goldclaim$rememberBeforePortal(ServerWorld target, CallbackInfoReturnable<Entity> cir) {
        GoldClaimMod.rememberRswLocation((ServerPlayerEntity)(Object)this);
    }

    @ModifyVariable(method = "moveToWorld", at = @At("HEAD"), argsOnly = true)
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
        double scale = DimensionType.getCoordinateScaleFactor(currentWorld.getDimension(), destination.getDimension());
        BlockPos blockPos = worldBorder.clamp(player.getX() * scale, player.getY(), player.getZ() * scale);
        return destination.getPortalForcer().getPortalRect(blockPos, isEnteringNether, worldBorder).map(rect -> {
            BlockPos portalPos = player.getBlockPos();
            BlockState blockState = currentWorld.getBlockState(portalPos);
            Direction.Axis axis;
            Vec3d vec3d;
            if (blockState.contains(Properties.HORIZONTAL_AXIS)) {
                axis = blockState.get(Properties.HORIZONTAL_AXIS);
                BlockLocating.Rectangle rectangle = BlockLocating.getLargestRectangle(
                    portalPos, axis, 21, Direction.Axis.Y, 21, pos -> currentWorld.getBlockState(pos) == blockState);
                vec3d = NetherPortal.entityPosInPortal(rectangle, axis, player.getPos(), player.getDimensions(player.getPose()));
            } else {
                axis = Direction.Axis.X;
                vec3d = new Vec3d(0.5, 0.0, 0.0);
            }
            return NetherPortal.getNetherTeleportTarget(destination, rect, axis, vec3d, player, player.getVelocity(), player.getYaw(), player.getPitch());
        }).orElse(null);
    }
}
