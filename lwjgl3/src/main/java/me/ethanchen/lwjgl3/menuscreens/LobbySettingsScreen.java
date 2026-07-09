package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.game.GameMode;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.lwjgl3.settings.LobbySettings;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.packets.c2s.StartGameRequest;

/**
 * Host-only lobby configuration screen: chooses which gamemode {@code StartGameRequest} will
 * launch. Reached from {@link MultiplayerLobby} (shared by both the online and LAN chat
 * lobbies) via a "Settings" button, and always returns to that exact chat screen instance so
 * chat history/player list state isn't lost while this screen is open.
 */
public class LobbySettingsScreen extends MenuScreen {
    private final MultiplayerLobby chatScreen;

    public LobbySettingsScreen(ClientApp app, MultiplayerLobby chatScreen) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.chatScreen = chatScreen;

        elements.add(new UIText(0.5, 0.85, "Lobby Settings", 4));

        LobbySettings settings = app.getLobbySettings();
        elements.add(new UIText(0.5, 0.65, "Game Mode", 1.5));
        UIButton modeButton = new UIButton(0.5, 0.56, 0.4, 0.1, modeLabel(settings.gamemode), null);
        modeButton.action = () -> {
            settings.gamemode = (settings.gamemode == GameMode.MULTIPLAYER_SCORE)
                    ? GameMode.MULTIPLAYER_PUZZLE : GameMode.MULTIPLAYER_SCORE;
            modeButton.text = modeLabel(settings.gamemode);
        };
        elements.add(modeButton);

        elements.add(new UIButton(0.5, 0.125, 0.3, 0.1, "Start Game", () -> {
            StartGameRequest p = new StartGameRequest();
            p.gamemode = app.getLobbySettings().gamemode;
            app.sendTCP(p);
        }));
        elements.add(new UIButton(0.82, 0.125, 0.28, 0.1, "View Chat", () -> app.switchMenu(chatScreen)));
    }

    private static String modeLabel(GameMode mode) {
        return mode == GameMode.MULTIPLAYER_PUZZLE ? "Mode: Puzzle" : "Mode: Score";
    }

    @Override
    protected void onEscPressed() {
        app.switchMenu(chatScreen);
    }

    @Override
    public void update() {
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        // Delegate to the retained chat screen so its state (chat lines, player list) stays
        // fresh, and so StartGameBroadcast/RoomClosedBroadcast handling works the same
        // regardless of whether Chat or Settings is the currently active screen.
        chatScreen.passClientPacket(w);
    }
}
