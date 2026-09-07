package me.ethanchen.testclient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;

final class TestClientApp extends ApplicationAdapter {
    static final int BOT_COUNT = 40;
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

        Gdx.app.log("harness", "starting " + BOT_COUNT + " bots host=" + host + " port=" + port
                + " usernames=test1..test" + BOT_COUNT + " password=" + PASSWORD);

        ExecutorService pool = Executors.newFixedThreadPool(BOT_COUNT);
        List<Future<TestBot.Result>> futures = new ArrayList<>();
        for (int i = 1; i <= BOT_COUNT; i++) {
            String username = "test_bot" + i;
            futures.add(pool.submit(new TestBot(username, PASSWORD, host, port)));
        }
        pool.shutdown();

        List<TestBot.Result> results = new ArrayList<>();
        try {
            if (!pool.awaitTermination(3, TimeUnit.MINUTES)) {
                Gdx.app.error("harness", "bots did not finish within 3 minutes; cancelling");
                pool.shutdownNow();
            }
            for (int i = 0; i < futures.size(); i++) {
                Future<TestBot.Result> future = futures.get(i);
                String username = "test" + (i + 1);
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
            String username = "test" + (i + 1);
            Gdx.app.log("harness", "bot " + username + " started=" + r.started
                    + " idled=" + r.idled + " disconnected=" + r.disconnected);
            if (r.started) started++;
            if (r.idled) idled++;
            if (r.disconnected) disconnected++;
        }

        Gdx.app.log("harness", started + "/" + BOT_COUNT + " started, "
                + idled + "/" + BOT_COUNT + " idled, "
                + disconnected + "/" + BOT_COUNT + " disconnected");

        int code = idled == BOT_COUNT ? 0 : 1;
        Gdx.app.log("harness", "exit code=" + code);
        Gdx.app.exit();
        System.exit(code);
    }
}
