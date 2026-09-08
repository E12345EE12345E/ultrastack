package me.ethanchen.game.progression;

/**
 * Display-only account level derived from lifetime XP. Matches the web leaderboard formula:
 * {@code floor(sqrt(xp / 100)) + 1}.
 */
public final class Levels {
    private Levels() {}

    public static int levelFromXp(long xp) {
        long safe = Math.max(0L, xp);
        return (int) Math.floor(Math.sqrt(safe / 100.0)) + 1;
    }
}
