package me.ethanchen.game;

import me.ethanchen.game.board.LineClearResult;

/**
 * Per-board counters and modifiers that must never leak between boards: combo/back-to-back
 * chains, the current gravity interval (ramped by scoring), and the ability-driven global
 * fall-speed factor (e.g. The Noob's disable/ramp). One instance exists per {@link
 * me.ethanchen.game.board.Board} in {@link GameHandler}.
 */
public final class BoardState {
    private int b2b = 0;
    private int combo = 0;
    private int previousComboPlayerId = -1;

    /** Match gravity interval in ms per row at full fall speed (ramped by scoring). */
    private int gravity;

    /**
     * Fall-speed factor from abilities such as The Noob's disable/ramp, scoped to this board
     * ({@code 0} = frozen, {@code 1} = full speed).
     */
    private float gravitySpeedFactor = 1f;

    public void reset(int initialGravityMs) {
        b2b = 0;
        combo = 0;
        previousComboPlayerId = -1;
        gravity = initialGravityMs;
        gravitySpeedFactor = 1f;
    }

    public int getB2b() { return b2b; }
    public int getCombo() { return combo; }
    public int getPreviousComboPlayerId() { return previousComboPlayerId; }

    public int getGravity() { return gravity; }
    public void setGravity(int g) { gravity = g; }

    public float getGravitySpeedFactor() { return gravitySpeedFactor; }
    public void setGravitySpeedFactor(float factor) {
        if (factor < 0f) factor = 0f;
        gravitySpeedFactor = factor;
    }

    /**
     * Updates this board's combo, b2b, and previousComboPlayerId counters based on the
     * result of a hard drop or falling-column landing. Must be called AFTER scoring so
     * that pre-clear values can be read during score calculation.
     */
    public void applyClearToCounters(LineClearResult r) {
        if (!r.placed) return;
        if (r.numClearedRows() == 0) {
            combo = 0;
        } else {
            combo++;
            previousComboPlayerId = r.playerId;
            if (GameHandler.isB2BEligible(r)) {
                b2b++;
            } else {
                b2b = 0;
            }
        }
    }
}
