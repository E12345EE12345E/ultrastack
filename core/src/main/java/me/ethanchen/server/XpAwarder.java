package me.ethanchen.server;

/** Implemented by whatever backs persistent storage of player accounts (e.g. SQLite), to award XP earned from finished games. */
public interface XpAwarder {
    void awardXp(String accountUuid, long xp);
}
