package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
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
                destination = player(server, end, "TeleportDestination");
                destination.refreshPositionAndAngles(4200.5, 150, 4200.5, 27, 11);
                require(end.getChunkManager().getWorldChunk(4200 >> 4, 4200 >> 4) == null,
                    "teleport destination must start cold");
                queue = field(mod, "teleportPreparation", TeleportPreparation.class);
                long before = System.nanoTime();
                require(mod.preparePlayerTeleport(traveler, destination), "cross-world TPA must queue");
                requestNanos = System.nanoTime() - before;
                require(requestNanos < 250_000_000L, "cold TPA enqueue exceeded 250ms");
                require(!mod.preparePlayerTeleport(traveler, destination), "duplicate teleport must be rejected");
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
                require(traveler.getServerWorld() == end && traveler.getPos().equals(destination.getPos()),
                    "TPA must arrive at exact accepted position");
                require(traveler.getYaw() == 27 && traveler.getPitch() == 11, "TPA rotation must be preserved");
                require(messages.stream().anyMatch(text -> text.startsWith("Teleported to ")),
                    "TPA completion must notify only after arrival");
                destination.refreshPositionAndAngles(4300.5, 150, 4300.5, 0, 0);
                require(mod.preparePlayerTeleport(traveler, destination), "second TPA must queue");
                destination.refreshPositionAndAngles(4400.5, 150, 4400.5, 0, 0);
                traveler.refreshPositionAndAngles(4250.5, 150, 4250.5, 0, 0);
                messages.clear();
                stage++;
            } else if (stage == 2) {
                if (queue.isPending(traveler.getUuid())) return;
                require(traveler.getPos().equals(new Vec3d(4300.5, 150, 4300.5)),
                    "moving either player must preserve arrival at the accepted location");
                require(messages.stream().noneMatch(text -> text.startsWith("Teleport cancelled")),
                    "walking must not cancel a player teleport");
                destination.refreshPositionAndAngles(4500.5, 150, 4500.5, 0, 0);
                require(mod.preparePlayerTeleport(traveler, destination), "disconnect fixture must queue");
                destination.setHealth(0);
                stage++;
            } else if (stage == 3) {
                require(!queue.isPending(traveler.getUuid()), "dead target must cancel preparation");
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
                try {
                    Files.writeString(Path.of("teleport-smoke-passed.txt"),
                        "PASS: cold cross-world RSW End TPA queued in " + requestNanos / 1_000_000.0
                        + "ms; " + waitingTicks + " server ticks continued during generation; exact arrival/rotation; "
                        + "duplicate rejection; both players can walk without cancellation or destination tracking; "
                        + "target death cancellation; actual RSW /rtp returns within "
                        + "250ms, yields ticks and lands safely within the 250-block border inset; longest "
                        + "preparation tick " + longestPreparationTick / 1_000_000.0 + "ms (required <1000ms).\n");
                } catch (IOException failure) {
                    throw new IllegalStateException("Cannot record teleport smoke result", failure);
                }
                server.stop(false);
                stage++;
            }
        });
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
        return player;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
