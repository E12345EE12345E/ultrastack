package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecorContext;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedText;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedTextBox;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.lwjgl3.render.shader.AuroraBackgroundRenderer;
import me.ethanchen.lwjgl3.util.AddressParser;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.NetConfig;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.packets.other.ConnectFailedPacket;
import me.ethanchen.network.packets.other.ConnectionEstablishedPacket;

/**
 * Decorated online-connect screen. Auto-connect from the main menu keeps the form visible and
 * reports status; a failed default connect leaves the address field for a manual retry.
 */
public class ServerConnectMenu extends DecoratedMenuScreen {
    private static final float FORM_X = 540f;

    private boolean connectingToDefault;
    private final DecoratedTextBox addressBox;
    private final DecoratedText statusText;
    private final AuroraBackgroundRenderer aurora;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = new PacketDispatcher<ClientPacketWrapper>()
            .on(ConnectionEstablishedPacket.class, w -> {
                connectingToDefault = false;
                app.switchMenu(new AuthMenu(app));
            })
            .on(ConnectFailedPacket.class, w -> showDefaultServerUnreachable(((ConnectFailedPacket) w.packet).reason));

    public ServerConnectMenu(ClientApp app) {
        this(app, false);
    }

    public ServerConnectMenu(ClientApp app, boolean connectingToDefault) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.connectingToDefault = connectingToDefault;

        DecoratedText title = new DecoratedText(1800f, 620f, "Online Multiplayer", 3.4f);
        title.align = UIText.TextAlign.CENTER_RIGHT;
        addDecorated(title);

        DecoratedText addrLabel = new DecoratedText(FORM_X, 720f, "Server Address (IP or IP:port)", 1.35f);
        addressBox = new DecoratedTextBox(FORM_X, 640f, 560f, 84f);
        addressBox.onEnter(this::connect);

        DecoratedButton connectBtn = new DecoratedButton(FORM_X, 500f, 320f, 88f, "Connect", this::connect);
        connectBtn.fill(0.95f, 0.32f, 0.68f);
        connectBtn.fontSize = 1.45f;
        connectBtn.info("Connect to the UltraStack server.");

        statusText = new DecoratedText(FORM_X, 380f, "", 1.2f);

        DecoratedButton backBtn = new DecoratedButton(200f, 120f, 200f, 68f, "Back", this::leaveToMain);
        backBtn.fontSize = 1.4f;

        addDecorated(addrLabel);
        addDecorated(addressBox);
        addDecorated(connectBtn);
        addDecorated(statusText);
        addDecorated(backBtn);

        aurora = new AuroraBackgroundRenderer();
        if (connectingToDefault) {
            setStatus("Connecting to " + NetConfig.DEFAULT_SERVER_HOST + ":" + NetConfig.PORT + "...");
        }
    }

    private void connect() {
        String addr = addressBox.get().trim();
        if (addr.isEmpty()) {
            app.setConnectDestination(NetConfig.HOST, NetConfig.PORT);
        } else {
            try {
                AddressParser.Result parsed = AddressParser.parse(addr, NetConfig.PORT);
                if (!app.validPort(parsed.port)) {
                    setStatus("Invalid port number.");
                    return;
                }
                app.setConnectDestination(parsed.host, parsed.port);
            } catch (AddressParser.ParseException e) {
                setStatus(e.getMessage());
                return;
            }
        }
        app.setLanMode(false);
        connectingToDefault = false;
        setStatus("Connecting...");
        app.tryConnect();
    }

    private void showDefaultServerUnreachable(String detail) {
        connectingToDefault = false;
        if (detail == null || detail.isEmpty()) {
            setStatus("Default server unreachable. Enter server address manually.");
        } else {
            setStatus("Default server unreachable: " + detail);
        }
    }

    private void setStatus(String message) {
        statusText.text = message != null ? message : "";
    }

    private void leaveToMain() {
        app.disconnect();
        app.switchMenu(new MainMenu(app));
    }

    @Override
    protected void onEscPressed() {
        leaveToMain();
    }

    @Override
    protected void renderBackground(DecorContext ctx) {
        aurora.draw(ctx.appElapsedMs / 1000f, 1f);
    }

    @Override
    public void dispose() {
        aurora.dispose();
        super.dispose();
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        dispatcher.dispatch(w);
    }
}
