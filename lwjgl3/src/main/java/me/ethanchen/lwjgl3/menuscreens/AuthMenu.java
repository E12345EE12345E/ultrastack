package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.*;
import me.ethanchen.lwjgl3.settings.SettingsManager;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.packets.s2c.AuthResponse;
import me.ethanchen.util.TextSanitizer;

public class AuthMenu extends MenuScreen {
    private TextInput messageText;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = new PacketDispatcher<ClientPacketWrapper>()
            .on(AuthResponse.class, w -> handleAuthResponse((AuthResponse) w.packet));

    public AuthMenu(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        TextBoxOutput usernameOutput = new TextBoxOutput();
        TextBoxOutput passcodeOutput = new TextBoxOutput();
        messageText = new TextInput();

        elements.add(new UIText(0.5, 0.8, "Login / Register", 4));

        elements.add(new UIText(0.5, 0.65, "Username", 1));
        UITextBox usernameBox = new UITextBox(0.5, 0.58, 0.45, 0.08, usernameOutput);
        usernameBox.sanitize = 2;
        String savedUsername = app.getSettings().lastUsername;
        if (savedUsername != null && !savedUsername.isEmpty()) {
            String normalized = TextSanitizer.sanitizeName(savedUsername);
            usernameBox.text = normalized;
            usernameOutput.set(normalized);
        }
        elements.add(usernameBox);

        elements.add(new UIText(0.5, 0.505, "Passcode", 1));
        elements.add(new UITextBox(0.5, 0.43, 0.45, 0.08, passcodeOutput));

        elements.add(new UIText(0.5, 0.33, messageText, 1));

        elements.add(new UIButton(0.35, 0.23, 0.25, 0.1, "Login", () -> {
            String user = usernameOutput.get().trim();
            String pass = passcodeOutput.get();
            if (user.isEmpty()) {
                messageText.set("Username cannot be empty.");
                return;
            }
            app.getSettings().lastUsername = user;
            SettingsManager.save(app.getSettings());
            messageText.set("Logging in...");
            app.sendLoginRequest(user, pass);
        }));

        elements.add(new UIButton(0.65, 0.23, 0.25, 0.1, "Register", () -> {
            String user = usernameOutput.get().trim();
            String pass = passcodeOutput.get();
            if (user.isEmpty()) {
                messageText.set("Username cannot be empty.");
                return;
            }
            if (user.length() < TextSanitizer.MIN_REGISTER_USERNAME_LENGTH) {
                messageText.set("Username must be at least "
                        + TextSanitizer.MIN_REGISTER_USERNAME_LENGTH + " characters.");
                return;
            }
            app.getSettings().lastUsername = user;
            SettingsManager.save(app.getSettings());
            messageText.set("Registering...");
            app.sendRegisterRequest(user, pass);
        }));

        MenuScreen.linkTextBoxTabChain(elements);
    }

    @Override
    protected void onEscPressed() {
        app.disconnect();
        app.switchMenu(new ServerConnectMenu(app));
    }

    @Override
    public void update() {
    }


    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        dispatcher.dispatch(w);
    }

    private void handleAuthResponse(AuthResponse res) {
        if (res.success) {
            app.switchMenu(new RoomBrowserMenu(app));
        } else {
            messageText.set(res.reason != null && !res.reason.isEmpty() ? res.reason : "Authentication failed.");
        }
    }
}
