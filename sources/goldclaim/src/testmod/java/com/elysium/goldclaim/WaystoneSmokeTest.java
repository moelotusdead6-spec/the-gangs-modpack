package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.api.TeleportDestination;
import net.blay09.mods.waystones.api.WaystoneStyle;
import net.blay09.mods.waystones.api.WaystoneTeleportError;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.WaystoneTeleportContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

final class WaystoneSmokeTest {
    private static IWaystone activated;
    private static IWaystone unused;
    private static NbtCompound offlineData;
    private static java.nio.file.Path offlineFile;
    private static final java.util.List<IWaystone> legacyStones = new java.util.ArrayList<>();

    static void register() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (LegacyDimensionPolicy.isClosed(world)) {
                BlockPos pos = new BlockPos(10, 100, 10);
                world.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
                IWaystone stone = WaystonesAPI.placeWaystone(world, pos,
                    new WaystoneStyle(new Identifier("waystones:waystone"))).orElseThrow();
                legacyStones.add(stone);
                WaystonesAPI.removeWaystoneFromDatabase(server, stone);
                var path = server.getSavePath(WorldSavePath.ROOT).resolve("data/goldclaim-legacy-waystones.dat");
                try {
                    Files.createDirectories(path.getParent());
                    var plan = Files.exists(path) ? NbtIo.readCompressed(path.toFile()) : new NbtCompound();
                    var stones = plan.getList("Waystones", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
                    var record = new NbtCompound();
                    record.putString("World", world.getRegistryKey().getValue().toString());
                    record.put("BlockPos", NbtHelper.fromBlockPos(stone.getPos()));
                    record.putUuid("WaystoneUid", stone.getWaystoneUid());
                    stones.add(record);
                    plan.put("Waystones", stones);
                    NbtIo.writeCompressed(plan, path.toFile());
                } catch (IOException failure) {
                    throw new IllegalStateException("Cannot prepare legacy physical cleanup fixture.", failure);
                }
                return;
            }
            if (world.getRegistryKey() != World.OVERWORLD) return;
            var style = new WaystoneStyle(new Identifier("waystones:waystone"));
            BlockPos pos = new BlockPos(96, 100, 96);
            world.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
            world.setBlockState(pos.east(4).down(), Blocks.STONE.getDefaultState());
            activated = WaystonesAPI.placeWaystone(world, pos, style).orElseThrow();
            unused = WaystonesAPI.placeWaystone(world, pos.east(4), style).orElseThrow();
            var offline = new ServerPlayerEntity(server, world,
                new GameProfile(UUID.randomUUID(), "OfflineWaystone"));
            var data = new NbtCompound();
            var ids = new NbtList();
            ids.add(NbtString.of(activated.getWaystoneUid().toString()));
            data.put("Waystones", ids);
            Balm.getHooks().getPersistentData(offline).put("WaystonesData", data);
            offlineData = offline.writeNbt(new NbtCompound());
            offlineFile = server.getSavePath(WorldSavePath.PLAYERDATA).resolve(offline.getUuid() + ".dat");
            try {
                Files.createDirectories(offlineFile.getParent());
                NbtIo.writeCompressed(offlineData, offlineFile.toFile());
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot create offline activation fixture.", failure);
            }
        });
    }

    static boolean verify(MinecraftServer server) {
        if (Files.exists(server.getSavePath(WorldSavePath.ROOT).resolve("data/goldclaim-legacy-waystones.dat"))) return false;
        if (legacyStones.size() != 2) throw new IllegalStateException("Both legacy Waystone fixtures must exist.");
        for (IWaystone stone : legacyStones) {
            var world = server.getWorld(stone.getDimension());
            if (WaystonesAPI.getWaystone(server, stone.getWaystoneUid()).isPresent()
                    || WaystonePolicy.isWaystone(world.getBlockState(stone.getPos()))
                    || WaystonePolicy.isWaystone(world.getBlockState(stone.getPos().down()))) {
                throw new IllegalStateException("Legacy Waystone registry or physical block cleanup failed.");
            }
        }
        var backup = FabricLoader.getInstance().getGameDir().resolve("backups/waystones-policy-v1.1.5")
            .resolve(server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getFileName());
        if (!Files.exists(backup.resolve("complete.txt"))) return false;
        if (WaystonesAPI.getWaystone(server, activated.getWaystoneUid()).isEmpty()
                || WaystonesAPI.getWaystone(server, unused.getWaystoneUid()).isPresent()) {
            throw new IllegalStateException("Cleanup removed an offline-activated stone or retained an unused stone.");
        }
        var wild = server.getOverworld();
        if (!WaystonePolicy.isWaystone(wild.getBlockState(activated.getPos()))
                || !WaystonePolicy.isWaystone(wild.getBlockState(activated.getPos().down()))
                || WaystonePolicy.isWaystone(wild.getBlockState(unused.getPos()))
                || WaystonePolicy.isWaystone(wild.getBlockState(unused.getPos().down()))) {
            throw new IllegalStateException("Waystone block cleanup did not preserve/remove both halves correctly.");
        }
        try {
            if (!NbtIo.readCompressed(offlineFile.toFile()).equals(offlineData)) {
                throw new IllegalStateException("Cleanup modified offline player activation data.");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot verify offline activation data.", failure);
        }
        var player = new ServerPlayerEntity(server, wild, new GameProfile(UUID.randomUUID(), "WaystoneSmoke"));
        player.networkHandler = new ServerPlayNetworkHandler(server,
            new ClientConnection(NetworkSide.SERVERBOUND), player);
        for (var key : java.util.List.of(World.NETHER, World.END)) {
            var target = server.getWorld(key);
            var context = new WaystoneTeleportContext(player, activated,
                new TeleportDestination(target, new Vec3d(0.5, 100, 0.5), Direction.NORTH));
            if (!(WaystonesAPI.tryTeleport(context).right().orElse(null)
                    instanceof WaystoneTeleportError.CancelledByEvent)) {
                throw new IllegalStateException("Normal Waystone entry into original dimension was not cancelled.");
            }
            WaystonesAPI.forceTeleport(context);
            if (player.getServerWorld() != wild) {
                throw new IllegalStateException("Forced Waystone teleport bypassed legacy dimension guard.");
            }
        }
        for (String id : GoldClaimMod.RSW_DIMENSIONS) {
            var world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(id)));
            BlockPos pos = new BlockPos(96, 100, 96);
            if (world.setBlockState(pos, Registries.BLOCK.get(new Identifier("waystones:waystone")).getDefaultState())
                    || WaystonePolicy.isWaystone(world.getBlockState(pos))) {
                throw new IllegalStateException("RSW allowed waystone placement in " + id);
            }
            var inbound = new WaystoneTeleportContext(player, activated,
                new TeleportDestination(world, Vec3d.ofCenter(pos), Direction.NORTH));
            if (!(WaystonesAPI.tryTeleport(inbound).right().orElse(null)
                    instanceof WaystoneTeleportError.CancelledByEvent)) {
                throw new IllegalStateException("Inbound RSW teleport was not cancelled in " + id);
            }
            var rswPlayer = new ServerPlayerEntity(server, world,
                new GameProfile(UUID.randomUUID(), "RswWaystoneSmoke"));
            rswPlayer.networkHandler = new ServerPlayNetworkHandler(server,
                new ClientConnection(NetworkSide.SERVERBOUND), rswPlayer);
            var outbound = new WaystoneTeleportContext(rswPlayer, activated,
                new TeleportDestination(wild, Vec3d.ofCenter(activated.getPos()), Direction.NORTH));
            if (!(WaystonesAPI.tryTeleport(outbound).right().orElse(null)
                    instanceof WaystoneTeleportError.CancelledByEvent)) {
                throw new IllegalStateException("Outbound RSW teleport was not cancelled in " + id);
            }
        }
        return true;
    }
}
