package me.ethanchen.headless;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AccountStore {
    private final ConcurrentHashMap<String, Account> byUsername = new ConcurrentHashMap<>(); // key: lowercase username
    private final File saveFile;
    private final Json json;
    private final ScheduledExecutorService scheduler;

    public AccountStore(String filePath) {
        this.saveFile = new File(filePath);
        this.json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "account-autosave");
            t.setDaemon(true);
            return t;
        });
        load();
        // Autosave every 5 minutes
        scheduler.scheduleAtFixedRate(this::save, 5, 5, TimeUnit.MINUTES);
        // Save on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(this::save, "account-save-shutdown"));
    }

    /** Returns null on success, error string on conflict. */
    public synchronized String createAccount(String username, String passcode) {
        String key = username.toLowerCase();
        if (byUsername.containsKey(key)) return "username already taken";
        byte[] salt = PasswordHasher.generateSalt();
        byte[] hash = PasswordHasher.hash(passcode, salt);
        String uuid = UUID.randomUUID().toString();
        Account acct = new Account(uuid, key,
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash));
        byUsername.put(key, acct);
        return null;
    }

    /** Returns the Account if credentials match, null otherwise. */
    public Account authenticate(String username, String passcode) {
        Account acct = byUsername.get(username.toLowerCase());
        if (acct == null) return null;
        byte[] salt = Base64.getDecoder().decode(acct.saltBase64);
        byte[] hash = Base64.getDecoder().decode(acct.hashBase64);
        return PasswordHasher.verify(passcode, salt, hash) ? acct : null;
    }

    public synchronized void save() {
        try {
            List<Account> list = new ArrayList<>(byUsername.values());
            String serialized = json.toJson(list);
            try (FileWriter fw = new FileWriter(saveFile)) {
                fw.write(serialized);
            }
            System.out.println("[AccountStore] Saved " + list.size() + " accounts to " + saveFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[AccountStore] Save failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!saveFile.exists()) {
            System.out.println("[AccountStore] No save file found, starting fresh.");
            return;
        }
        try (FileReader fr = new FileReader(saveFile)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = fr.read(buf)) != -1) sb.append(buf, 0, n);
            List<Account> list = json.fromJson(ArrayList.class, Account.class, sb.toString());
            for (Account a : list) byUsername.put(a.username, a);
            System.out.println("[AccountStore] Loaded " + list.size() + " accounts.");
        } catch (Exception e) {
            System.err.println("[AccountStore] Load failed: " + e.getMessage());
        }
    }
}
