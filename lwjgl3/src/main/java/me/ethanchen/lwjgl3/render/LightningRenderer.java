package me.ethanchen.lwjgl3.render;

import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Draws a thick, procedural lightning bolt. A generated shape remains frozen for one frame
 * interval, then changes deterministically at the next interval.
 */
public final class LightningRenderer {
    public static final long DEFAULT_FRAME_MS = 200L;

    /** Mutable style supplied when constructing a renderer. */
    public static final class Style {
        public long frameDurationMs = DEFAULT_FRAME_MS;
        public float segmentLength = 65f;
        public float lateralJitter = 85f;
        public float outerWidth = 30f;
        public float coreWidth = 10f;
        public final Color outerColor = new Color(0.45f, 0.72f, 1f, 0.42f);
        public final Color coreColor = new Color(1f, 1f, 1f, 1f);

        public Style frameDuration(long milliseconds) {
            if (milliseconds <= 0L) throw new IllegalArgumentException("frame duration must be positive");
            frameDurationMs = milliseconds;
            return this;
        }

        public Style thickness(float outer, float core) {
            if (outer <= 0f || core <= 0f || core > outer) {
                throw new IllegalArgumentException("lightning thicknesses must satisfy 0 < core <= outer");
            }
            outerWidth = outer;
            coreWidth = core;
            return this;
        }

        public Style shape(float segmentLength, float lateralJitter) {
            if (segmentLength <= 0f || lateralJitter < 0f) {
                throw new IllegalArgumentException("invalid lightning shape");
            }
            this.segmentLength = segmentLength;
            this.lateralJitter = lateralJitter;
            return this;
        }
    }

    private static final long DEFAULT_SEED = 0x4c494748544e494eL;

    private final Style style;

    public LightningRenderer() {
        this(new Style());
    }

    public LightningRenderer(Style style) {
        if (style == null) throw new IllegalArgumentException("style is required");
        this.style = style;
    }

    /** Draws from the top edge of the current window straight toward the target. */
    public void renderFromTop(ShapeRenderer shapes, float targetX, float targetY, long elapsedMs) {
        renderFromTop(shapes, targetX, targetY, elapsedMs, DEFAULT_SEED);
    }

    /** Draws from the top edge using a caller-provided seed for an independent bolt sequence. */
    public void renderFromTop(ShapeRenderer shapes, float targetX, float targetY,
                              long elapsedMs, long seed) {
        render(shapes, targetX, Gdx.graphics.getHeight(), targetX, targetY, elapsedMs, seed);
    }

    /**
     * Draws between arbitrary screen-space points. This method owns the ShapeRenderer begin/end
     * pair, so callers must end any active SpriteBatch or ShapeRenderer pass first.
     */
    public void render(ShapeRenderer shapes, float startX, float startY,
                       float targetX, float targetY, long elapsedMs, long seed) {
        if (shapes == null) throw new IllegalArgumentException("ShapeRenderer is required");

        long duration = Math.max(1L, style.frameDurationMs);
        long frame = Math.max(0L, elapsedMs) / duration;
        Random rng = new Random(mixSeed(seed, frame));

        float dx = targetX - startX;
        float dy = targetY - startY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        int segments = Math.max(4, (int) Math.ceil(distance / Math.max(1f, style.segmentLength)));
        float[] xs = new float[segments + 1];
        float[] ys = new float[segments + 1];
        xs[0] = startX;
        ys[0] = startY;
        xs[segments] = targetX;
        ys[segments] = targetY;

        float perpendicularX = distance <= 0.001f ? 1f : -dy / distance;
        float perpendicularY = distance <= 0.001f ? 0f : dx / distance;
        for (int i = 1; i < segments; i++) {
            float t = i / (float) segments;
            float envelope = (float) Math.sin(Math.PI * t);
            float offset = (rng.nextFloat() * 2f - 1f) * style.lateralJitter * envelope;
            xs[i] = startX + dx * t + perpendicularX * offset;
            ys[i] = startY + dy * t + perpendicularY * offset;
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPath(shapes, xs, ys, style.outerWidth, style.outerColor);
        drawPath(shapes, xs, ys, style.coreWidth, style.coreColor);
        shapes.end();
    }

    private static void drawPath(ShapeRenderer shapes, float[] xs, float[] ys,
                                 float width, Color color) {
        shapes.setColor(color);
        float radius = width * 0.5f;
        for (int i = 0; i < xs.length - 1; i++) {
            shapes.rectLine(xs[i], ys[i], xs[i + 1], ys[i + 1], width);
        }
        for (int i = 1; i < xs.length - 1; i++) {
            shapes.circle(xs[i], ys[i], radius, 12);
        }
        shapes.circle(xs[xs.length - 1], ys[ys.length - 1], radius, 12);
    }

    private static long mixSeed(long seed, long frame) {
        long value = seed ^ (frame * 0x9e3779b97f4a7c15L);
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
