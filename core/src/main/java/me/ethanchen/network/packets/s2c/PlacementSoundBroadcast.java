package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class PlacementSoundBroadcast extends NetworkPacket {
    /** Normal placement or no-clear. */
    public static final byte SPIN_NONE = 0;
    /** 4-line clear (Tetris). */
    public static final byte SPIN_TETRIS = 1;
    /** T-spin (including mini). */
    public static final byte SPIN_TSPIN = 2;
    /** All-spin (non-T piece spin) or small-spin (I3/L3). */
    public static final byte SPIN_ALL_SPIN = 3;

    /** Player index whose piece was placed. */
    public byte playerId;
    /** Clear type; one of the {@code SPIN_*} constants above. */
    public byte spinType;
    /**
     * Combo count at time of placement:
     *   -1 = no lines were cleared
     *    0 = first consecutive line clear
     *    1 = second consecutive, etc.
     */
    public byte combo;
}
