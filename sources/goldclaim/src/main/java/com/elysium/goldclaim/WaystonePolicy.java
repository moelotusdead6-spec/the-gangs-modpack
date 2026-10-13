package com.elysium.goldclaim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.api.WaystoneActivatedEvent;
import net.blay09.mods.waystones.api.WaystoneTeleportEvent;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WaystonePolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/Waystones");
    private static final String MESSAGE = "Waystones are disabled in all RSW dimensions.";
    private static final ChunkCleanupQueue<ServerWorld, WorldChunk> CHUNKS = new ChunkCleanupQueue<>();
    private static final ArrayDeque<UUID> PENDING = new ArrayDeque<>();
    private static final Set<UUID> ACTIVATED = new HashSet<>();
    private static final Set<ServerWorld> CHANGED_WORLDS = new HashSet<>();
    private static Path backup;
    private static boolean finishing;

    private WaystonePolicy() {}

    public static boolean isRsw(ServerWorld world) {
        return GoldClaimMod.RSW_DIMENSIONS.contains(world.getRegistryKey().getValue().toString());
    }

    public static boolean isWaystone(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).getNamespace().equals("waystones");
    }

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("waystones")) return;
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Pre.class, event -> {
            var context = event.getContext();
            if ((context.getEntity().getWorld() instanceof ServerWorld source && isRsw(source))
                    || isRsw(event.getDestination().getLevel())) {
                event.setCanceled(true);
                context.getEntity().sendMessage(Text.literal(MESSAGE));
            }
        });
        Balm.getEvents().onEvent(WaystoneActivatedEvent.class,
            event -> ACTIVATED.add(event.getWaystone().getWaystoneUid()));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world instanceof ServerWorld serverWorld && isRsw(serverWorld)
                    && (isWaystone(world.getBlockState(hit.getBlockPos()))
                        || Registries.ITEM.getId(player.getStackInHand(hand).getItem())
                            .getNamespace().equals("waystones"))) {
                player.sendMessage(Text.literal(MESSAGE), false);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getStackInHand(hand);
            if (world instanceof ServerWorld serverWorld && isRsw(serverWorld)
                    && Registries.ITEM.getId(stack.getItem()).getNamespace().equals("waystones")) {
                player.sendMessage(Text.literal(MESSAGE), false);
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.pass(stack);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(WaystonePolicy::prepareCleanup);
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (isRsw(world)) CHUNKS.enqueue(world, chunk);
        });
        ServerChunkEvents.CHUNK_UNLOAD.register(CHUNKS::cancel);
        ServerTickEvents.END_WORLD_TICK.register(world ->
            CHUNKS.drain(world, 1, chunk -> removeRswWaystones(world, chunk)));
        ServerTickEvents.END_SERVER_TICK.register(WaystonePolicy::tickCleanup);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CHUNKS.clear();
            PENDING.clear();
            ACTIVATED.clear();
            CHANGED_WORLDS.clear();
            backup = null;
            finishing = false;
        });
    }

    private static void prepareCleanup(MinecraftServer server) {
        Path root = server.getSavePath(WorldSavePath.ROOT);
        backup = FabricLoader.getInstance().getGameDir().resolve("backups")
            .resolve("waystones-policy-v1.1.5").resolve(root.toAbsolutePath().normalize().getFileName());
        try {
            Files.createDirectories(backup);
            Path players = root.resolve("playerdata");
            if (Files.isDirectory(players)) {
                try (var files = Files.list(players)) {
                    for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".dat")).toList()) {
                        WaystonePolicyRules.collectActivated(NbtIo.readCompressed(file.toFile()), ACTIVATED);
                    }
                }
            }
            for (var player : server.getPlayerManager().getPlayerList()) {
                for (var stone : WaystonesAPI.getActivatedWaystones(player)) {
                    ACTIVATED.add(stone.getWaystoneUid());
                }
            }
            if (Files.exists(backup.resolve("complete.txt"))) return;
            Path plan = backup.resolve("waystones-to-remove.txt");
            if (!Files.exists(plan)) {
                backupFile(root.resolve("data/waystones.dat"), root);
                var ids = WaystonesAPI.getAllWaystones(server)
                    .filter(stone -> WaystonePolicyRules.shouldRemove(
                        stone.getDimension().getValue().toString(), stone.getWaystoneUid(), ACTIVATED))
                    .map(stone -> stone.getWaystoneUid().toString()).toList();
                Files.write(plan, ids);
            }
            for (String line : Files.readAllLines(plan)) PENDING.add(UUID.fromString(line));
            finishing = true;
            LOGGER.info("Queued {} existing waystones for backed-up cleanup; preserving {} activated IDs from all players.",
                PENDING.size(), ACTIVATED.size());
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("Cannot safely determine or back up unused waystones; cleanup aborted.", failure);
        }
    }

    private static void tickCleanup(MinecraftServer server) {
        if (!finishing) return;
        UUID id = PENDING.peek();
        if (id != null) {
            IWaystone stone = WaystonesAPI.getWaystone(server, id).orElse(null);
            if (stone != null && WaystonePolicyRules.shouldRemove(
                    stone.getDimension().getValue().toString(), id, ACTIVATED)) {
                ServerWorld world = server.getWorld(stone.getDimension());
                if (world == null) {
                    throw new IllegalStateException("Cannot clean waystone in unloaded dimension " + stone.getDimension());
                }
                removeStone(world, stone.getPos(), stone);
            }
            PENDING.remove();
        }
        if (PENDING.isEmpty()) {
            for (ServerWorld world : CHANGED_WORLDS) world.getChunkManager().save(true);
            server.getOverworld().getPersistentStateManager().save();
            try {
                Files.writeString(backup.resolve("complete.txt"), "Existing unused Wild waystone cleanup completed.\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot persist waystone cleanup completion.", failure);
            }
            finishing = false;
            CHANGED_WORLDS.clear();
            LOGGER.info("Existing unused Wild waystone cleanup completed. Backups: {}", backup);
        }
    }

    private static void removeRswWaystones(ServerWorld world, WorldChunk chunk) {
        if (backup == null) return;
        for (BlockPos pos : new ArrayList<>(chunk.getBlockEntities().keySet())) {
            if (isWaystone(chunk.getBlockState(pos))) {
                removeStone(world, pos, WaystonesAPI.getWaystoneAt(world, pos).orElse(null));
            }
        }
    }

    private static void removeStone(ServerWorld world, BlockPos pos, IWaystone stone) {
        Path root = world.getServer().getSavePath(WorldSavePath.ROOT);
        Path dimension = DimensionType.getSaveDirectory(world.getRegistryKey(), root);
        try {
            if (!GoldClaimMod.RSW_DIMENSIONS.contains(world.getRegistryKey().getValue().toString())) {
                for (String folder : new String[]{"region", "entities", "poi"}) {
                    backupFile(dimension.resolve(folder).resolve("r." + (pos.getX() >> 9) + "."
                        + (pos.getZ() >> 9) + ".mca"), root);
                    backupFile(dimension.resolve(folder).resolve("c." + (pos.getX() >> 4) + "."
                        + (pos.getZ() >> 4) + ".mcc"), root);
                }
            }
            backupFile(root.resolve("data/waystones.dat"), root);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot back up waystone at " + pos + "; removal aborted.", failure);
        }
        world.getChunk(pos);
        BlockState state = world.getBlockState(pos);
        BlockPos lower = state.contains(Properties.DOUBLE_BLOCK_HALF)
                && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
        BlockPos[] blocks = state.contains(Properties.DOUBLE_BLOCK_HALF)
            ? new BlockPos[]{lower, lower.up()} : new BlockPos[]{pos};
        // Remove both halves without drops or neighbor chunk loads.
        for (BlockPos block : blocks) {
            if (isWaystone(world.getBlockState(block)) && !world.setBlockState(block, Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS)) {
                throw new IllegalStateException("Failed to remove waystone block at " + block);
            }
        }
        if (stone != null) WaystonesAPI.removeWaystoneFromDatabase(world.getServer(), stone);
        if (finishing) CHANGED_WORLDS.add(world);
        LOGGER.info("Removed waystone {} at {} in {} without drops.",
            stone == null ? "(unregistered)" : stone.getWaystoneUid(), pos, world.getRegistryKey().getValue());
    }

    private static void backupFile(Path source, Path root) throws IOException {
        Path target = backup.resolve(root.relativize(source));
        if (Files.isRegularFile(source) && !Files.exists(target)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
        }
    }
}
