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

    static void register() {
        ServerWorldEvents.LOAD.register((server, world) -> {
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
