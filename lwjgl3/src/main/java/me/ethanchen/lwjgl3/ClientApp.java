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
import me.ethanchen.network.packets.c2s.LoadoutRequest;
import me.ethanchen.network.packets.c2s.FusionRequest;
import me.ethanchen.network.packets.c2s.AbilityRequest;
import me.ethanchen.network.packets.other.ConnectFailedPacket;
import me.ethanchen.network.packets.other.ConnectionEstablishedPacket;
import me.ethanchen.network.packets.other.DisconnectPacket;
import me.ethanchen.network.packets.s2c.HostChangedBroadcast;
import me.ethanchen.network.packets.s2c.LobbySettingsBroadcast;
import me.ethanchen.network.packets.s2c.ProfileSyncBroadcast;
import me.ethanchen.network.packets.s2c.ArtifactGrantBroadcast;
import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.server.ServerCore;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class ClientApp extends ApplicationAdapter {
    private static final long RECONNECT_DELAY_MS = 2000;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    // Network
    /** A KryoNet client together with the listener that feeds {@link #rpackets}. */
    private static final class NetClient {
        final Client client;
        final ClientNetworkListener listener;

        NetClient(Client client, ClientNetworkListener listener) {
            this.client = client;
            this.listener = listener;
        }
    }

    // volatile: replaced by the connect thread on every connect attempt and by disconnect(),
    // read from the render thread (sendTCP/sendUDP) every frame.
    private volatile NetClient net;
    private volatile boolean shuttingDown;
    private final AtomicBoolean connectInProgress = new AtomicBoolean(false);
    // Serialises replacement of `net` between the connect thread and disconnect(). Only ever
    // held across cheap operations: connecting and closing both happen outside it, because
    // disconnect() runs on the render thread and must never stall a frame.
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

    // Character and leveling system (session cache, populated by ProfileSyncBroadcast)
    private volatile PlayerProfile profile;
    private volatile boolean profileReadOnly;
    /** Most recent victory-granted artifact, consumed by {@link me.ethanchen.lwjgl3.menuscreens.EndGameScreen}. */
    private volatile Artifact pendingVictoryArtifact;

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
        net = createStartedClient();

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

            // Only a genuinely lost connection (server crash/kick, network drop) reaches here,
            // because clients we shut down on purpose get their listener detached first. Bounce
            // back to the main menu so the player gets feedback instead of a stale screen.
            if (wrapper.packet instanceof DisconnectPacket) {
                if (!(menuScreen instanceof MainMenu)) {
                    switchMenu(new MainMenu(this));
                }
                roomHost = false;
            }

            if (wrapper.packet instanceof HostChangedBroadcast) {
                roomHost = ((HostChangedBroadcast) wrapper.packet).youAreHost;
            }

            if (wrapper.packet instanceof LobbySettingsBroadcast) {
                LobbySettingsBroadcast p = (LobbySettingsBroadcast) wrapper.packet;
                if (p.gamemode != null) {
                    lobbySettings.gamemode = p.gamemode;
                }
                lobbySettings.pveLevelId = p.pveLevelId;
                lobbySettings.pveDifficulty = p.pveDifficulty;
                // PvE rejects multi-local-player counts server-side; force the client roster to 1.
                if (p.gamemode == me.ethanchen.game.GameMode.PVE && getLocalPlayerCount() > 1) {
                    setLocalPlayerMode(me.ethanchen.lwjgl3.input.LocalPlayerMode.KEYBOARD_OR_CONTROLLER);
                    sendLocalPlayerCount();
                }
            }

            if (wrapper.packet instanceof ProfileSyncBroadcast) {
                ProfileSyncBroadcast p = (ProfileSyncBroadcast) wrapper.packet;
                profile = p.profile;
                profileReadOnly = p.readOnly;
                if (profile != null) profile.sortInventory();
            }

            if (wrapper.packet instanceof ArtifactGrantBroadcast) {
                // Optimistic merge: the server already persisted this artifact, so just reflect
                // it locally rather than waiting for a full ProfileSyncBroadcast round trip.
                ArtifactGrantBroadcast g = (ArtifactGrantBroadcast) wrapper.packet;
                if (profile != null && g.artifact != null) {
                    profile.inventory.add(g.artifact);
                    profile.sortInventory();
                }
                // Stash for the end-game popup (grant is sent after EndGameBroadcast).
                if (g.artifact != null) pendingVictoryArtifact = g.artifact;
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

        // Never block the GL/dispose thread on a KryoNet close (see retireClient) — that froze
        // the window on quit. Retire on a daemon thread so the window can exit immediately.
        retireClient(net);

        batch.dispose();
        font.dispose();
        shapes.dispose();
        BoardRenderer.disposeInstance();
        AudioManager.getInstance().dispose();
    }

    private NetClient createStartedClient() {
        Client client = NetEndpoints.createClient();
        // Close it before it has an update thread. KryoNet's connect() begins by closing, and
        // that close calls selector.selectNow(), which has to take the selector's monitor —
        // held for 250ms at a time by the client's own update thread sitting in select(). The
        // monitor is unfair, so connect() can lose that race over and over: measured here it
        // stalled connect by up to 26 seconds, which is the "sat on the menu and sometimes
        // never connected" symptom. Closing now, while no update thread exists yet, is
        // instant and makes the close inside connect() a no-op. It stays a no-op because a
        // client with no channels registered never gets a non-empty select.
        client.close();
        ClientNetworkListener listener = new ClientNetworkListener(rpackets);
        client.addListener(listener);
        client.start();
        return new NetClient(client, listener);
    }

    /**
     * Shuts a client down on a throwaway thread. Closing a live connection competes with the
     * client's own update thread for the selector monitor (see createStartedClient) and has
     * been measured taking tens of seconds, so nothing the player is waiting on may ever call
     * it directly — including quit, which it used to freeze. The listener is
     * detached first so a client we retired on purpose cannot report its own shutdown as a
     * lost connection or hand a doomed connection to the current screen.
     */
    private void retireClient(NetClient retired) {
        if (retired == null) return;
        retired.listener.detach();
        Thread t = new Thread(() -> {
            retired.client.close();
            retired.client.stop();
            try {
                // Let the update thread notice the shutdown before the selector it is
                // selecting on is closed underneath it.
                Thread updateThread = retired.client.getUpdateThread();
                if (updateThread != null) updateThread.join(2000);
                retired.client.dispose();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
            }
        }, "net-retire");
        t.setDaemon(true);
        t.start();
    }

    /** Swaps in a fresh idle client, retiring whatever was there. Never blocks. */
    private void replaceClientWithIdle() {
        netLock.lock();
        try {
            NetClient previous = net;
            net = createStartedClient();
            retireClient(previous);
        } finally {
            netLock.unlock();
        }
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
        if (shuttingDown) return;
        // Nothing to drop: keep the idle client rather than churning through a new one.
        if (!net.client.isConnected() && !connectInProgress.get()) return;
        replaceClientWithIdle();
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

    // Sends take no lock: they only touch the client that is current at this instant, and a
    // client is never closed while it is the current one (see retireClient). Locking here used
    // to mean a send could be dropped just because a connect was in flight — including the
    // JoinRequest that a screen fires the moment it is told the connection is up.
    public boolean sendTCP(NetworkPacket packet) {
        if (shuttingDown || packet == null) return false;
        Client client = net.client;
        if (!client.isConnected()) return false;
        return client.sendTCP(packet) != -1;
    }

    public boolean sendUDP(NetworkPacket packet) {
        if (shuttingDown || packet == null) return false;
        Client client = net.client;
        if (!client.isConnected()) return false;
        return client.sendUDP(packet) != -1;
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
            if (isAlreadyConnectedTo(req, resolvedHost)) return;

            // Always connect on a brand new client rather than reusing the current one.
            // KryoNet's connect() begins by closing the existing connection, and that close
            // fights the client's own update thread for an internal lock: measured locally it
            // stalled connect() by 3-13 seconds, which is the "stuck on the menu, sometimes
            // never connects" symptom. A fresh client has nothing to close and connects in
            // about a millisecond, and the old one is retired in the background.
            NetClient fresh = createStartedClient();
            netLock.lock();
            try {
                if (shuttingDown || isStale(req)) {
                    retireClient(fresh);
                    return;
                }
                // Publish before connecting so that the ConnectionEstablishedPacket raised
                // during connect() already refers to the current client.
                NetClient previous = net;
                net = fresh;
                retireClient(previous);
            } finally {
                netLock.unlock();
            }

            int timeout = req.auto
                    ? NetConfig.AUTO_CONNECT_TIMEOUT_MS
                    : NetConfig.CONNECT_TIMEOUT_MS;
            try {
                fresh.client.connect(timeout, resolvedHost, req.port, req.port);
            } catch (IOException e) {
                discardIfCurrent(fresh);
                throw e;
            }
            // connect() blocks for up to the timeout; by the time it returns the player may
            // have quit, left the screen, or asked for a different server. Nobody wants this
            // connection any more, and keeping it would feed a stale
            // ConnectionEstablishedPacket to whatever screen is now open.
            if (shuttingDown || isStale(req)) {
                discardIfCurrent(fresh);
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
     * True if the current client is already connected where {@code req} wants to go, in which
     * case the session is kept (e.g. re-entering a menu that expects a fresh
     * ConnectionEstablishedPacket). The packet is re-posted anyway so the screen doesn't sit
     * on "Connecting..." forever waiting for something that will never come.
     */
    private boolean isAlreadyConnectedTo(ConnectRequest req, String resolvedHost) {
        Client client = net.client;
        if (!client.isConnected()) return false;
        InetSocketAddress remote = client.getRemoteAddressTCP();
        if (remote == null
                || !remote.getAddress().getHostAddress().equals(resolvedHost)
                || remote.getPort() != req.port) {
            return false;
        }
        Gdx.app.postRunnable(() -> rpackets.addLast(
                new ClientPacketWrapper(new ConnectionEstablishedPacket(), client)));
        return true;
    }

    /** Retires {@code doomed}, leaving a fresh idle client behind if it was still current. */
    private void discardIfCurrent(NetClient doomed) {
        netLock.lock();
        try {
            if (net == doomed && !shuttingDown) {
                net = createStartedClient();
            }
        } finally {
            netLock.unlock();
        }
        retireClient(doomed);
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

    public PlayerProfile getProfile() {
        return profile;
    }

    public boolean isProfileReadOnly() {
        return profileReadOnly;
    }

    /**
     * Returns and clears the artifact from the latest {@link ArtifactGrantBroadcast}, if any.
     * Used by the victory screen popup.
     */
    public Artifact consumePendingVictoryArtifact() {
        Artifact a = pendingVictoryArtifact;
        pendingVictoryArtifact = null;
        return a;
    }

    /** Requests a character/artifact loadout change; the server echoes back a {@code ProfileSyncBroadcast}. */
    public boolean sendLoadoutRequest(int characterId, String artifactIdA, String artifactIdB) {
        LoadoutRequest req = new LoadoutRequest();
        req.characterId = characterId;
        req.artifactIdA = artifactIdA;
        req.artifactIdB = artifactIdB;
        return sendTCP(req);
    }

    /** Requests fusing exactly 5 owned artifacts; the server replies with {@code FusionResultBroadcast}. */
    public boolean sendFusionRequest(String[] artifactIds) {
        FusionRequest req = new FusionRequest();
        req.artifactIds = artifactIds;
        return sendTCP(req);
    }

    /** Requests activation of {@code localIndex}'s character ability; a no-op server-side if the meter isn't full. */
    public boolean sendAbilityRequest(byte localIndex) {
        AbilityRequest req = new AbilityRequest();
        req.localIndex = localIndex;
        return sendTCP(req);
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
