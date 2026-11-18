package dev.etzewetze.chunkyvoid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.World;

/** Bounded, sequential pregeneration; only the next cursor is kept in memory. */
public final class PregenerationManager {
    private final ChunkyVoidPlugin plugin;
    private final RuntimeBridge runtime;
    private final Path stateFile;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "chunkyvoid-progress");
        thread.setDaemon(true);
        return thread;
    });
    private Job job;
    private boolean stopping;

    public PregenerationManager(ChunkyVoidPlugin plugin, RuntimeBridge runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
        stateFile = plugin.getDataFolder().toPath().resolve("pregen.properties");
        load();
    }

    public synchronized String start(World world, BorderBounds bounds) {
        if (job != null && job.state != State.DONE) return "Erst den bisherigen Auftrag fortsetzen oder abbrechen.";
        ChunkRange range = ChunkRange.around(bounds.centerX(), bounds.centerZ(), bounds.radius());
        job = new Job(world.getName(), range, 0, State.RUNNING);
        persist();
        scheduleNext(job);
        return "Vorabgenerierung gestartet: " + range.total() + " Chunks in " + world.getName() + ".";
    }

    public synchronized String pause() {
        if (job == null || job.state != State.RUNNING) return "Kein laufender Auftrag.";
        job.state = State.PAUSED;
        persist();
        return status();
    }

    public synchronized String resume() {
        if (job == null || job.state != State.PAUSED) return "Kein pausierter Auftrag.";
        if (Bukkit.getWorld(job.world) == null) return "Welt ist nicht geladen: " + job.world;
        job.state = State.RUNNING;
        persist();
        if (!job.inFlight) scheduleNext(job);
        return status();
    }

    public synchronized String cancel() {
        if (job == null || job.state == State.DONE) return "Kein offener Auftrag.";
        job.state = State.DONE;
        persist();
        return "Auftrag abgebrochen. Bereits generierte Chunks bleiben gespeichert.";
    }

    public synchronized String status() {
        if (job == null) return "Noch kein Auftrag.";
        return job.world + ": " + job.next + "/" + job.range.total()
                + " Chunks, " + job.state.name().toLowerCase() + ".";
    }

    private void scheduleNext(Job expected) {
        if (stopping || expected.state != State.RUNNING || expected.inFlight) return;
        if (expected.next >= expected.range.total()) {
            expected.state = State.DONE;
            persist();
            plugin.getLogger().info("Vorabgenerierung abgeschlossen: " + status());
            return;
        }
        World world = Bukkit.getWorld(expected.world);
        if (world == null) {
            expected.state = State.PAUSED;
            persist();
            return;
        }
        int x = expected.range.xAt(expected.next);
        int z = expected.range.zAt(expected.next);
        expected.inFlight = true;
        try {
            runtime.atChunk(world, x, z, () -> {
                synchronized (PregenerationManager.this) {
                    if (stopping || job != expected || expected.state != State.RUNNING) {
                        expected.inFlight = false;
                        return;
                    }
                }
                runtime.requestChunk(world, x, z, failure -> completed(expected, x, z, failure));
            });
        } catch (RuntimeException error) {
            expected.inFlight = false;
            expected.state = State.PAUSED;
            persist();
            plugin.getLogger().warning("Chunk scheduling failed; paused: " + error);
        }
    }

    private synchronized void completed(Job expected, int x, int z, Throwable failure) {
        if (job != expected || stopping || expected.state == State.DONE) return;
        expected.inFlight = false;
        if (failure != null) {
            expected.state = State.PAUSED;
            persist();
            plugin.getLogger().warning("Chunk " + x + "," + z + " failed; paused: " + failure);
            return;
        }
        expected.next++;
        if (expected.next % 32 == 0 || expected.next == expected.range.total()
                || expected.state == State.PAUSED) persist();
        if (expected.next % 1000 == 0) plugin.getLogger().info(status());
        if (expected.state == State.RUNNING) scheduleNext(expected);
    }

    private void load() {
        if (!Files.exists(stateFile)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(stateFile)) {
            p.load(in);
            ChunkRange range = new ChunkRange(Integer.parseInt(p.getProperty("minX")),
                    Integer.parseInt(p.getProperty("minZ")), Integer.parseInt(p.getProperty("width")),
                    Integer.parseInt(p.getProperty("height")));
            long next = Long.parseLong(p.getProperty("next"));
            if (range.width() <= 0 || range.height() <= 0 || next < 0 || next > range.total()) {
                throw new IllegalArgumentException("Invalid pregeneration cursor");
            }
            // Never resume automatically on restart; an administrator decides when disk use continues.
            String savedState = p.getProperty("state", "RUNNING");
            // A crash can lose chunks loaded after the last server autosave. Scan again
            // rather than trusting the cursor of an interrupted RUNNING job.
            long safeNext = "RUNNING".equals(savedState) ? 0 : next;
            job = new Job(p.getProperty("world"), range, safeNext,
                    next == range.total() || "DONE".equals(savedState) ? State.DONE : State.PAUSED);
        } catch (IOException | RuntimeException error) {
            plugin.getLogger().warning("Could not load pregen.properties: " + error);
        }
    }

    private synchronized void persist() {
        if (job == null) return;
        Properties snapshot = new Properties();
        snapshot.setProperty("world", job.world);
        snapshot.setProperty("minX", Integer.toString(job.range.minX()));
        snapshot.setProperty("minZ", Integer.toString(job.range.minZ()));
        snapshot.setProperty("width", Integer.toString(job.range.width()));
        snapshot.setProperty("height", Integer.toString(job.range.height()));
        snapshot.setProperty("next", Long.toString(job.next));
        snapshot.setProperty("state", job.state.name());
        writer.execute(() -> {
            try {
                Files.createDirectories(stateFile.getParent());
                Path temp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
                try (OutputStream out = Files.newOutputStream(temp)) {
                    snapshot.store(out, "ChunkyVoid pregeneration cursor");
                }
                try {
                    Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException error) {
                plugin.getLogger().warning("Could not save pregeneration progress: " + error);
            }
        });
    }

    public void shutdown() {
        synchronized (this) {
            stopping = true;
            if (job != null && job.state == State.RUNNING) job.state = State.PAUSED;
            persist();
        }
        writer.shutdown();
        try {
            if (!writer.awaitTermination(3, TimeUnit.SECONDS)) plugin.getLogger().warning("Progress save timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private enum State { RUNNING, PAUSED, DONE }

    private static final class Job {
        private final String world;
        private final ChunkRange range;
        private long next;
        private State state;
        private boolean inFlight;

        private Job(String world, ChunkRange range, long next, State state) {
            this.world = world;
            this.range = range;
            this.next = next;
            this.state = state;
        }
    }
}
