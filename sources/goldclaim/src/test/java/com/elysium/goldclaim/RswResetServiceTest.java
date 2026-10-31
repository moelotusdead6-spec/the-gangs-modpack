package com.elysium.goldclaim;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class RswResetServiceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final Gson GSON = new Gson();
    private static final long NOW = Instant.parse("2026-10-06T12:00:00Z").toEpochMilli();

    private Path journal() {
        return temporary.getRoot().toPath().resolve("rsw-reset.json");
    }

    @Test
    public void pregenerationAndSavedCompletionRemainClosedAcrossRestart() throws Exception {
        for (String phase : java.util.List.of("PREGENERATING", "FINALIZING")) {
            var state = new RswResetService.State();
            state.phase = phase;
            state.backup = "reset-123";
            state.seed = 123;
            state.nextResetAtMs = NOW;
            Files.writeString(journal(), GSON.toJson(state));
            var service = new RswResetService(journal());
            service.initialize(NOW);
            assertTrue(service.isClosed());
            assertEquals(0, service.generation());
            assertThrows(IllegalStateException.class, () -> service.requestManualReset(NOW));
            assertEquals(phase, GSON.fromJson(Files.readString(journal()), RswResetService.State.class).phase);
        }
    }

    @Test
    public void persistedSeedsAcceptBothYamlIntegerWidthsButNeverTruncateOtherValues() {
        assertEquals(12345L, RswResetService.persistedSeed(12345));
        assertEquals(Long.MIN_VALUE, RswResetService.persistedSeed(Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, RswResetService.persistedSeed(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> RswResetService.persistedSeed(12345.5));
        assertThrows(IllegalArgumentException.class, () -> RswResetService.persistedSeed("12345"));
        assertThrows(IllegalArgumentException.class, () -> RswResetService.persistedSeed(null));
    }

    @Test
    public void resetStagingIsOutsideWorldAndCleanupIsScopedAndRepeatable() throws Exception {
        Path world = temporary.getRoot().toPath().resolve("world");
        Path staging = RswResetService.resetStaging(world, "reset-123");
        assertFalse(staging.startsWith(world));
        Path legacy = world.resolve("dimensions/multiworld/rsw-reset-backups/reset-123");
        for (Path directory : java.util.List.of(staging, legacy)) {
            Files.createDirectories(directory.resolve("world/rsw/region"));
            Files.writeString(directory.resolve("world/rsw/region/fixture.mca"), "old");
        }
        Path active = world.resolve("dimensions/multiworld/rsw/region/fixture.mca");
        Files.createDirectories(active.getParent());
        Files.writeString(active, "new");
        Path unrelated = staging.resolveSibling("reset-456");
        Files.createDirectories(unrelated);
        RswResetService.clearResetStaging(world, "reset-123");
        RswResetService.clearResetStaging(world, "reset-123");
        assertFalse(Files.exists(staging));
        assertFalse(Files.exists(legacy));
        assertEquals("new", Files.readString(active));
        assertTrue(Files.exists(unrelated));
        assertThrows(IllegalArgumentException.class,
            () -> RswResetService.clearResetStaging(world, "../world"));
    }

    @Test
    public void manualResetIsExactlyFifteenMinutesAndSurvivesRestart() throws Exception {
        RswResetService service = new RswResetService(journal());
        service.initialize(NOW);
        assertEquals(RswResetService.ManualResetResult.SCHEDULED, service.requestManualReset(NOW));
        assertEquals(NOW + 900000L, service.resetAt());
        assertFalse(service.isClosed());
        RswResetService.State state = GSON.fromJson(Files.readString(journal()), RswResetService.State.class);
        assertTrue(state.manualReset);
        assertEquals(1, state.warningMask);
        RswResetService restarted = new RswResetService(journal());
        restarted.initialize(NOW + 180000L);
        assertEquals(service.resetAt(), restarted.resetAt());
        assertFalse(restarted.isClosed());
        assertEquals(RswResetService.ManualResetResult.ALREADY_PENDING, restarted.requestManualReset(NOW + 180000L));
        assertEquals(NOW + 900000L, restarted.resetAt());
    }

    @Test
    public void missedManualDeadlineIsNotNormalizedToTomorrow() throws Exception {
        RswResetService service = new RswResetService(journal());
        service.initialize(NOW);
        service.requestManualReset(NOW);
        RswResetService restarted = new RswResetService(journal());
        restarted.initialize(NOW + 1800000L);
        assertEquals(NOW + 900000L, restarted.resetAt());
    }

    @Test
    public void anEarlierDailyResetCannotBePostponed() throws Exception {
        long now = Instant.parse("2026-10-06T09:55:00Z").toEpochMilli();
        RswResetService service = new RswResetService(journal());
        service.initialize(now);
        long dailyDeadline = service.resetAt();
        assertEquals(RswResetService.ManualResetResult.ALREADY_PENDING, service.requestManualReset(now));
        assertEquals(dailyDeadline, service.resetAt());
    }

    @Test
    public void closedAndUninitializedServicesRejectManualResets() throws Exception {
        RswResetService service = new RswResetService(journal());
        assertThrows(IllegalStateException.class, () -> service.requestManualReset(NOW));
        RswResetService.State state = new RswResetService.State();
        state.phase = "CREATING";
        state.backup = "reset-123";
        state.nextResetAtMs = NOW;
        Files.writeString(journal(), GSON.toJson(state));
        service.initialize(NOW);
        assertTrue(service.isClosed());
        assertThrows(IllegalStateException.class, () -> service.requestManualReset(NOW));
    }

    @Test
    public void positionsPersistWithOrientationAndAreClearedForAllPlayersOnCompletion() throws Exception {
        RswResetService service = new RswResetService(journal());
        service.initialize(NOW);
        UUID online = UUID.randomUUID();
        UUID offline = UUID.randomUUID();
        var location = new RswResetService.Location(123.25, 70, -432.75, 45, -12);
        service.remember(online, location);
        service.remember(offline, location);
        RswResetService restarted = new RswResetService(journal());
        restarted.initialize(NOW);
        assertEquals(location, restarted.location(offline));
        RswResetService.State state = GSON.fromJson(Files.readString(journal()), RswResetService.State.class);
        state.manualReset = true;
        state.phase = "CREATING";
        state.backup = "reset-123";
        var completed = RswResetService.completedState(state, NOW + 900000L);
        assertTrue(completed.locations.isEmpty());
        assertEquals(state.generation + 1, completed.generation);
        assertFalse(completed.manualReset);
        assertEquals("IDLE", completed.phase);
        assertEquals(0, completed.warningMask);
        assertEquals(RswSchedule.nextReset(NOW + 900000L), completed.nextResetAtMs);
        assertEquals(2, state.locations.size());
        Files.writeString(journal(), GSON.toJson(completed));
        restarted.initialize(NOW + 900000L);
        assertNull(restarted.location(online));
        assertNull(restarted.location(offline));
    }

    @Test
    public void latestFractionalPositionSurvivesRestartAndPendingReset() throws Exception {
        RswResetService service = new RswResetService(journal());
        service.initialize(NOW);
        UUID player = UUID.randomUUID();
        service.remember(player, new RswResetService.Location(0.5, 70, 0.5, 0, 0));
        var lastPosition = new RswResetService.Location(
            -638.25, -30.925, -583.75, 68.4f, 12.6f);
        service.remember(player, lastPosition);
        assertEquals(lastPosition, service.location(player));
        service.requestManualReset(NOW);
        assertEquals(lastPosition, service.location(player));
        RswResetService restarted = new RswResetService(journal());
        restarted.initialize(NOW + 180000L);
        assertEquals(lastPosition, restarted.location(player));
        assertEquals(0, restarted.generation());
        assertNull(restarted.location(UUID.randomUUID()));
    }

    @Test
    public void firstExitAfterResetRemembersTheNewWorldPosition() throws Exception {
        RswResetService.State state = new RswResetService.State();
        UUID player = UUID.randomUUID();
        state.locations.put(player, new RswResetService.Location(500, 80, -500, 90, 0));
        Files.writeString(journal(), GSON.toJson(RswResetService.completedState(state, NOW)));
        RswResetService service = new RswResetService(journal());
        service.initialize(NOW);
        assertNull(service.location(player));
        var newPosition = new RswResetService.Location(20.25, 70.5, -40.75, -90, 30);
        service.remember(player, newPosition);
        RswResetService restarted = new RswResetService(journal());
        restarted.initialize(NOW + 60000L);
        assertEquals(1, restarted.generation());
        assertEquals(newPosition, restarted.location(player));
    }

    @Test
    public void evacuationCannotResaveAnOldGenerationPosition() throws Exception {
        RswResetService.State state = new RswResetService.State();
        state.phase = "UNLOADING";
        state.backup = "reset-123";
        state.nextResetAtMs = NOW;
        Files.writeString(journal(), GSON.toJson(state));
        RswResetService service = new RswResetService(journal());
        service.initialize(NOW);
        UUID player = UUID.randomUUID();
        service.remember(player, new RswResetService.Location(10, 70, 10, 0, 0));
        assertNull(service.location(player));
        state = GSON.fromJson(Files.readString(journal()), RswResetService.State.class);
        assertTrue(state.locations.isEmpty());
    }

    @Test
    public void oldJournalMigratesWithoutDroppingItsDeadline() throws Exception {
        long deadline = RswSchedule.nextReset(NOW);
        Files.writeString(journal(), "{\"schemaVersion\":2,\"phase\":\"IDLE\",\"nextResetAtMs\":" + deadline + "}");
        RswResetService service = new RswResetService(journal());
        service.initialize(NOW);
        assertFalse(service.isClosed());
        assertEquals(deadline, service.resetAt());
        var state = GSON.fromJson(Files.readString(journal()), RswResetService.State.class);
        assertEquals(4, state.schemaVersion);
        assertTrue(state.locations.isEmpty());
    }

    @Test
    public void malformedPositionsAreRejectedAndWriteFailuresDoNotReportSuccess() throws Exception {
        RswResetService service = new RswResetService(journal());
        service.initialize(NOW);
        assertThrows(IllegalArgumentException.class, () -> service.remember(UUID.randomUUID(),
            new RswResetService.Location(Double.NaN, 70, 0, 0, 0)));
        Files.delete(journal());
        Files.createDirectory(journal());
        assertThrows(java.io.IOException.class, () -> service.requestManualReset(NOW));
        assertEquals(RswSchedule.nextReset(NOW), service.resetAt());
    }
}
