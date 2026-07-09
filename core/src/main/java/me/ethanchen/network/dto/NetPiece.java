package me.ethanchen.network.dto;

/**
 * Wire representation of a single active piece, sent as part of {@link NetBoardLight} /
 * {@link NetBoardFull}.
 */
public class NetPiece {
    public byte type;
    // I and O pieces have centers on 0.5, so location is doubled to become an integer
    // and halved again on packet arrival.
    public byte doubledlocationx;
    public byte doubledlocationy;
    public byte rotation;
    public boolean blocked;
}
