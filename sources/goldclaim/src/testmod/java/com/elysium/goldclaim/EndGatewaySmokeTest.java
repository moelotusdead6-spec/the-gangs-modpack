package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

final class EndGatewaySmokeTest {
    private static int ticks;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            require(++ticks <= 1200, "RSW End gateway smoke test timed out");
            ServerWorld end = server.getWorld(RegistryKey.of(RegistryKeys.WORLD,
                new Identifier(GoldClaimMod.RSW_THE_END_DIMENSION)));
            if (end == null) return;
            verify(server, end);
            try {
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: RSW gateway generated an outer-island destination and linked return gateway; "
                    + "player and owned pearl teleports, safe landing, cooldown, saved/reloaded links, "
                    + "exact teleports, and non-End dimension isolation passed.\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot record gateway smoke-test result", failure);
            }
            server.stop(false);
        });
    }

    private static void verify(MinecraftServer server, ServerWorld end) {
        BlockPos source = new BlockPos(96, 80, 0);
        EndGatewayBlockEntity gateway = placeGateway(end, source);
        require(!gateway.createNbt().contains("ExitPortal"), "test gateway already has a destination");
        ServerPlayerEntity player = player(server, end, source);
        teleport(end, source, player, gateway);
        require(player.getServerWorld() == end, "gateway changed the player's dimension");
        require(player.getBlockPos().getSquaredDistance(source) > 768 * 768,
            "fresh gateway did not teleport the player to the outer islands");
        require(end.getWorldBorder().contains(player.getBlockPos()), "gateway exit is outside the RSW border");
        require(end.getBlockState(player.getBlockPos().down()).isFullCube(end, player.getBlockPos().down())
                && !end.getBlockState(player.getBlockPos()).isFullCube(end, player.getBlockPos())
                && !end.getBlockState(player.getBlockPos().up()).isFullCube(end, player.getBlockPos().up()),
            "gateway did not find a solid landing with headroom");

        NbtCompound saved = gateway.createNbt();
        require(saved.contains("ExitPortal"), "gateway did not persist its generated destination");
        BlockPos outer = NbtHelper.toBlockPos(saved.getCompound("ExitPortal"));
        require(end.getBlockEntity(outer) instanceof EndGatewayBlockEntity, "return gateway was not generated");
        EndGatewayBlockEntity back = (EndGatewayBlockEntity) end.getBlockEntity(outer);
        require(NbtHelper.toBlockPos(back.createNbt().getCompound("ExitPortal")).equals(source),
            "return gateway is not linked to the main island");

        player.refreshPositionAndAngles(source.getX() + 0.5, source.getY(), source.getZ() + 0.5, 0, 0);
        teleport(end, source, player, gateway);
        require(player.getBlockPos().equals(source), "gateway cooldown was bypassed");

        EndGatewayBlockEntity restored = reload(end, source, saved);
        teleport(end, source, player, restored);
        require(restored.createNbt().getCompound("ExitPortal").equals(saved.getCompound("ExitPortal")),
            "saved gateway link was replaced");
        require(player.getBlockPos().getSquaredDistance(source) > 768 * 768,
            "saved/reloaded gateway failed to teleport");
        teleport(end, outer, player, reload(end, outer, back.createNbt()));
        double dx = player.getX() - source.getX();
        double dz = player.getZ() - source.getZ();
        require(player.getServerWorld() == end && dx * dx + dz * dz < 256,
            "return gateway failed to bring the player back to the main island");

        EnderPearlEntity pearl = new EnderPearlEntity(end, player);
        teleport(end, source, pearl, reload(end, source, saved));
        require(pearl.isRemoved() && player.getBlockPos().getSquaredDistance(source) > 768 * 768,
            "owned Ender Pearl failed to teleport its player");

        BlockPos exact = new BlockPos(1400, 100, 1600);
        end.getChunk(exact);
        NbtCompound exactData = new NbtCompound();
        exactData.put("ExitPortal", NbtHelper.fromBlockPos(exact));
        exactData.putBoolean("ExactTeleport", true);
        teleport(end, source, player, reload(end, source, exactData));
        require(player.getBlockPos().equals(exact), "explicit exact gateway destination was changed");
        end.removePlayer(player, Entity.RemovalReason.DISCARDED);

        ServerWorld overworld = server.getOverworld();
        EndGatewayBlockEntity inert = placeGateway(overworld, source);
        ServerPlayerEntity other = player(server, overworld, source);
        teleport(overworld, source, other, inert);
        require(other.getBlockPos().equals(source) && !inert.createNbt().contains("ExitPortal"),
            "gateway fix enabled destination generation outside the End");
        overworld.removePlayer(other, Entity.RemovalReason.DISCARDED);
    }

    private static EndGatewayBlockEntity placeGateway(ServerWorld world, BlockPos pos) {
        world.getChunk(pos);
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
        world.setBlockState(pos, Blocks.END_GATEWAY.getDefaultState());
        require(world.getBlockEntity(pos) instanceof EndGatewayBlockEntity, "gateway block entity is missing");
        return (EndGatewayBlockEntity) world.getBlockEntity(pos);
    }

    private static EndGatewayBlockEntity reload(ServerWorld world, BlockPos pos, NbtCompound nbt) {
        EndGatewayBlockEntity gateway = new EndGatewayBlockEntity(pos, Blocks.END_GATEWAY.getDefaultState());
        gateway.setWorld(world);
        gateway.readNbt(nbt);
        return gateway;
    }

    private static ServerPlayerEntity player(MinecraftServer server, ServerWorld world, BlockPos pos) {
        ServerPlayerEntity player = new ServerPlayerEntity(server, world,
            new GameProfile(UUID.randomUUID(), "GatewaySmoke"));
        player.networkHandler = new ServerPlayNetworkHandler(server,
            new ClientConnection(NetworkSide.SERVERBOUND), player);
        player.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        world.onPlayerConnected(player);
        return player;
    }

    private static void teleport(ServerWorld world, BlockPos pos, Entity entity, EndGatewayBlockEntity gateway) {
        EndGatewayBlockEntity.tryTeleportingEntity(world, pos, Blocks.END_GATEWAY.getDefaultState(), entity, gateway);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
