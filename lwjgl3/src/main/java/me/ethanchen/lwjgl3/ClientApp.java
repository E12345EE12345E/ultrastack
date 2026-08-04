package me.ethanchen.lwjgl3;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.utils.Queue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Client;

import me.ethanchen.lwjgl3.input.ControllerRoster;
import me.ethanchen.lwjgl3.input.LocalPlayerMode;
import me.ethanchen.lwjgl3.input.LocalPlayerRoster;
import me.ethanchen.lwjgl3.menuscreens.MainMenu;
import me.ethanchen.lwjgl3.menuscreens.MenuScreen;
import me.ethanchen.lwjgl3.menuscreens.ShaderTestScreen;

import me.ethanchen.lwjgl3.music.AudioManager;
import me.ethanchen.lwjgl3.music.MusicContainer;
import me.ethanchen.lwjgl3.music.MusicTag;
import me.ethanchen.lwjgl3.render.PieceTints;
import me.ethanchen.lwjgl3.settings.GameSettings;
import me.ethanchen.lwjgl3.settings.LobbySettings;
import me.ethanchen.lwjgl3.settings.SettingsManager;
import me.ethanchen.lwjgl3.render.BoardRenderer;
import me.ethanchen.network.ClientNetworkListener;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.NetConfig;
import me.ethanchen.network.NetEndpoints;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.CreateRoomRequest;
import me.ethanchen.network.packets.c2s.JoinRequest;
import me.ethanchen.network.packets.c2s.JoinRoomRequest;
import me.ethanchen.network.packets.c2s.LeaveRoomRequest;
import me.ethanchen.network.packets.c2s.LocalPlayerCountRequest;
import me.ethanchen.network.packets.c2s.LoginRequest;
import me.ethanchen.network.packets.c2s.RegisterRequest;
import me.ethanchen.network.packets.c2s.RoomListRequest;
import me.ethanchen.network.packets.other.ConnectFailedPacket;
import me.ethanchen.network.packets.other.ConnectionEstablishedPacket;
import me.ethanchen.network.packets.other.DisconnectPacket;
import me.ethanchen.network.packets.s2c.HostChangedBroadcast;
import me.ethanchen.server.ServerCore;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class ClientApp extends ApplicationAdapter {
    private static final long RECONNECT_DELAY_MS = 2000;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    // Network
    // volatile: reassigned by ensureClientAlive() on the connect thread when the KryoNet
    // update thread has died and needs recreating; read from the render thread (sendTCP/
    // sendUDP/disconnect) and other background threads.
    private volatile Client netClient;
    private ClientNetworkListener clientNetworkListener;
    private volatile boolean shuttingDown;
    private volatile boolean intentionalDisconnect;
    private final AtomicBoolean connectInProgress = new AtomicBoolean(false);
    // Guards netClient.connect()/close()/sendTCP()/sendUDP() against each other. connect() and
    // close() run on their own background threads (see runConnectAttempt/disconnect/dispose)
    // while sendTCP/sendUDP are called from the render thread every frame, so without this lock
    // a close() could race a concurrent send. Note KryoNet's connect() already calls close()
    // internally and the Client's update thread (started once via start() in create()) survives
    // close(), so no separate start()/recreate step is needed to reconnect.
    private final ReentrantLock netLock = new ReentrantLock();
    /**
     * Bumped by every user-initiated connect and by {@link #disconnect()}. A connect attempt
     * carries the epoch it was created with, so an attempt that is still resolving DNS or
     * blocked inside KryoNet's connect() can tell that its result is no longer wanted (the
     * player changed destination or left the screen) and drop the connection instead of
     * handing it to whatever screen happens to be open when it finally completes.
     */
    private final AtomicInteger connectEpoch = new AtomicInteger();
    /** Next attempt to run; replaced (not dropped) when a newer request arrives. */
    private final AtomicReference<ConnectRequest> queuedConnect = new AtomicReference<>();
    private volatile int reconnectAttempts;
    private Queue<ClientPacketWrapper> rpackets;
    private volatile String connectIP;
    private volatile int connectPort;

    // Embedded LAN server
    private ServerCore lanServer;
    private boolean lanMode;

    // Rendering
    private SpriteBatch batch;
    private BitmapFont font;
    private ShapeRenderer shapes;

    // Logic
    private MenuScreen menuScreen;
    private volatile MenuScreen switchToMenu;
    private GameSettings settings;
    private final LobbySettings lobbySettings = new LobbySettings();

    // Session-only local-player input mode (not persisted)
    private final ControllerRoster controllerRoster = new ControllerRoster();
    private LocalPlayerMode localPlayerMode = LocalPlayerMode.KEYBOARD_OR_CONTROLLER;
    /** Whether this client is currently the host of its room (session-only). */
    private boolean roomHost;

    @Override
    public void create() {
        settings = SettingsManager.load();
        Controllers.addListener(controllerRoster);
        controllerRoster.seedFromConnected();
        PieceTints.applyColorOffsets(settings.colors);
        AudioManager.getInstance().setVolumeSettings(settings.volume);
        AudioManager.getInstance().registerMusic(new MusicContainer(
            "music/mrethantetris_start.wav",
            new String[]{"music/mrethantetris_loop.wav", "music/mrethantetris_loop2.wav"},
            new MusicTag[]{MusicTag.MULTIPLAYER_GAME}
        ));
        rpackets = new Queue<ClientPacketWrapper>();
        reconnectAttempts = 0;
        this.connectIP = NetConfig.HOST;
        this.connectPort = NetConfig.PORT;
        netClient = NetEndpoints.createClient();
        clientNetworkListener = new ClientNetworkListener(this.rpackets);
        netClient.addListener(clientNetworkListener);
        netClient.start();

        //tryConnect();

        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        
        com.badlogic.gdx.files.FileHandle fontFile = Gdx.files.absolute("C:/Windows/Fonts/arial.ttf");
        if (fontFile.exists()) {
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(fontFile);
            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            parameter.size = 48;
            parameter.minFilter = Texture.TextureFilter.Linear;
            parameter.magFilter = Texture.TextureFilter.Linear;
            font = generator.generateFont(parameter);
            generator.dispose();
        } else {
            font = new BitmapFont();
            font.getRegion().getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        font.setUseIntegerPositions(false);

        menuScreen = new MainMenu(this);
        //menuScreen = new ShaderTestScreen(this);
    }


    private void update() {
        if (menuScreen != null) menuScreen.update();
        while (rpackets.notEmpty()) {
            ClientPacketWrapper wrapper = rpackets.removeFirst();

            // Unexpected disconnects (server crash/kick, network drop) bounce back to the main
            // menu so the player gets feedback instead of a stuck/stale screen. Disconnects we
            // initiated ourselves (see disconnect()) are expected: the current screen already
            // knows what to do next, so we don't override its navigation.
            if (wrapper.packet instanceof DisconnectPacket) {
                if (!intentionalDisconnect && !(menuScreen instanceof MainMenu)) {
                    switchMenu(new MainMenu(this));
                }
                intentionalDisconnect = false;
                roomHost = false;
            }

            if (wrapper.packet instanceof HostChangedBroadcast) {
                roomHost = ((HostChangedBroadcast) wrapper.packet).youAreHost;
            }

            menuScreen.passClientPacket(wrapper);
        }
    }

    @Override
    public void render() {
        if (Gdx.graphics.getBackBufferWidth() <= 0 || Gdx.graphics.getBackBufferHeight() <= 0) {
            return;
        }
        if (switchToMenu != null) {
            if (menuScreen != null) menuScreen.dispose();
            menuScreen = switchToMenu;
            switchToMenu = null;
        }
        update();
        ScreenUtils.clear(0, 0, 0, 1f);
        if (menuScreen != null) menuScreen.render();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (batch != null) {
            batch.setProjectionMatrix(batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height));
        }
        if (shapes != null) {
            shapes.setProjectionMatrix(shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height));
        }
    }

    @Override
    public void dispose() {
        shuttingDown = true;

        // Screen cleanup must run on quit too (render() only disposes on screen switch).
        if (menuScreen != null) {
            menuScreen.dispose();
            menuScreen = null;
        }
        if (switchToMenu != null) {
            switchToMenu.dispose();
            switchToMenu = null;
        }

        stopLanServer();

        Controllers.removeListener(controllerRoster);

        // Never block the GL/dispose thread on netLock or KryoNet close — connect can hold
        // the lock for the full connect timeout (and longer), which froze the window on quit.
        // Match disconnect(): close on a daemon thread so the window can exit immediately.
        closeNetClientAsync();

        batch.dispose();
        font.dispose();
        shapes.dispose();
        BoardRenderer.disposeInstance();
        AudioManager.getInstance().dispose();
    }

    /** Close the KryoNet client without blocking the caller (dispose / UI thread). */
    private void closeNetClientAsync() {
        if (netClient == null) return;
        Thread t = new Thread(() -> {
            netLock.lock();
            try {
                netClient.close();
            } finally {
                netLock.unlock();
            }
        }, "net-dispose-close");
        t.setDaemon(true);
        t.start();
    }

    // -------------------------------------------------------------------------
    // Screen management
    // -------------------------------------------------------------------------

    public void switchMenu(MenuScreen newMenu) {
        this.switchToMenu = newMenu; // switches to menu on next render() tick
        // Each MenuScreen sets itself as the input processor in its constructor, but that
        // only fires once. Re-assert it here so reusing an already-constructed screen (e.g.
        // navigating back to a retained lobby chat instance from LobbySettingsScreen) still
        // correctly regains input focus.
        Gdx.input.setInputProcessor(newMenu);
    }

    // -------------------------------------------------------------------------
    // Connection helpers
    // -------------------------------------------------------------------------

    /**
     * Drops the connection and cancels any connect attempt that has not finished yet. Safe to
     * call when nothing is connected: leaving a menu mid-connect must not leave a connect
     * running that would later report success to an unrelated screen.
     */
    public void disconnect() {
        connectEpoch.incrementAndGet();
        queuedConnect.set(null);
        boolean connectPending = connectInProgress.get();
        if (netClient == null || (!netClient.isConnected() && !connectPending)) return;
        intentionalDisconnect = true;
        closeNetClientAsync();
    }

    public void setConnectDestination(String newIP, int newPort) {
        this.connectIP = newIP;
        this.connectPort = newPort;
    }

    public boolean validPort(int test) {
        if (test <= 0 || test >= 65536) {
            return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Mode
    // -------------------------------------------------------------------------

    public void setLanMode(boolean lan) {
        this.lanMode = lan;
    }

    public boolean isLanMode() {
        return lanMode;
    }

    // -------------------------------------------------------------------------
    // Embedded LAN server
    // -------------------------------------------------------------------------

    public void startLanServer(int port, long joinCode) {
        if (lanServer != null) stopLanServer();
        lanServer = new ServerCore(joinCode, 4);
        try {
            lanServer.start(port);
        } catch (IOException e) {
            System.err.println("[ClientApp] Failed to start LAN server: " + e.getMessage());
            lanServer = null;
        }
    }

    public void stopLanServer() {
        if (lanServer != null) {
            lanServer.stop();
            lanServer = null;
        }
    }

    public boolean isLanServerRunning() {
        return lanServer != null;
    }

    // -------------------------------------------------------------------------
    // Packet send helpers
    // -------------------------------------------------------------------------

    public boolean sendTCP(NetworkPacket packet) {
        if (shuttingDown || packet == null) return false;
        // Never block the caller (typically the render thread): if a connect/close/dispose is
        // in flight, just drop this send rather than waiting for it to finish.
        if (!netLock.tryLock()) return false;
        try {
            if (!netClient.isConnected()) return false;
            return netClient.sendTCP(packet) != -1;
        } finally {
            netLock.unlock();
        }
    }

    public boolean sendUDP(NetworkPacket packet) {
        if (shuttingDown || packet == null) return false;
        if (!netLock.tryLock()) return false;
        try {
            if (!netClient.isConnected()) return false;
            return netClient.sendUDP(packet) != -1;
        } finally {
            netLock.unlock();
        }
    }

    public boolean sendJoinRequest(String username, long credential) {
        JoinRequest req = new JoinRequest();
        req.playerName = username;
        req.credential = credential;
        req.localPlayers = (byte) getLocalPlayerCount();
        return sendTCP(req);
    }

    public boolean sendLoginRequest(String username, String passcode) {
        LoginRequest req = new LoginRequest();
        req.username = username;
        req.passcode = passcode;
        return sendTCP(req);
    }

    public boolean sendRegisterRequest(String username, String passcode) {
        RegisterRequest req = new RegisterRequest();
        req.username = username;
        req.passcode = passcode;
        return sendTCP(req);
    }

    public boolean sendRoomListRequest() {
        return sendTCP(new RoomListRequest());
    }

    public boolean sendCreateRoomRequest() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.localPlayers = (byte) getLocalPlayerCount();
        return sendTCP(req);
    }

    public boolean sendJoinRoomRequest(String roomId) {
        JoinRoomRequest req = new JoinRoomRequest();
        req.roomId = roomId;
        req.localPlayers = (byte) getLocalPlayerCount();
        return sendTCP(req);
    }

    public boolean sendLeaveRoomRequest() {
        return sendTCP(new LeaveRoomRequest());
    }

    public boolean sendLocalPlayerCount() {
        LocalPlayerCountRequest req = new LocalPlayerCountRequest();
        req.count = (byte) getLocalPlayerCount();
        return sendTCP(req);
    }

    // -------------------------------------------------------------------------
    // Connect / reconnect
    // -------------------------------------------------------------------------

    /** One connect attempt, pinned to the destination and epoch it was requested with. */
    private static final class ConnectRequest {
        final String host;
        final int port;
        final boolean auto;
        final long delayMs;
        final int epoch;

        ConnectRequest(String host, int port, boolean auto, long delayMs, int epoch) {
            this.host = host;
            this.port = port;
            this.auto = auto;
            this.delayMs = delayMs;
            this.epoch = epoch;
        }
    }

    // thread-safe
    public void tryConnect() {
        enqueueConnect(false);
    }

    /** Connect with a short timeout; posts {@link ConnectFailedPacket} on failure. */
    public void tryConnectAuto() {
        enqueueConnect(true);
    }

    private void enqueueConnect(boolean auto) {
        if (shuttingDown) return;
        reconnectAttempts = 0;
        // A new request supersedes anything still in flight, and replaces anything queued.
        queuedConnect.set(new ConnectRequest(connectIP, connectPort, auto, 0,
                connectEpoch.incrementAndGet()));
        startConnectWorker();
    }

    /**
     * Starts the worker unless one is already running. A running worker drains
     * {@link #queuedConnect} when its current attempt finishes, so requests made while it is
     * busy are honoured rather than silently dropped.
     */
    private void startConnectWorker() {
        if (!connectInProgress.compareAndSet(false, true)) return;
        Thread connectThread = new Thread(this::runConnectWorker, "net-connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    private void runConnectWorker() {
        while (true) {
            ConnectRequest req = queuedConnect.getAndSet(null);
            if (req == null) {
                connectInProgress.set(false);
                // A request enqueued just before the flag was cleared saw the worker as busy
                // and skipped starting one, so pick it up here instead of losing it.
                if (queuedConnect.get() == null || !connectInProgress.compareAndSet(false, true)) {
                    return;
                }
                continue;
            }
            runConnectAttempt(req);
        }
    }

    /** True once a newer connect request or a disconnect has superseded this attempt. */
    private boolean isStale(ConnectRequest req) {
        return req.epoch != connectEpoch.get();
    }

    private void postConnectFailed(String reason) {
        Gdx.app.postRunnable(() ->
            rpackets.addLast(new ClientPacketWrapper(new ConnectFailedPacket(reason), null)));
    }

    private void runConnectAttempt(ConnectRequest req) {
        boolean shouldReconnect = false;
        try {
            if (req.delayMs > 0) Thread.sleep(req.delayMs);
            if (shuttingDown || isStale(req)) return;
            String resolvedHost;
            try {
                resolvedHost = resolveHost(req.host);
                System.out.println("[ClientApp] Resolved " + req.host + " -> " + resolvedHost
                        + ":" + req.port);
            } catch (UnknownHostException e) {
                System.err.println("[ClientApp] DNS resolution failed for " + req.host + ": "
                        + e.getMessage());
                if (req.auto) {
                    postConnectFailed("Could not resolve " + req.host);
                }
                return;
            }
            netLock.lock();
            try {
                if (shuttingDown || isStale(req)) return;

                // KryoNet's Client update thread can die permanently (e.g. an uncaught
                // KryoNetException from a bad/oversized packet) without anyone noticing.
                // Once that happens nothing services the selector anymore, so a later
                // connect() call just hangs forever with no error ("Connecting..." stuck
                // indefinitely). Detect that and recreate the client before connecting.
                ensureClientAlive();

                if (netClient.isConnected()) {
                    InetSocketAddress remote = netClient.getRemoteAddressTCP();
                    boolean sameDestination = remote != null
                            && remote.getAddress().getHostAddress().equals(resolvedHost)
                            && remote.getPort() == req.port;
                    if (sameDestination) {
                        // Already connected to the requested destination (e.g. re-entering a
                        // menu that expects a fresh ConnectionEstablishedPacket). Don't
                        // reconnect, but still notify the current screen so it doesn't sit on
                        // "Connecting..." forever waiting for a packet that will never come.
                        Client connected = netClient;
                        Gdx.app.postRunnable(() -> rpackets.addLast(
                                new ClientPacketWrapper(new ConnectionEstablishedPacket(), connected)));
                        return;
                    }
                    // Connected elsewhere (e.g. switched from the online server to a LAN
                    // server, or vice versa): fall through and reconnect to the new
                    // destination. Client#connect() closes the old connection first — that
                    // drop is ours, so don't let update() treat it as a lost connection.
                    intentionalDisconnect = true;
                }

                int timeout = req.auto
                        ? NetConfig.AUTO_CONNECT_TIMEOUT_MS
                        : NetConfig.CONNECT_TIMEOUT_MS;
                netClient.connect(timeout, resolvedHost, req.port, req.port);
                // connect() blocks for seconds; by the time it returns the player may have
                // quit, left the screen, or asked for a different server. Nobody wants this
                // connection any more, and keeping it would feed a stale
                // ConnectionEstablishedPacket to whatever screen is now open.
                if (shuttingDown || isStale(req)) {
                    intentionalDisconnect = true;
                    netClient.close();
                }
            } finally {
                netLock.unlock();
            }
        } catch (IOException e) {
            System.err.println("Connect failed: " + e.getMessage());
            if (req.auto) {
                postConnectFailed(e.getMessage());
            } else {
                shouldReconnect = true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (shouldReconnect) scheduleReconnect(req);
    }

    /**
     * Recreates {@link #netClient} if its background update thread has died (see caller for
     * why that can happen). Must be called while holding {@link #netLock}.
     */
    private void ensureClientAlive() {
        Thread updateThread = netClient.getUpdateThread();
        if (updateThread != null && updateThread.isAlive()) return;
        System.err.println("[ClientApp] Net client update thread is dead; recreating client.");
        Client dead = netClient;
        try {
            dead.close();
        } catch (Exception ignored) {
        }
        Client fresh = NetEndpoints.createClient();
        fresh.addListener(clientNetworkListener);
        fresh.start();
        netClient = fresh;
    }

    private static String resolveHost(String host) throws UnknownHostException {
        return InetAddress.getByName(host.trim()).getHostAddress();
    }

    private void scheduleReconnect(ConnectRequest failed) {
        if (shuttingDown || isStale(failed)) return;
        reconnectAttempts++;
        if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) return;
        ConnectRequest retry = new ConnectRequest(failed.host, failed.port, failed.auto,
                RECONNECT_DELAY_MS, failed.epoch);
        // Only retry if the player hasn't already asked for something newer.
        queuedConnect.compareAndSet(null, retry);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public GameSettings getSettings() {
        return settings;
    }

    public LobbySettings getLobbySettings() {
        return lobbySettings;
    }

    public ControllerRoster getControllerRoster() {
        return controllerRoster;
    }

    public LocalPlayerMode getLocalPlayerMode() {
        return localPlayerMode;
    }

    public void setLocalPlayerMode(LocalPlayerMode mode) {
        this.localPlayerMode = mode != null ? mode : LocalPlayerMode.KEYBOARD_OR_CONTROLLER;
    }

    public LocalPlayerRoster computeLocalPlayerRoster() {
        return LocalPlayerRoster.compute(localPlayerMode, controllerRoster);
    }

    public int getLocalPlayerCount() {
        return computeLocalPlayerRoster().size();
    }

    public boolean isRoomHost() {
        return roomHost;
    }

    public void setRoomHost(boolean host) {
        this.roomHost = host;
    }

    public SpriteBatch getSprites() {
        return this.batch;
    }

    public ShapeRenderer getShapes() {
        return this.shapes;
    }

    public BitmapFont getFont() {
        return this.font;
    }
}
