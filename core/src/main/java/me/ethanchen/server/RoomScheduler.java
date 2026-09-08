package me.ethanchen.server;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import me.ethanchen.game.GameConstants;

/**
 * Fixed pool of shard workers that tick {@link GameRoom}s. Each room is owned by exactly one
 * shard, so {@code drainInbound} / {@code ServerGame.update} never interleave for that room.
 * Pacing uses {@link System#nanoTime()} absolute deadlines at {@link GameConstants#TICK_MS}.
 */
public final class RoomScheduler {
    private static final long TICK_NS = GameConstants.TICK_MS * 1_000_000L;

    private final Shard[] shards;
    private final AtomicInteger nextShard = new AtomicInteger();
    private volatile boolean running;

    public RoomScheduler() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    public RoomScheduler(int workerCount) {
        int n = Math.max(1, workerCount);
        this.shards = new Shard[n];
        for (int i = 0; i < n; i++) {
            shards[i] = new Shard(i);
        }
    }

    public void start() {
        if (running) return;
        running = true;
        for (Shard shard : shards) shard.start();
        System.out.println("[RoomScheduler] Started " + shards.length + " shard workers");
    }

    public void stop() {
        running = false;
        for (Shard shard : shards) shard.stop();
    }

    public void register(GameRoom room) {
        int i = Math.floorMod(nextShard.getAndIncrement(), shards.length);
        shards[i].add(room);
    }

    public void unregister(GameRoom room) {
        for (Shard shard : shards) {
            shard.remove(room);
        }
    }

    int shardCount() {
        return shards.length;
    }

    private final class Shard implements Runnable {
        private final CopyOnWriteArrayList<GameRoom> rooms = new CopyOnWriteArrayList<>();
        private final int index;
        private Thread thread;

        Shard(int index) {
            this.index = index;
        }

        void start() {
            thread = new Thread(this, "room-shard-" + index);
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            if (thread != null) thread.interrupt();
        }

        void add(GameRoom room) {
            rooms.addIfAbsent(room);
        }

        void remove(GameRoom room) {
            rooms.remove(room);
        }

        @Override
        public void run() {
            long nextDeadline = System.nanoTime();
            while (RoomScheduler.this.running) {
                for (GameRoom room : rooms) {
                    try {
                        if (room.isRunning()) room.tickOnce();
                    } catch (Throwable t) {
                        Uncaught.log("[RoomScheduler shard " + index + "] Uncaught exception: ", t);
                    }
                }
                nextDeadline += TICK_NS;
                long now = System.nanoTime();
                if (now < nextDeadline) {
                    long sleepNs = nextDeadline - now;
                    long sleepMs = sleepNs / 1_000_000L;
                    int extraNs = (int) (sleepNs % 1_000_000L);
                    try {
                        Thread.sleep(sleepMs, extraNs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    // Missed the deadline: do not pile up catch-up ticks.
                    nextDeadline = now;
                }
            }
        }
    }
}
