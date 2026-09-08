package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.packets.NetworkPacket;

/** Asks the server for the public profile of {@link #accountUuid}. */
public class ProfileViewRequest extends NetworkPacket {
    public String accountUuid;
}
