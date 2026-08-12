package me.ethanchen.network.packets.s2c.gamemode;

public class ScoreModeData {
    public float[] glowingValues;
    /** Session-wide aggregate score across every board. */
    public long totalScore;
    /** This recipient's own board's score. */
    public long boardScore;
    public int repeatColumn;
    public int repeatColumn2;
}
