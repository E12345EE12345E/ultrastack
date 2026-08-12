package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.pve.GarbageInterval;
import me.ethanchen.game.pve.GarbageStyle;

class GarbageIntervalRunnerTest {

    @Test
    void doesNotFireBeforeInitialDelay() {
        GarbageIntervalRunner runner = new GarbageIntervalRunner();
        runner.reset(new GarbageInterval[]{ new GarbageInterval(1000, 500, GarbageStyle.DEFAULT, 1) });

        assertEquals(0, runner.tick(499));
    }

    @Test
    void firesOnceInitialDelayElapses() {
        GarbageIntervalRunner runner = new GarbageIntervalRunner();
        runner.reset(new GarbageInterval[]{ new GarbageInterval(1000, 500, GarbageStyle.DEFAULT, 2) });

        assertEquals(0, runner.tick(400));
        // Crossing the 500ms initial mark should immediately count toward the first interval too,
        // but not fire until a full intervalMs (1000ms) has additionally elapsed.
        assertEquals(0, runner.tick(100)); // now at 500ms elapsed (initial just crossed)
        assertEquals(0, runner.tick(900)); // 1400ms total: 900ms past the initial mark, not yet 1000
        assertEquals(2, runner.tick(100)); // 1500ms total: 1000ms past the initial mark -> fires
        assertEquals(GarbageStyle.DEFAULT, runner.style());
    }

    @Test
    void firesRepeatedlyAndCanFireMultipleTimesInOneTick() {
        GarbageIntervalRunner runner = new GarbageIntervalRunner();
        runner.reset(new GarbageInterval[]{ new GarbageInterval(100, 0, GarbageStyle.DOUBLE_HOLE, 1) });

        // A single large tick spanning 3.5 intervals should fire 3 times (amount=1 each).
        assertEquals(3, runner.tick(350));
        assertEquals(GarbageStyle.DOUBLE_HOLE, runner.style());
    }

    @Test
    void multipleIntervalsAccumulateIndependently() {
        GarbageIntervalRunner runner = new GarbageIntervalRunner();
        runner.reset(new GarbageInterval[]{
                new GarbageInterval(200, 0, GarbageStyle.DEFAULT, 1),
                new GarbageInterval(300, 0, GarbageStyle.DEFAULT, 5),
        });

        // At t=300: interval 1 has fired once (200), interval 2 has fired once (300) -> total 1+5=6
        assertEquals(6, runner.tick(300));
    }

    @Test
    void resetClearsAccumulatedProgress() {
        GarbageIntervalRunner runner = new GarbageIntervalRunner();
        runner.reset(new GarbageInterval[]{ new GarbageInterval(1000, 0, GarbageStyle.DEFAULT, 1) });
        runner.tick(900);

        runner.reset(new GarbageInterval[]{ new GarbageInterval(1000, 0, GarbageStyle.DEFAULT, 1) });
        assertEquals(0, runner.tick(200)); // would have fired at 900+200=1100 if not reset
    }

    @Test
    void emptyIntervalsNeverFire() {
        GarbageIntervalRunner runner = new GarbageIntervalRunner();
        runner.reset(null);
        assertEquals(0, runner.tick(100000));
    }
}
