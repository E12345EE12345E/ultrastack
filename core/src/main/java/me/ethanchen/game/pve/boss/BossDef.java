package me.ethanchen.game.pve.boss;

/**
 * Code-defined bossfight. Levels reference a boss by {@link #id} in section environment data;
 * the actual pattern and HP live here, not in JSON (implementation.md, Part 3).
 */
public final class BossDef {
    public final int id;
    public final int maxHp;
    public final long stunMsOnInterrupt;
    public final BossAttack[] pattern;
    public final BossIntroAnim intro;

    public BossDef(int id, int maxHp, long stunMsOnInterrupt, BossAttack[] pattern, BossIntroAnim intro) {
        this.id = id;
        this.maxHp = maxHp;
        this.stunMsOnInterrupt = stunMsOnInterrupt;
        this.pattern = pattern != null ? pattern : new BossAttack[0];
        this.intro = intro != null ? intro : BossIntroAnim.FLASH_IN;
    }
}
