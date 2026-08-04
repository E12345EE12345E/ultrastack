package me.ethanchen.headless;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically runs a WAL checkpoint so the main {@code .db} file stays current instead of
 * waiting for SQLite's page-count auto-checkpoint or connection {@code close()}.
 *
 * <p>Useful when operators copy only the main database file (e.g. into web_dbedit) while the
 * server is still running; without checkpoints, recent commits live only in {@code -wal}/{@code -shm}.
 */
final class SqliteWalSync implements AutoCloseable {
    static final long INTERVAL_SECONDS = 60;

    private final String name;
    private final Runnable checkpoint;
    private final ScheduledExecutorService scheduler;

    SqliteWalSync(String name, Runnable checkpoint) {
        this.name = name;
        this.checkpoint = checkpoint;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::safeCheckpoint,
                INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void safeCheckpoint() {
        try {
            checkpoint.run();
        } catch (Throwable t) {
            System.err.println("[" + name + "] WAL checkpoint failed: " + t.getMessage());
        }
    }

    /** Stops the timer and runs one final checkpoint. */
    @Override
    public void close() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        safeCheckpoint();
    }
}
