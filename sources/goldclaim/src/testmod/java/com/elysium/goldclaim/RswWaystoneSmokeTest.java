package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.blay09.mods.waystones.api.TeleportDestination;
import net.blay09.mods.waystones.api.WaystoneStyle;
import net.blay09.mods.waystones.api.WaystoneTeleportError;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.WaystoneTeleportContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.chunk.ChunkStatus;

final class RswWaystoneSmokeTest {
    private static final BlockPos EXISTING = new BlockPos(8, 150, 8);
    private static int prepared;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            require(server.getTicks() < 1200, "Waystone smoke timed out");
            if (!RankService.ready()) return;
            var worlds = GoldClaimMod.RSW_DIMENSIONS.stream()
                .map(id -> server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(id)))).toList();
            if (worlds.stream().anyMatch(world -> world == null)) return;
            try {
                BlockState stone = Registries.BLOCK.get(new Identifier("waystones:waystone")).getDefaultState();
                if (prepared == 0) {
                    var wild = server.getOverworld();
                    var wildPlayer = player(wild, "WildWaystone");
                    var pos = new BlockPos(8, 150, 8);
                    wild.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
                    var outside = WaystonesAPI.placeWaystone(wild, pos,
                        new WaystoneStyle(new Identifier("waystones:waystone"))).orElseThrow();
                    for (var world : worlds) {
                        var chunk = world.getChunk(EXISTING);
                        // Restore an existing saved block directly; new placement must be rejected below.
                        chunk.getSection(world.getSectionIndex(EXISTING.getY()))
                            .setBlockState(EXISTING.getX() & 15, EXISTING.getY() & 15, EXISTING.getZ() & 15, stone);
                        var region = new ChunkRegion(world, List.of(chunk), ChunkStatus.FEATURES, 0);
                        require(!region.setBlockState(EXISTING.east(), stone, 2, 0),
                            "Worldgen allowed an RSW Waystone");
                        require(region.setBlockState(EXISTING.east(2), Blocks.STONE.getDefaultState(), 2, 0),
                            "Unrelated generation was blocked");
                        require(!world.setBlockState(EXISTING.east(3), stone),
                            "Loaded RSW placement was allowed");
                        var rswPlayer = player(world, "RswWaystone");
                        rswPlayer.setStackInHand(Hand.MAIN_HAND,
                            new ItemStack(Registries.ITEM.get(new Identifier("waystones:warp_stone"))));
                        require(UseItemCallback.EVENT.invoker().interact(rswPlayer, world, Hand.MAIN_HAND).getResult()
                            == ActionResult.FAIL, "RSW Waystone item use was allowed");
                        var hit = new BlockHitResult(Vec3d.ofCenter(EXISTING), Direction.UP, EXISTING, false);
                        require(UseBlockCallback.EVENT.invoker().interact(rswPlayer, world, Hand.MAIN_HAND, hit)
                            == ActionResult.FAIL, "RSW existing Waystone interaction was allowed");
                        var inbound = new WaystoneTeleportContext(wildPlayer, outside,
                            new TeleportDestination(world, Vec3d.ofCenter(EXISTING), Direction.NORTH));
                        var outbound = new WaystoneTeleportContext(rswPlayer, outside,
                            new TeleportDestination(wild, Vec3d.ofCenter(pos), Direction.NORTH));
                        require(WaystonesAPI.tryTeleport(inbound).right().orElse(null)
                            instanceof WaystoneTeleportError.CancelledByEvent, "Inbound RSW travel was allowed");
                        require(WaystonesAPI.tryTeleport(outbound).right().orElse(null)
                            instanceof WaystoneTeleportError.CancelledByEvent, "Outbound RSW travel was allowed");
                    }
                    var wildRegion = new ChunkRegion(wild, List.of(wild.getChunk(pos)), ChunkStatus.FEATURES, 0);
                    require(wildRegion.setBlockState(pos.east(4), stone, 2, 0),
                        "Outside-RSW worldgen must remain available");
                    prepared = server.getTicks();
                } else if (server.getTicks() >= prepared + 10) {
                    for (var world : worlds) {
                        require(world.getBlockState(EXISTING).equals(stone), "Existing RSW Waystone was removed");
                    }
                    Files.writeString(Path.of("policy-smoke-passed.txt"),
                        "PASS: all three RSWs reject worldgen, placement, existing-block interaction, item use, "
                        + "inbound/outbound Waystone travel; existing blocks survive ticks; other blocks and "
                        + "outside-RSW Waystone generation/placement remain available.\n");
                    server.stop(false);
                    prepared = Integer.MAX_VALUE - 10;
                }
            } catch (Exception failure) {
                throw new IllegalStateException("RSW Waystone smoke failed", failure);
            }
        });
    }

    private static ServerPlayerEntity player(ServerWorld world, String name) {
        var player = new ServerPlayerEntity(world.getServer(), world, new GameProfile(UUID.randomUUID(), name));
        player.networkHandler = new ServerPlayNetworkHandler(world.getServer(),
            new ClientConnection(NetworkSide.SERVERBOUND), player);
        return player;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
