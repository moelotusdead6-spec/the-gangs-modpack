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
        assertEquals(3, state.schemaVersion);
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
