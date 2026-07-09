package me.ethanchen.server;

import com.badlogic.gdx.utils.Json;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameHandler;
import me.ethanchen.game.GameMode;
import me.ethanchen.game.GameModeRules;
import me.ethanchen.network.packets.s2c.gamemode.PuzzleModeEndData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeEndData;

/**
 * Manages the game-end state machine: detecting the win condition, freezing end-game payloads,
 * observing the grace period, and broadcasting {@link GameEndInfo} via the room. Extracted from
 * {@link ServerGame}.
 */
class GameEndController {

    private boolean gameEnded = false;
    private boolean pendingWin;
    private boolean pendingDisconnected;
    private long    gameEndGraceUntilMs;
    private ScoreModeEndData frozenScoreEnd;
    private PuzzleModeEndData frozenPuzzleEnd;
    private long frozenPuzzleElapsedMs;

    private long gameStartMs;
    private long gameEndTargetMs;

    GameEndController() {}

    /** Initialises timing fields for a new game. */
    void reset(long gameStartMs, long gameEndTargetMs) {
        this.gameStartMs = gameStartMs;
        this.gameEndTargetMs = gameEndTargetMs;
        gameEnded            = false;
        pendingWin           = false;
        pendingDisconnected  = false;
        gameEndGraceUntilMs  = 0;
        frozenScoreEnd       = null;
        frozenPuzzleEnd      = null;
        frozenPuzzleElapsedMs = 0;
    }

    boolean isGameEnded() { return gameEnded; }

    // -------------------------------------------------------------------------
    // Win-condition check (called each tick while game is running)
    // -------------------------------------------------------------------------

    /**
     * Checks the mode-specific win condition via {@link GameModeRules} and calls
     * {@link #beginGameEnd} if met. Must only be called when
     * {@code game.isStarted() && !isGameEnded()}.
     */
    void checkWinCondition(GameMode mode, GameHandler game, ScoreModeScorer scorer) {
        if (mode == GameMode.NONE) return;
        GameModeRules rules = mode.rules();
        if (rules.isWinConditionMet(game, gameEndTargetMs)) {
            beginGameEnd(true, false, mode, scorer);
        }
    }

    // -------------------------------------------------------------------------
    // Begin / finalize
    // -------------------------------------------------------------------------

    /** Called by {@link BlockedSpawnController} when the explode countdown expires. */
    void beginGameEndLoss(GameMode mode, ScoreModeScorer scorer) {
        beginGameEnd(false, false, mode, scorer);
    }

    /** Called when a player disconnects mid-game. */
    void beginGameEndDisconnect(GameMode mode, ScoreModeScorer scorer) {
        beginGameEnd(false, true, mode, scorer);
    }

    private void beginGameEnd(boolean win, boolean disconnected, GameMode mode, ScoreModeScorer scorer) {
        if (gameEnded) return;
        gameEnded = true;
        pendingWin = win;
        pendingDisconnected = disconnected;
        long graceMs = (mode == GameMode.MULTIPLAYER_PUZZLE) ? GameConstants.PUZZLE_GAME_END_GRACE_MS : 0L;
        gameEndGraceUntilMs = System.currentTimeMillis() + graceMs;

        frozenScoreEnd  = null;
        frozenPuzzleEnd = null;
        if (mode == GameMode.MULTIPLAYER_SCORE) {
            frozenScoreEnd = new ScoreModeEndData();
            frozenScoreEnd.finalScore = scorer != null ? scorer.getTotalScore() : 0;
            frozenScoreEnd.timeSurvivedMs = System.currentTimeMillis() - gameStartMs;
        } else if (mode == GameMode.MULTIPLAYER_PUZZLE) {
            frozenPuzzleElapsedMs = System.currentTimeMillis() - gameStartMs;
            frozenPuzzleEnd = new PuzzleModeEndData();
            frozenPuzzleEnd.timeMs = frozenPuzzleElapsedMs;
            frozenPuzzleEnd.score = (int)(Integer.MAX_VALUE - Math.min(frozenPuzzleElapsedMs, Integer.MAX_VALUE));
        }
    }

    /**
     * Called once per tick; broadcasts the end-game result and stops the game once the grace
     * period has elapsed.
     *
     * @param mode     current game mode
     * @param scorer   score-mode scorer (may be null)
     * @param room     room context used to broadcast end-game
     * @param stopGame runnable that tears down the in-progress game state
     */
    void tickGrace(GameMode mode, ScoreModeScorer scorer, GameRoomContext room, Runnable stopGame) {
        if (!gameEnded) return;
        if (System.currentTimeMillis() < gameEndGraceUntilMs) return;
        finalizeGameEnd(mode, scorer, room, stopGame);
    }

    private void finalizeGameEnd(GameMode mode, ScoreModeScorer scorer, GameRoomContext room, Runnable stopGame) {
        long score = computeFinalScore(mode, scorer);

        GameEndInfo info = new GameEndInfo();
        info.mode = mode;
        info.win = pendingWin;
        info.disconnected = pendingDisconnected;
        info.scoreModeEnd = frozenScoreEnd;
        info.puzzleModeEnd = frozenPuzzleEnd;
        info.score = score;
        info.displayScore = computeFinalDisplayScore(mode, score);
        if (frozenScoreEnd != null) {
            info.extraJson = new Json().toJson(frozenScoreEnd);
        } else if (frozenPuzzleEnd != null) {
            info.extraJson = new Json().toJson(frozenPuzzleEnd);
        }

        room.sendEndGame(info);
        stopGame.run();
    }

    // -------------------------------------------------------------------------
    // Score-mode data for live broadcasts
    // -------------------------------------------------------------------------

    long getFrozenPuzzleElapsedMs(long gameStartMs, boolean started) {
        if (gameEnded) return frozenPuzzleElapsedMs;
        if (!started) return 0;
        return Math.max(0, System.currentTimeMillis() - gameStartMs);
    }

    long getGameStartMs() { return gameStartMs; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private long computeFinalScore(GameMode mode, ScoreModeScorer scorer) {
        switch (mode) {
            case MULTIPLAYER_SCORE:  return scorer != null ? scorer.getTotalScore() : 0L;
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
