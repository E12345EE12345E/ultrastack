package me.ethanchen.server;

import java.util.Random;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.pve.GarbageStyle;
import me.ethanchen.game.pve.boss.BossAttack;
import me.ethanchen.game.pve.boss.BossAttackEffectType;
import me.ethanchen.game.pve.boss.BossDefeatAnim;
import me.ethanchen.game.pve.boss.BossDef;
import me.ethanchen.game.pve.boss.BossIntroAnim;

/**
 * Per-boss-section phase machine: {@code ENTERING -> IDLE -> WINDUP -> ATTACK -> IDLE} (plus
 * {@code STUNNED} on windup interrupt and {@code DEFEATED} before the section advances). Score
 * gained since the boss section started depletes HP except during {@code ENTERING} and
 * {@code DEFEATED}. Score gained during a windup counts toward that attack's interrupt
 * threshold (implementation.md, Part 3).
 */
public class BossController {

    public enum Phase { IDLE, WINDUP, ATTACK, STUNNED, ENTERING, DEFEATED }

    /** Applies an attack effect to every still-running board (e.g. garbage spawn). */
    public interface AttackEffectSink {
        void addGarbage(int amount, GarbageStyle style, Random rng);
    }

    private final BossDef def;
    private final AttackEffectSink effects;
    private final Random rng = new Random();

    private int attackIndex;
    private Phase phase = Phase.ENTERING;
    private long phaseElapsedMs;
    private long phaseDurationMs;
    private long windupScoreBaseline;
    private long lastSectionScore;
    private int hp;
    private boolean defeated;
    private boolean effectApplied;

    public BossController(BossDef def, AttackEffectSink effects) {
        this.def = def;
        this.effects = effects;
        this.hp = def.maxHp;
        enterEntering();
    }

    public boolean isDefeated() { return defeated; }
    public int getHp() { return Math.max(0, hp); }
    public int getMaxHp() { return def.maxHp; }
    public int getBossId() { return def.id; }
    public Phase getPhase() { return phase; }
    public long getPhaseElapsedMs() { return phaseElapsedMs; }
    public long getPhaseDurationMs() { return phaseDurationMs; }

    /**
     * Advances the boss by {@code deltaMs}. {@code sectionScore} is the global score gained since
     * the boss section started (same value {@code PveSectionController} uses for SCORE criteria).
     *
     * @return {@code true} when the boss has just been (or was already) defeated
     */
    public boolean tick(int deltaMs, long sectionScore) {
        if (defeated) return true;

        long scoreDelta = Math.max(0L, sectionScore - lastSectionScore);
        lastSectionScore = sectionScore;
        if (scoreDelta > 0 && phase != Phase.ENTERING && phase != Phase.DEFEATED) {
            long newHp = hp - scoreDelta;
            hp = (int) Math.max(0L, newHp);
            if (hp <= 0) enterDefeated();
        }

        if (phase == Phase.DEFEATED) {
            phaseElapsedMs += deltaMs;
            if (phaseElapsedMs >= phaseDurationMs) {
                defeated = true;
                return true;
            }
            return false;
        }

        if (def.pattern.length == 0) return false;

        phaseElapsedMs += deltaMs;
        switch (phase) {
            case ENTERING:
                if (phaseElapsedMs >= phaseDurationMs) beginAttack(0, sectionScore);
                break;
            case IDLE:
                if (phaseElapsedMs >= phaseDurationMs) enterWindup(sectionScore);
                break;
            case WINDUP:
                if (sectionScore - windupScoreBaseline >= currentAttack().interruptScore) {
                    enterStunned();
                } else if (phaseElapsedMs >= phaseDurationMs) {
                    enterAttack();
                }
                break;
            case ATTACK:
                if (!effectApplied) {
                    applyEffect(currentAttack());
                    effectApplied = true;
                }
                if (phaseElapsedMs >= phaseDurationMs) {
                    beginAttack((attackIndex + 1) % def.pattern.length, sectionScore);
                }
                break;
            case STUNNED:
                if (phaseElapsedMs >= phaseDurationMs) {
                    beginAttack((attackIndex + 1) % def.pattern.length, sectionScore);
                }
                break;
            default:
                break;
        }
        return false;
    }

    private BossAttack currentAttack() {
        return def.pattern[attackIndex];
    }

    private void enterEntering() {
        phase = Phase.ENTERING;
        phaseElapsedMs = 0;
        BossIntroAnim intro = def.intro != null ? def.intro : BossIntroAnim.FLASH_IN;
        phaseDurationMs = intro.enteringDurationMs();
        effectApplied = false;
    }

    private void beginAttack(int index, long sectionScore) {
        attackIndex = index;
        phase = Phase.IDLE;
        phaseElapsedMs = 0;
        phaseDurationMs = Math.max(0L, currentAttack().idleMs);
        effectApplied = false;
        // Skip empty idles immediately into windup.
        if (phaseDurationMs == 0) enterWindup(sectionScore);
    }

    private void enterWindup(long sectionScore) {
        phase = Phase.WINDUP;
        phaseElapsedMs = 0;
        phaseDurationMs = Math.max(0L, currentAttack().windupMs);
        windupScoreBaseline = sectionScore;
        if (phaseDurationMs == 0) enterAttack();
    }

    private void enterAttack() {
        phase = Phase.ATTACK;
        phaseElapsedMs = 0;
        phaseDurationMs = Math.max(1L, currentAttack().attackMs);
        effectApplied = false;
    }

    private void enterDefeated() {
        hp = 0;
        phase = Phase.DEFEATED;
        phaseElapsedMs = 0;
        phaseDurationMs = BossDefeatAnim.DURATION_MS;
        effectApplied = true;
    }

    private void enterStunned() {
        phase = Phase.STUNNED;
        phaseElapsedMs = 0;
        phaseDurationMs = Math.max(0L, def.stunMsOnInterrupt);
        effectApplied = true; // cancelled — do not apply the attack effect
    }

    private void applyEffect(BossAttack attack) {
        if (attack.effectType == BossAttackEffectType.ADD_GARBAGE && effects != null) {
            effects.addGarbage(attack.amount, attack.style, rng);
        }
    }

    /** Convenience sink that spawns garbage on every board of {@code game}. */
    public static AttackEffectSink garbageOnAllBoards(GameHandler game) {
        return (amount, style, rng) -> {
            if (game == null || amount <= 0) return;
            for (Board board : game.getBoards()) {
                board.spawnGarbageRows(amount, style, rng);
            }
        };
    }
}
