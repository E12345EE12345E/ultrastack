package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.menuscreens.MenuScreen;

public class UIKeybindButton extends UIButton {
    private boolean listening = false;
    private int boundKey;

    public UIKeybindButton(double x, double y, double w, double h, int initialKey) {
        super(x, y, w, h, keyName(initialKey), null);
        this.boundKey = initialKey;
    }

    public int getBoundKey() {
        return boundKey;
    }

    public boolean isListening() {
        return listening;
    }

    /** Cancels listening without changing the bound key. */
    public void cancelListening() {
        listening = false;
        text = keyName(boundKey);
    }

    /** Clears the bound key to unbound (-1). */
    public void clearKey() {
        boundKey = -1;
        listening = false;
        text = keyName(-1);
    }

    /** Binds the given keycode and exits listening mode. */
    public void bindKey(int keycode) {
        boundKey = keycode;
        listening = false;
        text = keyName(keycode);
        glow = 1.0f;
    }

    @Override
    public void onClick() {
        listening = true;
        text = "Press any key...";
        glow = 0f;
    }

    @Override
    public void render(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        float pxWidth  = MenuScreen.convertFromRelCoordsX((float) width);
        float pxHeight = (float) (height * Gdx.graphics.getHeight());
        float pxX = MenuScreen.convertFromRelCoordsX((float) centerX) - 0.5f * pxWidth;
        float pxY = (Gdx.graphics.getHeight() - MenuScreen.convertFromRelCoordsY((float) centerY)) - 0.5f * pxHeight;

        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
        hovered = !listening && mouseX >= pxX && mouseX <= pxX + pxWidth && mouseY >= pxY && mouseY <= pxY + pxHeight;

        if (glow > 0f) glow = Math.max(0f, glow - 0.05f);

        Gdx.gl.glEnable(GL20.GL_BLEND);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (listening) {
            shapes.setColor(0.12f, 0.10f, 0.02f, 1f);
        } else if (!hovered) {
            shapes.setColor(0.15f, 0.15f, 0.2f, 1f);
        }
        shapes.rect(pxX, pxY, pxWidth, pxHeight);
        float totalGlowOpacity = (hovered ? 0.15f : 0f) + glow * 0.5f;
        if (totalGlowOpacity > 0f) {
            shapes.setColor(1f, 1f, 1f, Math.min(1f, totalGlowOpacity));
            shapes.rect(pxX, pxY, pxWidth, pxHeight);
        }
        shapes.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (listening) {
            shapes.setColor(Color.YELLOW);
        } else if (hovered) {
            shapes.setColor(Color.CYAN);
        } else {
            shapes.setColor(Color.WHITE);
        }
        shapes.rect(pxX, pxY, pxWidth, pxHeight);
        shapes.end();

        float originalScaleX = font.getScaleX();
        float originalScaleY = font.getScaleY();
        font.getData().setScale(1f);
        float scaleAdjustment = 15f / font.getData().lineHeight;
        font.getData().setScale(size * scaleAdjustment * (Gdx.graphics.getHeight() / 640f));

        sprites.begin();
        GlyphLayout layout = new GlyphLayout(font, (text != null) ? text : "");
        float textX = pxX + (pxWidth - layout.width) / 2f;
        float textY = pxY + (pxHeight + layout.height) / 2f;
        font.setColor(listening ? Color.YELLOW : Color.WHITE);
        font.draw(sprites, (text != null) ? text : "", textX, textY);
        sprites.end();

        font.getData().setScale(originalScaleX, originalScaleY);
    }

    static String keyName(int keycode) {
        if (keycode == -1) return "";
        String name = Input.Keys.toString(keycode);
        return (name != null) ? name : "Key " + keycode;
    }
}
