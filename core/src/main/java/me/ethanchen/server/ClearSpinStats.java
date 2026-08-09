package me.ethanchen.server;

import java.util.Arrays;

import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.SpinType;

/**
 * Mutable per-player clear/spin tallies for a single run. Frozen into score/puzzle end-game
 * payloads (and thus {@code game_results.extra_json}) at game end.
 */
final class ClearSpinStats {
    /** Clears of exactly 4 lines, indexed by player slot. */
    final int[] fourLineClears;
    /** Full T-spin singles (not mini), indexed by player slot. */
    final int[] tSpinSingles;
    /** Full T-spin doubles (not mini), indexed by player slot. */
    final int[] tSpinDoubles;
    /** Full T-spin triples, indexed by player slot. */
    final int[] tSpinTriples;
    /** All-spin line clears (non-T pieces; excludes {@link SpinType#SMALL_SPIN}), indexed by player slot. */
    final int[] allSpinClears;

    ClearSpinStats(int players) {
        fourLineClears = new int[players];
        tSpinSingles = new int[players];
        tSpinDoubles = new int[players];
        tSpinTriples = new int[players];
        allSpinClears = new int[players];
    }

    private ClearSpinStats(int[] fourLineClears, int[] tSpinSingles, int[] tSpinDoubles,
                           int[] tSpinTriples, int[] allSpinClears) {
        this.fourLineClears = fourLineClears;
        this.tSpinSingles = tSpinSingles;
        this.tSpinDoubles = tSpinDoubles;
        this.tSpinTriples = tSpinTriples;
        this.allSpinClears = allSpinClears;
    }

    void record(LineClearResult result) {
        int id = result.playerId;
        if (id < 0 || id >= fourLineClears.length) return;
        int lines = result.numClearedRows();
        if (lines <= 0) return;

        if (lines == 4) fourLineClears[id]++;

        if (result.spinType == SpinType.T_SPIN) {
            if (lines == 1) tSpinSingles[id]++;
            else if (lines == 2) tSpinDoubles[id]++;
            else if (lines == 3) tSpinTriples[id]++;
        } else if (result.spinType == SpinType.ALL_SPIN) {
            allSpinClears[id]++;
        }
    }

    ClearSpinStats copy() {
        return new ClearSpinStats(
                Arrays.copyOf(fourLineClears, fourLineClears.length),
                Arrays.copyOf(tSpinSingles, tSpinSingles.length),
                Arrays.copyOf(tSpinDoubles, tSpinDoubles.length),
                Arrays.copyOf(tSpinTriples, tSpinTriples.length),
                Arrays.copyOf(allSpinClears, allSpinClears.length));
    }
}
