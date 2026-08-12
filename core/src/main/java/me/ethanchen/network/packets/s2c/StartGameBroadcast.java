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
    /**
     * Maps each global session slot (0..totalPlayers-1) to the index of the board it is seated
     * on, so clients can resolve which of {@link #boards} a given slot belongs to. Currently
     * every entry is {@code 0} (the single board created today).
     */
    public byte[] slotBoardIndex = new byte[0];
    /**
     * Maps each global session slot to its board-local seat index (into that board's own
     * piece/spawn arrays), paired with {@link #slotBoardIndex}.
     */
    public byte[] slotSeatIndex = new byte[0];
    /**
     * Milliseconds from the moment this packet was sent until the match starts, so clients can
     * rebase onto their own clock. Negative for a late (spectator) join into a running match.
     * Must not be an absolute timestamp: client and server clocks are not synchronised.
     */
    public long msUntilStart;
    public String[] playerNames;
    /** True when this packet is a late join into an already-running game (spectator). */
    public boolean spectatorJoin;
}
