package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class HoldSoundBroadcast extends NetworkPacket {
    /** Player index who attempted the hold. */
    public byte playerId;
    /** True if the hold succeeded; false if it was blocked by the global cooldown. */
    public boolean success;
}
