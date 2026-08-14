package me.ethanchen.game.pve.boss;

/**
 * Timing for the boss defeat animation: the portrait shakes, then shatters into texture
 * shards. {@link #DURATION_MS} is the server {@code DEFEATED} phase length so the section
 * does not advance until the client has time to play the effect.
 */
public final class BossDefeatAnim {
    /** Portrait shake before the shatter. */
    public static final long SHAKE_MS = 1000;
    /** Time after shatter for shards to fall before the section advances. */
    public static final long SHARD_MS = 1600;
    public static final long DURATION_MS = SHAKE_MS + SHARD_MS;

    private BossDefeatAnim() {}
}
