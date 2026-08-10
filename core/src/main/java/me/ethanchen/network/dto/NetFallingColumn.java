package me.ethanchen.network.dto;

/**
 * Wire representation of a {@link me.ethanchen.game.board.FallingColumn}, sent as part of
 * {@link NetBoardLight} / {@link NetBoardFull}.
 */
public class NetFallingColumn {
    public int id;
    public int x;
    public float bottomY;
    public byte[] types;
    public float velocity;
    public boolean moving;
    public int triggerPlayerId;
    public boolean pieceTrigger;
}
