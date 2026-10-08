package com.elysium.goldclaim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BannedContent {
    public static final String BIN = "furniture:bin";
    public static final String INCEPTION_UPGRADE = "sophisticatedbackpacks:inception_upgrade";
    private static final Set<String> BANNED_ITEMS = Set.of(BIN, INCEPTION_UPGRADE);
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/ContentPolicy");
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final int CHUNKS_PER_TICK = 4;
    private static final ChunkCleanupQueue<ServerWorld, WorldChunk> CLEANUP = new ChunkCleanupQueue<>();

    private BannedContent() {}

    public static boolean isBanned(Identifier id) {
        return id != null && BANNED_ITEMS.contains(id.toString());
    }

    public static boolean isBanned(BlockState state) {
        return isBanned(Registries.BLOCK.getId(state.getBlock()));
    }

    public static boolean isBannedRecipe(JsonElement recipe) {
        if (!recipe.isJsonObject()) {
            return false;
        }
        JsonElement result = recipe.getAsJsonObject().get("result");
        if (result != null && result.isJsonObject()) {
            JsonObject output = result.getAsJsonObject();
            result = output.has("item") ? output.get("item") : output.get("id");
        }
        return result != null && result.isJsonPrimitive() && result.getAsJsonPrimitive().isString()
            && BANNED_ITEMS.contains(result.getAsString());
    }

    public static int sanitize(NbtElement element) {
        int removed = 0;
        if (element instanceof NbtList list) {
            for (int index = list.size() - 1; index >= 0; index--) {
                NbtElement child = list.get(index);
                if (isBannedStack(child)) {
                    list.remove(index);
                    removed++;
                } else {
                    removed += sanitize(child);
                }
            }
        } else if (element instanceof NbtCompound compound) {
            if (isBannedStack(compound)) {
                compound.putString("id", "minecraft:air");
                compound.putByte("Count", (byte)0);
                removed++;
            }
            for (String key : new ArrayList<>(compound.getKeys())) {
                removed += sanitize(compound.get(key));
            }
        }
        return removed;
    }

    private static boolean isBannedStack(NbtElement element) {
        return element instanceof NbtCompound compound && compound.contains("Count", NbtElement.NUMBER_TYPE)
            && BANNED_ITEMS.contains(compound.getString("id"));
    }

    public static void report(String reason) {
        if (REPORTED.add(reason)) {
            LOGGER.info("Banned-content policy enforced (furniture bin / inception upgrade): {}. Further occurrences of this route are suppressed.", reason);
        }
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient && isBanned(world.getBlockState(hit.getBlockPos()))) {
                player.sendMessage(Text.literal("The furniture bin is disabled on this server."), false);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        // A CHUNK_LOAD callback runs before its chunk future completes; world writes can deadlock there.
        ServerChunkEvents.CHUNK_LOAD.register(CLEANUP::enqueue);
        ServerChunkEvents.CHUNK_UNLOAD.register(CLEANUP::cancel);
        ServerTickEvents.END_WORLD_TICK.register(world ->
            CLEANUP.drain(world, CHUNKS_PER_TICK, chunk -> removeLoadedBins(world, chunk)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CLEANUP.clear());
    }

    private static void removeLoadedBins(ServerWorld world, WorldChunk chunk) {
        int removed = 0;
        ChunkSection[] sections = chunk.getSectionArray();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section == null || !section.hasAny(BannedContent::isBanned)) {
                continue;
            }
            int bottomY = chunk.sectionIndexToCoord(sectionIndex) * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (isBanned(section.getBlockState(x, y, z))) {
                            BlockPos pos = new BlockPos(chunk.getPos().getStartX() + x, bottomY + y,
                                chunk.getPos().getStartZ() + z);
                            // Skip neighbor propagation: cleanup must not load adjacent chunks.
                            chunk.removeBlockEntity(pos);
                            if (world.setBlockState(pos, Blocks.AIR.getDefaultState(),
                                    Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS)) {
                                removed++;
                            } else {
                                LOGGER.error("Failed to remove furniture bin at {} in {}.",
                                    pos, world.getRegistryKey().getValue());
                            }
                        }
                    }
                }
            }
        }
        if (removed > 0) {
            LOGGER.info("Removed {} existing furniture bins from {} chunk {} without drops.",
                removed, world.getRegistryKey().getValue(), chunk.getPos());
        }
    }
}
