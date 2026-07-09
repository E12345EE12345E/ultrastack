package me.ethanchen.network.dto;

/**
 * Snapshot of a single room's lobby state, transmitted inside {@link me.ethanchen.network.packets.s2c.RoomListBroadcast}.
 * Replaces the previous parallel-array layout ({@code roomIds[]}, {@code hostNames[]},
 * {@code playerCounts[]}, {@code inProgress[]}) with a single cohesive struct so adding a new
 * field requires only one change here rather than four synchronized array additions.
 */
public class RoomInfo {
    public String roomId;
    public String hostName;
    public int playerCount;
    public boolean inProgress;
}
