package me.ethanchen.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import me.ethanchen.network.packets.NetworkPacket;

class PacketDispatcherTest {

    @Test
    void unknownPacketReturnsFalse() {
        PacketDispatcher<ClientPacketWrapper> d = new PacketDispatcher<>();
        ClientPacketWrapper w = new ClientPacketWrapper(new NetworkPacket() {}, null);
        assertFalse(d.dispatch(w));
    }

    @Test
    void registeredHandlerRuns() {
        AtomicInteger calls = new AtomicInteger();
        PacketDispatcher<ClientPacketWrapper> d = new PacketDispatcher<ClientPacketWrapper>()
                .on(LoginMarker.class, w -> calls.incrementAndGet());
        assertTrue(d.dispatch(new ClientPacketWrapper(new LoginMarker(), null)));
        assertEquals(1, calls.get());
        assertFalse(d.dispatch(new ClientPacketWrapper(new NetworkPacket() {}, null)));
        assertEquals(1, calls.get());
    }

    @Test
    void handlerExceptionPropagates() {
        PacketDispatcher<ClientPacketWrapper> d = new PacketDispatcher<ClientPacketWrapper>()
                .on(LoginMarker.class, w -> {
                    throw new IllegalStateException("boom");
                });
        assertThrows(IllegalStateException.class,
                () -> d.dispatch(new ClientPacketWrapper(new LoginMarker(), null)));
    }

    private static final class LoginMarker extends NetworkPacket {}
}
