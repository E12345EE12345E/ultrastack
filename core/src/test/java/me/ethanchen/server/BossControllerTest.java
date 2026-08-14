package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.pve.GarbageStyle;
import me.ethanchen.game.pve.boss.BossAttack;
import me.ethanchen.game.pve.boss.BossDefeatAnim;
import me.ethanchen.game.pve.boss.BossDef;
import me.ethanchen.game.pve.boss.BossIntroAnim;
import me.ethanchen.game.pve.boss.BossPhaseDef;

class BossControllerTest {

    private static BossDef oneAttackBoss(long idle, long windup, long attack, long interrupt, int hp) {
        return new BossDef(99, BossIntroAnim.FLASH_IN, new BossPhaseDef(
                hp, 500L, new BossAttack[]{
                        BossAttack.addGarbage(idle, windup, attack, interrupt, 2, GarbageStyle.DEFAULT)
                }));
    }

    /** Ticks past ENTERING (lane expand + FLASH_IN) at score 0 so later asserts match the attack cycle. */
    private static void skipIntro(BossController boss) {
        boss.tick((int) BossIntroAnim.FLASH_IN.enteringDurationMs(), 0);
    }

    @Test
    void startsInEnteringAndIsInvulnerable() {
        BossController boss = new BossController(oneAttackBoss(1000, 2000, 500, 99999, 100),
                (a, s, r) -> {});
        assertEquals(BossController.Phase.ENTERING, boss.getPhase());
        assertEquals(BossIntroAnim.FLASH_IN.enteringDurationMs(), boss.getPhaseDurationMs());
        assertFalse(boss.tick(10, 40));
        assertEquals(100, boss.getHp());
        assertEquals(BossController.Phase.ENTERING, boss.getPhase());

        skipIntro(boss);
        assertEquals(BossController.Phase.IDLE, boss.getPhase());
        assertEquals(100, boss.getHp());
        // Score after intro applies as damage.
        boss.tick(10, 40);
        assertEquals(60, boss.getHp());
    }

    @Test
    void enteringThenIdleMsZeroGoesToWindup() {
        BossController boss = new BossController(oneAttackBoss(0, 2000, 1000, 50, 10_000),
                (a, s, r) -> {});
        skipIntro(boss);
        assertEquals(BossController.Phase.WINDUP, boss.getPhase());
    }

    @Test
    void scoreDepletesHpAndReportsDefeat() {
        AtomicInteger garbage = new AtomicInteger();
        BossController boss = new BossController(oneAttackBoss(10_000, 10_000, 1000, 99999, 100),
                (amount, style, rng) -> garbage.addAndGet(amount));
        skipIntro(boss);

        assertFalse(boss.tick(10, 40));
        assertEquals(60, boss.getHp());
        assertFalse(boss.tick(10, 100));
        assertEquals(0, boss.getHp());
        assertEquals(BossController.Phase.DEFEATED, boss.getPhase());
        assertFalse(boss.isDefeated());
        assertTrue(boss.tick((int) BossDefeatAnim.DURATION_MS, 100));
        assertTrue(boss.isDefeated());
        assertEquals(0, garbage.get()); // never left idle into attack
    }

    @Test
    void windupInterruptStunsWithoutApplyingEffect() {
        AtomicInteger garbage = new AtomicInteger();
        BossController boss = new BossController(oneAttackBoss(0, 2000, 1000, 50, 10_000),
                (amount, style, rng) -> garbage.addAndGet(amount));
        skipIntro(boss);

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
        skipIntro(boss);

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
        skipIntro(boss);
        assertEquals(BossController.Phase.IDLE, boss.getPhase());
        assertEquals(1000L, boss.getPhaseDurationMs());
        boss.tick(1000, 0);
        assertEquals(BossController.Phase.WINDUP, boss.getPhase());
        assertEquals(2000L, boss.getPhaseDurationMs());
        boss.tick(2000, 0);
        assertEquals(BossController.Phase.ATTACK, boss.getPhase());
        assertEquals(500L, boss.getPhaseDurationMs());
    }

    @Test
    void maxHpEqualsSumOfPhaseHps() {
        BossDef def = new BossDef(1, BossIntroAnim.FLASH_IN,
                new BossPhaseDef(100, 500L, new BossAttack[]{
                        BossAttack.addGarbage(10_000, 10_000, 500, 99999, 2, GarbageStyle.DEFAULT)
                }),
                new BossPhaseDef(200, 500L, new BossAttack[]{
                        BossAttack.addGarbage(2500, 10_000, 500, 99999, 7, GarbageStyle.DEFAULT)
                }));
        assertEquals(300, def.maxHp);
        assertEquals(300, def.phaseStartHp[0]);
        assertEquals(200, def.phaseStartHp[1]);
        BossController boss = new BossController(def, (a, s, r) -> {});
        assertEquals(300, boss.getMaxHp());
        assertEquals(300, boss.getHp());
        assertEquals(0, boss.getPhaseIndex());
    }

    @Test
    void crossingFirstPhaseHpResetsToIdleAtStartOfNextPattern() {
        BossDef def = new BossDef(1, BossIntroAnim.FLASH_IN,
                new BossPhaseDef(100, 500L, new BossAttack[]{
                        BossAttack.addGarbage(10_000, 10_000, 500, 99999, 2, GarbageStyle.DEFAULT)
                }),
                new BossPhaseDef(200, 500L, new BossAttack[]{
                        BossAttack.addGarbage(2500, 10_000, 500, 99999, 7, GarbageStyle.DEFAULT)
                }));
        BossController boss = new BossController(def, (a, s, r) -> {});
        skipIntro(boss);
        assertEquals(BossController.Phase.IDLE, boss.getPhase());
        assertEquals(10_000L, boss.getPhaseDurationMs());
        assertEquals(0, boss.getPhaseIndex());
        assertEquals(0, boss.getAttackIndex());

        // Remaining HP 200 is the start of phase 1 (phaseStartHp[1] == 200).
        boss.tick(10, 100);
        assertEquals(200, boss.getHp());
        assertEquals(1, boss.getPhaseIndex());
        assertEquals(0, boss.getAttackIndex());
        assertEquals(BossController.Phase.IDLE, boss.getPhase());
        assertEquals(2500L, boss.getPhaseDurationMs());
    }

    @Test
    void overshootIntoLaterPhaseLandsOnMappedIndex() {
        BossDef def = new BossDef(1, BossIntroAnim.FLASH_IN,
                new BossPhaseDef(100, 500L, new BossAttack[]{
                        BossAttack.addGarbage(10_000, 10_000, 500, 99999, 1, GarbageStyle.DEFAULT)
                }),
                new BossPhaseDef(100, 500L, new BossAttack[]{
                        BossAttack.addGarbage(10_000, 10_000, 500, 99999, 2, GarbageStyle.DEFAULT)
                }),
                new BossPhaseDef(100, 500L, new BossAttack[]{
                        BossAttack.addGarbage(333, 10_000, 500, 99999, 3, GarbageStyle.DEFAULT)
                }));
        BossController boss = new BossController(def, (a, s, r) -> {});
        skipIntro(boss);

        // 300 - 250 = 50, which is inside phase 2 (hp 1–100).
        boss.tick(10, 250);
        assertEquals(50, boss.getHp());
        assertEquals(2, boss.getPhaseIndex());
        assertEquals(0, boss.getAttackIndex());
        assertEquals(BossController.Phase.IDLE, boss.getPhase());
        assertEquals(333L, boss.getPhaseDurationMs());
        assertFalse(boss.isDefeated());
    }

    @Test
    void overshootToZeroEntersDefeatedNotMidPhase() {
        BossDef def = new BossDef(1, BossIntroAnim.FLASH_IN,
                new BossPhaseDef(100, 500L, new BossAttack[]{
                        BossAttack.addGarbage(10_000, 10_000, 500, 99999, 1, GarbageStyle.DEFAULT)
                }),
                new BossPhaseDef(100, 500L, new BossAttack[]{
                        BossAttack.addGarbage(10_000, 10_000, 500, 99999, 2, GarbageStyle.DEFAULT)
                }));
        BossController boss = new BossController(def, (a, s, r) -> {});
        skipIntro(boss);

        boss.tick(10, 200);
        assertEquals(0, boss.getHp());
        assertEquals(BossController.Phase.DEFEATED, boss.getPhase());
        assertEquals(0, boss.getPhaseIndex());
        assertFalse(boss.isDefeated());
    }

    @Test
    void stunLengthUsesCurrentPhase() {
        BossDef def = new BossDef(1, BossIntroAnim.FLASH_IN,
                new BossPhaseDef(100, 500L, new BossAttack[]{
                        BossAttack.addGarbage(10_000, 2000, 500, 50, 2, GarbageStyle.DEFAULT)
                }),
                new BossPhaseDef(200, 1500L, new BossAttack[]{
                        BossAttack.addGarbage(0, 2000, 500, 10, 7, GarbageStyle.DEFAULT)
                }));
        BossController boss = new BossController(def, (a, s, r) -> {});
        skipIntro(boss);

        boss.tick(10, 100);
        assertEquals(1, boss.getPhaseIndex());
        assertEquals(BossController.Phase.WINDUP, boss.getPhase());

        boss.tick(10, 110);
        assertEquals(BossController.Phase.STUNNED, boss.getPhase());
        assertEquals(1500L, boss.getPhaseDurationMs());
    }
}
