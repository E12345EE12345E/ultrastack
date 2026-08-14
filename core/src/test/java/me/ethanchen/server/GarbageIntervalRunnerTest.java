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
        // First wave fires at initialMs (500), not at initialMs + intervalMs.
        assertEquals(2, runner.tick(100)); // 500ms: first wave
        assertEquals(0, runner.tick(900)); // 1400ms: next wave is due at 1500
        assertEquals(2, runner.tick(100)); // 1500ms: second wave
        assertEquals(GarbageStyle.DEFAULT, runner.style());
    }

    @Test
    void initialMsZeroFiresImmediatelyThenOnInterval() {
        GarbageIntervalRunner runner = new GarbageIntervalRunner();
        runner.reset(new GarbageInterval[]{ new GarbageInterval(1000, 0, GarbageStyle.DEFAULT, 1) });

        assertEquals(1, runner.tick(1)); // first wave at t=0
        assertEquals(0, runner.tick(998));
        assertEquals(1, runner.tick(1)); // second wave at t=1000
    }

    @Test
    void firesRepeatedlyAndCanFireMultipleTimesInOneTick() {
        GarbageIntervalRunner runner = new GarbageIntervalRunner();
        runner.reset(new GarbageInterval[]{ new GarbageInterval(100, 0, GarbageStyle.DOUBLE_HOLE, 1) });

        // initialMs=0 fires at t=0, then 100/200/300 → 4 waves in a 350ms tick.
        assertEquals(4, runner.tick(350));
        assertEquals(GarbageStyle.DOUBLE_HOLE, runner.style());
    }

    @Test
    void multipleIntervalsAccumulateIndependently() {
        GarbageIntervalRunner runner = new GarbageIntervalRunner();
        runner.reset(new GarbageInterval[]{
                new GarbageInterval(200, 0, GarbageStyle.DEFAULT, 1),
                new GarbageInterval(300, 0, GarbageStyle.DEFAULT, 5),
        });

        // initialMs=0: each interval also fires immediately at t=0, then on its own cadence.
        // t=300: (0+200) + (0+300) → 1+1+5+5=12
        assertEquals(12, runner.tick(300));
    }

    @Test
    void resetClearsAccumulatedProgress() {
        GarbageIntervalRunner runner = new GarbageIntervalRunner();
        runner.reset(new GarbageInterval[]{ new GarbageInterval(1000, 500, GarbageStyle.DEFAULT, 1) });
        assertEquals(0, runner.tick(400));

        runner.reset(new GarbageInterval[]{ new GarbageInterval(1000, 500, GarbageStyle.DEFAULT, 1) });
        assertEquals(0, runner.tick(400)); // would have fired at 500 if not reset
        assertEquals(1, runner.tick(100));
    }

    @Test
    void emptyIntervalsNeverFire() {
        GarbageIntervalRunner runner = new GarbageIntervalRunner();
        runner.reset(null);
        assertEquals(0, runner.tick(100000));
    }
}
