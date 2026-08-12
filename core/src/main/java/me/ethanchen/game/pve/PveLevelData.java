package me.ethanchen.game.pve;

/**
 * Full data for one level/difficulty, as loaded from a level JSON file by {@link PveLevelLoader}.
 */
public class PveLevelData {
    public PveSection[] sections = new PveSection[0];
    public int difficultyRank;
    /** Geometry for a board seating exactly 1 player. */
    public PveBoardSpec board1;
    /** Geometry for a board seating exactly 2 players. */
    public PveBoardSpec board2;

    public PveLevelData() {}

    /** Resolves the geometry to use for a board seating {@code seatCount} players (1 or 2). */
    public PveBoardSpec boardSpecFor(int seatCount) {
        if (seatCount >= 2 && board2 != null) return board2;
        return board1;
    }

    public PveSection sectionAt(int index) {
        if (sections == null || index < 0 || index >= sections.length) return null;
        return sections[index];
    }
}
