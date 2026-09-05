package me.ethanchen.network.dto;

/** Smaller board snapshot, sent over UDP constantly (20-30 times/sec). */
public class NetBoardLight {
    public byte[] tileid;
    public byte[] tileconnections;
    public NetPiece[] pieces;
    public byte heldPieceType;
    public boolean[] playerHoldUsed;
    public NetFallingColumn[] falling;
    /**
     * Upcoming piece types, seat-major: index {@code seat * NEXT_PREVIEW_MAX + preview}.
     * {@code 0} means empty / unknown.
     */
    public byte[] nextPieceTypes;
}
