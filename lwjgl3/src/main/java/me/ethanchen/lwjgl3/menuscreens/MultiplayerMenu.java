package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.*;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.NetConfig;
import me.ethanchen.network.packets.other.ConnectionEstablishedPacket;
import me.ethanchen.network.packets.s2c.JoinResponse;

public class MultiplayerMenu extends MenuScreen {
    private TextInput messageText;
    private String pendingUsername;
    private long pendingCredential;

    public MultiplayerMenu(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        TextBoxOutput username = new TextBoxOutput();
        TextBoxOutput hostip = new TextBoxOutput();
        TextBoxOutput authstring = new TextBoxOutput();

        elements.add(new UIText(0.5, 0.8, "Multiplayer", 4));
        elements.add(new UIText(0.5, 0.675, "Username", 1));
        UITextBox usernameBox = new UITextBox(0.5, 0.6, 0.4, 0.08, username);
        usernameBox.sanitize = 2;
        elements.add(usernameBox);
        elements.add(new UIText(0.5, 0.525, "Host IP", 1));
        elements.add(new UITextBox(0.5, 0.45, 0.4, 0.08, hostip));
        elements.add(new UIText(0.5, 0.375, "Join Code", 1));
        elements.add(new UITextBox(0.5, 0.3, 0.4, 0.08, authstring));
        messageText = new TextInput();
        elements.add(new UIText(0.5, 0.2, messageText, 1));
        elements.add(new UIButton(0.5, 0.125, 0.3, 0.1, "Join Server", () -> {
            boolean shouldTryConnect = true;
            if (hostip.get().length() == 0) {
                messageText.set("host ip not specified, defaulting to " + NetConfig.HOST + ":" + NetConfig.PORT);
                app.setConnectDestination(NetConfig.HOST, NetConfig.PORT);
            } else {
                try {
                    String[] splitip = hostip.get().split(":");
                    if (splitip.length > 1) {
                        if (app.validIP(splitip[0])) {
                            if (app.validPort(Integer.parseInt(splitip[1]))) {
                                app.setConnectDestination(splitip[0], Integer.parseInt(splitip[1]));
                            } else {
                                messageText.set("host ip port is an invalid number");
                                shouldTryConnect = false;
                            }
                        }
                    }
                } catch (NumberFormatException exception) {
                    messageText.set("host ip port is not a number");
                    shouldTryConnect = false;
                }
            }
            if (shouldTryConnect) {
                try {
                    if (authstring.get().length() == 0) {
                        messageText.set("no authentication code set");
                        shouldTryConnect = false;
                    } else {
                        pendingUsername = username.get().length()==0?"emptyname":username.get();
                        pendingCredential = Long.parseLong(authstring.get());
                    }
                } catch (NumberFormatException exception) {
                    messageText.set("authentication string is not a number");
                    shouldTryConnect = false;
                }
            }
            if (shouldTryConnect) {
                app.tryConnect();
            }
        }));
        MenuScreen.linkTextBoxTabChain(elements);
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
        elements.forEach(element -> {
            element.render(shapes, sprites, font);
        });
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        if (w.packet instanceof ConnectionEstablishedPacket) {
            app.sendJoinRequest(pendingUsername, pendingCredential);
        }
        if (w.packet instanceof JoinResponse) {
            System.out.println(w.packet);
            JoinResponse p = (JoinResponse) w.packet;
            if (p.accepted) {
                app.switchMenu(new MultiplayerLobby(app, false));
            } else {
                messageText.set("Join denied: " + p.reason);
            }
        }
    }
}
