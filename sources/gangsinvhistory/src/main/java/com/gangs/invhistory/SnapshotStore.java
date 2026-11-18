package com.gangs.invhistory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

/** Writes one compressed NBT file per snapshot: {@code <root>/<player-uuid>/<id>-<event>.dat}. */
public final class SnapshotStore {
    public record Entry(long id, SnapshotEvent event, Path file) {}

    private static final Pattern NAME = Pattern.compile("(\\d+)-([a-z]+)\\.dat");
    private final Path root;
    private final Duration retention;
    private final AtomicLong lastId = new AtomicLong();
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "gangsinvhistory-io");
        thread.setDaemon(true);
        return thread;
    });

    public SnapshotStore(Path root, Duration retention) {
        this.root = root;
        this.retention = retention;
    }

    public Path root() {
        return root;
    }

    public Duration retention() {
        return retention;
    }

    void start() {
        io.scheduleWithFixedDelay(() -> prune(System.currentTimeMillis()), 0, 1, TimeUnit.HOURS);
    }

    /** Ids are millisecond timestamps, bumped when needed so they stay unique and ordered. */
    long submit(UUID player, SnapshotEvent event, NbtCompound data) {
        long now = System.currentTimeMillis();
        long id = lastId.updateAndGet(previous -> Math.max(previous + 1, now));
        data.putLong("SnapshotId", id);
        io.execute(() -> write(player, id, event, data));
        return id;
    }

    private void write(UUID player, long id, SnapshotEvent event, NbtCompound data) {
        Path directory = root.resolve(player.toString());
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "snapshot-", ".tmp");
            NbtIo.writeCompressed(data, temporary.toFile());
            Files.move(temporary, directory.resolve(id + "-" + event.id() + ".dat"), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException failure) {
            InvHistoryMod.LOGGER.error("Cannot save {} inventory snapshot {} for {}.", event.id(), id, player, failure);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException failure) {
                    InvHistoryMod.LOGGER.error("Cannot remove incomplete snapshot {}.", temporary, failure);
                }
            }
        }
    }

    /** Newest first. */
    public List<Entry> list(UUID player) throws IOException {
        Path directory = root.resolve(player.toString());
        var result = new ArrayList<Entry>();
        if (!Files.isDirectory(directory)) return result;
        try (var files = Files.list(directory)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Entry entry = parse(file);
                if (entry != null) result.add(entry);
            }
        }
        result.sort(Comparator.comparingLong(Entry::id).reversed());
        return result;
    }

    public NbtCompound read(UUID player, long id) throws IOException {
        for (Entry entry : list(player)) {
            if (entry.id() == id) return NbtIo.readCompressed(entry.file().toFile());
        }
        return null;
    }

    int prune(long now) {
        long cutoff = now - retention.toMillis();
        int removed = 0;
        try {
            if (!Files.isDirectory(root)) return 0;
            try (var players = Files.list(root)) {
                for (Path directory : (Iterable<Path>) players::iterator) {
                    if (!Files.isDirectory(directory)) continue;
                    try (var files = Files.list(directory)) {
                        for (Path file : (Iterable<Path>) files::iterator) {
                            Entry entry = parse(file);
                            if (entry != null && entry.id() < cutoff) {
                                Files.deleteIfExists(file);
                                removed++;
                            }
                        }
                    }
                    try (var remaining = Files.list(directory)) {
                        if (remaining.findAny().isEmpty()) Files.deleteIfExists(directory);
                    }
                }
            }
            if (removed > 0) {
                InvHistoryMod.LOGGER.info("Removed {} inventory snapshots older than {} days.", removed, retention.toDays());
            }
        } catch (IOException | RuntimeException failure) {
            InvHistoryMod.LOGGER.error("Inventory snapshot cleanup failed.", failure);
        }
        return removed;
    }

    /** Waits until every snapshot submitted before this call has been written. */
    void flush() throws Exception {
        io.submit(() -> {}).get(60, TimeUnit.SECONDS);
    }

    void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(60, TimeUnit.SECONDS)) {
                InvHistoryMod.LOGGER.error("Timed out writing pending inventory snapshots.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static Entry parse(Path file) {
        Matcher matcher = NAME.matcher(file.getFileName().toString());
        if (!matcher.matches()) return null;
        SnapshotEvent event = SnapshotEvent.fromId(matcher.group(2));
        if (event == null) return null;
        try {
            return new Entry(Long.parseLong(matcher.group(1)), event, file);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
