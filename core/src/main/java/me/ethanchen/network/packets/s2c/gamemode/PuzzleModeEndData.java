package me.ethanchen.network.packets.s2c.gamemode;

public class PuzzleModeEndData {
    /** Final time on the timer at win/loss, in milliseconds; this is the displayed result. */
    public long timeMs;
    /** Database-sortable value: {@code Integer.MAX_VALUE - timeMs} (higher is faster/better). */
    public int score;
    /** Per-player count of mutual lateral bumps against another player's piece, indexed by player slot. */
    public int[] bumpCounts;
    /** Per-player count of hard drops blocked by resting on another player's piece, indexed by player slot. */
    public int[] blockedCounts;
    /** Per-player count of 4-line clears, indexed by player slot. */
    public int[] fourLineClears;
    /** Per-player count of full T-spin singles (not mini), indexed by player slot. */
    public int[] tSpinSingles;
    /** Per-player count of full T-spin doubles (not mini), indexed by player slot. */
    public int[] tSpinDoubles;
    /** Per-player count of full T-spin triples, indexed by player slot. */
    public int[] tSpinTriples;
    /** Per-player count of all-spin line clears (non-T pieces), indexed by player slot. */
    public int[] allSpinClears;
}
