package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.dto.NetBoardLight;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.s2c.gamemode.PuzzleModeData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;

public class LightGameStateBroadcast extends NetworkPacket {
    public NetBoardLight[] boards;
    /** Highest move id processed per local player of the receiving connection. */
    public int[] ackMoveIds = new int[0];
    public int[] piecesPlaced;
    /** Whether each local player of the receiving connection can currently use hold. */
    public boolean[] holdAvailable = new boolean[0];

    // Blocked-spawn / explode countdown state
    /** Seconds into the explode countdown [0, 2]; -1 when inactive. */
    public float explodeProgress = -1f;
    /** Per local player: true when that player's blocked piece may be held. */
    public boolean[] ownPieceHoldGlow = new boolean[0];

    // Gravity sync (used for client-side prediction accuracy)
    /** Current gravity interval in ms (server-authoritative). */
    public int gravity;
    /** Server's gravity tick accumulator at broadcast time (ms). */
    public int gravityTickCounter;

    // Mode-specific (null in all modes except the corresponding one)
    public ScoreModeData scoreMode;
    public PuzzleModeData puzzleMode;

    /** True once the server has detected win/loss; the rest of this packet's payload is frozen
     *  and may be re-sent unchanged until EndGameBroadcast follows (see
     *  GameConstants.PUZZLE_GAME_END_GRACE_MS). */
    public boolean gameEnded;
}
