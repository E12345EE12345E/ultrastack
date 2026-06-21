package me.ethanchen.lwjgl3.render;

/**
 * A single screen particle simulated entirely on the client.
 *
 * Coordinates are in board-tile space; {@code BoardRenderer.drawParticles()} converts
 * them to screen pixels using the same {@code originX/Y} and {@code tileSize} used to
 * draw the board.
 */
public class Particle {
    /** Particle varieties. */
    public enum Kind {
        /** One white square that fades over a few frames — no gravity. */
        FLASH,
        /** Colored shard that falls under gravity and fades over ~0.5 s. */
        TILE_BREAK,
        /**
         * Floating score text ("+NNN").  Launches with an upward velocity that damps
         * exponentially, then drifts, fading over ~1 s.
         */
        POPUP_SCORE,
        /**
         * Floating bonus-multiplier text (one line per bonus).  Rises at constant speed
         * and fades over ~1 s.
         */
        POPUP_SCORE_MULTIPLIER,
        /**
         * Game-over explosion shard for blocked pieces: white square, flies out in all
         * directions at speed similar to TILE_BREAK but with NO gravity. Fades over ~0.7 s.
         */
        PIECE_EXPLODE
    }

    public Kind kind;

    /** Board-tile coordinates of the particle center. */
    public float x;
    public float y;

    /** Velocity in board-tile units per second. */
    public float vx;
    public float vy;

    /** RGB color (alpha is derived from age). */
    public float r, g, b;

    /** Size as a fraction of one tile (e.g. 0.25 = quarter-tile). */
    public float size;

    /** Elapsed life in seconds. */
    public float age;

    /** Total lifespan in seconds. */
    public float lifetime;

    /** Score or bonus bitfield value for {@link Kind#POPUP_SCORE} / {@link Kind#POPUP_SCORE_MULTIPLIER}. */
    public int value;

    /**
     * Decoded bonus flags for {@link Kind#POPUP_SCORE_MULTIPLIER}.
     * Index 0 = B2B, 1 = DifferentColumn, 2 = Combo, 3 = Glow.
     */
    public boolean[] bonuses;

    /** Gravitational acceleration in board-tile units per second². Applied only to TILE_BREAK. */
    private static final float GRAVITY = -12f;
    /** Velocity damping factor per second for {@link Kind#POPUP_SCORE} (exponential decay). */
    private static final float POPUP_SCORE_DAMPING = 6f;
    /** Constant upward speed (tile units/s) for {@link Kind#POPUP_SCORE_MULTIPLIER}. */
    private static final float MULTIPLIER_RISE_SPEED = 1.5f;

    /** True when this particle has lived past its lifetime and should be removed. */
    public boolean isDead() {
        return age >= lifetime;
    }

    /** Current alpha computed from fractional remaining life. */
    public float alpha() {
        if (lifetime <= 0) return 0;
        return Math.max(0f, 1f - age / lifetime);
    }

    /**
     * Advances the particle by {@code dtMs} milliseconds.
     * Applies gravity to {@link Kind#TILE_BREAK} particles, exponential velocity damping
     * to {@link Kind#POPUP_SCORE}, and constant rise to {@link Kind#POPUP_SCORE_MULTIPLIER}.
     */
    public void update(int dtMs) {
        float dt = dtMs / 1000f;
        age += dt;
        if (kind == Kind.POPUP_SCORE) {
            // Exponential damping: vx/vy decay toward zero then drift
            float damping = (float) Math.exp(-POPUP_SCORE_DAMPING * dt);
            vx *= damping;
            vy *= damping;
            x += vx * dt;
            y += vy * dt;
        } else if (kind == Kind.POPUP_SCORE_MULTIPLIER) {
            y += MULTIPLIER_RISE_SPEED * dt;
        } else if (kind == Kind.PIECE_EXPLODE) {
            // No gravity — just drift outward and fade
            x += vx * dt;
            y += vy * dt;
        } else {
            x += vx * dt;
            y += vy * dt;
            if (kind == Kind.TILE_BREAK) {
                vy += GRAVITY * dt;
            }
        }
    }
}
