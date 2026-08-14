package me.ethanchen.network.packets.s2c.gamemode;

import me.ethanchen.game.pve.PveBoardDisplay;

/**
 * Live PvE section / boss / scoring-visual state sent each net tick inside
 * {@code LightGameStateBroadcast}. Boss phase ints match {@code BossController.Phase} ordinals:
 * IDLE=0, WINDUP=1, ATTACK=2, STUNNED=3, ENTERING=4, DEFEATED=5.
 *
 * <p>This is the sole live payload for PvE — it does not reuse {@link ScoreModeData}.
 */
public class PveModeData {
    public int sectionIndex;
    public long sectionElapsedMs;
    /** {@code -1} when the current section has no timeout. */
    public long sectionTimeoutMs = -1;
    /** Score gained during the current section (drives SCORE criteria). */
    public long sectionScore;
    /** Session-wide aggregate score across every board. */
    public long totalScore;
    /** This broadcast board's own accumulated score. */
    public long boardScore;
    public PveBoardDisplay displayMode = PveBoardDisplay.BOARD_DEFAULT;

    /**
     * Preformatted objective lines for the section HUD (pass criteria with live progress, or
     * boss status). Empty for nothing to show. Server builds these each tick.
     */
    public String[] objectiveLines = new String[0];

    /** Per-seat glow multipliers for scoring-formula visuals (same semantics as score mode). */
    public float[] glowingValues = new float[0];
    public int repeatColumn = -1;
    public int repeatColumn2 = -1;

    public int bossId = -1;
    public int bossHp;
    public int bossMaxHp;
    /** {@code BossController.Phase} ordinal, or {@code -1} when no boss is active. */
    public int bossPhase = -1;
    /** Combat-stage index into {@code BossDef.phases}, or {@code -1} when no boss is active. */
    public int bossPhaseIndex = -1;
    public long bossPhaseElapsedMs;
    public long bossPhaseDurationMs;
    /** Current attack sends a client shockwave; false for smaller hits. */
    public boolean bossAttackShockwave;
}
