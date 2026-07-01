package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class BumpSoundBroadcast extends NetworkPacket {
    /** Player index who was blocked/bumped. */
    public byte playerId;
    /** Player index of the other player involved in the bump or block. */
    public byte otherPlayerId;
    /** False = bumpedEvent (mutual lateral block); true = blockedEvent (hard-drop blocked by player). */
    public boolean blocked;
}
