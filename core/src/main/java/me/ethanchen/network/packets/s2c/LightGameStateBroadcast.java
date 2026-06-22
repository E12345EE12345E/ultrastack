package me.ethanchen.network.packets.s2c;

import me.ethanchen.game.board.Board;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;

public class LightGameStateBroadcast extends NetworkPacket {
    public Board.NetBoardLight[] boards;
    public int ackMoveId = -1; // highest move id the server has processed for the receiving player
    public int[] piecesPlaced;
    public boolean holdAvailable; // whether the receiving player can currently use hold

    // Blocked-spawn / explode countdown state
    /** Seconds into the explode countdown [0, 2]; -1 when inactive. */
    public float explodeProgress = -1f;
    /** True when this player's own blocked piece has reached min interval and may be held. */
    public boolean ownPieceHoldGlow = false;

    // Gravity sync (used for client-side prediction accuracy)
    /** Current gravity interval in ms (server-authoritative). */
    public int gravity;
    /** Server's gravity tick accumulator at broadcast time (ms). */
    public int gravityTickCounter;

    // Mode-specific (null in all modes except the corresponding one)
    public ScoreModeData scoreMode;
}
