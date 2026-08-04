package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.menuscreens.MenuScreen;

/**
 * A square button that draws a centred icon texture instead of (or alongside) text: used for
 * character portraits, artifact grid tiles, equip slots, and fusion slots throughout the
 * character/artifact screens (implementation.md, Part 5). Grayscale/selection/overlay state is
 * exposed as plain fields so callers can drive it directly instead of subclassing.
 */
public class UIIconButton extends UIElement {
    public Texture icon;
    public Runnable action;
    public boolean hovered;
    public boolean selected;
    /** Renders {@link #icon} desaturated and dims the border (locked characters, unusable mode). */
    public boolean grayscale;
    /** Small text in the bottom-right corner, e.g. an artifact's level. */
    public String cornerLabel;
    /** Painted as a translucent tint over the whole tile (e.g. red for "queued for fusion"). */
    public Color overlayColor;
    /** Shown centred when {@link #icon} is null (e.g. "Empty" for an unequipped slot). */
    public String placeholderText;

    public UIIconButton(double x, double y, double size, Texture icon, Runnable action) {
        super(x, y, size, size);
        this.icon = icon;
        this.action = action;
    }

    @Override
    public void render(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        float pxSize = MenuScreen.convertFromRelCoordsX((float) width);
        float pxX = MenuScreen.convertFromRelCoordsX((float) centerX) - 0.5f * pxSize;
        float pxY = (Gdx.graphics.getHeight() - MenuScreen.convertFromRelCoordsY((float) centerY)) - 0.5f * pxSize;

        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
        hovered = mouseX >= pxX && mouseX <= pxX + pxSize && mouseY >= pxY && mouseY <= pxY + pxSize;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.12f, 0.12f, 0.15f, 1f);
        shapes.rect(pxX, pxY, pxSize, pxSize);
        shapes.end();

        if (icon != null) {
            float iconSize = pxSize * 0.8f;
            Color tint = grayscale ? new Color(0.4f, 0.4f, 0.4f, 1f) : Color.WHITE;
            sprites.begin();
            sprites.setColor(tint);
            sprites.draw(icon, pxX + (pxSize - iconSize) * 0.5f, pxY + (pxSize - iconSize) * 0.5f, iconSize, iconSize);
            sprites.setColor(Color.WHITE);
            sprites.end();
        } else if (placeholderText != null) {
            GlyphLayout layout = new GlyphLayout();
            float[] saved = UIFont.saveAndSetScale(font, 0.7f);
            layout.setText(font, placeholderText);
            sprites.begin();
            font.setColor(0.5f, 0.5f, 0.5f, 1f);
            font.draw(sprites, placeholderText, pxX + (pxSize - layout.width) * 0.5f, pxY + (pxSize + layout.height) * 0.5f);
            sprites.end();
            font.setColor(Color.WHITE);
            UIFont.restoreScale(font, saved);
        }

        if (overlayColor != null) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(overlayColor);
            shapes.rect(pxX, pxY, pxSize, pxSize);
            shapes.end();
        }

        if (hovered) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(1f, 1f, 1f, 0.12f);
            shapes.rect(pxX, pxY, pxSize, pxSize);
            shapes.end();
        }

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(selected ? Color.YELLOW : (grayscale ? Color.GRAY : Color.WHITE));
        shapes.rect(pxX, pxY, pxSize, pxSize);
        shapes.end();

        if (cornerLabel != null && !cornerLabel.isEmpty()) {
            GlyphLayout layout = new GlyphLayout();
            float[] saved = UIFont.saveAndSetScale(font, 0.55f);
            layout.setText(font, cornerLabel);
            sprites.begin();
            font.setColor(Color.WHITE);
            font.draw(sprites, cornerLabel, pxX + pxSize - layout.width - 2f, pxY + layout.height + 2f);
            sprites.end();
            UIFont.restoreScale(font, saved);
        }
    }

    @Override
    public void onClick() {
        if (action != null) action.run();
    }

    @Override
    public void handleClick(int screenX, int screenY) {
        if (isClicked(screenX, screenY)) onClick();
    }

    @Override
    public void handleKeyTyped(char key) {}
}
