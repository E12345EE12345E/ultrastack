package me.ethanchen.lwjgl3.render;

/** A short-lived Wizard lightning target stored in board-tile coordinates. */
public final class AbilityLightning {
    public static final long DURATION_MS = 200L;

    public final int boardIndex;
    public final float tileX;
    public final float tileY;
    public final long seed;
    private long ageMs;

    public AbilityLightning(int boardIndex, float tileX, float tileY, long seed) {
        this.boardIndex = boardIndex;
        this.tileX = tileX;
        this.tileY = tileY;
        this.seed = seed;
    }

    public void update(int deltaMs) {
        ageMs += Math.max(0, deltaMs);
    }

    public long ageMs() {
        return ageMs;
    }

    public boolean isDead() {
        return ageMs >= DURATION_MS;
    }
}
