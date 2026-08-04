package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.menuscreens.MenuScreen;

/**
 * Non-interactive image drawn in relative screen space (Simple UI). Used for loadout previews
 * in lobbies where inventory-tile chrome would be inappropriate.
 */
public class UIImage extends UIElement {
    public Texture texture;
    public boolean grayscale;
    /** When {@link #texture} is null, optionally fill the rect with a dim placeholder. */
    public boolean showEmptyPlaceholder = true;

    public UIImage(double x, double y, double size) {
        this(x, y, size, size);
    }

    public UIImage(double x, double y, double w, double h) {
        super(x, y, w, h);
    }

    @Override
    public void render(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        float pxW = MenuScreen.toScreenWidth((float) width);
        // Simple UI: keep images square from width so aspect matches inventory-tile previews.
        float pxH = pxW;
        float pxX = MenuScreen.convertFromRelCoordsX((float) centerX) - 0.5f * pxW;
        float pxY = MenuScreen.toScreenYBottom((float) centerY) - 0.5f * pxH;

        if (texture != null) {
            Color tint = grayscale ? new Color(0.4f, 0.4f, 0.4f, 1f) : Color.WHITE;
            sprites.begin();
            sprites.setColor(tint);
            sprites.draw(texture, pxX, pxY, pxW, pxH);
            sprites.setColor(Color.WHITE);
            sprites.end();
        } else if (showEmptyPlaceholder) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.15f, 0.15f, 0.18f, 1f);
            shapes.rect(pxX, pxY, pxW, pxH);
            shapes.end();
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(grayscale ? Color.GRAY : Color.DARK_GRAY);
            shapes.rect(pxX, pxY, pxW, pxH);
            shapes.end();
        }
    }

    @Override
    public void onClick() {}

    @Override
    public void handleClick(int screenX, int screenY) {}

    @Override
    public void handleKeyTyped(char key) {}
}
