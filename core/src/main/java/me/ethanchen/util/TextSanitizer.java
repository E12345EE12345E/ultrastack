package me.ethanchen.util;

public final class TextSanitizer {
    private static final int MAX_CHAT_LENGTH = 256;
    private static final int MAX_NAME_LENGTH = 16;

    public static String sanitizeChat(String input) {
        if (input == null) return "";

        String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC);
        if (normalized.length() > MAX_CHAT_LENGTH) {
            normalized = normalized.substring(0, MAX_CHAT_LENGTH);
        }

        StringBuilder out = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(cp -> {
            if (isAllowedChatCodePoint(cp)) {
                out.appendCodePoint(cp);
            }
        });

        return out.toString();
    }

    private static boolean isAllowedChatCodePoint(int cp) {
        if (cp == ' ') return true;
        if (cp >= 'A' && cp <= 'Z') return true;
        if (cp >= 'a' && cp <= 'z') return true;
        if (cp >= '0' && cp <= '9') return true;
        switch (cp) {
            case '.':
            case ',':
            case '!':
            case '?':
            case '\'':
            case '"':
            case '-':
            case '_':
            case '(':
            case ')':
            case ':':
            case ';': return true;
            default: return false;
        }
    }

    public static String sanitizeName(String input) {
        if (input == null) return "";

        String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC);
        if (normalized.length() > MAX_NAME_LENGTH) {
            normalized = normalized.substring(0, MAX_NAME_LENGTH);
        }

        StringBuilder out = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(cp -> {
            if (isAllowedNameCodePoint(cp)) {
                out.appendCodePoint(cp);
            }
        });

        return out.toString().strip().replaceAll(" +", " ");
    }

    public static boolean isAllowedNameCodePoint(int cp) {
        if (cp >= 'A' && cp <= 'Z') return true;
        if (cp >= 'a' && cp <= 'z') return true;
        if (cp >= '0' && cp <= '9') return true;
        return cp == '_';
    }
}