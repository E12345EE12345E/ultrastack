package me.ethanchen.server;

import me.ethanchen.game.progression.BestGameRecord;

/** Implemented by whatever backs persistent storage of finished game results (e.g. SQLite). */
public interface ResultRecorder {
    /**
     * Persists {@code data} and returns the new row id, or {@code null} if the write failed.
     */
    String recordGameResult(GameResultData data);

    /**
     * Migration-only: best non-disconnected game for {@code accountUuid} in {@code gamemode}.
     * Widget views read account-stored {@link BestGameRecord}s instead.
     */
    default BestGameRecord bestGame(String accountUuid, String gamemode) {
        return null;
    }
}
