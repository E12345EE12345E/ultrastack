package me.ethanchen.server;

import me.ethanchen.game.GameMode;
import me.ethanchen.network.packets.s2c.gamemode.PuzzleModeEndData;
import me.ethanchen.network.packets.s2c.gamemode.PveModeEndData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeEndData;

/**
 * Bundles everything {@link ServerGame} knows when a game ends, passed to
 * {@link GameRoomContext#sendEndGame(GameEndInfo)} so it can both broadcast the
 * {@link me.ethanchen.network.packets.s2c.EndGameBroadcast} to clients and persist a
 * {@link GameResultData} via a {@link ResultRecorder}.
 */
public class GameEndInfo {
    public GameMode mode;
    public boolean win;
    public boolean disconnected;
    /** Score-mode end data; null when mode is not MULTIPLAYER_SCORE. */
    public ScoreModeEndData scoreModeEnd;
    /** Puzzle-mode end data; null when mode is not MULTIPLAYER_PUZZLE. */
    public PuzzleModeEndData puzzleModeEnd;
    /** PvE-mode end data; null when mode is not PVE. */
    public PveModeEndData pveModeEnd;
    /** Hidden sortable score for this game, regardless of gamemode: the session-wide aggregate across all boards. */
    public long score;
    /**
     * Each player's own personal result: their own board's score, indexed by global slot.
     * Null for modes without a score concept. Used for per-player XP so a player's reward
     * reflects their own board, not other boards' contributions to {@link #score}.
     */
    public long[] scorePerPlayer;
    /** Display-facing score string for this game, regardless of gamemode. */
    public String displayScore;
    /** Optional gamemode-specific extra data, serialized as a JSON string. May be null. */
    public String extraJson;
}
