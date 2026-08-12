package me.ethanchen.game.pve.boss;

import me.ethanchen.game.pve.GarbageStyle;

/**
 * One entry in a {@link BossDef}'s attack pattern: idle wait, windup (interruptible), then attack
 * animation (even instant effects keep a nonzero {@link #attackMs} for client feedback).
 */
public final class BossAttack {
    public final long idleMs;
    public final long windupMs;
    public final long attackMs;
    /** Section-relative score that must land during windup to cancel the attack. */
    public final long interruptScore;
    public final BossAttackEffectType effectType;
    public final int amount;
    public final GarbageStyle style;

    public BossAttack(long idleMs, long windupMs, long attackMs, long interruptScore,
                      BossAttackEffectType effectType, int amount, GarbageStyle style) {
        this.idleMs = idleMs;
        this.windupMs = windupMs;
        this.attackMs = attackMs;
        this.interruptScore = interruptScore;
        this.effectType = effectType;
        this.amount = amount;
        this.style = style != null ? style : GarbageStyle.DEFAULT;
    }

    public static BossAttack addGarbage(long idleMs, long windupMs, long attackMs, long interruptScore,
                                         int amount, GarbageStyle style) {
        return new BossAttack(idleMs, windupMs, attackMs, interruptScore,
                BossAttackEffectType.ADD_GARBAGE, amount, style);
    }
}
