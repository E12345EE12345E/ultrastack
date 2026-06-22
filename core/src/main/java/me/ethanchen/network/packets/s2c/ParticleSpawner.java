package me.ethanchen.network.packets.s2c;

/**
 * Compact particle spawn descriptor sent in place of individual {@link NetParticle} objects
 * for bulk effects (hard-drop flash, line-clear tile-break).  One spawner object replaces
 * the many per-cell NetParticles previously required.
 *
 * <p>Two spawner types are defined:
 * <ul>
 *   <li>{@link #TYPE_HARD_DROP} — encodes the piece that just locked (type, anchor, rotation).
 *       The client reconstructs each mino's board position and emits a FLASH particle there.</li>
 *   <li>{@link #TYPE_LINE_CLEAR} — encodes one cleared row: the y-coordinate and a tile-type
 *       array of length board_width.  A value of {@code -1} means no particle at that column
 *       (the cell was empty or on an {@code allowedTiles=false} position).  The client emits
 *       a TILE_BREAK particle for each column that is not {@code -1}.</li>
 * </ul>
 *
 * <p>Coordinates follow the same conventions as {@link me.ethanchen.game.board.Piece.NetPiece}:
 * {@code doubledX} / {@code doubledY} are the anchor coordinates multiplied by 2 so that the
 * half-cell offsets used by I and O pieces survive integer truncation.
 */
public class ParticleSpawner {

    /** Hard-drop flash: one FLASH particle per mino of the locked piece. */
    public static final byte TYPE_HARD_DROP  = 0;

    /**
     * Line-clear tile-break: one TILE_BREAK particle per non-(-1) entry in {@link #tileIds}
     * for the row at {@link #lineY}.
     */
    public static final byte TYPE_LINE_CLEAR = 1;

    // ----- common -----

    /** Which spawner type this is ({@link #TYPE_HARD_DROP} or {@link #TYPE_LINE_CLEAR}). */
    public byte spawnerType;

    /** Index into the {@code boards[]} array that these particles belong to. */
    public byte boardIndex;

    // ----- TYPE_HARD_DROP fields -----

    /** Piece type byte (e.g. {@code Piece.I}, {@code Piece.T}, …). */
    public byte pieceType;

    /**
     * Piece anchor x-coordinate multiplied by 2 (matches
     * {@link me.ethanchen.game.board.Piece.NetPiece#doubledlocationx}).
     * Divide by 2 on the client to recover the floating-point anchor.
     */
    public byte doubledX;

    /** Piece anchor y-coordinate multiplied by 2 (see {@link #doubledX}). */
    public byte doubledY;

    /** Rotation state of the piece at the moment of locking (0–3). */
    public byte pieceRotation;

    // ----- TYPE_LINE_CLEAR fields -----

    /**
     * Tile-type byte for each column of the cleared row, indexed by board x.
     * A value of {@code -1} means no TILE_BREAK particle should be spawned at that column
     * (the position was empty or on an {@code allowedTiles=false} cell).
     * Length equals the board width.
     */
    public byte[] tileIds;

    /** Board y-coordinate of the cleared row. */
    public byte lineY;
}
