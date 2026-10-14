package com.elysium.goldclaim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import org.junit.Test;
import static org.junit.Assert.*;

public class DifficultyAndEndExitTest {
    static final class FakeConfig {
        Difficulty difficulty = Difficulty.NORMAL;
        public FakeConfig setDifficulty(Difficulty d) { this.difficulty = d; return this; }
        public Difficulty getDifficulty() { return this.difficulty; }
    }

    static class FakeBase { private final String unrelated = "x"; }

    static final class FakeRuntimeProperties extends FakeBase {
        protected final FakeConfig config = new FakeConfig();
    }

    @Test
    public void appliesDifficultyToRuntimeWorldConfig() {
        FakeRuntimeProperties props = new FakeRuntimeProperties();
        assertTrue(DifficultySync.apply(props, Difficulty.HARD));
        assertEquals(Difficulty.HARD, props.config.difficulty);
    }

    @Test
    public void ignoresPropertiesWithoutDifficultyConfig() {
        assertFalse(DifficultySync.apply(new FakeBase(), Difficulty.HARD));
    }

    @Test
    public void persistsOnlyTheDifficultyLine() throws Exception {
        Path dir = Files.createTempDirectory("gc-diff");
        Path file = dir.resolve("server.properties");
        Files.write(file, List.of("#Minecraft server properties", "motd=Gangs", "difficulty=easy", "pvp=true"), StandardCharsets.ISO_8859_1);
        assertTrue(DifficultySync.persist(file, Difficulty.HARD));
        assertEquals(List.of("#Minecraft server properties", "motd=Gangs", "difficulty=hard", "pvp=true"),
            Files.readAllLines(file, StandardCharsets.ISO_8859_1));
        assertFalse(DifficultySync.persist(file, Difficulty.HARD));
        Files.delete(file);
        Files.delete(dir);
    }

    @Test
    public void appendsDifficultyWhenMissing() throws Exception {
        Path dir = Files.createTempDirectory("gc-diff");
        Path file = dir.resolve("server.properties");
        Files.write(file, List.of("motd=Gangs"), StandardCharsets.ISO_8859_1);
        assertTrue(DifficultySync.persist(file, Difficulty.PEACEFUL));
        assertEquals(List.of("motd=Gangs", "difficulty=peaceful"), Files.readAllLines(file, StandardCharsets.ISO_8859_1));
        Files.delete(file);
        Files.delete(dir);
    }

    @Test
    public void endExitUsesOnlyRswSpawnPoints() {
        assertTrue(RswEndExit.isRswSpawn("multiworld:rsw", new BlockPos(10, 70, 10)));
        assertFalse(RswEndExit.isRswSpawn("minecraft:overworld", new BlockPos(10, 70, 10)));
        assertFalse(RswEndExit.isRswSpawn("multiworld:rsw_the_end", new BlockPos(10, 70, 10)));
        assertFalse(RswEndExit.isRswSpawn("multiworld:rsw", null));
    }

    @Test
    public void endExitRejectsLandingsAtOrBelowTheWorldFloor() {
        assertFalse(RswEndExit.usableLanding(-64, -64, 320));
        assertFalse(RswEndExit.usableLanding(-70, -64, 320));
        assertFalse(RswEndExit.usableLanding(320, -64, 320));
        assertTrue(RswEndExit.usableLanding(65.0, -64, 320));
    }
}
