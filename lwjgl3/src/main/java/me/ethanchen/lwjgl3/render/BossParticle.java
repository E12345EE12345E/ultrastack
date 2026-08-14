package me.ethanchen.lwjgl3.render;

import java.util.Random;

import com.badlogic.gdx.graphics.Color;

/**
 * Client-only circular combat orb around the boss portrait. Coordinates are screen pixels.
 */
public final class BossParticle {
    public enum Kind { CHARGE, EXPLODE }

    public static final float CHARGE_LIFE_S = 0.25f;
    /** Stop spawning charge orbs this many ms before windup ends. */
    public static final float CHARGE_SPAWN_CUTOFF_MS = 500f;
    public static final float CHARGE_SPAWN_PER_SEC = 56f;
    /** Donut inner radius as a fraction of portrait size (just outside the sprite). */
    public static final float CHARGE_DONUT_INNER = 0.52f;
    /** Donut outer radius as a fraction of portrait size. */
    public static final float CHARGE_DONUT_OUTER = 0.90f;
    /** Starting circle radius as a fraction of portrait size. */
    public static final float CHARGE_START_SIZE = 0.12f;
    public static final float CHARGE_START_ALPHA = 0.12f;
    public static final float CHARGE_END_ALPHA = 0.40f;

    public static final int EXPLODE_COUNT = 100;
    public static final float EXPLODE_HOLD_S = 0.5f;
    public static final float EXPLODE_FADE_S = 0.3f;
    public static final float EXPLODE_LIFE_S = EXPLODE_HOLD_S + EXPLODE_FADE_S;
    public static final float EXPLODE_SPAWN_RADIUS = 0.08f;
    public static final float EXPLODE_SIZE = 0.04f;
    public static final float EXPLODE_ALPHA = 0.35f;
    public static final float EXPLODE_SPEED_MIN = 3.5f;
    public static final float EXPLODE_SPEED_MAX = 5.5f;
    public static final float EXPLODE_DRAG_END = 0.5f;

    public static final float HUE_SAT = 0.9f;
    public static final float HUE_VAL = 1f;
    public static final int CIRCLE_SEGMENTS = 16;

    private static final Color HSV = new Color();

    public Kind kind;
    public float x, y;
    public float vx, vy;
    public float spawnX, spawnY, targetX, targetY;
    public float r, g, b;
    public float startSize, size;
    public float age, lifetime;

    public boolean isDead() {
        return age >= lifetime;
    }

    public float radius() {
        if (kind == Kind.CHARGE) {
            float t = lifetime <= 0f ? 1f : Math.min(1f, age / lifetime);
            return startSize * (1f - t);
        }
        return size;
    }

    public float alpha() {
        if (kind == Kind.CHARGE) {
            float t = lifetime <= 0f ? 1f : Math.min(1f, age / lifetime);
            return CHARGE_START_ALPHA + (CHARGE_END_ALPHA - CHARGE_START_ALPHA) * t;
        }
        if (age <= EXPLODE_HOLD_S) return EXPLODE_ALPHA;
        float fadeT = Math.min(1f, (age - EXPLODE_HOLD_S) / EXPLODE_FADE_S);
        return EXPLODE_ALPHA * (1f - fadeT);
    }

    public void update(int dtMs) {
        float dt = dtMs / 1000f;
        float prevAge = age;
        age += dt;
        if (kind == Kind.CHARGE) {
            float t = lifetime <= 0f ? 1f : Math.min(1f, age / lifetime);
            x = spawnX + (targetX - spawnX) * t;
            y = spawnY + (targetY - spawnY) * t;
            return;
        }
        float prevScale = explodeDragScale(prevAge);
        float newScale = explodeDragScale(age);
        float ratio = prevScale > 1e-6f ? newScale / prevScale : 1f;
        vx *= ratio;
        vy *= ratio;
        x += vx * dt;
        y += vy * dt;
    }

    private static float explodeDragScale(float ageS) {
        float t = Math.max(0f, Math.min(1f, ageS / EXPLODE_HOLD_S));
        return 1f + (EXPLODE_DRAG_END - 1f) * t;
    }

    public static BossParticle charge(Random rng, float cx, float cy, float portraitSize,
                                      float hueMin, float hueMax) {
        float angle = rng.nextFloat() * (float) (Math.PI * 2);
        float inner = CHARGE_DONUT_INNER * portraitSize;
        float outer = CHARGE_DONUT_OUTER * portraitSize;
        float radius = inner + rng.nextFloat() * (outer - inner);
        BossParticle p = new BossParticle();
        p.kind = Kind.CHARGE;
        p.spawnX = cx + (float) Math.cos(angle) * radius;
        p.spawnY = cy + (float) Math.sin(angle) * radius;
        p.targetX = cx;
        p.targetY = cy;
        p.x = p.spawnX;
        p.y = p.spawnY;
        p.startSize = CHARGE_START_SIZE * portraitSize;
        p.size = p.startSize;
        p.lifetime = CHARGE_LIFE_S;
        tintFromHue(p, rng, hueMin, hueMax);
        return p;
    }

    public static BossParticle explode(Random rng, float cx, float cy, float portraitSize,
                                       float hueMin, float hueMax) {
        float spawnAngle = rng.nextFloat() * (float) (Math.PI * 2);
        float spawnR = (float) Math.sqrt(rng.nextFloat()) * EXPLODE_SPAWN_RADIUS * portraitSize;
        float speed = (EXPLODE_SPEED_MIN + rng.nextFloat() * (EXPLODE_SPEED_MAX - EXPLODE_SPEED_MIN))
                * portraitSize;
        float outAngle = rng.nextFloat() * (float) (Math.PI * 2);
        BossParticle p = new BossParticle();
        p.kind = Kind.EXPLODE;
        p.x = cx + (float) Math.cos(spawnAngle) * spawnR;
        p.y = cy + (float) Math.sin(spawnAngle) * spawnR;
        p.vx = (float) Math.cos(outAngle) * speed;
        p.vy = (float) Math.sin(outAngle) * speed;
        p.size = EXPLODE_SIZE * portraitSize;
        p.startSize = p.size;
        p.lifetime = EXPLODE_LIFE_S;
        tintFromHue(p, rng, hueMin, hueMax);
        return p;
    }

    private static void tintFromHue(BossParticle p, Random rng, float hueMin, float hueMax) {
        float lo = Math.min(hueMin, hueMax);
        float hi = Math.max(hueMin, hueMax);
        float hue = lo + rng.nextFloat() * (hi - lo);
        HSV.fromHsv(hue, HUE_SAT, HUE_VAL);
        p.r = HSV.r;
        p.g = HSV.g;
        p.b = HSV.b;
    }
}
