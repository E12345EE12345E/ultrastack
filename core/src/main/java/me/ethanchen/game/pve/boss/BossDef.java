package me.ethanchen.game.pve.boss;

/**
 * Code-defined bossfight. Levels reference a boss by {@link #id} in section environment data;
 * the actual pattern and HP live here, not in JSON (implementation.md, Part 3).
 */
public final class BossDef {
    public final int id;
    public final BossIntroAnim intro;
    public final BossPhaseDef[] phases;
    /** Sum of {@link BossPhaseDef#hp} across {@link #phases}. */
    public final int maxHp;
    /**
     * Remaining HP at which phase {@code i} is entered ({@code phaseStartHp[0] == maxHp}).
     * Phase {@code i} is active while {@code phaseStartHp[i+1] < hp <= phaseStartHp[i]},
     * treating {@code phaseStartHp[last+1]} as {@code 0}.
     */
    public final int[] phaseStartHp;

    public BossDef(int id, BossIntroAnim intro, BossPhaseDef... phases) {
        this.id = id;
        this.intro = intro != null ? intro : BossIntroAnim.FLASH_IN;
        this.phases = phases != null && phases.length > 0 ? phases.clone() : new BossPhaseDef[0];
        int total = 0;
        for (BossPhaseDef phase : this.phases) {
            total += Math.max(0, phase.hp);
        }
        this.maxHp = total;
        this.phaseStartHp = new int[this.phases.length];
        int remaining = total;
        for (int i = 0; i < this.phases.length; i++) {
            this.phaseStartHp[i] = remaining;
            remaining -= Math.max(0, this.phases[i].hp);
        }
    }

    /**
     * Combat-phase index for remaining {@code hp}. Callers treat {@code hp <= 0} as defeat
     * rather than using this result.
     */
    public int phaseIndexForHp(int hp) {
        if (phases.length == 0 || hp <= 0) return 0;
        for (int i = 0; i < phases.length; i++) {
            int nextStart = (i + 1 < phases.length) ? phaseStartHp[i + 1] : 0;
            if (hp > nextStart) return i;
        }
        return phases.length - 1;
    }
}
