package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.menuscreens.MenuScreen;

/**
 * Horizontal integer slider. The track spans the element's full width;
 * the thumb snaps to the closest integer step.
 *
 * Drag support requires the host {@link MenuScreen} to forward
 * {@code touchDragged} / {@code touchUp} via {@link #handleDrag} / {@link #handleRelease}.
 */
public class UISlider extends UIElement {

    private int value;
    private final int min;
    private final int max;
    private final String label;
    private boolean dragging = false;
    private Runnable onChange;

    public UISlider(double x, double y, double w, double h, String label, int min, int max, int initial) {
        super(x, y, w, h);
        this.label = label;
        this.min = min;
        this.max = max;
        this.value = clamp(initial);
    }

    public int getValue() { return value; }

    public void setValue(int v) {
        value = clamp(v);
    }

    /** Optional callback invoked whenever the value changes. */
    public void setOnChange(Runnable r) { onChange = r; }

    @Override
    public void render(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        float pxW    = MenuScreen.convertFromRelCoordsX((float) width);
        float pxH    = (float) (height * Gdx.graphics.getHeight());
        float pxX    = MenuScreen.convertFromRelCoordsX((float) centerX) - 0.5f * pxW;
        float pxY    = (Gdx.graphics.getHeight() - MenuScreen.convertFromRelCoordsY((float) centerY)) - 0.5f * pxH;

        float trackH   = Math.max(2f, pxH * 0.18f);
        float trackY   = pxY + pxH * 0.55f;
        float thumbR   = pxH * 0.2f;
        float thumbX   = pxX + normalised() * pxW;
        float thumbY   = trackY + trackH * 0.5f;

        Gdx.gl.glEnable(GL20.GL_BLEND);

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Track background
        shapes.setColor(0.2f, 0.2f, 0.25f, 1f);
        shapes.rect(pxX, trackY, pxW, trackH);

        // Filled portion
        shapes.setColor(0.4f, 0.7f, 1.0f, 1f);
        shapes.rect(pxX, trackY, normalised() * pxW, trackH);

        // Thumb
        shapes.setColor(dragging ? Color.CYAN : Color.WHITE);
        shapes.circle(thumbX, thumbY, thumbR, 12);

        shapes.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Labels
        float originalScaleX = font.getScaleX();
        float originalScaleY = font.getScaleY();

        font.getData().setScale(1f);
        float unscaledLineHeight = font.getData().lineHeight;
        float scaleAdjustment = 15f / unscaledLineHeight;
        float fontScale = 0.6f * scaleAdjustment * (Gdx.graphics.getHeight() / 640f);
        font.getData().setScale(fontScale);

        sprites.begin();
        font.setColor(Color.LIGHT_GRAY);

        GlyphLayout labelLayout = new GlyphLayout(font, label);
        font.draw(sprites, label, pxX + (pxW - labelLayout.width) / 2f, pxY + pxH * 0.35f);

        String valStr = String.valueOf(value);
        GlyphLayout valLayout = new GlyphLayout(font, valStr);
        font.draw(sprites, valStr, pxX + (pxW - valLayout.width) / 2f, pxY + pxH - 1f);

        sprites.end();

        font.getData().setScale(originalScaleX, originalScaleY);
    }

    @Override
    public void onClick() {
        // handled via handleClick
    }

    @Override
    public void handleClick(int screenX, int screenY) {
        if (isClicked(screenX, screenY)) {
            dragging = true;
            updateFromScreenX(screenX);
        }
    }

    @Override
    public void handleDrag(int screenX, int screenY) {
        if (dragging) {
            updateFromScreenX(screenX);
        }
    }

    @Override
    public void handleRelease(int screenX, int screenY) {
        dragging = false;
    }

    @Override
    public void handleKeyTyped(char key) {}

    private void updateFromScreenX(int screenX) {
        float relX    = MenuScreen.convertToRelCoordsX(screenX);
        float left    = (float) (centerX - width * 0.5);
        float right   = (float) (centerX + width * 0.5);
        float t       = (relX - left) / (right - left);
        t = Math.max(0f, Math.min(1f, t));
        int newValue = Math.round(min + t * (max - min));
        if (newValue != value) {
            value = newValue;
            if (onChange != null) onChange.run();
        }
    }

    private float normalised() {
        if (max == min) return 0f;
        return (float) (value - min) / (max - min);
    }

    private int clamp(int v) {
        return Math.max(min, Math.min(max, v));
    }
}
