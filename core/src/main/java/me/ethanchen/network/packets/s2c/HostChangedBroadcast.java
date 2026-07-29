package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

/**
 * Sent to each remaining room member when the host connection leaves and host duties
 * are transferred to the earliest-joined remaining member. Personalized per recipient.
 */
public class HostChangedBroadcast extends NetworkPacket {
    /** True if the receiving connection is the new host. */
    public boolean youAreHost;
    /** Display name of the new host. */
    public String hostName;
}
