package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.SpinType;

class ClearSpinStatsTest {

    @Test
    void recordsFourLineClearsTSpinsAndAllSpinsPerPlayer() {
        ClearSpinStats stats = new ClearSpinStats(2);

        stats.record(clear(0, 4, SpinType.NONE));
        stats.record(clear(0, 4, SpinType.ALL_SPIN));
        stats.record(clear(0, 1, SpinType.T_SPIN));
        stats.record(clear(0, 2, SpinType.T_SPIN));
        stats.record(clear(0, 3, SpinType.T_SPIN));
        stats.record(clear(0, 1, SpinType.T_SPIN_MINI));
        stats.record(clear(0, 2, SpinType.SMALL_SPIN));
        stats.record(clear(0, 1, SpinType.ALL_SPIN));
        stats.record(clear(1, 4, SpinType.NONE));
        stats.record(clear(1, 0, SpinType.ALL_SPIN));

        assertArrayEquals(new int[]{2, 1}, stats.fourLineClears);
        assertArrayEquals(new int[]{1, 0}, stats.tSpinSingles);
        assertArrayEquals(new int[]{1, 0}, stats.tSpinDoubles);
        assertArrayEquals(new int[]{1, 0}, stats.tSpinTriples);
        assertArrayEquals(new int[]{2, 0}, stats.allSpinClears);
    }

    @Test
    void copyIsIndependent() {
        ClearSpinStats stats = new ClearSpinStats(1);
        stats.record(clear(0, 4, SpinType.NONE));
        ClearSpinStats frozen = stats.copy();
        stats.record(clear(0, 4, SpinType.NONE));
        assertArrayEquals(new int[]{1}, frozen.fourLineClears);
        assertArrayEquals(new int[]{2}, stats.fourLineClears);
    }

    private static LineClearResult clear(int playerId, int lines, SpinType spin) {
        LineClearResult r = new LineClearResult();
        r.playerId = playerId;
        r.spinType = spin;
        r.clearedRows = new int[lines];
        return r;
    }
}
