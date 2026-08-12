package me.ethanchen.server;

import java.util.Random;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.pve.GarbageStyle;
import me.ethanchen.game.pve.boss.BossAttack;
import me.ethanchen.game.pve.boss.BossAttackEffectType;
import me.ethanchen.game.pve.boss.BossDef;

/**
 * Per-boss-section phase machine: {@code IDLE -> WINDUP -> ATTACK -> IDLE} (plus {@code STUNNED}
 * on windup interrupt). Score gained since the boss section started depletes HP; score gained
 * during a windup counts toward that attack's interrupt threshold (implementation.md, Part 3).
 */
public class BossController {

    public enum Phase { IDLE, WINDUP, ATTACK, STUNNED }

    /** Applies an attack effect to every still-running board (e.g. garbage spawn). */
    public interface AttackEffectSink {
        void addGarbage(int amount, GarbageStyle style, Random rng);
    }

    private final BossDef def;
    private final AttackEffectSink effects;
    private final Random rng = new Random();

    private int attackIndex;
    private Phase phase = Phase.IDLE;
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
        beginAttack(0, 0L);
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
        if (scoreDelta > 0) {
            long newHp = hp - scoreDelta;
            hp = (int) Math.max(0L, newHp);
            if (hp <= 0) {
                defeated = true;
                return true;
            }
        }

        if (def.pattern.length == 0) return false;

        phaseElapsedMs += deltaMs;
        switch (phase) {
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
        return defeated;
    }

    private BossAttack currentAttack() {
        return def.pattern[attackIndex];
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
