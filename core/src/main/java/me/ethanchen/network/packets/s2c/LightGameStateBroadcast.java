package me.ethanchen.network.packets.s2c;

import me.ethanchen.game.board.Board;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;

public class LightGameStateBroadcast extends NetworkPacket {
    public Board.NetBoardLight[] boards;
    public int ackMoveId = -1; // highest move id the server has processed for the receiving player
    public int[] piecesPlaced;
    public boolean holdAvailable; // whether the receiving player can currently use hold

    // Mode-specific (null in all modes except the corresponding one)
    public ScoreModeData scoreMode;
}
