package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;

/**
 * Fits a fixed design resolution (e.g. 1920×1080) into the current window using the largest
 * aspect-preserving size, centered — the same letterbox/pillarbox idea as
 * {@link me.ethanchen.lwjgl3.render.BoardRenderer#computeTileSize}.
 *
 * <p>While {@link #push(AspectLockedViewport) pushed}, relative UI coordinates (0–1) are
 * interpreted as fractions of this design rect rather than the full window.
 */
public final class AspectLockedViewport {
    private static final ThreadLocal<AspectLockedViewport> CURRENT = new ThreadLocal<>();

    public final float designW;
    public final float designH;

    /** Bottom-left of the fitted rect in back-buffer pixels (Y-up). */
    public float originX;
    public float originY;
    public float viewW;
    public float viewH;
    public float scale;

    public AspectLockedViewport(float designW, float designH) {
        this.designW = designW;
        this.designH = designH;
    }

    /** Recomputes {@link #originX}/{@link #originY}/{@link #viewW}/{@link #viewH}/{@link #scale} for the current window. */
    public void update() {
        float windowW = Gdx.graphics.getWidth();
        float windowH = Gdx.graphics.getHeight();
        scale = Math.min(windowW / designW, windowH / designH);
        viewW = designW * scale;
        viewH = designH * scale;
        originX = (windowW - viewW) * 0.5f;
        originY = (windowH - viewH) * 0.5f;
    }

    public static void push(AspectLockedViewport viewport) {
        CURRENT.set(viewport);
    }

    public static void pop() {
        CURRENT.remove();
    }

    /** Active aspect-locked viewport, or {@code null} for Simple (full-window stretch) UI. */
    public static AspectLockedViewport current() {
        return CURRENT.get();
    }

    public float toScreenX(float relX) {
        return originX + relX * viewW;
    }

    /** Bottom-up screen Y of a design-relative Y (0 = bottom of design, 1 = top). */
    public float toScreenY(float relY) {
        return originY + relY * viewH;
    }

    public float toScreenW(float relW) {
        return relW * viewW;
    }

    public float toScreenH(float relH) {
        return relH * viewH;
    }

    public float toRelX(int screenX) {
        float backX = HdpiUtils.toBackBufferX(screenX);
        return (backX - originX) / viewW;
    }

    public float toRelY(int screenY) {
        float backYFromTop = HdpiUtils.toBackBufferY(screenY);
        float backYFromBottom = Gdx.graphics.getHeight() - backYFromTop;
        return (backYFromBottom - originY) / viewH;
    }
}
