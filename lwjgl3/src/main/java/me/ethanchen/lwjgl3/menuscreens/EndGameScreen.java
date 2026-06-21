package me.ethanchen.lwjgl3.menuscreens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.Align;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.MainMenu;
import me.ethanchen.network.packets.s2c.EndGameBroadcast;

public class EndGameScreen extends MenuScreen {
    private EndGameBroadcast data;
    private float timer = 0f;

    public EndGameScreen(ClientApp app, EndGameBroadcast data) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.data = data;
    }

    @Override
    public void update() {
        timer += Gdx.graphics.getDeltaTime();
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        sprites.begin();
        font.setColor(Color.WHITE);

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        font.draw(sprites, "GAME OVER", 0, sh * 0.8f, sw, Align.center, false);

        float y = sh * 0.6f;
        if (data.playerNames != null) {
            for (int i = 0; i < data.playerNames.length; i++) {
                String name = data.playerNames[i];
                if (name == null) name = "Player " + i;
                font.draw(sprites, name, 0, y, sw, Align.center, false);
                y -= 40f;
            }
        }

        if (data.scoreModeEnd != null) {
            y -= 20f;
            font.draw(sprites, "Final Score: " + data.scoreModeEnd.finalScore, 0, y, sw, Align.center, false);
        }

        if (timer > 1.0f) {
            font.draw(sprites, "Press any key to return to lobby", 0, sh * 0.2f, sw, Align.center, false);
        }

        sprites.end();
    }

    private void returnToLobby() {
        app.switchMenu(new MainMenu(app));
    }

    @Override
    public boolean keyDown(int keycode) {
        if (timer > 1.0f) {
            returnToLobby();
            return true;
        }
        return super.keyDown(keycode);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (timer > 1.0f) {
            returnToLobby();
            return true;
        }
        return super.touchDown(screenX, screenY, pointer, button);
    }
}
