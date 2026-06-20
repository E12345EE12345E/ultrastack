package me.ethanchen.network.packets.s2c;

/**
 * Lightweight particle spawn event broadcast inside {@link LightGameStateBroadcast}.
 * Dropped packets are harmless — particles are purely cosmetic.
 *
 * Coordinates are in board-tile space (matching {@code Board.getBoard()[y][x]} indices).
 */
public class NetParticle {
    /** Index into the {@code boards[]} array that this particle belongs to. */
    public byte boardIndex;

    /**
     * Kind of particle effect to spawn.
     * 0 = FLASH  — white square flash at placement (no gravity, fades quickly)
     * 1 = TILE_BREAK — colored shard affected by gravity, fades over ~0.5 s
     * 2 = POPUP_SCORE — floating score text; {@code value} = points awarded
     * 3 = POPUP_SCORE_MULTIPLIER — floating bonus text; {@code value} = 4-bit bonus bitfield
     *     (bit0=B2B, bit1=DifferentColumn, bit2=Combo, bit3=Glow)
     */
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
