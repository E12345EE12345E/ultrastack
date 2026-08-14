package me.ethanchen.lwjgl3.render;

import com.badlogic.gdx.graphics.Texture;

/**
 * One 2×2 (or larger) source-pixel chunk of the boss portrait, simulated in screen pixels
 * with the same gravity/burst feel as {@link Particle.Kind#TILE_BREAK}. Client-only.
 */
public final class BossTextureShard {
    /** Matches {@link Particle} TILE_BREAK gravity, in “tile” units/s²; multiplied by {@code unit}. */
    private static final float GRAVITY_TILES = -12f;

    public Texture texture;
    public int srcX, srcY, srcW, srcH;
    public float x, y, vx, vy;
    public float w, h;
    public float age, lifetime;
    public float gravity;

    public boolean isDead() {
        return age >= lifetime;
    }

    public float alpha() {
        if (lifetime <= 0f) return 0f;
        return Math.max(0f, 1f - age / lifetime);
    }

    public void update(int dtMs) {
        float dt = dtMs / 1000f;
        age += dt;
        x += vx * dt;
        y += vy * dt;
        vy += gravity * dt;
    }

    /** Gravity in px/s² analogous to TILE_BREAK at the given pixel-per-tile scale. */
    public static float gravityForUnit(float unit) {
        return GRAVITY_TILES * unit;
    }
}
