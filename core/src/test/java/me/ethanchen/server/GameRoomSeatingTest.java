package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameMode;
import me.ethanchen.network.ServerPacketWrapper;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.StartGameRequest;

/**
 * Seating / spectator allocation for multi-local-player clients.
 */
class GameRoomSeatingTest {

    private static final class RecordingSender implements PacketSender {
        final List<NetworkPacket> sent = new ArrayList<>();

        @Override
        public void sendTCP(int connectionId, NetworkPacket packet) {
            sent.add(packet);
        }

        @Override
        public void sendUDP(int connectionId, NetworkPacket packet) {
            sent.add(packet);
        }
    }

    @Test
    void twoMembersWithOverflowBecomeActivePlusSpectator() {
        RecordingSender sender = new RecordingSender();
        // Host with 2 local players
        GameRoom room = new GameRoom("t1", sender, 1, "Alex", "uuid-alex", 2, null, null);
        assertEquals(0, room.getSlotFor(1, 0));
        assertEquals(1, room.getSlotFor(1, 1));
        assertEquals(2, room.getPlayerCount());
        assertEquals(0, room.getSpectatorCount());

        // Second member wants 3 local players → 2 active + 1 spectator (cap 4)
        GameRoom.AddMemberResult add = room.tryAddMember(2, "Bob", "uuid-bob", 3, GameConstants.MAX_PLAYERS);
        assertTrue(add.success);
        assertFalse(add.gameInProgress);
        assertEquals(4, room.getPlayerCount());
        assertEquals(1, room.getSpectatorCount());

        assertEquals(2, room.getSlotFor(2, 0));
        assertEquals(3, room.getSlotFor(2, 1));
        assertEquals(-1, room.getSlotFor(2, 2)); // spectator
    }

    @Test
    void spectatorPromotedWhenMemberLeaves() {
        RecordingSender sender = new RecordingSender();
        // Host 1 seat + Alex 2 + Bob 2 = 4 active + 1 spectator (Bob - 2)
        GameRoom room = new GameRoom("t2", sender, 1, "Host", "uh", 1, null, null);
        room.tryAddMember(2, "Alex", "ua", 2, GameConstants.MAX_PLAYERS);
        room.tryAddMember(3, "Bob", "ub", 2, GameConstants.MAX_PLAYERS);
        assertEquals(4, room.getPlayerCount());
        assertEquals(1, room.getSpectatorCount());
        assertEquals(-1, room.getSlotFor(3, 1));

        room.handleDisconnect(2); // non-host Alex leaves → Bob - 2 promoted
        assertEquals(3, room.getPlayerCount());
        assertEquals(0, room.getSpectatorCount());
        assertEquals(0, room.getSlotFor(1, 0));
        assertEquals(1, room.getSlotFor(3, 0));
        assertEquals(2, room.getSlotFor(3, 1));
    }

    @Test
    void connLocalIndexResolvesToExpectedSlot() {
        RecordingSender sender = new RecordingSender();
        GameRoom room = new GameRoom("t3", sender, 10, "John", "u1", 1, null, null);
        room.tryAddMember(20, "Alex", "u2", 2, GameConstants.MAX_PLAYERS);
        room.tryAddMember(30, "Bob", "u3", 1, GameConstants.MAX_PLAYERS);

        assertEquals(0, room.getSlotFor(10, 0)); // John
        assertEquals(1, room.getSlotFor(20, 0)); // Alex
        assertEquals(2, room.getSlotFor(20, 1)); // Alex - 2
        assertEquals(3, room.getSlotFor(30, 0)); // Bob
        assertEquals(-1, room.getSlotFor(20, 5)); // OOB
        assertEquals(-1, room.getSlotFor(99, 0)); // unknown conn
    }

    @Test
    void reseatFrozenWhileGameInProgress() {
        RecordingSender sender = new RecordingSender();
        GameRoom room = new GameRoom("t4", sender, 1, "Host", "uh", 1, null, null);
        room.tryAddMember(2, "Alex", "ua", 2, GameConstants.MAX_PLAYERS);
        assertEquals(3, room.getPlayerCount());

        StartGameRequest start = new StartGameRequest();
        start.gamemode = GameMode.MULTIPLAYER_SCORE;
        room.handlePacket(new ServerPacketWrapper(start, 1, null));

        // Start the room thread and wait a tick so the start request is processed.
        room.start();
        try {
            Thread.sleep(80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(room.isInProgress());

        // Mid-game join becomes spectator; active count unchanged
        GameRoom.AddMemberResult late = room.tryAddMember(3, "Carl", "uc", 2, GameConstants.MAX_PLAYERS);
        assertTrue(late.success);
        assertTrue(late.gameInProgress);
        assertTrue(late.spectatorOnly);
        assertEquals(3, room.getPlayerCount());
        assertEquals(2, room.getSpectatorCount()); // Carl + Carl-2
        assertEquals(-1, room.getSlotFor(3, 0));

        // Local-player count change ignored while in progress
        room.setLocalPlayerCount(2, 4);
        assertEquals(3, room.getPlayerCount());
        assertEquals(1, room.getSlotFor(2, 0));
        assertEquals(2, room.getSlotFor(2, 1));

        room.stop();
    }

    @Test
    void hostLeaveTransfersToEarliestRemainingMember() {
        RecordingSender sender = new RecordingSender();
        GameRoom room = new GameRoom("t5", sender, 1, "Host", "uh", 1, null, null);
        room.tryAddMember(2, "Alex", "ua", 1, GameConstants.MAX_PLAYERS);
        room.tryAddMember(3, "Bob", "ub", 1, GameConstants.MAX_PLAYERS);
        assertEquals(1, room.getHostConnId());

        room.handleDisconnect(1); // original host leaves
        assertEquals(2, room.getHostConnId()); // Alex (earliest remaining) is host
        assertFalse(room.isEmpty());
        assertEquals(2, room.getPlayerCount());

        boolean sawAlexHost = false;
        boolean sawBobNotHost = false;
        for (NetworkPacket p : sender.sent) {
            if (p instanceof me.ethanchen.network.packets.s2c.HostChangedBroadcast) {
                me.ethanchen.network.packets.s2c.HostChangedBroadcast h =
                        (me.ethanchen.network.packets.s2c.HostChangedBroadcast) p;
                if ("Alex".equals(h.hostName) && h.youAreHost) sawAlexHost = true;
                if ("Alex".equals(h.hostName) && !h.youAreHost) sawBobNotHost = true;
            }
        }
        assertTrue(sawAlexHost);
        assertTrue(sawBobNotHost);
    }

    @Test
    void hostLeaveAloneEmptiesRoom() {
        RecordingSender sender = new RecordingSender();
        GameRoom room = new GameRoom("t6", sender, 1, "Host", "uh", 1, null, null);
        room.handleDisconnect(1);
        assertTrue(room.isEmpty());
        assertEquals(-1, room.getHostConnId());
    }
}
