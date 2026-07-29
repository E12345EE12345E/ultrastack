package me.ethanchen.network.packets.s2c;

import me.ethanchen.game.GameMode;
import me.ethanchen.network.dto.NetBoardFull;
import me.ethanchen.network.packets.NetworkPacket;

public class StartGameBroadcast extends NetworkPacket {
    public GameMode mode;
    public NetBoardFull[] boards;
    public byte totalPlayers;
    /** Board slots this connection controls, in local-player order. Empty = spectating. */
    public byte[] localPlayerIds = new byte[0];
    public long startTimeMS;
    public String[] playerNames;
    /** True when this packet is a late join into an already-running game (spectator). */
    public boolean spectatorJoin;
}
