package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import dev.fiw.modsapi.FiwModsApi;
import dev.fiw.modsapi.core.freeze.FreezeState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

final class JoinTeleportSmokeTest {
    private static ServerPlayerEntity player;
    private static Map<UUID, Integer> pending;
    private static Map<UUID, ServerPlayerEntity> players;
    private static int ticks;
    private static int transfers;
    private static int releaseTick;
    private static Vec3d destination;
    private static boolean withFiw;

    private JoinTeleportSmokeTest() {}

    @SuppressWarnings("unchecked")
    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                if (++ticks < 30) return;
                require(ticks < 400, "Join teleport test timed out");
                if (player == null) {
                    var configField = GoldClaimMod.class.getDeclaredField("config");
                    configField.setAccessible(true);
                    var config = (GoldClaimConfig)configField.get(GoldClaimMod.getInstance());
                    config.portalHubDimension = "multiworld:pvp";
                    require(server.getWorld(net.minecraft.registry.RegistryKey.of(
                        net.minecraft.registry.RegistryKeys.WORLD,
                        new net.minecraft.util.Identifier(config.portalHubDimension))) != null,
                        "Cross-dimension hub fixture is not loaded");
                    config.hubTeleportX = 0;
                    config.hubTeleportY = 68;
                    config.hubTeleportZ = 0;
                    destination = new Vec3d(0.5, 68, 0.5);
                    player = new ServerPlayerEntity(server, server.getOverworld(),
                            new GameProfile(UUID.randomUUID(), "JoinFixture")) {
                        @Override public boolean isDisconnected() { return false; }
                        @Override public void teleport(net.minecraft.server.world.ServerWorld world,
                                double x, double y, double z, float yaw, float pitch) {
                            transfers++;
                            super.teleport(world, x, y, z, yaw, pitch);
                        }
                    };
                    player.networkHandler = new ServerPlayNetworkHandler(server,
                            new ClientConnection(NetworkSide.SERVERBOUND), player) {
                        @Override public void sendPacket(Packet<?> packet) {}
                    };
                    player.refreshPositionAndAngles(699.5, 89, 1421.5, 0, 0);
                    var mapField = java.util.Arrays.stream(PlayerManager.class.getDeclaredFields())
                        .filter(field -> field.getGenericType().getTypeName().equals(
                            "java.util.Map<java.util.UUID, " + ServerPlayerEntity.class.getName() + ">"))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Player UUID map not found"));
                    mapField.setAccessible(true);
                    players = (Map<UUID, ServerPlayerEntity>)mapField.get(server.getPlayerManager());
                    players.put(player.getUuid(), player);
                    server.getPlayerManager().getPlayerList().add(player);
                    server.getOverworld().onPlayerConnected(player);
                    var pendingField = GoldClaimMod.class.getDeclaredField("pendingHubTeleports");
                    pendingField.setAccessible(true);
                    pending = (Map<UUID, Integer>)pendingField.get(GoldClaimMod.getInstance());
                    withFiw = FabricLoader.getInstance().isModLoaded("fiw-mods-api");
                    if (withFiw) {
                        FiwModsApi.engine().freezes().freeze(player.getUuid(),
                            new FreezeState(player.getX(), player.getY(), player.getZ(), 0, 0,
                                System.currentTimeMillis()));
                    }
                    pending.put(player.getUuid(), 20);
                    return;
                }
                if (withFiw && ticks <= 90) {
                    require(transfers == 0 && pending.get(player.getUuid()) == 20,
                        "Automatic hub transfer ran during verification");
                    require(player.getPos().equals(new Vec3d(699.5, 89, 1421.5)),
                        "Frozen player moved before verification completed");
                    if (ticks == 90) {
                        FiwModsApi.engine().freezes().unfreeze(player.getUuid());
                        releaseTick = ticks;
                    }
                    return;
                }
                if (!withFiw && releaseTick == 0) releaseTick = 30;
                if (ticks < releaseTick + 20) {
                    require(transfers == 0, "Post-verification login settling delay was skipped");
                    return;
                }
                require(transfers == 1 && player.getPos().equals(destination),
                    "Player did not arrive exactly once at the configured hub spawn");
                require(GoldClaimMod.isHubWorld(player.getServerWorld()),
                    "Player did not arrive in the hub dimension");
                require(!pending.containsKey(player.getUuid()), "Completed hub transfer remains queued");
                player.getServerWorld().getChunkManager().updatePosition(player);
                if (ticks < releaseTick + 50) return;
                players.remove(player.getUuid());
                server.getPlayerManager().getPlayerList().remove(player);
                player.getServerWorld().removePlayer(player, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: " + (withFiw ? "real Fiw freeze and release; " : "Fiw absent; ")
                    + "one cross-dimension hub transfer after settling delay; exact spawn; stable final position; chunk tracking.\n");
                server.stop(false);
            } catch (ReflectiveOperationException | java.io.IOException failure) {
                throw new IllegalStateException("Join teleport integration test failed", failure);
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
