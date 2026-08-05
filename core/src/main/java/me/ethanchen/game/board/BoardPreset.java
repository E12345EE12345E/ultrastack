package me.ethanchen.game.board;

import java.util.Arrays;
import java.util.Random;

import com.badlogic.gdx.math.Vector2;

/**
 * Factory that encodes the initial geometry of each {@link Board.Presets} variant.
 * Extracts the ~100-line switch block that previously lived inside {@link Board#Board(Board.Presets)},
 * keeping {@link Board} focused on state management and game logic.
 *
 * <p>Call {@link #of(Board.Presets)} to get an instance, then use its public fields to initialise
 * a {@link Board}'s {@code final} fields.
 */
public final class BoardPreset {

    public final byte width;
    public final byte height;
    public final boolean[][] allowedTiles;
    public final Vector2[] spawnPositions;
    public final PieceQueue[] pieceQueues;

    private BoardPreset(byte width, byte height, boolean[][] allowedTiles,
                        Vector2[] spawnPositions, PieceQueue[] pieceQueues) {
        this.width = width;
        this.height = height;
        this.allowedTiles = allowedTiles;
        this.spawnPositions = spawnPositions;
        this.pieceQueues = pieceQueues;
    }

    /** Creates the preset configuration for the given variant. */
    public static BoardPreset of(Board.Presets preset) {
        Random rng = new Random();
        switch (preset) {
            default:
            case STANDARD_SINGLE:
                return build(rng, (byte) 10, (byte) 24, spawns(new Vector2(4, 20)));
            case STANDARD_DUO:
                return build(rng, (byte) 10, (byte) 24, spawns(new Vector2(1, 20), new Vector2(7, 20)));
            case STANDARD_TRIO:
                return build(rng, (byte) 16, (byte) 24, spawns(new Vector2(1, 20), new Vector2(7, 20), new Vector2(13, 20)));
            case STANDARD_4P:
                return build(rng, (byte) 22, (byte) 24, spawns(new Vector2(1, 20), new Vector2(7, 20), new Vector2(13, 20), new Vector2(19, 20)));
            case SHORT_SINGLE:
                return build(rng, (byte) 10, (byte) 16, spawns(new Vector2(4, 12)));
            case SHORT_DUO:
                return build(rng, (byte) 10, (byte) 16, spawns(new Vector2(1, 12), new Vector2(7, 12)));
            case SHORT_TRIO:
                return build(rng, (byte) 16, (byte) 16, spawns(new Vector2(1, 12), new Vector2(7, 12), new Vector2(13, 12)));
            case SHORT_4P:
                return build(rng, (byte) 22, (byte) 16, spawns(new Vector2(1, 12), new Vector2(7, 12), new Vector2(13, 12), new Vector2(19, 12)));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static BoardPreset build(Random rng, byte w, byte h, Vector2[] spawnPositions) {
        boolean[][] allowed = new boolean[h][w];
        for (boolean[] row : allowed) Arrays.fill(row, true);
        PieceQueue[] queues = new PieceQueue[spawnPositions.length];
        for (int i = 0; i < queues.length; i++) {
            queues[i] = new PieceQueue(rng.nextInt(), PieceQueue.BagTypes.BAG_7);
        }
        return new BoardPreset(w, h, allowed, spawnPositions, queues);
    }

    private static Vector2[] spawns(Vector2... positions) {
        return positions;
    }
}
