package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.menuscreens.ui.AspectLockedViewport;

/**
 * Per-frame draw bundle for decorated UI. Pushed on a {@link ThreadLocal} for the duration of
 * a {@link me.ethanchen.lwjgl3.menuscreens.DecoratedMenuScreen} render so a bridging
 * {@code render(shapes, sprites, font)} can still find clocks and the active viewport.
 */
public final class DecorContext {
    private static final ThreadLocal<DecorContext> CURRENT = new ThreadLocal<>();

    public final ShapeRenderer shapes;
    public final SpriteBatch sprites;
    public final BitmapFont font;
    public final AspectLockedViewport viewport;
    public final long menuElapsedMs;
    public final long appElapsedMs;
    public final float dtS;

    public DecorContext(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font,
                        AspectLockedViewport viewport,
                        long menuElapsedMs, long appElapsedMs, float dtS) {
        this.shapes = shapes;
        this.sprites = sprites;
        this.font = font;
        this.viewport = viewport;
        this.menuElapsedMs = menuElapsedMs;
        this.appElapsedMs = appElapsedMs;
        this.dtS = dtS;
    }

    public static void push(DecorContext ctx) {
        CURRENT.set(ctx);
    }

    public static void pop() {
        CURRENT.remove();
    }

    public static DecorContext current() {
        return CURRENT.get();
    }
}
