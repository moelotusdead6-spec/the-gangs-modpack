package com.elysium.goldclaim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;

final class EndCitySmokeTest {
    private static RswResetService reset;
    private static int validated;
    private static Path resetJournal;

    static void register() {
        RswEndCityService service = new RswEndCityService();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            require(server.getTicks() < 12000, "End City reset fixture timed out");
            var end = server.getWorld(RegistryKey.of(RegistryKeys.WORLD,
                new Identifier(GoldClaimMod.RSW_THE_END_DIMENSION)));
            if (end == null) return;
            if (reset == null) {
                var state = new RswResetService.State();
                state.phase = "CREATING";
                state.seed = end.getSeed();
                state.backup = "reset-12345";
                state.startedAtMs = System.currentTimeMillis();
                state.nextResetAtMs = state.startedAtMs + 86400000L;
                try {
                    resetJournal = Files.createTempDirectory("goldclaim-city-reset-").resolve("reset.json");
                    Files.writeString(resetJournal, new com.google.gson.Gson().toJson(state));
                } catch (IOException failure) {
                    throw new IllegalStateException("Cannot initialize reset fixture", failure);
                }
                reset = new RswResetService(resetJournal);
                reset.initialize(state.startedAtMs);
            }
            reset.tick(server, player -> {
                throw new IllegalStateException("Fresh fixture should not need evacuation");
            }, (active, seed) -> {
                throw new IllegalStateException("Fixture dimensions should already be loaded");
            }, active -> service.prepare(end));
            if (reset.isClosed()) {
                require(service.completedCities().size() < 7, "Reset stayed closed after seven cities were ready");
                return;
            }
            var boxes = service.completedCities();
            require(boxes.size() == 7, "Expected exactly seven verified cities");
            require(reset.generation() == 1, "Reset completed more than once");
            if (validated < boxes.size()) {
                var box = boxes.get(validated++);
                verifyCity(end, box);
                return;
            }
            require(service.prepare(end) && service.completedCities().size() == 7,
                "Completed preparation is not idempotent");
            try {
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: seven natural End cities fully generated on outer islands, complete bounding boxes "
                    + "inside the border, real purpur blocks verified in all seven, reset remained closed until "
                    + "all seven were generated and saved, completion idempotent; seed="
                    + end.getSeed() + "; bounds=" + boxes + "\n");
                Files.delete(resetJournal);
                Files.delete(resetJournal.getParent());
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot record End City result", failure);
            }
            server.stop(false);
        });
    }

    static void verifyCity(net.minecraft.server.world.ServerWorld end, net.minecraft.util.math.BlockBox box) {
        require(RswEndCityService.fits(box, end.getWorldBorder()), "A city crosses the playable boundary");
        int purpur = 0;
        for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
            for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
                require(end.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)).getStatus() == ChunkStatus.FULL,
                    "A saved city chunk was not fully generated");
                for (int y = box.getMinY(); y <= box.getMaxY() && purpur < 100; y++) {
                    if (end.getBlockState(new BlockPos(x, y, z)).isOf(Blocks.PURPUR_BLOCK)) purpur++;
                }
            }
        }
        require(purpur >= 100, "Structure metadata exists but actual city blocks are missing: " + box);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
