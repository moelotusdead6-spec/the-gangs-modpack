package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class TeleportSmokeTest {
    private static ServerPlayerEntity traveler;
    private static ServerPlayerEntity destination;
    private static TeleportPreparation queue;
    private static int stage;
    private static int started;
    private static int waitingTicks;
    private static long requestNanos;
    private static long tickStarted;
    private static long longestPreparationTick;
    private static final List<String> messages = new ArrayList<>();

    static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStarted = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            require(server.getTicks() < 2400, "teleport smoke timed out");
            ServerWorld end = server.getWorld(RegistryKey.of(RegistryKeys.WORLD,
                new Identifier(GoldClaimMod.RSW_THE_END_DIMENSION)));
            ServerWorld rsw = server.getWorld(RegistryKey.of(RegistryKeys.WORLD,
                new Identifier(GoldClaimMod.RSW_DIMENSION)));
            if (end == null || rsw == null) return;
            GoldClaimMod mod = GoldClaimMod.getInstance();
            if (stage == 1 || stage == 4) {
                longestPreparationTick = Math.max(longestPreparationTick, System.nanoTime() - tickStarted);
                require(longestPreparationTick < 1_000_000_000L,
                    "terrain preparation blocked a server tick for more than one second");
            }
            if (stage == 0) {
                traveler = player(server, server.getOverworld(), "TeleportTraveler");
                destination = player(server, server.getOverworld(), "TeleportDestination");
                destination.refreshPositionAndAngles(4200.5, 150, 4200.5, 27, 11);
                require(end.getChunkManager().getWorldChunk(4200 >> 4, 4200 >> 4) == null,
                    "teleport destination must start cold");
                queue = field(mod, "teleportPreparation", TeleportPreparation.class);
                for (String name : List.of("tpa", "tpahere")) {
                    suggestions(traveler, name + " ", List.of("TeleportDestination"));
                    suggestions(traveler, name + " tElEpOrTd", List.of("TeleportDestination"));
                    suggestions(traveler, name + " Nobody", List.of());
                    suggestions(destination, name + " ", List.of("TeleportTraveler"));
                }
                command(traveler, "tpa TeleportTraveler", 0);
                command(traveler, "tpahere OfflinePlayer", 0);
                long before = System.nanoTime();
                command(traveler, "tpa TeleportDestination", 1);
                destination.setServerWorld(end);
                destination.refreshPositionAndAngles(4210.5, 151, 4210.5, 60, 20);
                command(destination, "tpaccept", 1);
                requestNanos = System.nanoTime() - before;
                require(requestNanos < 250_000_000L, "cold TPA enqueue exceeded 250ms");
                command(traveler, "tpa TeleportDestination", 1);
                command(destination, "tpaccept", 0);
                destination.refreshPositionAndAngles(4220.5, 152, 4220.5, 90, 30);
                require(traveler.getServerWorld() == server.getOverworld(), "TPA must wait for terrain");
                require(end.getChunkManager().getWorldChunk(4200 >> 4, 4200 >> 4) == null,
                    "TPA command must not synchronously generate the destination");
                started = server.getTicks();
                stage++;
            } else if (stage == 1) {
                if (queue.isPending(traveler.getUuid())) {
                    waitingTicks++;
                    return;
                }
                require(waitingTicks > 1, "server ticks must continue while cold terrain is preparing");
                require(traveler.getServerWorld() == end && traveler.getPos().equals(new Vec3d(4210.5, 151, 4210.5)),
                    "TPA must use the exact acceptance-time dimension and coordinates without safety offsets");
                require(traveler.getYaw() == 60 && traveler.getPitch() == 20,
                    "TPA must preserve acceptance-time facing despite movement during preparation");
                require(messages.stream().anyMatch(text -> text.startsWith("Teleported to ")),
                    "TPA completion must notify only after arrival");
                destination.refreshPositionAndAngles(4300.5, 150, 4300.5, 37, 14);
                command(destination, "tpahere TeleportTraveler", 1);
                destination.teleport(server.getOverworld(), 32.5, 100, 32.5, 0, 0);
                command(traveler, "tpaccept", 1);
                destination.refreshPositionAndAngles(48.5, 100, 48.5, 0, 0);
                traveler.refreshPositionAndAngles(4250.5, 150, 4250.5, 0, 0);
                messages.clear();
                stage++;
            } else if (stage == 2) {
                if (queue.isPending(traveler.getUuid())) return;
                require(traveler.getServerWorld() == end && traveler.getPos().equals(new Vec3d(4300.5, 150, 4300.5)),
                    "TPAHere must preserve the request-time dimension and coordinates despite both players moving");
                require(traveler.getYaw() == 37 && traveler.getPitch() == 14,
                    "TPAHere must preserve request-time rotation");
                require(messages.stream().noneMatch(text -> text.startsWith("Teleport cancelled")),
                    "walking must not cancel a player teleport");
                destination.refreshPositionAndAngles(4500.5, 150, 4500.5, 0, 0);
                command(traveler, "tpa TeleportDestination", 1);
                command(destination, "tpaccept", 1);
                destination.setHealth(0);
                stage++;
            } else if (stage == 3) {
                require(!queue.isPending(traveler.getUuid()), "dead target must cancel preparation");
                if ("true".equals(System.getenv("GOLDCLAIM_REQUEST_SMOKE"))) {
                    finish(server, "PASS: actual /tpa preserves acceptance-time dimension, exact position and facing; "
                        + "/tpahere preserves request-time dimension, exact position and facing; "
                        + "both commands autocomplete online players, exclude self and filter case-insensitive prefixes; "
                        + "self/offline rejection; destination changes dimensions before acceptance; both players move; "
                        + "duplicate rejection and target death cancellation; cold cross-world enqueue "
                        + requestNanos / 1_000_000.0 + "ms (<250ms); " + waitingTicks
                        + " ticks continued; longest preparation tick "
                        + longestPreparationTick / 1_000_000.0 + "ms (<1000ms).\n");
                    stage = 5;
                    return;
                }
                traveler = player(server, rsw, "RtpTraveler");
                traveler.refreshPositionAndAngles(0.5, 150, 0.5, 35, 12);
                long before = System.nanoTime();
                int result;
                try {
                    result = server.getCommandManager().getDispatcher().execute("rtp", traveler.getCommandSource());
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                    throw new IllegalStateException("Actual /rtp command failed", failure);
                }
                require(result == 1, "actual /rtp command must queue");
                require(System.nanoTime() - before < 250_000_000L, "cold /rtp command exceeded 250ms");
                require(traveler.getPos().equals(new Vec3d(0.5, 150, 0.5)), "RTP must not move before preparation");
                started = server.getTicks();
                stage++;
            } else if (stage == 4) {
                if (queue.isPending(traveler.getUuid())) return;
                require(server.getTicks() > started, "RTP must yield to server ticks");
                BlockPos feet = traveler.getBlockPos();
                require(traveler.getServerWorld() == rsw && traveler.getY() != 150,
                    "RTP must reach safe ground in RSW");
                require(Math.abs(traveler.getX()) <= 4750 && Math.abs(traveler.getZ()) <= 4750,
                    "RTP must retain 250-block border inset");
                require(rsw.getBlockState(feet.down()).isSolidBlock(rsw, feet.down())
                        && rsw.getBlockState(feet).isAir() && rsw.getBlockState(feet.up()).isAir(),
                    "RTP landing must have solid dry ground and headroom");
                require(traveler.getYaw() == 35 && traveler.getPitch() == 12, "RTP rotation must be preserved");
                finish(server,
                        "PASS: cold cross-world RSW End TPA queued in " + requestNanos / 1_000_000.0
                        + "ms; " + waitingTicks + " server ticks continued during generation; exact arrival/rotation; "
                        + "actual /tpa captures acceptance-time world/coordinates/rotation and /tpahere captures request-time; "
                        + "online-player autocomplete excludes self and filters case-insensitive prefixes; "
                        + "duplicate rejection; movement before/after acceptance and destination dimension changes; "
                        + "target death cancellation; actual RSW /rtp returns within "
                        + "250ms, yields ticks and lands safely within the 250-block border inset; longest "
                        + "preparation tick " + longestPreparationTick / 1_000_000.0 + "ms (required <1000ms).\n");
                stage++;
            }
        });
    }

    private static void finish(MinecraftServer server, String result) {
        try {
            Files.writeString(Path.of("policy-smoke-passed.txt"), result);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot record teleport smoke result", failure);
        }
        server.stop(false);
    }

    private static <T> T field(Object object, String name, Class<T> type) {
        try {
            var field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(object));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect teleport smoke state", failure);
        }
    }

    private static void command(ServerPlayerEntity player, String command, int expected) {
        try {
            require(player.getServer().getCommandManager().getDispatcher().execute(command, player.getCommandSource()) == expected,
                "Unexpected command result: " + command + "; messages: " + messages);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new IllegalStateException("Actual command failed: " + command, failure);
        }
    }

    private static void suggestions(ServerPlayerEntity player, String input, List<String> expected) {
        var dispatcher = player.getServer().getCommandManager().getDispatcher();
        var result = dispatcher.getCompletionSuggestions(dispatcher.parse(input, player.getCommandSource())).join();
        require(result.getList().stream().map(suggestion -> suggestion.getText()).toList().equals(expected),
            "Unexpected player autocomplete for " + input + ": " + result.getList());
    }

    @SuppressWarnings("unchecked")
    private static ServerPlayerEntity player(MinecraftServer server, ServerWorld world, String name) {
        ServerPlayerEntity player = new ServerPlayerEntity(server, world, new GameProfile(UUID.randomUUID(), name)) {
            @Override
            public void sendMessage(Text text, boolean overlay) {
                messages.add(text.getString());
            }
            @Override
            public boolean isDisconnected() {
                return false;
            }
        };
        player.networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND), player);
        server.getPlayerManager().getPlayerList().add(player);
        try {
            var mapField = PlayerManager.class.getDeclaredField("playerMap");
            mapField.setAccessible(true);
            var players = (Map<UUID, ServerPlayerEntity>)mapField.get(server.getPlayerManager());
            players.put(player.getUuid(), player);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot register teleport fixture player", failure);
        }
        return player;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
