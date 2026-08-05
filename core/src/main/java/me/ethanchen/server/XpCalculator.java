package me.ethanchen.server;

import me.ethanchen.game.GameMode;

/** Computes how much XP a finished game is worth, based on gamemode-specific rules. */
public final class XpCalculator {
    private XpCalculator() {}

    public static long computeXp(GameMode mode, long score) {
        if (mode == null) return 0L;
        switch (mode) {
            case MULTIPLAYER_SCORE:
            case CHARACTER_SCORE:
                return (long) Math.ceil(score / 100.0);
            case MULTIPLAYER_PUZZLE:
                return 20L;
            default:
                return 0L;
        }
    }
}
