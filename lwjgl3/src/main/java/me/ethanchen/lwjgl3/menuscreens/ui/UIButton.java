package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import me.ethanchen.lwjgl3.menuscreens.MenuScreen;

public class UIButton extends UIElement {
    public String text;
    public Runnable action;
    public boolean hovered;
    public float size = 1.0f;
    public float glow = 0.0f;

    public UIButton(double x, double y, double w, double h, String text, Runnable action) {
        this(x, y, w, h, text, action, 1.0f);
    }

    public UIButton(double x, double y, double w, double h, String text, Runnable action, double size) {
        this(x, y, w, h, text, action, (float) size);
    }

    public UIButton(double x, double y, double w, double h, String text, Runnable action, float size) {
        super(x, y, w, h);
        this.text = text;
        this.action = action;
        this.hovered = false;
        this.size = size;
    }

    @Override
    public void render(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        float pxWidth = MenuScreen.convertFromRelCoordsX((float) width);
        float pxHeight = (float) (height * Gdx.graphics.getHeight());
        float pxX = MenuScreen.convertFromRelCoordsX((float) centerX) - 0.5f * pxWidth;
        float pxY = (Gdx.graphics.getHeight() - MenuScreen.convertFromRelCoordsY((float) centerY)) - 0.5f * pxHeight;

        // Simple hover detection
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
        if (mouseX >= pxX && mouseX <= pxX + pxWidth && mouseY >= pxY && mouseY <= pxY + pxHeight) {
            hovered = true;
        } else {
            hovered = false;
        }

        // Decrease glow over time
        if (glow > 0f) {
            glow = Math.max(0f, glow - 0.05f);
        }

        // Enable blending for transparency
        Gdx.gl.glEnable(GL20.GL_BLEND);

        // Draw background and glow flash
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (!hovered) {
            shapes.setColor(0.15f, 0.15f, 0.2f, 1f);
            shapes.rect(pxX, pxY, pxWidth, pxHeight);
        }
        float totalGlowOpacity = (hovered ? 0.15f : 0f) + glow * 0.5f;
        if (totalGlowOpacity > 0f) {
            shapes.setColor(1f, 1f, 1f, Math.min(1f, totalGlowOpacity));
            shapes.rect(pxX, pxY, pxWidth, pxHeight);
        }
        shapes.end();

        // Disable blending
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Draw border
        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (hovered) {
            shapes.setColor(Color.CYAN);
        } else {
            shapes.setColor(Color.WHITE);
        }
        shapes.rect(pxX, pxY, pxWidth, pxHeight);
        shapes.end();

        // Store original scale and calculate window height scale factor
        float originalScaleX = font.getScaleX();
        float originalScaleY = font.getScaleY();
        
        font.getData().setScale(1f);
        float unscaledLineHeight = font.getData().lineHeight;
        float scaleAdjustment = 15f / unscaledLineHeight;
        
        float fontScale = size * scaleAdjustment * (Gdx.graphics.getHeight() / 640f);
        font.getData().setScale(fontScale);

        // Draw text centered inside button
        sprites.begin();
        GlyphLayout layout = new GlyphLayout(font, (text != null) ? text : "");
        float textX = pxX + (pxWidth - layout.width) / 2f;
        float textY = pxY + (pxHeight + layout.height) / 2f;

        font.setColor(Color.WHITE);
        font.draw(sprites, (text != null) ? text : "", textX, textY);
        sprites.end();

        // Restore original scale
        font.getData().setScale(originalScaleX, originalScaleY);
    }

    @Override
    public void onClick() {
        glow = 1.0f;
        if (action != null) {
            action.run();
        }
    }

    @Override
    public void handleClick(int screenX, int screenY) {
        if (isClicked(screenX, screenY)) {
            onClick();
        }
    }

    @Override
    public void handleKeyTyped(char c) {}
}
