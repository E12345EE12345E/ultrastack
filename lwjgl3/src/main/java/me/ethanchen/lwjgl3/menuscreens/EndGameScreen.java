package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.game.GameMode;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.network.packets.s2c.EndGameBroadcast;

public class EndGameScreen extends MenuScreen {
    public EndGameScreen(ClientApp app, EndGameBroadcast pkt) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

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

        // Score-mode final score
        if (pkt.mode == GameMode.MULTIPLAYER_SCORE && pkt.scoreModeEnd != null) {
            double scoreY = startY - (pkt.playerNames != null ? pkt.playerNames.length : 0) * stepY - 0.04;
            elements.add(new UIText(0.5, scoreY, "Final Score: " + pkt.scoreModeEnd.finalScore, 2.5));
        }

        elements.add(new UIButton(0.5, 0.15, 0.4, 0.1, "Back to Menu",
                () -> app.switchMenu(new MainMenu(app))));
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
}
