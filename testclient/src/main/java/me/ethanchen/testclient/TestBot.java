package me.ethanchen.testclient;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;

import me.ethanchen.game.GameMode;
import me.ethanchen.network.NetConfig;
import me.ethanchen.network.NetEndpoints;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.CreateRoomRequest;
import me.ethanchen.network.packets.c2s.LeaveRoomRequest;
import me.ethanchen.network.packets.c2s.LoginRequest;
import me.ethanchen.network.packets.c2s.RegisterRequest;
import me.ethanchen.network.packets.c2s.StartGameRequest;
import me.ethanchen.network.packets.s2c.AuthResponse;
import me.ethanchen.network.packets.s2c.LightGameStateBroadcast;
import me.ethanchen.network.packets.s2c.RoomJoinResponse;
import me.ethanchen.network.packets.s2c.StartGameBroadcast;

final class TestBot implements Callable<TestBot.Result> {
    static final long STEP_TIMEOUT_MS = 10_000;
    static final long IDLE_MS = 30_000;
    static final long IDLE_HEARTBEAT_MS = 5_000;

    private static final String USERNAME_TAKEN = "username already taken";

    final String username;
    private final String passcode;
    private final String host;
    private final int port;

    private final BlockingQueue<AuthResponse> authQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<RoomJoinResponse> joinQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<StartGameBroadcast> startQueue = new LinkedBlockingQueue<>();

    private com.esotericsoftware.kryonet.Client client;

    TestBot(String username, String passcode, String host, int port) {
        this.username = username;
        this.passcode = passcode;
        this.host = host;
        this.port = port;
    }

    static final class Result {
        boolean started;
        boolean idled;
        boolean disconnected;
    }

    @Override
    public Result call() {
        Thread.currentThread().setName("testbot-" + username);
        Result result = new Result();
        try {
            runSequence(result);
        } catch (Exception e) {
            error("bot failed", e);
        } finally {
            shutdown(result);
        }
        log("final status started=" + result.started + " idled=" + result.idled
                + " disconnected=" + result.disconnected);
        return result;
    }

    private void runSequence(Result result) throws Exception {
        phase("connecting");
        client = NetEndpoints.createClient();
        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                log("connected connectionId=" + connection.getID()
                        + " remoteTcp=" + connection.getRemoteAddressTCP()
                        + " remoteUdp=" + connection.getRemoteAddressUDP());
            }

            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof FrameworkMessage) return;
                if (object instanceof LightGameStateBroadcast) return;
                log("RECV " + PacketSummarizer.summarize(object));
                if (object instanceof AuthResponse) {
                    authQueue.offer((AuthResponse) object);
                } else if (object instanceof RoomJoinResponse) {
                    joinQueue.offer((RoomJoinResponse) object);
                } else if (object instanceof StartGameBroadcast) {
                    startQueue.offer((StartGameBroadcast) object);
                }
            }

            @Override
            public void disconnected(Connection connection) {
                int id = connection == null ? -1 : connection.getID();
                log("disconnected connectionId=" + id);
            }
        });
        client.start();

        int timeout = NetConfig.CONNECT_TIMEOUT_MS;
        log("connect attempt host=" + host + " tcp=" + port + " udp=" + port
                + " timeoutMs=" + timeout);
        try {
            client.connect(timeout, host, port, port);
        } catch (IOException e) {
            error("connect failed host=" + host + " port=" + port, e);
            throw e;
        }
        log("connect returned ok isConnected=" + client.isConnected());

        phase("registering");
        RegisterRequest register = new RegisterRequest();
        register.username = username;
        register.passcode = passcode;
        sendTcp(register);

        AuthResponse auth = waitFor("AuthResponse", authQueue);
        if (!auth.success && auth.reason != null && auth.reason.contains(USERNAME_TAKEN)) {
            phase("logging in");
            LoginRequest login = new LoginRequest();
            login.username = username;
            login.passcode = passcode;
            sendTcp(login);
            auth = waitFor("AuthResponse", authQueue);
        }
        if (!auth.success) {
            throw new IllegalStateException("auth failed for " + username + ": " + auth.reason);
        }
        log("auth ok accountUuid=" + auth.accountUuid);

        phase("creating room");
        CreateRoomRequest create = new CreateRoomRequest();
        create.localPlayers = 1;
        sendTcp(create);
        RoomJoinResponse join = waitFor("RoomJoinResponse", joinQueue);
        if (!join.success) {
            throw new IllegalStateException("create room failed: " + join.reason);
        }
        log("room created roomId=" + join.roomId + " isHost=" + join.isHost);

        phase("starting game");
        StartGameRequest start = new StartGameRequest();
        start.gamemode = GameMode.MULTIPLAYER_SCORE;
        sendTcp(start);
        StartGameBroadcast started = waitFor("StartGameBroadcast", startQueue);
        result.started = true;
        log("game started mode=" + started.mode + " msUntilStart=" + started.msUntilStart
                + " totalPlayers=" + (started.totalPlayers & 0xFF));

        phase("idling");
        idle();
        result.idled = true;
        log("idle complete");
    }

    private void idle() throws InterruptedException {
        long start = System.currentTimeMillis();
        long nextHeartbeat = IDLE_HEARTBEAT_MS;
        while (true) {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= IDLE_MS) break;
            Thread.sleep(Math.min(250L, IDLE_MS - elapsed));
            elapsed = System.currentTimeMillis() - start;
            while (elapsed >= nextHeartbeat && nextHeartbeat <= IDLE_MS) {
                log("idle " + (nextHeartbeat / 1000) + "/" + (IDLE_MS / 1000) + "s");
                nextHeartbeat += IDLE_HEARTBEAT_MS;
            }
        }
    }

    private void shutdown(Result result) {
        try {
            if (client != null && client.isConnected()) {
                phase("leaving");
                sendTcp(new LeaveRoomRequest());
            }
        } catch (Exception e) {
            error("leave failed", e);
        }
        try {
            if (client != null) {
                client.stop();
                result.disconnected = true;
            }
        } catch (Exception e) {
            error("client.stop failed", e);
        }
        phase("stopped");
    }

    private void sendTcp(NetworkPacket packet) {
        log("SEND TCP " + PacketSummarizer.summarize(packet));
        client.sendTCP(packet);
    }

    private <T> T waitFor(String label, BlockingQueue<T> queue) throws InterruptedException {
        log("waiting for " + label + " timeoutMs=" + STEP_TIMEOUT_MS);
        T value = queue.poll(STEP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (value == null) {
            throw new IllegalStateException("timed out waiting for " + label + " after "
                    + STEP_TIMEOUT_MS + "ms");
        }
        return value;
    }

    private void phase(String name) {
        log("phase " + name);
    }

    private void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.log(username, message);
        } else {
            System.out.println("[" + TimestampedLogger.now() + "] [" + username + "] " + message);
        }
    }

    private void error(String message, Throwable t) {
        if (Gdx.app != null) {
            Gdx.app.error(username, message, t);
        } else {
            System.err.println("[" + TimestampedLogger.now() + "] [" + username + "] " + message);
            t.printStackTrace(System.err);
        }
    }
}
