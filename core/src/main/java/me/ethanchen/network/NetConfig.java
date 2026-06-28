package me.ethanchen.network;

public final class NetConfig {
    // Defaults for connection
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 7777;
    public static final String DEFAULT_SERVER_HOST = "ultrastack.ethanchen.me";

    // Timeouts
    public static final int CONNECT_TIMEOUT_MS = 5000;
    /** Short timeout for the automatic default-server connect attempt. */
    public static final int AUTO_CONNECT_TIMEOUT_MS = 500;
}
