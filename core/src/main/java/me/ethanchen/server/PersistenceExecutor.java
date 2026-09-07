package me.ethanchen.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Single-threaded executor for blocking persistence (SQLite result rows, XP, profile
 * load/save) so those calls never stall a room-scheduler worker.
 */
public final class PersistenceExecutor {
    private final ExecutorService exec;

    public PersistenceExecutor() {
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "persistence");
            t.setDaemon(true);
            return t;
        });
    }

    public void submit(Runnable task) {
        if (task == null) return;
        exec.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                System.err.println("[PersistenceExecutor] Uncaught exception: " + e);
                e.printStackTrace(System.err);
            }
        });
    }

    public void shutdown() {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(5, TimeUnit.SECONDS)) {
                exec.shutdownNow();
            }
        } catch (InterruptedException e) {
            exec.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
