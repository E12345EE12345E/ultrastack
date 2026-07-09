package me.ethanchen.network.dto;

import me.ethanchen.game.board.PieceQueue;

/**
 * Wire representation of a {@link PieceQueue}'s resumable state (seed + progress).
 * Only sent on init and desyncs.
 */
public class NetQueue {
    public int seed;
    public PieceQueue.BagTypes bag;
    public byte[] piecesAlreadyInBag;
    public int alreadyGeneratedNumber;
}
