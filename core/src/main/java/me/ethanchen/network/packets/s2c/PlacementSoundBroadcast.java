package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class PlacementSoundBroadcast extends NetworkPacket {
    /** Player index whose piece was placed. */
    public byte playerId;
    /**
     * Clear type:
     *   0 = normal placement or no-clear
     *   1 = 4-line clear
     *   2 = T-spin
     *   3 = all-spin
     */
    public byte spinType;
    /**
     * Combo count at time of placement:
     *   -1 = no lines were cleared
     *    0 = first consecutive line clear
     *    1 = second consecutive, etc.
     */
    public byte combo;
}
