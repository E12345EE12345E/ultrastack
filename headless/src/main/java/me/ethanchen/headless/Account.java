package me.ethanchen.headless;

public class Account {
    public String uuid;
    public String username;     // lowercase-normalized
    public String saltBase64;   // Base64-encoded 16-byte salt
    public String hashBase64;   // Base64-encoded PBKDF2WithHmacSHA256 hash

    public Account() {} // required for libGDX Json deserialization

    public Account(String uuid, String username, String saltBase64, String hashBase64) {
        this.uuid = uuid;
        this.username = username;
        this.saltBase64 = saltBase64;
        this.hashBase64 = hashBase64;
    }
}
