package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.packets.NetworkPacket;

/** Requests one or ten server-authoritative Card Dealer results. */
public class DealerRequest extends NetworkPacket {
    public int count;
}
