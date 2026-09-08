package me.ethanchen.network.dto;

/**
 * One lobby seat or spectator, transmitted inside {@link me.ethanchen.network.packets.s2c.LobbyPlayerListBroadcast}.
 */
public class LobbyPlayerInfo {
    public String name;
    /** Empty for extra local players that have no account. */
    public String accountUuid;
    public int characterId;
    public boolean spectating;
}
