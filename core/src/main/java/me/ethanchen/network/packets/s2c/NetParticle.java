package me.ethanchen.network.packets.s2c;

/**
 * Lightweight particle spawn event broadcast inside {@link LightGameStateBroadcast}.
 * Dropped packets are harmless — particles are purely cosmetic.
 *
 * Coordinates are in board-tile space (matching {@code Board.getBoard()[y][x]} indices).
 */
public class NetParticle {
    /** White square flash at placement (no gravity, fades quickly). */
    public static final byte KIND_FLASH = 0;
    /** Colored shard affected by gravity, fades over ~0.5 s. */
    public static final byte KIND_TILE_BREAK = 1;
    /** Floating score text; {@code value} = points awarded. */
    public static final byte KIND_POPUP_SCORE = 2;
    /**
     * Floating bonus text; {@code value} = 4-bit bonus bitfield
     * (bit0=B2B, bit1=DifferentColumn, bit2=Combo, bit3=Glow).
     */
    public static final byte KIND_POPUP_SCORE_MULTIPLIER = 3;

    /** Index into the {@code boards[]} array that this particle belongs to. */
    public byte boardIndex;

    /** Kind of particle effect to spawn; one of the {@code KIND_*} constants above. */
    public byte kind;

    /**
     * Extra integer payload.  Meaning depends on {@code kind}:
     *   kind 2: points awarded for the line clear
     *   kind 3: bonus bitfield (bits 0–3 as documented above)
     */
    public int value;

    /** Piece/tile type byte used by the client to look up the correct tint color. */
    public byte tileType;

    /** Horizontal board-tile coordinate of the spawn origin. */
    public float x;

    /** Vertical board-tile coordinate of the spawn origin. */
    public float y;
}
