package me.ethanchen.game;

/**
 * Shared tuning constants used across the server, game simulation, and client rendering.
 * Centralized here so gameplay timings only need to change in one place.
 */
public final class GameConstants {
    private GameConstants() {}

    /** Maximum number of players supported by any {@link me.ethanchen.game.board.Board} preset. */
    public static final int MAX_PLAYERS = 4;

    /** Initial gravity interval (ms per row) for MULTIPLAYER_SCORE mode. */
    public static final int INITIAL_GRAVITY_MS = 1000;

    /** Total duration of a MULTIPLAYER_SCORE match once it starts. */
    public static final long SCORE_MODE_DURATION_MS = 4L * 60 * 1000;

    /** Minimum global cooldown between hold actions across all players on a board. */
    public static final long HOLD_GLOBAL_LOCK_MS = 1000;

    /** How long hard drops are suppressed for a player right after an auto-lock. */
    public static final long HARD_DROP_SUPPRESS_MS = 250L;

    // Blocked-spawn cycling / explode countdown tuning.
    public static final float CYCLE_START = 1.0f;
    public static final float CYCLE_MULT = 0.8f;
    public static final float CYCLE_MIN = 0.25f;
    public static final long COYOTE_MS = 50L;
    public static final float EXPLODE_DURATION = 2.0f;
    public static final float EXPLODE_MIN_INTERVAL = 0.1f;

    // Score multipliers (MULTIPLAYER_SCORE mode).
    public static final double B2B_MULTIPLIER = 1.25;
    public static final double COMBO_MULTIPLIER = 1.5;
    public static final double GLOW_MULTIPLIER = 2.0;
    public static final double DIFF_COLUMN_MULTIPLIER = 1.2;

    // Gravity ramp applied per line cleared.
    public static final int GRAVITY_FLOOR_MS = 50;
    public static final double GRAVITY_RAMP = 0.95;

    /** Number of grounded moves allowed before a piece is force-locked. */
    public static final int MOVEMENT_LOCK_COUNTER_LIMIT = 15;

    /** Lock delay (ms) before a grounded piece is auto-locked. */
    public static final float LOCK_DELAY_MS = 500f;

    /** Server/room tick rate in milliseconds (~60 Hz). */
    public static final long TICK_MS = 16;
}
