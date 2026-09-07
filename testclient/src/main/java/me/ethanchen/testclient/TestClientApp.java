package me.ethanchen.testclient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;

/**
 * Headless load driver. Defaults match the original 40-host-each-own-room harness.
 *
 * <p>System properties:
 * <ul>
 *   <li>{@code ultrastack.rooms} — number of rooms (default 40)
 *   <li>{@code ultrastack.botsPerRoom} — bots per room, including the host (default 1)
 *   <li>{@code ultrastack.idleMs} — how long each bot stays in a running game
 *   <li>{@code ultrastack.quiet} — suppress per-bot logs
 * </ul>
 */
final class TestClientApp extends ApplicationAdapter {
    static final int DEFAULT_ROOMS = 40;
    static final String PASSWORD = "test";

    private final String host;
    private final int port;

    TestClientApp(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void create() {
        Gdx.app.setApplicationLogger(new TimestampedLogger());
        Gdx.app.setLogLevel(Application.LOG_DEBUG);

        int rooms = Integer.getInteger("ultrastack.rooms", DEFAULT_ROOMS);
        int botsPerRoom = Math.max(1, Integer.getInteger("ultrastack.botsPerRoom", 1));
        long idleMs = Long.getLong("ultrastack.idleMs", 30_000L);
        boolean quiet = Boolean.getBoolean("ultrastack.quiet");
        int botCount = rooms * botsPerRoom;

        Gdx.app.log("harness", "starting " + botCount + " bots rooms=" + rooms
                + " botsPerRoom=" + botsPerRoom + " host=" + host + " port=" + port
                + " idleMs=" + idleMs + " quiet=" + quiet);

        ExecutorService pool = Executors.newFixedThreadPool(botCount);
        List<Future<TestBot.Result>> futures = new ArrayList<>();
        List<String> usernames = new ArrayList<>();
        int botIndex = 1;
        for (int r = 0; r < rooms; r++) {
            AtomicReference<String> roomId = new AtomicReference<>();
            CountDownLatch roomReady = new CountDownLatch(1);
            CountDownLatch guestsJoined = new CountDownLatch(botsPerRoom - 1);
            for (int s = 0; s < botsPerRoom; s++) {
                String username = "test_bot" + botIndex++;
                usernames.add(username);
                TestBot.Role role = s == 0 ? TestBot.Role.HOST : TestBot.Role.GUEST;
                futures.add(pool.submit(new TestBot(username, PASSWORD, host, port, role,
                        roomId, roomReady, guestsJoined, idleMs, quiet)));
            }
        }
        pool.shutdown();

        List<TestBot.Result> results = new ArrayList<>();
        long waitMinutes = Math.max(3L, (idleMs / 60_000L) + 2L);
        try {
            if (!pool.awaitTermination(waitMinutes, TimeUnit.MINUTES)) {
                Gdx.app.error("harness", "bots did not finish within " + waitMinutes + " minutes; cancelling");
                pool.shutdownNow();
            }
            for (int i = 0; i < futures.size(); i++) {
                Future<TestBot.Result> future = futures.get(i);
                String username = usernames.get(i);
                try {
                    results.add(future.get(1, TimeUnit.SECONDS));
                } catch (Exception e) {
                    Gdx.app.error("harness", "bot " + username + " did not return a result", e);
                    results.add(new TestBot.Result());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Gdx.app.error("harness", "interrupted while waiting for bots", e);
            pool.shutdownNow();
        }

        int started = 0;
        int idled = 0;
        int disconnected = 0;
        for (int i = 0; i < results.size(); i++) {
            TestBot.Result r = results.get(i);
            String username = usernames.get(i);
            Gdx.app.log("harness", "bot " + username + " started=" + r.started
                    + " idled=" + r.idled + " disconnected=" + r.disconnected);
            if (r.started) started++;
            if (r.idled) idled++;
            if (r.disconnected) disconnected++;
        }

        Gdx.app.log("harness", started + "/" + botCount + " started, "
                + idled + "/" + botCount + " idled, "
                + disconnected + "/" + botCount + " disconnected");

        int code = idled == botCount ? 0 : 1;
        Gdx.app.log("harness", "exit code=" + code);
        Gdx.app.exit();
        System.exit(code);
    }
}
