package me.ethanchen.network.packets.s2c;

import me.ethanchen.game.GameMode;
import me.ethanchen.game.board.Board;
import me.ethanchen.network.packets.NetworkPacket;

public class StartGameBroadcast extends NetworkPacket {
    public GameMode mode;
    public Board.NetBoardFull[] boards;
    public byte totalPlayers;
    public byte playerID; // id is resent since it might have changed
    public long startTimeMS;
}
