package com.elysium.goldclaim;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.packet.s2c.play.DifficultyS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Difficulty;
import net.minecraft.world.WorldProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps every loaded world (vanilla, Multiworld/Fantasy runtime worlds such as RSW, Wild, RSW Nether and RSW End)
 * on the server difficulty, so /difficulty applies server-wide and survives restarts via server.properties.
 */
public final class DifficultySync {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/Difficulty");
    private static final Map<Class<?>, Accessor> ACCESSORS = new ConcurrentHashMap<>();
    private static final Accessor NONE = new Accessor(null, null);

    private record Accessor(Field config, Method setter) {}

    private DifficultySync() {}

    /** Applies the server difficulty to all worlds and resends it to affected players. Returns worlds changed. */
    public static int sync(MinecraftServer server) {
        if (server == null) return 0;
        Difficulty target = server.getSaveProperties().getDifficulty();
        int changed = 0;
        for (ServerWorld world : server.getWorlds()) {
            WorldProperties props = world.getLevelProperties();
            if (props.getDifficulty() == target) continue;
            if (!apply(props, target)) continue;
            changed++;
            for (ServerPlayerEntity player : world.getPlayers()) {
                player.networkHandler.sendPacket(new DifficultyS2CPacket(props.getDifficulty(), props.isDifficultyLocked()));
            }
        }
        return changed;
    }

    /** Called after MinecraftServer.setDifficulty (the /difficulty command and the startup properties load). */
    public static void onServerDifficultyChanged(MinecraftServer server) {
        int changed = sync(server);
        if (changed > 0) {
            LOGGER.info("Applied difficulty {} to {} additional world(s)", server.getSaveProperties().getDifficulty().getName(), changed);
        }
        if (server.isDedicated()) {
            persist(server.getRunDirectory().toPath().resolve("server.properties"), server.getSaveProperties().getDifficulty());
        }
    }

    static boolean apply(Object props, Difficulty target) {
        Accessor accessor = ACCESSORS.computeIfAbsent(props.getClass(), DifficultySync::find);
        if (accessor == NONE) return false;
        try {
            accessor.setter().invoke(accessor.config().get(props), target);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.warn("Could not set difficulty on {}", props.getClass().getName(), error);
            return false;
        }
    }

    private static Accessor find(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                try {
                    Method setter = field.getType().getMethod("setDifficulty", Difficulty.class);
                    if (field.getType().getMethod("getDifficulty").getReturnType() != Difficulty.class) continue;
                    field.setAccessible(true);
                    setter.setAccessible(true);
                    return new Accessor(field, setter);
                } catch (NoSuchMethodException | RuntimeException ignored) {
                    // Not a runtime world config holder.
                }
            }
        }
        return NONE;
    }

    /** Rewrites only the difficulty line, keeping every other server.properties line untouched. */
    static boolean persist(Path file, Difficulty difficulty) {
        try {
            if (!Files.isRegularFile(file)) return false;
            List<String> lines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
            String wanted = "difficulty=" + difficulty.getName();
            List<String> out = new ArrayList<>(lines.size() + 1);
            boolean found = false;
            boolean changed = false;
            for (String line : lines) {
                if (line.startsWith("difficulty=")) {
                    if (found) {
                        changed = true;
                        continue;
                    }
                    found = true;
                    if (!line.equals(wanted)) changed = true;
                    out.add(wanted);
                } else {
                    out.add(line);
                }
            }
            if (!found) {
                out.add(wanted);
                changed = true;
            }
            if (!changed) return false;
            Path temp = file.resolveSibling(file.getFileName() + ".goldclaim-tmp");
            Files.write(temp, out, StandardCharsets.ISO_8859_1);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Saved difficulty={} to {}", difficulty.getName(), file.getFileName());
            return true;
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Could not persist difficulty to {}", file, error);
            return false;
        }
    }
}
