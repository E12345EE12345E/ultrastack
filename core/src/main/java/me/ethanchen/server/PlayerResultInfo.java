package me.ethanchen.server;

/** A single player's identity as recorded in a persisted {@link GameResultData}. */
public class PlayerResultInfo {
    public String username;
    public String accountUuid;

    public PlayerResultInfo() {}

    public PlayerResultInfo(String username, String accountUuid) {
        this.username = username;
        this.accountUuid = accountUuid;
    }
}
