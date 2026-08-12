package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.pve.GarbageStyle;
import me.ethanchen.game.pve.boss.BossAttack;
import me.ethanchen.game.pve.boss.BossDef;

class BossControllerTest {

    private static BossDef oneAttackBoss(long idle, long windup, long attack, long interrupt, int hp) {
        return new BossDef(99, hp, 500L, new BossAttack[]{
                BossAttack.addGarbage(idle, windup, attack, interrupt, 2, GarbageStyle.DEFAULT)
        });
    }

    @Test
    void scoreDepletesHpAndReportsDefeat() {
        AtomicInteger garbage = new AtomicInteger();
        BossController boss = new BossController(oneAttackBoss(10_000, 10_000, 1000, 99999, 100),
                (amount, style, rng) -> garbage.addAndGet(amount));

        assertFalse(boss.tick(10, 40));
        assertEquals(60, boss.getHp());
        assertTrue(boss.tick(10, 100));
        assertEquals(0, boss.getHp());
        assertTrue(boss.isDefeated());
        assertEquals(0, garbage.get()); // never left idle into attack
    }

    @Test
    void windupInterruptStunsWithoutApplyingEffect() {
        AtomicInteger garbage = new AtomicInteger();
        BossController boss = new BossController(oneAttackBoss(0, 2000, 1000, 50, 10_000),
                (amount, style, rng) -> garbage.addAndGet(amount));

        // idleMs=0 → immediately in WINDUP
        assertEquals(BossController.Phase.WINDUP, boss.getPhase());
        // Score during windup reaches interrupt threshold
        boss.tick(10, 50);
        assertEquals(BossController.Phase.STUNNED, boss.getPhase());
        assertEquals(0, garbage.get());

        // After stun, resumes at next attack (wraps to same single attack); idleMs=0 so
        // beginAttack immediately re-enters WINDUP.
        boss.tick(500, 50);
        assertEquals(BossController.Phase.WINDUP, boss.getPhase());
    }

    @Test
    void attackAppliesGarbageOnce() {
        AtomicInteger garbage = new AtomicInteger();
        BossController boss = new BossController(oneAttackBoss(0, 0, 500, 99999, 10_000),
                (amount, style, rng) -> garbage.addAndGet(amount));

        // idle+windup skipped → ATTACK, effect applied on first tick
        boss.tick(10, 0);
        assertEquals(BossController.Phase.ATTACK, boss.getPhase());
        assertEquals(2, garbage.get());
        boss.tick(10, 0);
        assertEquals(2, garbage.get()); // still once
    }

    @Test
    void phaseDurationsMatchAttackDef() {
        BossController boss = new BossController(oneAttackBoss(1000, 2000, 500, 99999, 10_000),
                (a, s, r) -> {});
        assertEquals(BossController.Phase.IDLE, boss.getPhase());
        assertEquals(1000L, boss.getPhaseDurationMs());
        boss.tick(1000, 0);
        assertEquals(BossController.Phase.WINDUP, boss.getPhase());
        assertEquals(2000L, boss.getPhaseDurationMs());
        boss.tick(2000, 0);
        assertEquals(BossController.Phase.ATTACK, boss.getPhase());
        assertEquals(500L, boss.getPhaseDurationMs());
    }
}
