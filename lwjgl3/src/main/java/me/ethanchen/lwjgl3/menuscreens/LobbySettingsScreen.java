package me.ethanchen.lwjgl3.menuscreens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.game.GameMode;
import me.ethanchen.game.pve.PveLevelRegistry;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.lwjgl3.render.PveLevelAssets;
import me.ethanchen.lwjgl3.settings.LobbySettings;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.packets.c2s.LobbySettingsRequest;
import me.ethanchen.network.packets.c2s.StartGameRequest;

/**
 * Host-only lobby configuration screen: chooses which gamemode {@code StartGameRequest} will
 * launch, and (for PvE) which level/difficulty. Reached from {@link MultiplayerLobby}.
 */
public class LobbySettingsScreen extends MenuScreen {
    private static final double DIFFICULTY_X = 0.18;
    private static final double DIFFICULTY_Y = 0.125;
    private static final double DIFFICULTY_W = 0.32;
    private static final double DIFFICULTY_H = 0.1;

    private final MultiplayerLobby chatScreen;
    private final UIButton modeButton;
    private final UIButton difficultyButton;
    private final LobbySettings settings;

    public LobbySettingsScreen(ClientApp app, MultiplayerLobby chatScreen) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.chatScreen = chatScreen;
        this.settings = app.getLobbySettings();

        elements.add(new UIText(0.5, 0.85, "Lobby Settings", 4));
        elements.add(new UIText(0.5, 0.65, "Game Mode", 1.5));
        modeButton = new UIButton(0.5, 0.56, 0.4, 0.1, modeLabel(settings.gamemode), null);
        modeButton.action = () -> {
            settings.gamemode = nextMode(settings.gamemode);
            modeButton.text = modeLabel(settings.gamemode);
            if (settings.gamemode != GameMode.PVE) {
                settings.pveLevelId = 0;
                settings.pveDifficulty = 0;
            } else {
                clampSelectionToUnlocked();
            }
            syncDifficultyButton();
            sendLobbySettings();
        };
        elements.add(modeButton);

        // Keep this in `elements` for the screen's lifetime. Adding/removing it from a click
        // handler races MenuScreen's for-each over the same list (ConcurrentModificationException).
        difficultyButton = new UIButton(DIFFICULTY_X, DIFFICULTY_Y, DIFFICULTY_W, DIFFICULTY_H,
                "Difficulty: 1", null) {
            @Override
            public void render(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
                if (width <= 0 || height <= 0) return;
                super.render(shapes, sprites, font);
            }
        };
        difficultyButton.action = () -> {
            PveLevelRegistry.Entry e = PveLevelRegistry.byId(settings.pveLevelId);
            if (e == null || e.difficultyCount() <= 1) return;
            settings.pveDifficulty = (settings.pveDifficulty + 1) % e.difficultyCount();
            syncDifficultyButton();
            sendLobbySettings();
        };
        elements.add(difficultyButton);
        syncDifficultyButton();

        elements.add(new UIButton(0.5, 0.125, 0.3, 0.1, "Start Game", () -> {
            StartGameRequest p = new StartGameRequest();
            p.gamemode = app.getLobbySettings().gamemode;
            app.sendTCP(p);
        }));
        elements.add(new UIButton(0.82, 0.125, 0.28, 0.1, "View Chat", () -> app.switchMenu(chatScreen)));
    }

    /** Shows the difficulty cycle button only for PvE levels that register more than one JSON. */
    private void syncDifficultyButton() {
        PveLevelRegistry.Entry entry = settings.gamemode == GameMode.PVE
                ? PveLevelRegistry.byId(settings.pveLevelId) : null;
        boolean show = entry != null && entry.difficultyCount() > 1;
        if (show) {
            difficultyButton.centerX = DIFFICULTY_X;
            difficultyButton.centerY = DIFFICULTY_Y;
            difficultyButton.width = DIFFICULTY_W;
            difficultyButton.height = DIFFICULTY_H;
            difficultyButton.text = "Difficulty: " + entry.difficultyName(settings.pveDifficulty);
        } else {
            difficultyButton.width = 0;
            difficultyButton.height = 0;
            difficultyButton.centerY = -1;
        }
    }

    private void sendLobbySettings() {
        LobbySettingsRequest req = new LobbySettingsRequest();
        req.gamemode = settings.gamemode;
        req.pveLevelId = settings.pveLevelId;
        req.pveDifficulty = settings.pveDifficulty;
        app.sendTCP(req);
    }

    private void clampSelectionToUnlocked() {
        int unlocked = unlockedLevels();
        if (PveLevelRegistry.count() == 0) {
            settings.pveLevelId = 0;
            settings.pveDifficulty = 0;
            return;
        }
        if (settings.pveLevelId < 0 || settings.pveLevelId >= unlocked
                || PveLevelRegistry.byId(settings.pveLevelId) == null) {
            settings.pveLevelId = 0;
        }
        PveLevelRegistry.Entry e = PveLevelRegistry.byId(settings.pveLevelId);
        if (e == null || settings.pveDifficulty < 0 || settings.pveDifficulty >= e.difficultyCount()) {
            settings.pveDifficulty = 0;
        }
    }

    private int unlockedLevels() {
        PlayerProfile profile = app.getProfile();
        if (profile == null) return Math.max(1, PveLevelRegistry.count());
        return Math.max(1, profile.pveUnlockedLevels);
    }

    private static GameMode nextMode(GameMode mode) {
        switch (mode) {
            case MULTIPLAYER_SCORE: return GameMode.MULTIPLAYER_PUZZLE;
            case MULTIPLAYER_PUZZLE: return GameMode.CHARACTER_SCORE;
            case CHARACTER_SCORE: return GameMode.PVE;
            default: return GameMode.MULTIPLAYER_SCORE;
        }
    }

    private static String modeLabel(GameMode mode) {
        switch (mode) {
            case MULTIPLAYER_PUZZLE: return "Mode: Puzzle";
            case CHARACTER_SCORE: return "Mode: Characters";
            case PVE: return "Mode: PvE";
            default: return "Mode: Score";
        }
    }

    @Override
    protected void onEscPressed() {
        app.switchMenu(chatScreen);
    }

    @Override
    public void update() {
        // Keep the mode label in sync if a LobbySettingsBroadcast arrives while this screen is open.
        modeButton.text = modeLabel(settings.gamemode);
        syncDifficultyButton();
    }

    @Override
    public void render() {
        if (settings.gamemode == GameMode.PVE) {
            renderPveCarousel();
        }
        super.render();
    }

    /**
     * Five-thumbnail carousel in the empty band (~Y 0.22–0.48): center large, ±1 and ±2 smaller.
     * Locked levels are dimmed and not clickable.
     */
    private void renderPveCarousel() {
        int count = PveLevelRegistry.count();
        if (count <= 0) return;
        clampSelectionToUnlocked();

        int unlocked = unlockedLevels();
        int centerId = settings.pveLevelId;
        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        float bandCenterY = screenH * 0.36f;
        float[] offsets = { -2, -1, 0, 1, 2 };
        float[] sizeFracs = { 0.07f, 0.09f, 0.13f, 0.09f, 0.07f };

        GlyphLayout layout = new GlyphLayout();
        float savedX = font.getScaleX(), savedY = font.getScaleY();

        for (int i = 0; i < offsets.length; i++) {
            int levelId = centerId + (int) offsets[i];
            if (levelId < 0 || levelId >= count) continue;
            PveLevelRegistry.Entry entry = PveLevelRegistry.byId(levelId);
            if (entry == null) continue;
            boolean locked = levelId >= unlocked;
            float size = screenW * sizeFracs[i];
            float cx = screenW * (0.5f + offsets[i] * 0.16f);
            float x = cx - size * 0.5f;
            float y = bandCenterY - size * 0.5f;

            Texture thumb = PveLevelAssets.thumbnailFor(levelId);
            shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
            if (thumb == null) {
                Color c = PveLevelAssets.fallbackColor(levelId);
                if (locked) shapes.setColor(c.r * 0.35f, c.g * 0.35f, c.b * 0.35f, 1f);
                else shapes.setColor(c);
                shapes.rect(x, y, size, size);
            } else {
                shapes.setColor(locked ? 0.25f : 0.1f, locked ? 0.25f : 0.1f, locked ? 0.25f : 0.1f, 1f);
                shapes.rect(x - 2, y - 2, size + 4, size + 4);
            }
            shapes.end();

            if (thumb != null) {
                sprites.begin();
                if (locked) sprites.setColor(0.4f, 0.4f, 0.4f, 1f);
                else sprites.setColor(Color.WHITE);
                sprites.draw(thumb, x, y, size, size);
                sprites.setColor(Color.WHITE);
                sprites.end();
            }

            // Title under the center thumbnail.
            if (offsets[i] == 0) {
                font.getData().setScale(1f);
                float fs = 1.1f * (size * 0.18f / font.getData().lineHeight);
                font.getData().setScale(fs);
                layout.setText(font, entry.name);
                sprites.begin();
                font.setColor(locked ? Color.GRAY : Color.WHITE);
                font.draw(sprites, entry.name, cx - layout.width * 0.5f, y - size * 0.08f);
                sprites.end();
                font.setColor(Color.WHITE);
            }
        }
        font.getData().setScale(savedX, savedY);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (settings.gamemode == GameMode.PVE && handleCarouselClick(screenX, screenY)) {
            return true;
        }
        return super.touchDown(screenX, screenY, pointer, button);
    }

    private boolean handleCarouselClick(int screenX, int screenY) {
        int count = PveLevelRegistry.count();
        if (count <= 0) return false;
        int unlocked = unlockedLevels();
        int centerId = settings.pveLevelId;
        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        float bandCenterY = screenH * 0.36f;
        float mouseY = screenH - screenY;
        float[] offsets = { -2, -1, 0, 1, 2 };
        float[] sizeFracs = { 0.07f, 0.09f, 0.13f, 0.09f, 0.07f };

        for (int i = 0; i < offsets.length; i++) {
            int levelId = centerId + (int) offsets[i];
            if (levelId < 0 || levelId >= count || levelId >= unlocked) continue;
            if (levelId == centerId) continue; // already selected
            float size = screenW * sizeFracs[i];
            float cx = screenW * (0.5f + offsets[i] * 0.16f);
            float x = cx - size * 0.5f;
            float y = bandCenterY - size * 0.5f;
            if (screenX >= x && screenX <= x + size && mouseY >= y && mouseY <= y + size) {
                settings.pveLevelId = levelId;
                settings.pveDifficulty = 0;
                syncDifficultyButton();
                sendLobbySettings();
                return true;
            }
        }
        return false;
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        // Delegate to the retained chat screen so its state (chat lines, player list) stays
        // fresh, and so StartGameBroadcast/RoomClosedBroadcast handling works the same
        // regardless of whether Chat or Settings is the currently active screen.
        chatScreen.passClientPacket(w);
    }
}
