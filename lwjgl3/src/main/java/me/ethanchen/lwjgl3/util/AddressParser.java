package me.ethanchen.lwjgl3.util;

/**
 * Parses user-entered "host" or "host:port" server addresses. Shared by the menus that let the
 * player type in a connection target (online server, LAN host) so the parsing rules and error
 * messages stay consistent between them.
 */
public final class AddressParser {
    private AddressParser() {}

    public static final class Result {
        public final String host;
        public final int port;

        public Result(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    /** Thrown when the address string is malformed (e.g. a non-numeric port). */
    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    /**
     * Parses a non-empty, already-trimmed "host" or "host:port" string. If no port is present,
     * {@code defaultPort} is used. Does not validate the resulting port range; callers should do
     * that separately (e.g. via {@code ClientApp.validPort}).
     */
    public static Result parse(String addr, int defaultPort) throws ParseException {
        String[] parts = addr.split(":");
        if (parts.length >= 2) {
            int port;
            try {
                port = Integer.parseInt(parts[parts.length - 1]);
            } catch (NumberFormatException e) {
                throw new ParseException("Port must be a number.");
            }
            String host = addr.substring(0, addr.lastIndexOf(':'));
            return new Result(host, port);
        }
        return new Result(parts[0], defaultPort);
    }
}
