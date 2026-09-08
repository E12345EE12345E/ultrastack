package me.ethanchen.game.progression;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import me.ethanchen.game.GameMode;

/**
 * Snapshot of a player's best finished game in one tracked mode. Stored on the account
 * ({@code extra_json}) so profile views do not scan {@code game_results}.
 */
public class BestGameRecord {
    private static final DateTimeFormatter PLAYED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public String gameResultId;
    public String gamemode;
    public long score;
    public String displayScore;
    public long timestampMs;
    public String[] playerNames;

    public BestGameRecord() {}

    public BestGameRecord(String gameResultId, String gamemode, long score, String displayScore) {
        this.gameResultId = gameResultId;
        this.gamemode = gamemode;
        this.score = score;
        this.displayScore = displayScore;
    }

    /** True when this result should replace {@code current} (missing or strictly lower raw score). */
    public boolean isStrictlyBetterThan(BestGameRecord current) {
        return current == null || score > current.score;
    }

    public static boolean tracksBests(GameMode mode) {
        return mode == GameMode.MULTIPLAYER_SCORE
                || mode == GameMode.MULTIPLAYER_PUZZLE
                || mode == GameMode.CHARACTER_SCORE;
    }

    /** Hover copy: played date and participating names. */
    public String hoverText() {
        String date = timestampMs > 0 ? PLAYED_AT.format(Instant.ofEpochMilli(timestampMs)) : "Unknown date";
        StringBuilder names = new StringBuilder();
        if (playerNames != null) {
            for (String name : playerNames) {
                if (name == null || name.isEmpty()) continue;
                if (names.length() > 0) names.append(", ");
                names.append(name);
            }
        }
        if (names.length() == 0) names.append("—");
        return "Played: " + date + "\nPlayers: " + names;
    }
}
