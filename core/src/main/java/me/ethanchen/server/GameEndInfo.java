package me.ethanchen.server;

import me.ethanchen.game.GameMode;
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
    /** Hidden sortable score for this game, regardless of gamemode. */
    public long score;
    /** Display-facing score string for this game, regardless of gamemode. */
    public String displayScore;
    /** Optional gamemode-specific extra data, serialized as a JSON string. May be null. */
    public String extraJson;
}
