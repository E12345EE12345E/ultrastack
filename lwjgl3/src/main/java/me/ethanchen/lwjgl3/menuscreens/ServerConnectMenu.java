package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.*;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.NetConfig;
import me.ethanchen.network.packets.other.ConnectionEstablishedPacket;

public class ServerConnectMenu extends MenuScreen {
    private TextInput messageText;

    public ServerConnectMenu(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        TextBoxOutput serverAddress = new TextBoxOutput();
        messageText = new TextInput();

        elements.add(new UIText(0.5, 0.8, "Online Multiplayer", 4));
        elements.add(new UIText(0.5, 0.575, "Server Address (IP or IP:port)", 1));
        elements.add(new UITextBox(0.5, 0.5, 0.5, 0.08, serverAddress));
        elements.add(new UIText(0.5, 0.35, messageText, 1));
        elements.add(new UIButton(0.5, 0.25, 0.35, 0.1, "Connect", () -> {
            String addr = serverAddress.get().trim();
            if (addr.isEmpty()) {
                app.setConnectDestination(NetConfig.HOST, NetConfig.PORT);
            } else {
                String[] parts = addr.split(":");
                if (parts.length >= 2) {
                    try {
                        int port = Integer.parseInt(parts[parts.length - 1]);
                        if (!app.validPort(port)) {
                            messageText.set("Invalid port number.");
                            return;
                        }
                        String ip = addr.substring(0, addr.lastIndexOf(':'));
                        app.setConnectDestination(ip, port);
                    } catch (NumberFormatException e) {
                        messageText.set("Port must be a number.");
                        return;
                    }
                } else {
                    app.setConnectDestination(parts[0], NetConfig.PORT);
                }
            }
            app.setLanMode(false);
            messageText.set("Connecting...");
            app.tryConnect();
        }));
    }

    @Override
    protected void onEscPressed() {
        app.switchMenu(new MainMenu(app));
    }

    @Override
    public void update() {
    }

    @Override
    public void render() {
        elements.forEach(element -> element.render(shapes, sprites, font));
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        if (w.packet instanceof ConnectionEstablishedPacket) {
            app.switchMenu(new AuthMenu(app));
        }
    }
}
