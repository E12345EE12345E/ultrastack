package me.ethanchen.network;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import me.ethanchen.network.packets.NetworkPacket;

/**
 * Registry mapping a packet's exact class to a handler that consumes the {@link PacketWrapper}
 * it arrived in. Replaces repeated {@code instanceof} dispatch chains: registering a new
 * packet type becomes a single {@link #on} call instead of another {@code if} branch.
 *
 * <p>Since Kryo always delivers a packet as one concrete class, at most one handler ever fires
 * per {@link #dispatch} call — this mirrors the behavior of a chain of
 * {@code if (packet instanceof X) { ...; return; }} checks exactly, as long as each concrete
 * packet class is registered to only one handler.
 *
 * @param <W> the wrapper type (e.g. {@link ServerPacketWrapper}, {@link ClientPacketWrapper})
 */
public class PacketDispatcher<W extends PacketWrapper> {
    private final Map<Class<?>, Consumer<W>> handlers = new HashMap<>();

    /** Registers {@code handler} to run whenever a wrapper's packet is exactly {@code type}. */
    public <P extends NetworkPacket> PacketDispatcher<W> on(Class<P> type, Consumer<W> handler) {
        handlers.put(type, handler);
        return this;
    }

    /**
     * Looks up and invokes the handler registered for {@code w.packet}'s exact class, if any.
     *
     * @return true if a handler was found and invoked, false otherwise
     */
    public boolean dispatch(W w) {
        if (w == null || w.packet == null) return false;
        Consumer<W> handler = handlers.get(w.packet.getClass());
        if (handler == null) return false;
        handler.accept(w);
        return true;
    }
}
