package me.ethanchen.lwjgl3.menuscreens.decorated;

/**
 * Easing and timeline helpers. Intro animations are a pure function of an elapsed clock
 * (typically {@code appElapsedMs}) so returning to a screen past the end of a timeline
 * lands in the settled pose with no extra flags.
 */
public final class Anim {
    private Anim() {}

    /** Linear 0–1 progress of {@code elapsed} through {@code [startMs, startMs + durMs]}. */
    public static float progress(long elapsed, long startMs, long durMs) {
        if (elapsed <= startMs) return 0f;
        if (durMs <= 0L) return 1f;
        float t = (elapsed - startMs) / (float) durMs;
        return t >= 1f ? 1f : t;
    }

    public static float clamp01(float t) {
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float easeInCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    public static float easeInOutCubic(float t) {
        t = clamp01(t);
        if (t < 0.5f) return 4f * t * t * t;
        float u = -2f * t + 2f;
        return 1f - (u * u * u) * 0.5f;
    }

    public static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }
}
