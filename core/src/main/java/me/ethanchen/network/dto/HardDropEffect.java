package me.ethanchen.network.dto;

/**
 * Compact descriptor of a single piece placement, carrying everything the client needs to
 * play the placement sound, spawn the hard-drop flash particles, and poof the player's ripple
 * circle. One entry is queued per successful lock (manual hard drop, movement auto-lock, or
 * gravity auto-lock).
 *
 * <p><strong>Purely cosmetic:</strong> delivered unreliably over UDP inside a
 * {@link me.ethanchen.network.packets.s2c.HardDropEffectsBroadcast}, may be dropped in transit,
 * and must never be used to drive game logic or authoritative state — only sound/particle/visual
 * effects.
 *
 * <p>Coordinates follow the same conventions as {@link NetPiece}: {@code doubledX}/{@code doubledY}
 * are the anchor coordinates multiplied by 2 so that the half-cell offsets used by I and O pieces
 * survive integer truncation.
 */
public class HardDropEffect {

    /** Normal placement or no-clear. */
    public static final byte SPIN_NONE = 0;
    /** 4-line clear (Tetris). */
    public static final byte SPIN_TETRIS = 1;
    /** T-spin (including mini). */
    public static final byte SPIN_TSPIN = 2;
    /** All-spin (non-T piece spin) or small-spin (I3/L3). */
    public static final byte SPIN_ALL_SPIN = 3;

    /** Player index whose piece was placed. */
    public byte playerId;

    /** Piece type byte (e.g. {@code Piece.I}, {@code Piece.T}, ...) of the placed piece. */
    public byte pieceType;

    /**
     * Piece anchor x-coordinate multiplied by 2 (matches {@link NetPiece#doubledlocationx}).
     * Divide by 2 on the client to recover the floating-point anchor.
     */
    public byte doubledX;

    /** Piece anchor y-coordinate multiplied by 2 (see {@link #doubledX}). */
    public byte doubledY;

    /** Rotation state of the piece at the moment of locking (0-3). */
    public byte pieceRotation;

    /** Clear/spin type; one of the {@code SPIN_*} constants above. */
    public byte spinType;

    /** Number of lines cleared by this placement (0 if none). */
    public byte lines;

    /**
     * Combo count at time of placement:
     *   -1 = no lines were cleared
     *    0 = first consecutive line clear
     *    1 = second consecutive, etc.
     */
    public byte combo;

    /** True when this placement emptied the board (All Clear / Perfect Clear). */
    public boolean allClear;
}
