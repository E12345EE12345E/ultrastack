package me.ethanchen.server;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameHandler;
import me.ethanchen.game.GameMode;
import me.ethanchen.game.GameModeRules;
import me.ethanchen.network.packets.s2c.gamemode.PuzzleModeEndData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeEndData;

/**
 * Manages the game-end state machine across every board: detecting each board's own win
 * condition, tracking elimination when a board's explode countdown expires, freezing end-game
 * payloads, observing the grace period, and broadcasting {@link GameEndInfo} via the room.
 * Extracted from {@link ServerGame}.
 * <p>
 * A board is <em>resolved</em> once it has won or been eliminated; the session as a whole ends
 * once every board is resolved, or immediately when a session-wide win condition (e.g. the
 * score-mode timer) fires and resolves every still-running board at once.
 */
class GameEndController {

    private int numBoards;
    /** True once a board has won or been eliminated. */
    private boolean[] boardResolved;
    private boolean[] boardWon;
    /** Elapsed ms (from game start) at the moment each board resolved. */
    private long[] boardElapsedMs;

    private boolean sessionEnded = false;
    private boolean pendingWin;
    private boolean pendingDisconnected;
    private long    gameEndGraceUntilMs;
    private ScoreModeEndData frozenScoreEnd;
    private PuzzleModeEndData frozenPuzzleEnd;
    private long frozenPuzzleElapsedMs;

    private long gameStartMs;
    private long gameEndTargetMs;

    GameEndController() {}

    /** Initialises timing and per-board resolution state for a new game. */
    void reset(long gameStartMs, long gameEndTargetMs, int numBoards) {
        this.gameStartMs = gameStartMs;
        this.gameEndTargetMs = gameEndTargetMs;
        this.numBoards = numBoards;
        boardResolved = new boolean[numBoards];
        boardWon = new boolean[numBoards];
        boardElapsedMs = new long[numBoards];
        sessionEnded = false;
        pendingWin = false;
        pendingDisconnected = false;
        gameEndGraceUntilMs = 0;
        frozenScoreEnd = null;
        frozenPuzzleEnd = null;
        frozenPuzzleElapsedMs = 0;
    }

    boolean isGameEnded() { return sessionEnded; }

    /** True while board {@code boardIndex} is still being played (not yet won/eliminated) and the session hasn't ended. */
    boolean isBoardRunning(int boardIndex) {
        if (sessionEnded) return false;
        return boardResolved != null && boardIndex >= 0 && boardIndex < boardResolved.length && !boardResolved[boardIndex];
    }

    // -------------------------------------------------------------------------
    // Win-condition check (called each tick, per running board)
    // -------------------------------------------------------------------------

    /**
     * Checks the mode-specific win condition for {@code boardIndex} via {@link GameModeRules}
     * and resolves that board (as a win) if met. Must only be called when
     * {@code game.isStarted() && isBoardRunning(boardIndex)}.
     */
    void checkWinCondition(int boardIndex, GameMode mode, GameHandler game, long globalScore,
                            long[] boardScorePerPlayer, int[] bumpCounts, int[] blockedCounts,
                            int[] piecesPlaced, ClearSpinStats clearSpinStats) {
        if (mode == GameMode.NONE || sessionEnded) return;
        GameModeRules rules = mode.rules();
        if (rules.isWinConditionMet(game, boardIndex, gameEndTargetMs)) {
            resolveBoard(boardIndex, true, mode, globalScore, boardScorePerPlayer,
                    bumpCounts, blockedCounts, piecesPlaced, clearSpinStats);
        }
    }

    // -------------------------------------------------------------------------
    // Begin / finalize
    // -------------------------------------------------------------------------

    /** Called by a board's {@link BlockedSpawnController} when that board's explode countdown expires. */
    void beginBoardLoss(int boardIndex, GameMode mode, long globalScore, long[] boardScorePerPlayer,
                         int[] bumpCounts, int[] blockedCounts, int[] piecesPlaced, ClearSpinStats clearSpinStats) {
        resolveBoard(boardIndex, false, mode, globalScore, boardScorePerPlayer,
                bumpCounts, blockedCounts, piecesPlaced, clearSpinStats);
    }

    /** Called when a player disconnects mid-game: ends the whole session immediately (no board keeps running). */
    void beginGameEndDisconnect(GameMode mode, long globalScore, long[] boardScorePerPlayer,
                                 int[] bumpCounts, int[] blockedCounts, int[] piecesPlaced, ClearSpinStats clearSpinStats) {
        if (sessionEnded) return;
        pendingDisconnected = true;
        for (int b = 0; b < numBoards; b++) {
            if (!boardResolved[b]) markResolved(b, false);
        }
        finalizeSession(mode, globalScore, boardScorePerPlayer, bumpCounts, blockedCounts, piecesPlaced, clearSpinStats);
    }

    private void resolveBoard(int boardIndex, boolean win, GameMode mode, long globalScore, long[] boardScorePerPlayer,
                               int[] bumpCounts, int[] blockedCounts, int[] piecesPlaced, ClearSpinStats clearSpinStats) {
        if (sessionEnded || boardIndex < 0 || boardIndex >= boardResolved.length || boardResolved[boardIndex]) return;
        markResolved(boardIndex, win);
        // A session-wide win condition (e.g. score mode's shared timer) resolves every
        // still-running board identically in the same tick, so this loop naturally also
        // finalizes the session the instant that fires, matching pre-refactor behaviour.
        boolean allResolved = true;
        for (boolean r : boardResolved) if (!r) { allResolved = false; break; }
        if (allResolved) {
            finalizeSession(mode, globalScore, boardScorePerPlayer, bumpCounts, blockedCounts, piecesPlaced, clearSpinStats);
        }
    }

    private void markResolved(int boardIndex, boolean win) {
        boardResolved[boardIndex] = true;
        boardWon[boardIndex] = win;
        boardElapsedMs[boardIndex] = System.currentTimeMillis() - gameStartMs;
    }

    private void finalizeSession(GameMode mode, long globalScore, long[] boardScorePerPlayer,
                                  int[] bumpCounts, int[] blockedCounts, int[] piecesPlaced, ClearSpinStats clearSpinStats) {
        sessionEnded = true;
        pendingWin = false;
        for (boolean w : boardWon) if (w) { pendingWin = true; break; }
        long graceMs = (mode == GameMode.MULTIPLAYER_PUZZLE) ? GameConstants.PUZZLE_GAME_END_GRACE_MS : 0L;
        gameEndGraceUntilMs = System.currentTimeMillis() + graceMs;

        frozenScoreEnd  = null;
        frozenPuzzleEnd = null;
        ClearSpinStats frozenClears = clearSpinStats != null ? clearSpinStats.copy() : null;
        if (mode == GameMode.MULTIPLAYER_SCORE || mode == GameMode.CHARACTER_SCORE) {
            frozenScoreEnd = new ScoreModeEndData();
            frozenScoreEnd.finalScore = globalScore;
            frozenScoreEnd.boardScore = boardScorePerPlayer;
            frozenScoreEnd.timeSurvivedMs = System.currentTimeMillis() - gameStartMs;
            frozenScoreEnd.bumpCounts = copyOf(bumpCounts);
            frozenScoreEnd.blockedCounts = copyOf(blockedCounts);
            frozenScoreEnd.piecesPlaced = copyOf(piecesPlaced);
            applyClearSpinStats(frozenScoreEnd, frozenClears);
        } else if (mode == GameMode.MULTIPLAYER_PUZZLE) {
            // Puzzle mode still has a single board today; boardElapsedMs[0] is that board's own
            // finish time. A future multi-board puzzle mode would report each player's own
            // board's time via a per-player array alongside this legacy single value.
            frozenPuzzleElapsedMs = boardElapsedMs.length > 0 ? boardElapsedMs[0] : (System.currentTimeMillis() - gameStartMs);
            frozenPuzzleEnd = new PuzzleModeEndData();
            frozenPuzzleEnd.timeMs = frozenPuzzleElapsedMs;
            frozenPuzzleEnd.score = (int)(Integer.MAX_VALUE - Math.min(frozenPuzzleElapsedMs, Integer.MAX_VALUE));
            frozenPuzzleEnd.bumpCounts = copyOf(bumpCounts);
            frozenPuzzleEnd.blockedCounts = copyOf(blockedCounts);
            frozenPuzzleEnd.piecesPlaced = copyOf(piecesPlaced);
            applyClearSpinStats(frozenPuzzleEnd, frozenClears);
        }
    }

    private static void applyClearSpinStats(ScoreModeEndData end, ClearSpinStats stats) {
        if (stats == null) return;
        end.fourLineClears = stats.fourLineClears;
        end.tSpinSingles = stats.tSpinSingles;
        end.tSpinDoubles = stats.tSpinDoubles;
        end.tSpinTriples = stats.tSpinTriples;
        end.allSpinClears = stats.allSpinClears;
    }

    private static void applyClearSpinStats(PuzzleModeEndData end, ClearSpinStats stats) {
        if (stats == null) return;
        end.fourLineClears = stats.fourLineClears;
        end.tSpinSingles = stats.tSpinSingles;
        end.tSpinDoubles = stats.tSpinDoubles;
        end.tSpinTriples = stats.tSpinTriples;
        end.allSpinClears = stats.allSpinClears;
    }

    private static int[] copyOf(int[] arr) {
        return arr != null ? java.util.Arrays.copyOf(arr, arr.length) : null;
    }

    /**
     * Called once per tick; broadcasts the end-game result and stops the game once the grace
     * period has elapsed.
     *
     * @param mode     current game mode
     * @param room     room context used to broadcast end-game
     * @param stopGame runnable that tears down the in-progress game state
     */
    void tickGrace(GameMode mode, GameRoomContext room, Runnable stopGame) {
        if (!sessionEnded) return;
        if (System.currentTimeMillis() < gameEndGraceUntilMs) return;
        finalizeGameEnd(mode, room, stopGame);
    }

    private void finalizeGameEnd(GameMode mode, GameRoomContext room, Runnable stopGame) {
        long score = computeFinalScore(mode);

        GameEndInfo info = new GameEndInfo();
        info.mode = mode;
        info.win = pendingWin;
        info.disconnected = pendingDisconnected;
        info.scoreModeEnd = frozenScoreEnd;
        info.puzzleModeEnd = frozenPuzzleEnd;
        info.score = score;
        info.scorePerPlayer = frozenScoreEnd != null ? frozenScoreEnd.boardScore : null;
        info.displayScore = computeFinalDisplayScore(mode, score);
        if (frozenScoreEnd != null || frozenPuzzleEnd != null) {
            // Must use OutputType.json — default (minimal) is not valid JSON and breaks
            // SQLite/web consumers that parse extra_json with JSON.parse.
            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            info.extraJson = json.toJson(frozenScoreEnd != null ? frozenScoreEnd : frozenPuzzleEnd);
        }

        room.sendEndGame(info);
        stopGame.run();
    }

    // -------------------------------------------------------------------------
    // Score-mode data for live broadcasts
    // -------------------------------------------------------------------------

    long getFrozenPuzzleElapsedMs(long gameStartMs, boolean started) {
        if (sessionEnded) return frozenPuzzleElapsedMs;
        if (!started) return 0;
        return Math.max(0, System.currentTimeMillis() - gameStartMs);
    }

    long getGameStartMs() { return gameStartMs; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private long computeFinalScore(GameMode mode) {
        switch (mode) {
            case MULTIPLAYER_SCORE:
            case CHARACTER_SCORE:    return frozenScoreEnd != null ? frozenScoreEnd.finalScore : 0L;
            case MULTIPLAYER_PUZZLE: return frozenPuzzleEnd != null ? frozenPuzzleEnd.score : 0L;
            default:                 return 0L;
        }
    }

    private String computeFinalDisplayScore(GameMode mode, long score) {
        if (mode == GameMode.MULTIPLAYER_PUZZLE) {
            return frozenPuzzleEnd != null ? formatMinutesSeconds(frozenPuzzleEnd.timeMs) : "0:00";
        }
        return String.valueOf(score);
    }

    private static String formatMinutesSeconds(long ms) {
        long mins = ms / 60000;
        long secs = (ms % 60000) / 1000;
        return mins + ":" + String.format("%02d", secs);
    }
}
