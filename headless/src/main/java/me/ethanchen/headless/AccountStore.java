package me.ethanchen.headless;

import me.ethanchen.server.XpAwarder;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durably persists player accounts to a SQLite database. Each account is committed as soon as
 * it's created (JDBC autocommit), so unlike the previous JSON-file approach there is no
 * periodic/shutdown save step to lose data on a crash.
 *
 * <p>Core fields live in dedicated columns; anything added later goes into {@code extra_json}
 * so old rows and new player-account features stay compatible without a schema migration.
 */
public class AccountStore implements XpAwarder {
    private static final int SCHEMA_VERSION = 1;

    private final ConcurrentHashMap<String, Account> byUsername = new ConcurrentHashMap<>(); // key: lowercase username
    private final ConcurrentHashMap<String, Account> byUuid = new ConcurrentHashMap<>();
    private final Connection connection;

    public AccountStore(String dbPath) {
        try {
            Class.forName("org.sqlite.JDBC");
            File dbFile = new File(dbPath);
            File parent = dbFile.getAbsoluteFile().getParentFile();
            if (parent != null) parent.mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getPath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");
                st.execute("CREATE TABLE IF NOT EXISTS accounts (" +
                        "uuid TEXT PRIMARY KEY," +
                        "username TEXT NOT NULL UNIQUE," +
                        "salt_base64 TEXT NOT NULL," +
                        "hash_base64 TEXT NOT NULL," +
                        "created_at_ms INTEGER NOT NULL," +
                        "xp INTEGER NOT NULL DEFAULT 0," +
                        "schema_version INTEGER NOT NULL," +
                        "extra_json TEXT" +
                        ");");
            }
            System.out.println("[AccountStore] Using database at " + dbFile.getAbsolutePath());
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException("Failed to initialize account database", e);
        }
        load();
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "account-store-shutdown"));
    }

    /** Returns null on success, error string on conflict. */
    public synchronized String createAccount(String username, String passcode) {
        String key = username.toLowerCase();
        if (byUsername.containsKey(key)) return "username already taken";
        byte[] salt = PasswordHasher.generateSalt();
        byte[] hash = PasswordHasher.hash(passcode, salt);
        String uuid = UUID.randomUUID().toString();
        long createdAtMs = System.currentTimeMillis();
        Account acct = new Account(uuid, key,
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash),
                createdAtMs, 0L, null);

        String sql = "INSERT INTO accounts " +
                "(uuid, username, salt_base64, hash_base64, created_at_ms, xp, schema_version, extra_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, acct.uuid);
            ps.setString(2, acct.username);
            ps.setString(3, acct.saltBase64);
            ps.setString(4, acct.hashBase64);
            ps.setLong(5, acct.createdAtMs);
            ps.setLong(6, acct.xp);
            ps.setInt(7, SCHEMA_VERSION);
            ps.setString(8, acct.extraJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[AccountStore] Failed to create account: " + e.getMessage());
            return "username already taken";
        }
        byUsername.put(key, acct);
        byUuid.put(acct.uuid, acct);
        return null;
    }

    /** Adds {@code xp} to the account's total, persisting the update immediately. No-op if the account is unknown. */
    @Override
    public synchronized void awardXp(String accountUuid, long xp) {
        if (accountUuid == null || xp == 0) return;
        Account acct = byUuid.get(accountUuid);
        if (acct == null) return;
        long newXp = acct.xp + xp;
        String sql = "UPDATE accounts SET xp = ? WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, newXp);
            ps.setString(2, accountUuid);
            ps.executeUpdate();
            acct.xp = newXp;
        } catch (SQLException e) {
            System.err.println("[AccountStore] Failed to award XP: " + e.getMessage());
        }
    }

    /** Returns the Account if credentials match, null otherwise. */
    public Account authenticate(String username, String passcode) {
        Account acct = byUsername.get(username.toLowerCase());
        if (acct == null) return null;
        byte[] salt = Base64.getDecoder().decode(acct.saltBase64);
        byte[] hash = Base64.getDecoder().decode(acct.hashBase64);
        return PasswordHasher.verify(passcode, salt, hash) ? acct : null;
    }

    private void load() {
        String sql = "SELECT uuid, username, salt_base64, hash_base64, created_at_ms, xp, extra_json FROM accounts";
        int count = 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Account acct = new Account(
                        rs.getString("uuid"),
                        rs.getString("username"),
                        rs.getString("salt_base64"),
                        rs.getString("hash_base64"),
                        rs.getLong("created_at_ms"),
                        rs.getLong("xp"),
                        rs.getString("extra_json"));
                byUsername.put(acct.username, acct);
                byUuid.put(acct.uuid, acct);
                count++;
            }
            System.out.println("[AccountStore] Loaded " + count + " accounts.");
        } catch (SQLException e) {
            System.err.println("[AccountStore] Load failed: " + e.getMessage());
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
    }
}
