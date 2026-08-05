package me.ethanchen.lwjgl3.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Draws one player's character portrait with a circular "donut" meter ring around it, for the
 * in-game HUD (implementation.md, Part 1: "meter that fills during gameplay ... when the meter is
 * full, the player can activate the ability"). Placed to the right of the board in
 * {@code GameScreen}, one per seated player. The label under the portrait is the player's display
 * name (portrait already identifies the character).
 */
public final class CharacterMeterRenderer {
    private CharacterMeterRenderer() {}

    private static final Color RING_BG = new Color(0.25f, 0.25f, 0.25f, 1f);
    private static final Color READY_FLASH = new Color(1f, 1f, 1f, 0.35f);

    /**
     * @param characterId -1 if no character is active for this slot (nothing is drawn)
     * @param playerName  display name drawn under the portrait (may be null/empty)
     * @param fill        current meter amount
     * @param max         meter amount required to activate (0 disables the ring)
     * @param slotColor   this player's ripple/board color, used to tint the fill ring
     * @param boxSize     square footprint of the whole widget (portrait + ring + label)
     */
    public static void draw(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font,
                             int characterId, String playerName, float fill, float max, Color slotColor,
                             float x, float y, float boxSize) {
        if (characterId < 0) return;

        float cx = x + boxSize * 0.5f;
        float cy = y + boxSize * 0.55f;
        float outerRadius = boxSize * 0.5f;
        float ringThickness = boxSize * 0.12f;
        float innerRadius = outerRadius - ringThickness;
        float pct = max > 0f ? Math.max(0f, Math.min(1f, fill / max)) : 0f;
        boolean ready = pct >= 1f;

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(RING_BG);
        shapes.circle(cx, cy, outerRadius, 48);
        if (pct > 0f) {
            shapes.setColor(slotColor);
            shapes.arc(cx, cy, outerRadius, 90f, -pct * 360f, 48);
        }
        // Punch the portrait hole out of the ring using the screen clear color (matches backdrop).
        shapes.setColor(0f, 0f, 0f, 1f);
        shapes.circle(cx, cy, innerRadius, 40);
        if (ready) {
            shapes.setColor(READY_FLASH);
            shapes.circle(cx, cy, outerRadius, 48);
        }
        shapes.end();

        Texture portrait = CharacterAssets.portraitFor(characterId);
        float portraitSize = innerRadius * 2f * 0.92f;
        sprites.begin();
        sprites.setColor(Color.WHITE);
        sprites.draw(portrait, cx - portraitSize * 0.5f, cy - portraitSize * 0.5f, portraitSize, portraitSize);
        sprites.end();

        if (playerName != null && !playerName.isEmpty()) {
            GlyphLayout layout = new GlyphLayout();
            float savedX = font.getScaleX(), savedY = font.getScaleY();
            font.getData().setScale(1f);
            float fs = 0.35f * (boxSize / font.getData().lineHeight);
            font.getData().setScale(fs);
            layout.setText(font, playerName);
            sprites.begin();
            font.setColor(ready ? Color.YELLOW : Color.WHITE);
            font.draw(sprites, playerName, x + (boxSize - layout.width) * 0.5f, y + boxSize * 0.08f + layout.height);
            sprites.end();
            font.setColor(Color.WHITE);
            font.getData().setScale(savedX, savedY);
        }
    }
}
