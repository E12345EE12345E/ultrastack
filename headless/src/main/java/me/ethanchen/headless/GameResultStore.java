package me.ethanchen.headless;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import me.ethanchen.game.progression.BestGameRecord;
import me.ethanchen.server.GameResultData;
import me.ethanchen.server.PlayerResultInfo;
import me.ethanchen.server.ResultRecorder;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Durably persists finished-game results to a SQLite database. Each result is committed as
 * soon as it's recorded (JDBC autocommit), so there is no buffered write window to lose data
 * on a crash.
 *
 * <p>WAL pages are checkpointed into the main {@code .db} file on a schedule (see
 * {@link SqliteWalSync}) so tools that copy only that file see recent commits without waiting
 * for auto-checkpoint or process shutdown.
 *
 * <p>Core (sortable/queryable) fields live in dedicated columns; anything gamemode-specific
 * or added later goes into {@code extra_json} so old rows and new gamemodes stay compatible
 * without a schema migration.
 */
public class GameResultStore implements ResultRecorder {
    private static final int SCHEMA_VERSION = 1;

    private final Connection connection;
    private final Json json;
    private final SqliteWalSync walSync;

    public GameResultStore(String dbPath) {
        this.json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        try {
            Class.forName("org.sqlite.JDBC");
            File dbFile = new File(dbPath);
            File parent = dbFile.getAbsoluteFile().getParentFile();
            if (parent != null) parent.mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getPath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");
                st.execute("CREATE TABLE IF NOT EXISTS game_results (" +
                        "id TEXT PRIMARY KEY," +
                        "timestamp_ms INTEGER NOT NULL," +
                        "gamemode TEXT NOT NULL," +
                        "score INTEGER NOT NULL," +
                        "display_score TEXT NOT NULL," +
                        "players TEXT NOT NULL," +
                        "win INTEGER NOT NULL," +
                        "disconnected INTEGER NOT NULL," +
                        "schema_version INTEGER NOT NULL," +
                        "extra_json TEXT" +
                        ");");
                st.execute("CREATE INDEX IF NOT EXISTS idx_game_results_gamemode_score " +
                        "ON game_results(gamemode, score DESC);");
            }
            System.out.println("[GameResultStore] Using database at " + dbFile.getAbsolutePath());
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException("Failed to initialize game result database", e);
        }
        walSync = new SqliteWalSync("game-result-store-wal-sync", this::checkpointWal);
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "game-result-store-shutdown"));
    }

    @Override
    public synchronized String recordGameResult(GameResultData data) {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO game_results " +
                "(id, timestamp_ms, gamemode, score, display_score, players, win, disconnected, schema_version, extra_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setLong(2, data.timestampMs);
            ps.setString(3, data.gamemode);
            ps.setLong(4, data.score);
            ps.setString(5, data.displayScore != null ? data.displayScore : "");
            ps.setString(6, json.toJson(data.players));
            ps.setInt(7, data.win ? 1 : 0);
            ps.setInt(8, data.disconnected ? 1 : 0);
            ps.setInt(9, SCHEMA_VERSION);
            ps.setString(10, data.extraJson);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            System.err.println("[GameResultStore] Failed to record game result: " + e.getMessage());
            return null;
        }
    }

    /**
     * Best non-disconnected row for this account in {@code gamemode}. Used only to seed
     * account-stored {@link BestGameRecord}s for profiles that predate that field.
     */
    @Override
    public synchronized BestGameRecord bestGame(String accountUuid, String gamemode) {
        if (accountUuid == null || accountUuid.isEmpty() || gamemode == null || gamemode.isEmpty()) {
            return null;
        }
        String sql = "SELECT id, gamemode, score, display_score, timestamp_ms, players FROM game_results "
                + "WHERE disconnected = 0 AND gamemode = ? AND EXISTS ("
                + "  SELECT 1 FROM json_each("
                + "    CASE WHEN json_valid(game_results.players) THEN game_results.players ELSE '[]' END"
                + "  ) AS player"
                + "  WHERE json_extract(player.value, '$.accountUuid') = ?"
                + ") ORDER BY score DESC, timestamp_ms ASC, id ASC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, gamemode);
            ps.setString(2, accountUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                BestGameRecord rec = new BestGameRecord();
                rec.gameResultId = rs.getString("id");
                rec.gamemode = rs.getString("gamemode");
                rec.score = rs.getLong("score");
                rec.displayScore = rs.getString("display_score");
                rec.timestampMs = rs.getLong("timestamp_ms");
                rec.playerNames = playerNamesFromJson(rs.getString("players"));
                return rec;
            }
        } catch (SQLException e) {
            System.err.println("[GameResultStore] bestGame failed: " + e.getMessage());
            return null;
        }
    }

    private String[] playerNamesFromJson(String playersJson) {
        if (playersJson == null || playersJson.isEmpty()) return new String[0];
        try {
            PlayerResultInfo[] players = json.fromJson(PlayerResultInfo[].class, playersJson);
            if (players == null) return new String[0];
            int n = 0;
            for (PlayerResultInfo p : players) {
                if (p != null && p.username != null && !p.username.isEmpty()) n++;
            }
            String[] names = new String[n];
            int i = 0;
            for (PlayerResultInfo p : players) {
                if (p != null && p.username != null && !p.username.isEmpty()) {
                    names[i++] = p.username;
                }
            }
            return names;
        } catch (Exception e) {
            return new String[0];
        }
    }

    /** Folds {@code results.db-wal} into {@code results.db} and truncates the WAL. */
    private synchronized void checkpointWal() {
        try {
            if (connection == null || connection.isClosed()) return;
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA wal_checkpoint(TRUNCATE);");
            }
        } catch (SQLException e) {
            System.err.println("[GameResultStore] WAL checkpoint failed: " + e.getMessage());
        }
    }

    public void close() {
        walSync.close();
        synchronized (this) {
            try {
                if (connection != null && !connection.isClosed()) connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
