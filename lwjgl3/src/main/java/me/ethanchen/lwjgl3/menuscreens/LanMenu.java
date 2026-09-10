package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecorContext;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedText;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedTextBox;
import me.ethanchen.lwjgl3.menuscreens.decorated.Widget;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.lwjgl3.util.AddressParser;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.NetConfig;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.packets.other.ConnectFailedPacket;
import me.ethanchen.network.packets.other.ConnectionEstablishedPacket;
import me.ethanchen.network.packets.s2c.JoinResponse;
import me.ethanchen.network.packets.s2c.StartGameBroadcast;

/**
 * Decorated LAN host/join screen. Username and optional join code are shared; Host IP is
 * collected in a modal widget when joining.
 */
public class LanMenu extends DecoratedMenuScreen {
    private static final float FORM_X = 1380f;

    private boolean isHosting;
    /** Set once Host or Join is pressed; guards against acting on someone else's connection. */
    private boolean connectRequested;
    private String pendingUsername;
    private long pendingJoinCode;

    private final DecoratedTextBox usernameBox;
    private final DecoratedTextBox joinCodeBox;
    private final DecoratedTextBox hostIpBox;
    private final DecoratedText statusText;
    private final DecoratedText widgetStatusText;
    private final Widget joinWidget;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = new PacketDispatcher<ClientPacketWrapper>()
            .on(ConnectionEstablishedPacket.class, w -> {
                if (connectRequested) app.sendJoinRequest(pendingUsername, pendingJoinCode);
            })
            .on(JoinResponse.class, w -> {
                if (connectRequested) handleJoinResponse((JoinResponse) w.packet);
            })
            .on(ConnectFailedPacket.class, w -> {
                if (connectRequested) handleConnectFailed((ConnectFailedPacket) w.packet);
            })
            .on(StartGameBroadcast.class, w -> app.switchMenu(new GameScreen(app, (StartGameBroadcast) w.packet, isHosting)));

    public LanMenu(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        isHosting = false;
        connectRequested = false;
        pendingUsername = "";
        pendingJoinCode = 0;

        DecoratedText title = new DecoratedText(120f, 620f, "LAN Host/Join", 3.4f);
        title.align = UIText.TextAlign.CENTER_LEFT;
        addDecorated(title);

        DecoratedText userLabel = new DecoratedText(FORM_X, 760f, "Username", 1.35f);
        usernameBox = new DecoratedTextBox(FORM_X, 680f, 560f, 84f)
                .sanitize(DecoratedTextBox.SANITIZE_NAME);

        DecoratedText codeLabel = new DecoratedText(FORM_X, 580f, "Join Code (optional)", 1.35f);
        joinCodeBox = new DecoratedTextBox(FORM_X, 500f, 560f, 84f)
                .sanitize(DecoratedTextBox.SANITIZE_JOIN_CODE);

        DecoratedButton hostBtn = new DecoratedButton(1180f, 360f, 300f, 88f, "Host & Play", this::hostAndPlay);
        hostBtn.fill(0.22f, 0.88f, 0.52f);
        hostBtn.fontSize = 1.45f;
        hostBtn.info("Start a LAN room and join it.");

        DecoratedButton joinLobbyBtn = new DecoratedButton(1540f, 360f, 380f, 88f, "Join LAN Lobby", this::openJoinWidget);
        joinLobbyBtn.fill(0.35f, 0.78f, 1.00f);
        joinLobbyBtn.fontSize = 1.35f;
        joinLobbyBtn.info("Connect to a LAN host.");

        statusText = new DecoratedText(FORM_X, 240f, "", 1.2f);

        DecoratedButton backBtn = new DecoratedButton(200f, 120f, 200f, 68f, "Back", this::leaveToMain);
        backBtn.fontSize = 1.4f;

        addDecorated(userLabel);
        addDecorated(usernameBox);
        addDecorated(codeLabel);
        addDecorated(joinCodeBox);
        addDecorated(hostBtn);
        addDecorated(joinLobbyBtn);
        addDecorated(statusText);
        addDecorated(backBtn);

        hostIpBox = new DecoratedTextBox(0f, 0f, 440f, 80f);
        hostIpBox.set(NetConfig.HOST);
        hostIpBox.onEnter(this::joinLan);

        widgetStatusText = new DecoratedText(0f, 0f, "", 1.1f);
        joinWidget = buildJoinWidget();
    }

    @Override
    public boolean usesAurora() {
        return true;
    }

    private Widget buildJoinWidget() {
        Widget w = new Widget(960f, 520f, 560f, 420f);
        w.add(new DecoratedText(0f, 0f, "Join LAN", 2.2f), 0f, 150f);
        w.add(new DecoratedText(0f, 0f, "Host IP", 1.3f), 0f, 70f);
        w.add(hostIpBox, 0f, -10f);
        DecoratedButton join = new DecoratedButton(0f, 0f, 280f, 72f, "Join", this::joinLan);
        join.fill(0.35f, 0.78f, 1.00f);
        join.fontSize = 1.45f;
        w.add(join, 0f, -130f);
        w.add(widgetStatusText, 0f, -200f);
        return w;
    }

    private void openJoinWidget() {
        if (connectRequested) return;
        if (hasOpenWidget()) return;
        if (hostIpBox.get().trim().isEmpty()) {
            hostIpBox.set(NetConfig.HOST);
        }
        widgetStatusText.text = "";
        openWidget(joinWidget);
        navigator.focus(hostIpBox);
    }

    private void hostAndPlay() {
        if (connectRequested) return;
        String hostUser = usernameBox.get().trim();
        if (hostUser.isEmpty()) {
            setStatus("Enter a username.");
            return;
        }
        Long code = parseJoinCode();
        if (code == null) return;
        pendingUsername = hostUser;
        pendingJoinCode = code;
        isHosting = true;
        connectRequested = true;
        setStatus("Starting server...");
        app.startLanServer(NetConfig.PORT, code);
        app.setLanMode(true);
        app.setConnectDestination("127.0.0.1", NetConfig.PORT);
        app.tryConnect();
    }

    private void joinLan() {
        if (connectRequested) return;
        String joinUser = usernameBox.get().trim();
        if (joinUser.isEmpty()) {
            setStatus("Enter a username.");
            return;
        }
        Long code = parseJoinCode();
        if (code == null) return;

        String addr = hostIpBox.get().trim();
        String ip = NetConfig.HOST;
        int port = NetConfig.PORT;
        if (!addr.isEmpty()) {
            try {
                AddressParser.Result parsed = AddressParser.parse(addr, NetConfig.PORT);
                if (!app.validPort(parsed.port)) {
                    setStatus("Invalid port number.");
                    return;
                }
                ip = parsed.host;
                port = parsed.port;
            } catch (AddressParser.ParseException e) {
                setStatus(e.getMessage());
                return;
            }
        }

        pendingUsername = joinUser;
        pendingJoinCode = code;
        isHosting = false;
        connectRequested = true;
        app.setLanMode(true);
        app.setConnectDestination(ip, port);
        setStatus("Connecting...");
        app.tryConnect();
    }

    private Long parseJoinCode() {
        String codeStr = joinCodeBox.get().trim();
        if (codeStr.isEmpty()) return 0L;
        try {
            return Long.parseLong(codeStr);
        } catch (NumberFormatException e) {
            setStatus("Join code must be a number.");
            return null;
        }
    }

    private void setStatus(String message) {
        String text = message != null ? message : "";
        statusText.text = text;
        if (hasOpenWidget()) {
            widgetStatusText.text = text;
        }
    }

    private void leaveToMain() {
        connectRequested = false;
        app.disconnect();
        if (isHosting) {
            app.stopLanServer();
            isHosting = false;
        }
        app.setLanMode(false);
        app.switchMenu(new MainMenu(app));
    }

    @Override
    protected void onEscPressed() {
        leaveToMain();
    }

    @Override
    protected void renderBackground(DecorContext ctx) {
        app.drawAurora(ctx.appElapsedMs / 1000f, 1f);
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        dispatcher.dispatch(w);
    }

    private void handleJoinResponse(JoinResponse res) {
        if (res.accepted) {
            app.switchMenu(new MultiplayerLobby(app, isHosting, res.gameInProgress));
        } else {
            connectRequested = false;
            String reason = (res.reason != null && !res.reason.isEmpty()) ? res.reason : "Join denied.";
            setStatus(reason);
            if (isHosting) {
                app.stopLanServer();
                isHosting = false;
            }
        }
    }

    private void handleConnectFailed(ConnectFailedPacket pkt) {
        connectRequested = false;
        String reason = (pkt.reason != null && !pkt.reason.isEmpty()) ? pkt.reason : "Connection failed.";
        setStatus(reason);
        if (isHosting) {
            app.stopLanServer();
            isHosting = false;
        }
    }
}
