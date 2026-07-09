package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;

/**
 * Shared font-scale helpers for all UI widgets.
 *
 * <p>A "unit" multiplier ({@code mult = 1}) scales the font so that a typical glyph is
 * approximately {@value #REF_LINE_HEIGHT}px tall at a {@value #REF_HEIGHT}px window height,
 * then scales linearly with the actual window height so the UI stays proportional on any
 * resolution.
 */
public final class UIFont {

    static final float REF_HEIGHT      = 640f;
    static final float REF_LINE_HEIGHT = 15f;

    private UIFont() {}

    /**
     * Sets the font scale so that {@code mult = 1} produces a ~{@value #REF_LINE_HEIGHT}px glyph
     * at {@value #REF_HEIGHT}px window height.
     *
     * <p>The caller is responsible for saving and restoring the previous scale if needed.
     */
    public static void setScale(BitmapFont font, float mult) {
        font.getData().setScale(1f);
        float adj = REF_LINE_HEIGHT / font.getData().lineHeight;
        font.getData().setScale(mult * adj * (Gdx.graphics.getHeight() / REF_HEIGHT));
    }

    /**
     * Saves the current font scale, calls {@link #setScale(BitmapFont, float)}, and returns
     * the saved scale as a two-element {@code float[]} {@code {scaleX, scaleY}}.
     *
     * <p>Pass the returned array to {@link #restoreScale(BitmapFont, float[])} when done.
     */
    public static float[] saveAndSetScale(BitmapFont font, float mult) {
        float[] saved = { font.getScaleX(), font.getScaleY() };
        setScale(font, mult);
        return saved;
    }

    /**
     * Restores font scale from a saved array returned by {@link #saveAndSetScale}.
     */
    public static void restoreScale(BitmapFont font, float[] saved) {
        font.getData().setScale(saved[0], saved[1]);
    }
}
