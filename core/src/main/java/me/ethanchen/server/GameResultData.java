package me.ethanchen.server;

/**
 * A single completed game's persisted result. Passed to {@link ResultRecorder} once a game
 * ends so it can be durably stored (e.g. for a leaderboard) independently of the
 * {@link me.ethanchen.network.packets.s2c.EndGameBroadcast} sent to clients.
 */
public class GameResultData {
    public String gamemode;
    /** Hidden score used for sorting/ranking; always a long regardless of gamemode. */
    public long score;
    /** What a leaderboard should display; may differ from {@link #score} (e.g. a formatted time). */
    public String displayScore;
    public PlayerResultInfo[] players;
    public boolean win;
    public boolean disconnected;
    public long timestampMs;
    /** Optional gamemode-specific extra data, serialized as a JSON string. May be null. */
    public String extraJson;
}
