package me.ethanchen.server;

/** Implemented by whatever backs persistent storage of finished game results (e.g. SQLite). */
public interface ResultRecorder {
    void recordGameResult(GameResultData data);
}
