package me.ethanchen.network.packets.other;

import me.ethanchen.network.packets.NetworkPacket;

/** Posted to the client packet queue when a TCP connection is established. Never sent over the wire. */
public class ConnectionEstablishedPacket extends NetworkPacket {}
