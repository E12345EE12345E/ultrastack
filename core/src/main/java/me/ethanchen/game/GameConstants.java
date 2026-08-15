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

    /** Initial gravity interval for MULTIPLAYER_PUZZLE mode. */
    public static final int INITIAL_GRAVITY_PUZZLE_MS = 2500;

    /** Number of garbage lines pre-filled onto the board at the start of MULTIPLAYER_PUZZLE. */
    public static final int PUZZLE_GARBAGE_LINES = 8;

    /** Total duration of a MULTIPLAYER_SCORE match once it starts. */
    public static final long SCORE_MODE_DURATION_MS = 4L * 60 * 1000;

    /** Grace period (ms) MULTIPLAYER_PUZZLE spends re-broadcasting the frozen final board/timer
     *  after win/loss is detected, before EndGameBroadcast is sent. MULTIPLAYER_SCORE uses 0
     *  (ends immediately — see ServerGame.beginGameEnd). */
    public static final long PUZZLE_GAME_END_GRACE_MS = 500L;

    /** Minimum global cooldown between hold actions across all players on a board. */
    public static final long HOLD_GLOBAL_LOCK_MS = 500;

    /** How long hard drops are suppressed for a player right after an auto-lock. */
    public static final long HARD_DROP_SUPPRESS_MS = 250L;

    // Blocked-spawn cycling / explode countdown tuning.
    public static final float CYCLE_START = 1.6f;
    public static final float CYCLE_MULT = 0.8f;
    public static final float CYCLE_MIN = 0.5f;
    public static final long COYOTE_MS = 50L;
    public static final float EXPLODE_DURATION = 1.6f;
    public static final float EXPLODE_MIN_INTERVAL = 0.1f;

    // Score multipliers (MULTIPLAYER_SCORE mode).
    public static final double B2B_MULTIPLIER = 1.25;
    public static final double COMBO_MULTIPLIER = 1.5;
    public static final double GLOW_MULTIPLIER = 2.0;
    public static final double DIFF_COLUMN_MULTIPLIER = 1.2;

    // Gravity ramp applied per line cleared (MULTIPLAYER_SCORE mode).
    public static final int GRAVITY_FLOOR_MS = 50;
    public static final double GRAVITY_RAMP = 0.95;

    /** Number of grounded moves allowed before a piece is force-locked. */
    public static final int MOVEMENT_LOCK_COUNTER_LIMIT = 15;

    /** Lock delay (ms) before a grounded piece is auto-locked. Guideline: 500ms, we are doubling it for playability */
    public static final float LOCK_DELAY_MS = 1000f;

    /** Server/room tick rate in milliseconds (~60 Hz). */
    public static final long TICK_MS = 16;

    /** Countdown (ms) between a host starting a game and the game actually beginning. */
    public static final int GAME_START_DELAY_MS = 5000;

    /** Room-thread ticks between lobby player-list UDP refreshes (~160ms at 60Hz). */
    public static final int LOBBY_UDP_REFRESH_INTERVAL_TICKS = 10;

    /** Server-loop ticks between public room-list broadcasts (~5s at 60Hz). */
    public static final int ROOM_LIST_BROADCAST_INTERVAL_TICKS = 300;

    /** ServerGame ticks between net-state broadcasts (halves the ~60Hz tick rate to ~30Hz). */
    public static final int NET_UPDATE_BROADCAST_INTERVAL_TICKS = 2;

    // Base score by lines cleared (index 1-3; index 4 only populated for standard/all-spin
    // clears). Score modes / PvE; see ScoreFormulas#baseScore.
    public static final long SCORE_SINGLE = 100;
    public static final long SCORE_DOUBLE = 200;
    public static final long SCORE_TRIPLE = 300;
    public static final long SCORE_TETRIS = 800;

    /** Base score for a 3-line clear with an I3 (vertical 3-mino), analogous to a Tetris. */
    public static final long SCORE_I3_TRIPLE = 600;

    public static final long SCORE_TSPIN_SINGLE = 400;
    public static final long SCORE_TSPIN_DOUBLE = 800;
    public static final long SCORE_TSPIN_TRIPLE = 1200;

    public static final long SCORE_TSPIN_MINI_SINGLE = 200;
    public static final long SCORE_TSPIN_MINI_DOUBLE = 800;

    public static final long SCORE_ALL_SPIN_SINGLE = 150;
    public static final long SCORE_ALL_SPIN_DOUBLE = 300;
    public static final long SCORE_ALL_SPIN_TRIPLE = 450;
    public static final long SCORE_ALL_SPIN_TETRIS = 800;

    public static final long SCORE_SMALL_SPIN_SINGLE = 200;
    public static final long SCORE_SMALL_SPIN_DOUBLE = 400;
    public static final long SCORE_SMALL_SPIN_TRIPLE = 600;

    /**
     * Flat bonus added to a placement's score when it results in an All Clear (Perfect Clear).
     * Applied post-multiplication, i.e. after all other bonuses have been applied to the
     * base line-clear score. Score modes / PvE; see ScoreFormulas#scoreHardDrop.
     */
    public static final long SCORE_ALL_CLEAR_BONUS = 1000;

    // Falling-block motion (tiles/sec and tiles/sec²). Terminal ≈ 125 ms/tile; acceleration
    // reaches terminal after ~0.2 s so the first tile ramps and later tiles are full speed.
    public static final float FALL_TERMINAL_VELOCITY = 8f;
    public static final float FALL_ACCELERATION = 40f;

    /** Flat score per cleared row for a falling-block line clear (no multipliers). */
    public static final long SCORE_FALLING_PER_LINE = 150;
}
