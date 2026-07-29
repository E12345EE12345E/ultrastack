package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.game.GameMode;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.packets.s2c.EndGameBroadcast;
import me.ethanchen.network.packets.s2c.RoomClosedBroadcast;
import me.ethanchen.network.packets.s2c.StartGameBroadcast;

public class EndGameScreen extends MenuScreen {
    private boolean isHost;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = new PacketDispatcher<ClientPacketWrapper>()
            // A host who returns to the lobby first can start a new game while other players
            // are still viewing results, so pull them straight into the GameScreen too.
            .on(StartGameBroadcast.class, w -> app.switchMenu(new GameScreen(app, (StartGameBroadcast) w.packet, app.isRoomHost())))
            .on(RoomClosedBroadcast.class, w -> handleRoomClosed());

    public EndGameScreen(ClientApp app, EndGameBroadcast pkt, boolean isHost) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        // Prefer live host flag (may have transferred mid-game via HostChangedBroadcast).
        this.isHost = app.isRoomHost() || isHost;

        String title = pkt.win ? "VICTORY" : "DEFEAT";
        elements.add(new UIText(0.5, 0.85, title, 5));

        // Player list (ordered by id)
        double startY = 0.68;
        double stepY  = 0.07;
        if (pkt.playerNames != null) {
            for (int i = 0; i < pkt.playerNames.length; i++) {
                String label = "Player " + (i + 1) + ": " + pkt.playerNames[i];
                elements.add(new UIText(0.5, startY - i * stepY, label, 2));
            }
        }

        // Score-mode final score and time survived
        if (pkt.mode == GameMode.MULTIPLAYER_SCORE && pkt.scoreModeEnd != null) {
            double scoreY = startY - (pkt.playerNames != null ? pkt.playerNames.length : 0) * stepY - 0.04;
            elements.add(new UIText(0.5, scoreY, "Final Score: " + pkt.scoreModeEnd.finalScore, 2.5));
            long ms = pkt.scoreModeEnd.timeSurvivedMs;
            long mins = ms / 60000;
            long secs = (ms % 60000) / 1000;
            String timeText = "Time: " + mins + ":" + String.format("%02d", secs);
            elements.add(new UIText(0.5, scoreY - stepY, timeText, 2.5));
        }

        // Puzzle-mode final time (the timer value at win/loss is the displayed result)
        if (pkt.mode == GameMode.MULTIPLAYER_PUZZLE && pkt.puzzleModeEnd != null) {
            double scoreY = startY - (pkt.playerNames != null ? pkt.playerNames.length : 0) * stepY - 0.04;
            long ms = pkt.puzzleModeEnd.timeMs;
            long mins = ms / 60000;
            long secs = (ms % 60000) / 1000;
            String timeText = "Time: " + mins + ":" + String.format("%02d", secs);
            elements.add(new UIText(0.5, scoreY, timeText, 2.5));
        }

        elements.add(new UIButton(0.5, 0.15, 0.4, 0.1, "Back to Menu", this::backToRoom));
    }

    private void backToRoom() {
        // Still a member of the same room (game end doesn't remove anyone from the room), so
        // just return to its lobby rather than leaving and going back to the room browser/LAN menu.
        app.switchMenu(new MultiplayerLobby(app, app.isRoomHost()));
    }

    @Override
    protected void onEscPressed() {
        backToRoom();
    }

    @Override
    public void update() {
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        dispatcher.dispatch(w);
    }

    private void handleRoomClosed() {
        // Host left the lobby — return to the room browser (stay connected online).
        if (app.isLanMode()) {
            app.disconnect();
            app.switchMenu(new LanMenu(app));
        } else {
            app.switchMenu(new RoomBrowserMenu(app));
        }
    }

}
