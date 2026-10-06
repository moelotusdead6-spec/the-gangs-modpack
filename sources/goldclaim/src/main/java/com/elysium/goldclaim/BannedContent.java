package com.elysium.goldclaim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BannedContent {
    public static final String BIN = "furniture:bin";
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/ContentPolicy");
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private BannedContent() {}

    public static boolean isBanned(Identifier id) {
        return id != null && BIN.equals(id.toString());
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
            && BIN.equals(result.getAsString());
    }

    public static int sanitize(NbtElement element) {
        int removed = 0;
        if (element instanceof NbtList list) {
            for (int index = list.size() - 1; index >= 0; index--) {
                NbtElement child = list.get(index);
                if (isBinStack(child)) {
                    list.remove(index);
                    removed++;
                } else {
                    removed += sanitize(child);
                }
            }
        } else if (element instanceof NbtCompound compound) {
            if (isBinStack(compound)) {
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

    private static boolean isBinStack(NbtElement element) {
        return element instanceof NbtCompound compound && compound.contains("Count", NbtElement.NUMBER_TYPE)
            && BIN.equals(compound.getString("id"));
    }

    public static void report(String reason) {
        if (REPORTED.add(reason)) {
            LOGGER.info("Furniture bin ban enforced: {}. Further occurrences of this route are suppressed.", reason);
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
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
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
                                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                                removed++;
                            }
                        }
                    }
                }
            }
            if (removed > 0) {
                LOGGER.info("Removed {} existing furniture bins from {} chunk {} without drops.",
                    removed, world.getRegistryKey().getValue(), chunk.getPos());
            }
        });
    }
}
