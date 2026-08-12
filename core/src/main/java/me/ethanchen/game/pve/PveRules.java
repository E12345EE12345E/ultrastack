package me.ethanchen.game.pve;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameHandler;
import me.ethanchen.game.GameModeRules;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.BoardPreset;

/**
 * {@link GameModeRules} for {@code GameMode.PVE}, built fresh per session from the selected
 * level's data — never a shared singleton, since level/difficulty vary per session (see
 * {@link PveSessionState}). {@code ServerGame.startGame} constructs one and passes it directly to
 * {@code GameHandler.init(GameMode, GameModeRules, long)}.
 *
 * <p>Section pass/fail, environment overrides, and win/loss are driven by
 * {@code PveSectionController}, not by this class's {@link #isWinConditionMet}, which always
 * returns false here.
 */
public class PveRules implements GameModeRules {
    private final PveLevelData level;

    public PveRules(PveLevelData level) {
        this.level = level;
    }

    public PveLevelData getLevel() {
        return level;
    }

    /**
     * PvE slot split (implementation.md): 1-2 players share one board; 3 players split into
     * boards of 1 and 2; 4 players split into two boards of 2.
     */
    static int[] seatsPerBoard(int numPlayers) {
        switch (numPlayers) {
            case 3: return new int[]{1, 2};
            case 4: return new int[]{2, 2};
            default: return new int[]{Math.max(1, numPlayers)};
        }
    }

    @Override
    public Board.Presets boardPreset(int numPlayers) {
        // Unused: boardLayout() below is authoritative for PvE's custom level geometry. Kept only
        // to satisfy the GameModeRules contract.
        return Board.Presets.STANDARD_SINGLE;
    }

    @Override
    public BoardPreset[] boardLayout(int numPlayers) {
        int[] seats = seatsPerBoard(numPlayers);
        BoardPreset[] result = new BoardPreset[seats.length];
        for (int i = 0; i < seats.length; i++) {
            result[i] = BoardPreset.fromPve(level.boardSpecFor(seats[i]));
        }
        return result;
    }

    @Override
    public int[] slotToBoard(int numPlayers) {
        int[] seats = seatsPerBoard(numPlayers);
        int[] result = new int[numPlayers];
        int slot = 0;
        for (int b = 0; b < seats.length && slot < numPlayers; b++) {
            for (int s = 0; s < seats[b] && slot < numPlayers; s++) {
                result[slot++] = b;
            }
        }
        return result;
    }

    @Override
    public int initialGravityMs() {
        PveSection section0 = level.sectionAt(0);
        if (section0 != null && section0.env != null && section0.env.gravityMs != null) {
            return section0.env.gravityMs;
        }
        return GameConstants.INITIAL_GRAVITY_MS;
    }

    @Override
    public void prepareBoard(Board board) {
        int seatCount = board.getSpawnPositions() != null ? board.getSpawnPositions().length : 1;
        PveBoardSpec spec = level.boardSpecFor(seatCount);
        if (spec != null) board.applyInitialTiles(spec.initialTiles);
    }

    @Override
    public boolean isWinConditionMet(GameHandler game, int boardIndex, long gameEndTargetMs) {
        return false;
    }
}
