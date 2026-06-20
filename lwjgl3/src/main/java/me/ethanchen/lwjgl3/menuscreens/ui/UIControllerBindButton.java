package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.menuscreens.MenuScreen;

public class UIControllerBindButton extends UIButton {
    private static final int UNBOUND = -1;

    private boolean listening = false;
    private int boundButton;

    public UIControllerBindButton(double x, double y, double w, double h, int initialButton) {
        super(x, y, w, h, buttonName(initialButton), null);
        this.boundButton = initialButton;
    }

    public int getBoundButton() {
        return boundButton;
    }

    public boolean isListening() {
        return listening;
    }

    public void cancelListening() {
        listening = false;
        text = buttonName(boundButton);
    }

    /** Clears the bound button to unbound (-1). */
    public void clearButton() {
        boundButton = UNBOUND;
        listening = false;
        text = buttonName(UNBOUND);
    }

    public void bindButton(int buttonIndex) {
        boundButton = buttonIndex;
        listening = false;
        text = buttonName(buttonIndex);
        glow = 1.0f;
    }

    @Override
    public void onClick() {
        listening = true;
        text = "Press button...";
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
            shapes.setColor(0.02f, 0.10f, 0.12f, 1f);
        } else if (!hovered) {
            shapes.setColor(0.10f, 0.15f, 0.20f, 1f);
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
            shapes.setColor(Color.CYAN);
        } else if (hovered) {
            shapes.setColor(Color.CYAN);
        } else {
            shapes.setColor(new Color(0.5f, 0.7f, 1.0f, 1f));
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
        font.setColor(listening ? Color.CYAN : Color.WHITE);
        font.draw(sprites, (text != null) ? text : "", textX, textY);
        sprites.end();

        font.getData().setScale(originalScaleX, originalScaleY);
    }

    public static String buttonName(int idx) {
        switch (idx) {
            case UNBOUND: return "";
            // SDL2 GameController button order (used by gdx-controllers 2.x)
            case 0:  return "A";
            case 1:  return "B";
            case 2:  return "X";
            case 3:  return "Y";
            case 4:  return "Back";
            case 5:  return "Guide";
            case 6:  return "Start";
            case 7:  return "LS";
            case 8:  return "RS";
            case 9:  return "LB";
            case 10: return "RB";
            case 11: return "D\u2191";
            case 12: return "D\u2193";
            case 13: return "D\u2190";
            case 14: return "D\u2192";
            default: return "Btn " + idx;
        }
    }
}
