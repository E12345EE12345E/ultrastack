package me.ethanchen.network.packets.s2c.gamemode;

public class ScoreModeEndData {
    public long finalScore;
    public long timeSurvivedMs;
    /** Per-player count of mutual lateral bumps against another player's piece, indexed by player slot. */
    public int[] bumpCounts;
    /** Per-player count of hard drops blocked by resting on another player's piece, indexed by player slot. */
    public int[] blockedCounts;
}
