package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

/**
 * Notifies clients that a player's active piece was forcibly swapped to a new type at spawn.
 * Cosmetic companion to the authoritative board snapshot — clients apply the same swap locally
 * and trigger the ripple poof animation.
 */
public class PieceSwapBroadcast extends NetworkPacket {
    /** Player slot whose active piece was swapped. */
    public byte playerId;
    /** Piece type that was spawned. */
    public byte pieceType;
    /** Index of the board this swap occurred on. */
    public byte boardIndex;
}
