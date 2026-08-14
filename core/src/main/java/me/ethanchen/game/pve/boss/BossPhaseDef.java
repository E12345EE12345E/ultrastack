package me.ethanchen.game.pve.boss;

/**
 * One combat stage of a {@link BossDef}: HP pool, stun length, attack cycle, and particle hues.
 * Remaining HP across all stages is summed into the boss's max HP; crossing a stage's pool
 * advances to the next {@link BossPhaseDef} in the array.
 */
public final class BossPhaseDef {
    public final int hp;
    public final long stunMsOnInterrupt;
    public final BossAttack[] pattern;
    /** Inclusive HSV hue (degrees) lower bound for client combat particles. */
    public final float particleHueMin;
    /** Inclusive HSV hue (degrees) upper bound for client combat particles. */
    public final float particleHueMax;

    public BossPhaseDef(int hp, long stunMsOnInterrupt, BossAttack[] pattern) {
        this(hp, stunMsOnInterrupt, pattern, 0f, 360f);
    }

    public BossPhaseDef(int hp, long stunMsOnInterrupt, BossAttack[] pattern,
                        float particleHueMin, float particleHueMax) {
        this.hp = hp;
        this.stunMsOnInterrupt = stunMsOnInterrupt;
        this.pattern = pattern != null ? pattern : new BossAttack[0];
        this.particleHueMin = particleHueMin;
        this.particleHueMax = particleHueMax;
    }
}
