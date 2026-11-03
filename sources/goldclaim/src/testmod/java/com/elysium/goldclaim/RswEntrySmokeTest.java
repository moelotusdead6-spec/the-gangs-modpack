package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class RswEntrySmokeTest {
    private static RswEntrySearch search;
    private static ServerPlayerEntity player;
    private static int ticks;
    private static int transfers;
    private static int waiting;
    private static long longest;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                require(++ticks < 2400, "RSW entry test timed out");
                var world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD,
                    new Identifier(GoldClaimMod.RSW_DIMENSION)));
                if (world == null || ticks < 40) return;
                if (search == null) {
                    player = new ServerPlayerEntity(server, server.getOverworld(),
                            new GameProfile(UUID.randomUUID(), "RswEntryFixture")) {
                        @Override public boolean isDisconnected() { return false; }
                        @Override public void sendMessage(Text text, boolean overlay) {}
                    };
                    player.networkHandler = new ServerPlayNetworkHandler(server,
                            new ClientConnection(NetworkSide.SERVERBOUND), player) {
                        @Override public void sendPacket(Packet<?> packet) {}
                    };
                    var resetsField = GoldClaimMod.class.getDeclaredField("rswResetService");
                    resetsField.setAccessible(true);
                    var resets = (RswResetService)resetsField.get(GoldClaimMod.getInstance());
                    require(!resets.isClosed(), "Fixture RSW reset service must be initialized and open");
                    search = new RswEntrySearch(resets, (w, pos) -> true, entry -> {
                        transfers++;
                        player.teleport(entry.world, entry.landing.getX() + 0.5,
                            entry.landing.getY(), entry.landing.getZ() + 0.5, 0, 0);
                    });
                    require(world.getChunkManager().getWorldChunk(0, 0) == null,
                        "RSW test destination must start unloaded");
                    long before = System.nanoTime();
                    require(search.request(player.getCommandSource(), player, world, 128) == 1,
                        "RSW entry was not queued");
                    require(System.nanoTime() - before < 250_000_000L, "RSW enqueue exceeded 250ms");
                    require(transfers == 0, "RSW command teleported synchronously");
                    require(search.request(player.getCommandSource(), player, world, 128) == 0,
                        "Duplicate RSW request was accepted");
                    return;
                }
                long before = System.nanoTime();
                search.tick();
                longest = Math.max(longest, System.nanoTime() - before);
                require(longest < 1_000_000_000L, "RSW preparation blocked for over one second");
                if (search.isPending(player.getUuid())) {
                    waiting++;
                    return;
                }
                require(waiting > 1, "Cold RSW generation did not yield across server ticks");
                require(transfers == 1 && player.getServerWorld() == world,
                    "RSW did not complete exactly one cross-world transfer");
                require(RswLanding.isSafe(world, player.getPos(), player.getWidth(), player.getHeight()),
                    "RSW arrival was unsafe");
                require(search.requestDeferred(player.getCommandSource(), player, world, 128),
                    "Deferred RSW entry was not queued");
                player.setHealth(0);
                search.tick();
                require(!search.isPending(player.getUuid()) && transfers == 1,
                    "Dead player retained a pending RSW transfer");
                player.getServerWorld().removePlayer(player,
                    net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: cold RSW entry queues in <250ms; duplicate rejected; "
                    + waiting + " waiting ticks; longest preparation "
                    + longest / 1_000_000.0
                    + "ms (<1000ms); exactly one safe cross-world arrival; deferred death cancellation.\n");
                server.stop(false);
            } catch (java.io.IOException | ReflectiveOperationException failure) {
                throw new IllegalStateException("RSW entry integration failed", failure);
            }
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
