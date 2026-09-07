package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecorContext;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedText;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedTextBox;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.lwjgl3.render.shader.AuroraBackgroundRenderer;
import me.ethanchen.lwjgl3.settings.SettingsManager;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.packets.s2c.AuthResponse;
import me.ethanchen.util.TextSanitizer;

/**
 * Decorated login / register screen. Password is stored plaintext in the field and drawn masked.
 */
public class AuthMenu extends DecoratedMenuScreen {
    private static final float FORM_X = 540f;

    private final DecoratedTextBox usernameBox;
    private final DecoratedTextBox passwordBox;
    private final DecoratedText statusText;
    private final AuroraBackgroundRenderer aurora;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = new PacketDispatcher<ClientPacketWrapper>()
            .on(AuthResponse.class, w -> handleAuthResponse((AuthResponse) w.packet));

    public AuthMenu(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        DecoratedText title = new DecoratedText(1800f, 620f, "Multiplayer Login", 3.4f);
        title.align = UIText.TextAlign.CENTER_RIGHT;
        addDecorated(title);

        DecoratedText userLabel = new DecoratedText(FORM_X, 760f, "Username", 1.35f);
        usernameBox = new DecoratedTextBox(FORM_X, 680f, 560f, 84f)
                .sanitize(DecoratedTextBox.SANITIZE_NAME);
        String savedUsername = app.getSettings().lastUsername;
        if (savedUsername != null && !savedUsername.isEmpty()) {
            usernameBox.set(TextSanitizer.sanitizeName(savedUsername));
        }

        DecoratedText passLabel = new DecoratedText(FORM_X, 580f, "Password", 1.35f);
        passwordBox = new DecoratedTextBox(FORM_X, 500f, 560f, 84f)
                .masked()
                .onEnter(this::login);

        DecoratedButton loginBtn = new DecoratedButton(380f, 360f, 280f, 88f, "Login", this::login);
        loginBtn.fill(0.95f, 0.32f, 0.68f);
        loginBtn.fontSize = 1.45f;
        loginBtn.info("Sign in with an existing account.");

        DecoratedButton registerBtn = new DecoratedButton(700f, 360f, 280f, 88f, "Register", this::register);
        registerBtn.fill(0.35f, 0.78f, 1.00f);
        registerBtn.fontSize = 1.45f;
        registerBtn.info("Create a new account.");

        statusText = new DecoratedText(FORM_X, 240f, "", 1.2f);

        DecoratedButton backBtn = new DecoratedButton(200f, 120f, 200f, 68f, "Back", this::leaveToConnect);
        backBtn.fontSize = 1.4f;

        addDecorated(userLabel);
        addDecorated(usernameBox);
        addDecorated(passLabel);
        addDecorated(passwordBox);
        addDecorated(loginBtn);
        addDecorated(registerBtn);
        addDecorated(statusText);
        addDecorated(backBtn);

        aurora = new AuroraBackgroundRenderer();
    }

    private void login() {
        String user = usernameBox.get().trim();
        String pass = passwordBox.get();
        if (user.isEmpty()) {
            setStatus("Username cannot be empty.");
            return;
        }
        app.getSettings().lastUsername = user;
        SettingsManager.save(app.getSettings());
        setStatus("Logging in...");
        app.sendLoginRequest(user, pass);
    }

    private void register() {
        String user = usernameBox.get().trim();
        String pass = passwordBox.get();
        if (user.isEmpty()) {
            setStatus("Username cannot be empty.");
            return;
        }
        if (user.length() < TextSanitizer.MIN_REGISTER_USERNAME_LENGTH) {
            setStatus("Username must be at least "
                    + TextSanitizer.MIN_REGISTER_USERNAME_LENGTH + " characters.");
            return;
        }
        app.getSettings().lastUsername = user;
        SettingsManager.save(app.getSettings());
        setStatus("Registering...");
        app.sendRegisterRequest(user, pass);
    }

    private void setStatus(String message) {
        statusText.text = message != null ? message : "";
    }

    private void leaveToConnect() {
        app.disconnect();
        app.switchMenu(new ServerConnectMenu(app));
    }

    @Override
    protected void onEscPressed() {
        leaveToConnect();
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

    private void handleAuthResponse(AuthResponse res) {
        if (res.success) {
            app.switchMenu(new RoomBrowserMenu(app));
        } else {
            setStatus(res.reason != null && !res.reason.isEmpty() ? res.reason : "Authentication failed.");
        }
    }
}
