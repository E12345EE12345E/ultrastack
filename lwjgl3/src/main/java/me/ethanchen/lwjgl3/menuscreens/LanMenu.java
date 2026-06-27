package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.*;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.NetConfig;
import me.ethanchen.network.packets.other.ConnectionEstablishedPacket;
import me.ethanchen.network.packets.s2c.JoinResponse;

public class LanMenu extends MenuScreen {
    private boolean isHosting;
    private String pendingUsername;
    private long pendingJoinCode;
    private TextInput messageText;

    public LanMenu(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        isHosting = false;
        pendingUsername = "";
        pendingJoinCode = 0;
        messageText = new TextInput();

        TextBoxOutput hostJoinCodeOutput = new TextBoxOutput();
        TextBoxOutput hostUsernameOutput = new TextBoxOutput();

        TextBoxOutput joinUsernameOutput = new TextBoxOutput();
        TextBoxOutput joinHostIpOutput = new TextBoxOutput();
        TextBoxOutput joinJoinCodeOutput = new TextBoxOutput();

        // ---- Host section ----
        elements.add(new UIText(0.25, 0.85, "Host", 3));

        elements.add(new UIText(0.25, 0.73, "Username", 1));
        UITextBox hostUsernameBox = new UITextBox(0.25, 0.665, 0.35, 0.08, hostUsernameOutput);
        hostUsernameBox.sanitize = 2;
        elements.add(hostUsernameBox);

        elements.add(new UIText(0.25, 0.59, "Join Code (numbers only)", 1));
        UITextBox hostJoinCodeBox = new UITextBox(0.25, 0.515, 0.35, 0.08, hostJoinCodeOutput);
        hostJoinCodeBox.sanitize = 3;
        elements.add(hostJoinCodeBox);

        elements.add(new UIButton(0.25, 0.4, 0.35, 0.1, "Host & Play", () -> {
            String codeStr = hostJoinCodeOutput.get().trim();
            String hostUser = hostUsernameOutput.get().trim();
            if (hostUser.isEmpty()) {
                messageText.set("Enter a username.");
                return;
            }
            if (codeStr.isEmpty()) {
                messageText.set("Enter a join code.");
                return;
            }
            long code;
            try {
                code = Long.parseLong(codeStr);
            } catch (NumberFormatException e) {
                messageText.set("Join code must be a number.");
                return;
            }
            pendingUsername = hostUser;
            pendingJoinCode = code;
            isHosting = true;
            messageText.set("Starting server...");
            app.startLanServer(NetConfig.PORT, code);
            app.setLanMode(true);
            app.setConnectDestination("127.0.0.1", NetConfig.PORT);
            app.tryConnect();
        }));

        // ---- Join section ----
        elements.add(new UIText(0.75, 0.85, "Join", 3));

        elements.add(new UIText(0.75, 0.73, "Username", 1));
        UITextBox joinUsernameBox = new UITextBox(0.75, 0.665, 0.35, 0.08, joinUsernameOutput);
        joinUsernameBox.sanitize = 2;
        elements.add(joinUsernameBox);

        elements.add(new UIText(0.75, 0.59, "Host IP (or IP:port)", 1));
        elements.add(new UITextBox(0.75, 0.515, 0.35, 0.08, joinHostIpOutput));

        elements.add(new UIText(0.75, 0.44, "Join Code (numbers only)", 1));
        UITextBox joinJoinCodeBox = new UITextBox(0.75, 0.365, 0.35, 0.08, joinJoinCodeOutput);
        joinJoinCodeBox.sanitize = 3;
        elements.add(joinJoinCodeBox);

        elements.add(new UIButton(0.75, 0.25, 0.35, 0.1, "Join", () -> {
            String joinUser = joinUsernameOutput.get().trim();
            String addr = joinHostIpOutput.get().trim();
            String codeStr = joinJoinCodeOutput.get().trim();

            if (joinUser.isEmpty()) {
                messageText.set("Enter a username.");
                return;
            }
            if (codeStr.isEmpty()) {
                messageText.set("Enter a join code.");
                return;
            }
            long code;
            try {
                code = Long.parseLong(codeStr);
            } catch (NumberFormatException e) {
                messageText.set("Join code must be a number.");
                return;
            }

            String ip = NetConfig.HOST;
            int port = NetConfig.PORT;
            if (!addr.isEmpty()) {
                String[] parts = addr.split(":");
                if (parts.length >= 2) {
                    try {
                        port = Integer.parseInt(parts[parts.length - 1]);
                        if (!app.validPort(port)) {
                            messageText.set("Invalid port number.");
                            return;
                        }
                        ip = addr.substring(0, addr.lastIndexOf(':'));
                    } catch (NumberFormatException e) {
                        messageText.set("Port must be a number.");
                        return;
                    }
                } else {
                    ip = parts[0];
                }
            }

            pendingUsername = joinUser;
            pendingJoinCode = code;
            isHosting = false;
            app.setLanMode(true);
            app.setConnectDestination(ip, port);
            messageText.set("Connecting...");
            app.tryConnect();
        }));

        elements.add(new UIText(0.5, 0.13, messageText, 1));
    }

    @Override
    protected void onEscPressed() {
        if (isHosting) {
            app.stopLanServer();
        }
        app.disconnect();
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
            app.sendJoinRequest(pendingUsername, pendingJoinCode);
        }
        if (w.packet instanceof JoinResponse) {
            JoinResponse res = (JoinResponse) w.packet;
            if (res.accepted) {
                app.switchMenu(new MultiplayerLobby(app, isHosting));
            } else {
                String reason = (res.reason != null && !res.reason.isEmpty()) ? res.reason : "Join denied.";
                messageText.set(reason);
                if (isHosting) {
                    app.stopLanServer();
                    isHosting = false;
                }
            }
        }
    }
}
