package me.ethanchen.lwjgl3;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.utils.Queue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Client;

import me.ethanchen.game.GameMode;
import me.ethanchen.lwjgl3.menuscreens.MainMenu;
import me.ethanchen.lwjgl3.menuscreens.MenuScreen;
import me.ethanchen.lwjgl3.music.AudioManager;
import me.ethanchen.lwjgl3.music.MusicContainer;
import me.ethanchen.lwjgl3.music.MusicTag;
import me.ethanchen.lwjgl3.render.PieceTints;
import me.ethanchen.lwjgl3.settings.GameSettings;
import me.ethanchen.lwjgl3.settings.SettingsManager;
import me.ethanchen.network.ClientNetworkListener;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.NetConfig;
import me.ethanchen.network.NetEndpoints;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.CreateRoomRequest;
import me.ethanchen.network.packets.c2s.JoinRequest;
import me.ethanchen.network.packets.c2s.JoinRoomRequest;
import me.ethanchen.network.packets.c2s.LeaveRoomRequest;
import me.ethanchen.network.packets.c2s.LoginRequest;
import me.ethanchen.network.packets.c2s.RegisterRequest;
import me.ethanchen.network.packets.c2s.RoomListRequest;
import me.ethanchen.server.ServerCore;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class ClientApp extends ApplicationAdapter {
    private static final long RECONNECT_DELAY_MS = 2000;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    // Network
    private Client netClient;
    private ClientNetworkListener clientNetworkListener;
    private volatile boolean shuttingDown;
    private final AtomicBoolean connectInProgress = new AtomicBoolean(false);
    private int reconnectAttempts;
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

    @Override
    public void create() {
        settings = SettingsManager.load();
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
    }

    private void update() {
        if (menuScreen != null) menuScreen.update();
        while (rpackets.notEmpty()) {
            ClientPacketWrapper wrapper = rpackets.removeFirst();
            
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
        stopLanServer();
        netClient.close();
        batch.dispose();
        font.dispose();
        shapes.dispose();
        AudioManager.getInstance().dispose();
    }

    // -------------------------------------------------------------------------
    // Screen management
    // -------------------------------------------------------------------------

    public void switchMenu(MenuScreen newMenu) {
        this.switchToMenu = newMenu; // switches to menu on next render() tick
    }

    // -------------------------------------------------------------------------
    // Connection helpers
    // -------------------------------------------------------------------------

    public void disconnect() {
        if (netClient == null) return;
        Thread t = new Thread(() -> netClient.close(), "net-disconnect");
        t.setDaemon(true);
        t.start();
    }

    public void setConnectDestination(String newIP, int newPort) {
        this.connectIP = newIP;
        this.connectPort = newPort;
    }

    public boolean validIP(String test) {
        return true;
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
        if (shuttingDown || packet == null || !netClient.isConnected()) return false;
        return netClient.sendTCP(packet) != -1;
    }

    public boolean sendUDP(NetworkPacket packet) {
        if (shuttingDown || packet == null || !netClient.isConnected()) return false;
        return netClient.sendUDP(packet) != -1;
    }

    public boolean sendJoinRequest(String username, long credential) {
        JoinRequest req = new JoinRequest();
        req.playerName = username;
        req.credential = credential;
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

    public boolean sendCreateRoomRequest(GameMode gamemode) {
        CreateRoomRequest req = new CreateRoomRequest();
        req.gamemode = gamemode;
        return sendTCP(req);
    }

    public boolean sendJoinRoomRequest(String roomId) {
        JoinRoomRequest req = new JoinRoomRequest();
        req.roomId = roomId;
        return sendTCP(req);
    }

    public boolean sendLeaveRoomRequest() {
        return sendTCP(new LeaveRoomRequest());
    }

    // -------------------------------------------------------------------------
    // Connect / reconnect
    // -------------------------------------------------------------------------

    // thread-safe
    public void tryConnect() {
        reconnectAttempts = 0;
        tryConnect(0);
    }

    private void tryConnect(long delayBeforeConnectMs) {
        if (shuttingDown || !connectInProgress.compareAndSet(false, true)) {
            return;
        }
        Thread connectThread = new Thread(() -> runConnectAttempt(delayBeforeConnectMs), "net-connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    private void runConnectAttempt(long delayBeforeConnectMs) {
        boolean shouldReconnect = false;
        try {
            if (delayBeforeConnectMs > 0) Thread.sleep(delayBeforeConnectMs);
            if (shuttingDown) return;
            if (netClient.isConnected()) {
                System.out.println("duplicate connect attempt");
                return;
            }
            netClient.connect(NetConfig.CONNECT_TIMEOUT_MS, connectIP, connectPort, connectPort);
        } catch (IOException e) {
            System.err.println("Connect failed: " + e.getMessage());
            shouldReconnect = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            connectInProgress.set(false);
        }
        if (shouldReconnect) scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (shuttingDown) return;
        reconnectAttempts++;
        if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) return;
        tryConnect(RECONNECT_DELAY_MS);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public GameSettings getSettings() {
        return settings;
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
