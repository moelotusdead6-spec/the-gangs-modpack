package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

final class RswDragonRecoverySmokeTest {
    private static int ticks;
    private static int stage;
    private static EnderDragonFight.Data oldData;
    private static EnderDragonFight.Data vanillaData;
    private static ServerPlayerEntity visitor;
    private static long seed;
    private static final BlockPos MARKER = new BlockPos(32, 90, 32);
    private static final String RESET = "rsw dragon reset confirm";

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            require(++ticks < 2400, "Dragon recovery smoke timed out");
            ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD,
                new Identifier(GoldClaimMod.RSW_THE_END_DIMENSION)));
            if (world == null || ticks < 40) return;
            try {
                var dispatcher = server.getCommandManager().getDispatcher();
                var source = server.getCommandSource();
                if (stage == 0) {
                    var dragonNode = dispatcher.getRoot().getChild("rsw").getChild("dragon");
                    require(!dragonNode.canUse(source.withLevel(0)), "Non-operator can use dragon recovery");
                    require(dragonNode.canUse(source), "Console cannot use dragon recovery");
                    require(dispatcher.parse("rsw dragon reset", source).getContext().getCommand() == null,
                        "Reset must require explicit confirm");
                    vanillaData = server.getSaveProperties().getDragonFight();
                    seed = world.getSeed();
                    oldData = new EnderDragonFight.Data(false, true, true, false,
                        Optional.empty(), Optional.empty(), Optional.of(List.of(1, 3, 5)));
                    world.setEnderDragonFight(new EnderDragonFight(world, seed,
                        new EnderDragonFight.Data(false, false, true, false,
                            Optional.of(UUID.randomUUID()), Optional.empty(), oldData.gateways()), BlockPos.ORIGIN));
                    require(dispatcher.execute(RESET, source) == 0, "Reset accepted with a tracked unloaded dragon");
                    world.setEnderDragonFight(new EnderDragonFight(world, seed,
                        new EnderDragonFight.Data(false, true, true, true,
                            Optional.empty(), Optional.empty(), oldData.gateways()), BlockPos.ORIGIN));
                    require(dispatcher.execute(RESET, source) == 0, "Reset accepted during a dragon respawn");
                    world.setEnderDragonFight(new EnderDragonFight(world, seed, oldData, BlockPos.ORIGIN));
                    Files.writeString(Path.of("closed-rsw-reset.json"),
                        "{\"phase\":\"PREGENERATING\",\"backup\":\"reset-123\",\"nextResetAtMs\":1}");
                    var closed = new RswResetService(Path.of("closed-rsw-reset.json"));
                    closed.initialize(System.currentTimeMillis());
                    var recovery = new RswDragonFightService();
                    require(recovery.requestReset(source, closed) == 0, "Reset accepted during an RSW reset");
                    require(recovery.requestReset(source.withLevel(0), closed) == 0,
                        "Reset permission check could be bypassed");
                    RswDragonFightService.saveFightData(world, oldData);
                    world.setBlockState(MARKER, Blocks.DIAMOND_BLOCK.getDefaultState());
                    require(dispatcher.execute("rsw dragon status", source) == 1, "Status failed");
                    long before = System.nanoTime();
                    require(dispatcher.execute(RESET, source) == 1, "Reset did not enqueue");
                    require(System.nanoTime() - before < 250_000_000L, "Reset enqueue blocked for >=250ms");
                    require(world.getEnderDragonFight().toData().equals(oldData), "Reset executed synchronously");
                    require(dispatcher.execute(RESET, source) == 0, "Duplicate reset accepted");
                    visitor = visitor(server, world);
                    stage = 1;
                    return;
                }
                if (stage == 1) {
                    require(world.getEnderDragonFight().toData().equals(oldData),
                        "Reset was not cancelled when a player entered");
                    require(dispatcher.execute(RESET, source) == 0, "Reset accepted with a player in RSW End");
                    world.removePlayer(visitor, Entity.RemovalReason.DISCARDED);
                    require(dispatcher.execute(RESET, source) == 1, "Cancelled reset could not be retried");
                    stage = 2;
                    return;
                }
                if (stage == 2) {
                    var data = world.getEnderDragonFight().toData();
                    if (data.dragonKilled()) return;
                    require(!data.needsStateScanning() && !data.isRespawning(), "Reset retained legacy/respawn state");
                    require(data.previouslyKilled() && data.gateways().equals(oldData.gateways()),
                        "Reset lost prior kill/gateway progress");
                    require(data.dragonUUID().isEmpty() && data.exitPortalLocation().isPresent(),
                        "Reset did not clear UUID or build a fountain");
                    BlockPos portal = data.exitPortalLocation().orElseThrow();
                    require(world.getBlockState(portal).isOf(Blocks.BEDROCK), "Exit fountain was not generated");
                    require(world.getSeed() == seed && world.getBlockState(MARKER).isOf(Blocks.DIAMOND_BLOCK),
                        "Reset changed seed or terrain outside the fountain");
                    require(server.getSaveProperties().getDragonFight().equals(vanillaData),
                        "RSW recovery modified vanilla End state");
                    Path directory = server.getSavePath(WorldSavePath.ROOT)
                        .resolve("dimensions/multiworld/rsw_the_end");
                    try (var files = Files.list(directory)) {
                        var backups = files.filter(p -> p.getFileName().toString()
                            .startsWith("rsw_dragon_fight-before-reset-")).toList();
                        require(backups.size() == 1 && read(backups.get(0)).equals(oldData),
                            "Missing or incorrect pre-reset fight backup");
                    }
                    require(read(directory.resolve("rsw_dragon_fight.dat")).equals(data),
                        "Reset was not persisted immediately");
                    world.setEnderDragonFight(null);
                    RswDragonFightService.ensureDragonFight(world);
                    require(world.getEnderDragonFight().toData().equals(data), "Reset state did not survive reload");
                    visitor = visitor(server, world);
                    stage = 3;
                    return;
                }
                var dragons = world.getAliveEnderDragons();
                if (dragons.isEmpty()) return;
                require(dragons.size() == 1, "Recovery spawned duplicate dragons");
                world.removePlayer(visitor, Entity.RemovalReason.DISCARDED);
                require(dispatcher.execute(RESET, source) == 0, "Reset accepted with a live dragon");
                var fight = world.getEnderDragonFight();
                require(dragons.get(0).getUuid().equals(fight.getDragonUuid()), "Spawned dragon is not fight-managed");
                BlockPos fountain = fight.toData().exitPortalLocation().orElseThrow();
                require(portalBlocks(world, fountain).isEmpty(), "Exit portal was open before the dragon was defeated");
                fight.dragonKilled(dragons.get(0));
                var killedData = fight.toData();
                var opened = portalBlocks(world, fountain);
                require(killedData.dragonKilled() && !opened.isEmpty(),
                    "Recovered fight did not open the exit portal after dragon defeat: data=" + killedData
                        + ", sameFight=" + (fight == world.getEnderDragonFight()));
                require(server.getSaveProperties().getDragonFight().equals(vanillaData),
                    "Dragon spawn modified vanilla End state");
                Files.writeString(Path.of("dragon-recovery-smoke-passed.txt"),
                    "PASS: operator-only confirmed command; console support; RSW reset/respawn/unloaded-dragon guards; "
                    + "enqueue <250ms; duplicate and "
                    + "occupied-End refusal; player-entry cancellation and retry; exact old-state backup; "
                    + "closed fountain rebuilt; killed/legacy flags cleared; kill/gateway progress, terrain, "
                    + "seed and vanilla End preserved; state survives reload; exactly one dragon spawns; "
                    + "live-dragon reset refused; managed dragon defeat opens the exit portal ("
                    + opened.size() + " end_portal blocks at y=" + opened.get(0).getY() + ", fountain "
                    + fountain.toShortString() + ").\n");
                server.stop(false);
            } catch (Exception failure) {
                throw new IllegalStateException("RSW dragon recovery integration failed", failure);
            }
        });
    }

    private static ServerPlayerEntity visitor(MinecraftServer server, ServerWorld world) {
        var player = new ServerPlayerEntity(server, world, new GameProfile(UUID.randomUUID(), "DragonRecovery")) {
            @Override public boolean isDisconnected() { return false; }
            @Override public void sendMessage(Text text, boolean overlay) {}
        };
        player.networkHandler = new ServerPlayNetworkHandler(server,
            new ClientConnection(NetworkSide.SERVERBOUND), player) {
            @Override public void sendPacket(Packet<?> packet) {}
        };
        player.refreshPositionAndAngles(0.5, 90, 0.5, 0, 0);
        world.onPlayerConnected(player);
        return player;
    }

    private static List<BlockPos> portalBlocks(ServerWorld world, BlockPos fountain) {
        List<BlockPos> found = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(fountain.add(-3, -4, -3), fountain.add(3, 4, 3))) {
            if (world.getBlockState(pos).isOf(Blocks.END_PORTAL)) found.add(pos.toImmutable());
        }
        return found;
    }

    private static EnderDragonFight.Data read(Path file) throws IOException {
        return EnderDragonFight.Data.CODEC.parse(NbtOps.INSTANCE,
            NbtIo.readCompressed(file.toFile()).get("DragonFight")).getOrThrow(false,
                error -> { throw new IllegalStateException(error); });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
