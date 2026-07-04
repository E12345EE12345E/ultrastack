package me.ethanchen.headless;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import me.ethanchen.server.GameResultData;
import me.ethanchen.server.ResultRecorder;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Durably persists finished-game results to a SQLite database. Each result is committed as
 * soon as it's recorded (JDBC autocommit), so unlike {@link AccountStore} there is no
 * periodic/shutdown save step to lose data on a crash.
 *
 * <p>Core (sortable/queryable) fields live in dedicated columns; anything gamemode-specific
 * or added later goes into {@code extra_json} so old rows and new gamemodes stay compatible
 * without a schema migration.
 */
public class GameResultStore implements ResultRecorder {
    private static final int SCHEMA_VERSION = 1;

    private final Connection connection;
    private final Json json;

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
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "game-result-store-shutdown"));
    }

    @Override
    public synchronized void recordGameResult(GameResultData data) {
        String sql = "INSERT INTO game_results " +
                "(id, timestamp_ms, gamemode, score, display_score, players, win, disconnected, schema_version, extra_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
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
        } catch (SQLException e) {
            System.err.println("[GameResultStore] Failed to record game result: " + e.getMessage());
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
    }
}
