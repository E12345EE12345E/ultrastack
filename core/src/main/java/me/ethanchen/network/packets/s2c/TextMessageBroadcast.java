package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class TextMessageBroadcast extends NetworkPacket {
    public String sender;
    public String message;
}
