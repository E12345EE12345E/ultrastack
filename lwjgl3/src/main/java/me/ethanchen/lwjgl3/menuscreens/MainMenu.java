package me.ethanchen.lwjgl3.menuscreens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import me.ethanchen.lwjgl3.AppLinks;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.decorated.Anim;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecorContext;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedImage;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedText;
import me.ethanchen.lwjgl3.menuscreens.decorated.Widget;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.render.MenuAssets;
import me.ethanchen.lwjgl3.render.shader.AuroraBackgroundRenderer;
import me.ethanchen.network.NetConfig;

/**
 * Decorated title screen. The U/S intro is a pure function of {@code appElapsedMs}: first
 * launch plays the full animation; later returns are already past the last keyframe.
 */
public class MainMenu extends DecoratedMenuScreen {
    /** Window is usually still arranging; keep the logo hidden until this elapses. */
    private static final long INTRO_DELAY_MS = 1000L;
    private static final long FADE_MS = 900L;
    private static final long HOLD_MS = 350L;
    private static final long HOLD_END_MS = INTRO_DELAY_MS + FADE_MS + HOLD_MS; // 2250
    private static final long SETTLE_MS = 800L;
    private static final long SETTLE_END_MS = HOLD_END_MS + SETTLE_MS; // 3050
    private static final long BUTTONS_FADE_MS = 550L;
    private static final long BUTTONS_START_MS = SETTLE_END_MS;

    private static final float LOGO_X = 960f;
    private static final float LOGO_INTRO_Y = 540f;
    private static final float LOGO_REST_Y = 760f;
    /** S starts below and rises until it shares {@link #LOGO_INTRO_Y} with U (full layer overlap). */
    private static final float S_START_Y = 200f;
    private static final float SCALE_INTRO = 3f;
    private static final float SCALE_REST = 2f;

    private final DecoratedImage markU;
    private final DecoratedImage markS;
    private final DecoratedButton multiplayerBtn;
    private final DecoratedButton lanBtn;
    private final DecoratedButton settingsBtn;
    private final DecoratedButton wikiBtn;
    private final DecoratedButton exitBtn;
    private final Widget settingsWidget;
    private final AuroraBackgroundRenderer aurora;

    public MainMenu(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        markU = new DecoratedImage(LOGO_X, LOGO_INTRO_Y, MenuAssets.ultrastackU());
        markS = new DecoratedImage(LOGO_X, S_START_Y, MenuAssets.ultrastackS());
        markU.scaleMult = SCALE_INTRO;
        markS.scaleMult = SCALE_INTRO;
        markU.alpha = 0f;
        markS.alpha = 0f;
        addDecorated(markU);
        addDecorated(markS);

        multiplayerBtn = new DecoratedButton(720f, 400f, 380f, 92f, "Multiplayer", () -> {
            app.setLanMode(false);
            app.setConnectDestination(NetConfig.DEFAULT_SERVER_HOST, NetConfig.PORT);
            app.tryConnectAuto();
            app.switchMenu(new ServerConnectMenu(app, true));
        });
        lanBtn = new DecoratedButton(1200f, 400f, 380f, 92f, "LAN",
                () -> app.switchMenu(new LanMenu(app)));
        settingsBtn = DecoratedButton.icon(800f, 120f, 96f, MenuAssets.settingsIcon(),
                this::openSettingsWidget);
        wikiBtn = DecoratedButton.icon(960f, 120f, 96f, MenuAssets.wikiIcon(),
                () -> Gdx.net.openURI(AppLinks.WIKI_URL));
        exitBtn = DecoratedButton.icon(1120f, 120f, 96f, MenuAssets.exitIcon(),
                () -> Gdx.app.exit());
        multiplayerBtn.fill(0.95f, 0.32f, 0.68f);
        lanBtn.fill(0.22f, 0.88f, 0.52f);
        settingsBtn.fill(0.95f, 0.70f, 0.22f);
        wikiBtn.fill(0.35f, 0.78f, 1.00f);
        exitBtn.fill(0.95f, 0.28f, 0.32f);

        multiplayerBtn.info("Login to the server and play online!");
        lanBtn.info("Offline play. Scores aren't saved to leaderboard.");
        settingsBtn.info("Settings");
        wikiBtn.info("Wiki");
        exitBtn.info("Exit");

        DecoratedButton[] buttons = { multiplayerBtn, lanBtn, settingsBtn, wikiBtn, exitBtn };
        for (DecoratedButton b : buttons) {
            b.alpha = 0f;
            b.visible = false;
            b.focusable = false;
            addDecorated(b);
        }

        settingsWidget = buildSettingsWidget();
        aurora = new AuroraBackgroundRenderer();
        applyIntro(appElapsedMs());
    }

    private Widget buildSettingsWidget() {
        Widget w = new Widget(960f, 520f, 520f, 500f);
        w.add(new DecoratedText(0f, 0f, "Settings", 2.2f), 0f, 190f);
        DecoratedButton movement = new DecoratedButton(0f, 0f, 400f, 78f, "Movement",
                () -> app.switchMenu(new MovementSettingsScreen(app)));
        DecoratedButton color = new DecoratedButton(0f, 0f, 400f, 78f, "Color",
                () -> app.switchMenu(new ColorSettingsScreen(app)));
        DecoratedButton sound = new DecoratedButton(0f, 0f, 400f, 78f, "Sound",
                () -> app.switchMenu(new SoundSettingsScreen(app)));
        DecoratedButton back = new DecoratedButton(0f, 0f, 280f, 68f, "Back", this::closeTopWidget);
        movement.fontSize = 1.55f;
        color.fontSize = 1.55f;
        sound.fontSize = 1.55f;
        back.fontSize = 1.4f;
        w.add(movement, 0f, 90f);
        w.add(color, 0f, -20f);
        w.add(sound, 0f, -130f);
        w.add(back, 0f, -210f);
        return w;
    }

    private void openSettingsWidget() {
        if (hasOpenWidget()) return;
        openWidget(settingsWidget);
    }

    @Override
    protected void updateScreen(long menuElapsedMs, long appMs) {
        applyIntro(appMs);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            aurora.reloadShader();
        }
    }

    @Override
    protected void renderBackground(DecorContext ctx) {
        float fade = Anim.smoothstep(Anim.progress(ctx.appElapsedMs, 0L, FADE_MS));
        aurora.draw(ctx.appElapsedMs / 1000f, fade);
    }

    @Override
    public void dispose() {
        aurora.dispose();
        super.dispose();
    }

    private void applyIntro(long t) {
        float fade = Anim.easeOutCubic(Anim.progress(t, INTRO_DELAY_MS, FADE_MS));
        markU.alpha = fade;
        markS.alpha = fade;
        markU.visible = fade > 0.01f;
        markS.visible = fade > 0.01f;

        float rise = Anim.easeOutCubic(Anim.progress(t, INTRO_DELAY_MS, FADE_MS));
        float sY = Anim.lerp(S_START_Y, LOGO_INTRO_Y, rise);
        float settle = Anim.easeInOutCubic(Anim.progress(t, HOLD_END_MS, SETTLE_MS));
        float logoY = Anim.lerp(LOGO_INTRO_Y, LOGO_REST_Y, settle);
        float scale = Anim.lerp(SCALE_INTRO, SCALE_REST, settle);

        markU.centerX = DesignUi.nx(LOGO_X);
        markS.centerX = DesignUi.nx(LOGO_X);
        markU.centerY = DesignUi.ny(logoY);
        // After the rise, S locks to the same point as U so the two layers recreate the logo.
        markS.centerY = DesignUi.ny(Anim.lerp(sY, LOGO_REST_Y, settle));
        markU.scaleMult = scale;
        markS.scaleMult = scale;

        float btn = Anim.smoothstep(Anim.progress(t, BUTTONS_START_MS, BUTTONS_FADE_MS));
        DecoratedButton[] buttons = { multiplayerBtn, lanBtn, settingsBtn, wikiBtn, exitBtn };
        for (DecoratedButton b : buttons) {
            b.alpha = btn;
            b.visible = btn > 0.01f;
            b.focusable = btn > 0.35f;
        }
    }
}
