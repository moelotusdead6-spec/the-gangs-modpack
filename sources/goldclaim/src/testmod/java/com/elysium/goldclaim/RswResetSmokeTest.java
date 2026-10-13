package com.elysium.goldclaim;

import java.nio.file.Files;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

final class RswResetSmokeTest {
    private static RswResetService service;
    private static long generation;
    private static long previousSeed;
    private static String transaction;

    static void start(MinecraftServer server) {
        try {
            var instance = GoldClaimMod.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            var reset = GoldClaimMod.class.getDeclaredField("rswResetService");
            reset.setAccessible(true);
            if (!(reset.get(instance.get(null)) instanceof RswResetService actual)) {
                throw new IllegalStateException("Missing RSW reset service");
            }
            service = actual;
            generation = service.generation();
            previousSeed = server.getWorld(RegistryKey.of(RegistryKeys.WORLD,
                new Identifier(GoldClaimMod.RSW_THE_END_DIMENSION))).getSeed();
            var state = RswResetService.class.getDeclaredField("state");
            state.setAccessible(true);
            if (!(state.get(service) instanceof RswResetService.State current)) {
                throw new IllegalStateException("Missing reset state");
            }
            current.nextResetAtMs = System.currentTimeMillis() - 1;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot request isolated RSW reset", failure);
        }
    }

    static boolean verify(MinecraftServer server) {
        try {
            var stateField = RswResetService.class.getDeclaredField("state");
            stateField.setAccessible(true);
            if (!(stateField.get(service) instanceof RswResetService.State state)) {
                throw new IllegalStateException("Missing reset state");
            }
            if (service.isClosed()) transaction = state.backup;
            if (service.generation() == generation) return false;
            if (service.isClosed() || transaction == null || state.seed == previousSeed) {
                throw new IllegalStateException("RSW reset did not complete with a different seed");
            }
            for (String dimension : RswResetService.RSW_SUB_DIMENSIONS) {
                var world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier("multiworld", dimension)));
                if (world == null || world.getSeed() != state.seed) {
                    throw new IllegalStateException("Replacement dimension missing or wrong seed: " + dimension);
                }
            }
            var root = server.getSavePath(WorldSavePath.ROOT);
            if (Files.exists(RswResetService.resetStaging(root, transaction))
                    || Files.exists(root.resolve("dimensions/multiworld/rsw-reset-backups").resolve(transaction))) {
                throw new IllegalStateException("Completed reset retained an RSW backup");
            }
            return true;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot verify isolated RSW reset", failure);
        }
    }
}
