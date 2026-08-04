package me.ethanchen.network;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Server;

/**
 * KryoNet endpoint factory. Buffer sizes are intentionally far above KryoNet's 8KB/2KB defaults:
 * {@code ProfileSyncBroadcast} ships the full artifact inventory in one TCP packet, and a few
 * dozen artifacts (or a testing burst of grants) already overflow the defaults, which closes the
 * connection and softlocks login for that account until buffers are raised.
 */
public final class NetEndpoints {
    /** Outbound TCP write buffer; must fit the largest single packet we send. */
    private static final int WRITE_BUFFER_SIZE = 256 * 1024;
    /** Scratch buffer for one object graph during (de)serialization. */
    private static final int OBJECT_BUFFER_SIZE = 128 * 1024;

    private NetEndpoints() {}

    public static Server createServer() {
        Server server = new Server(WRITE_BUFFER_SIZE, OBJECT_BUFFER_SIZE);
        NetworkRegister.registerClasses(server.getKryo());
        return server;
    }

    public static Client createClient() {
        Client client = new Client(WRITE_BUFFER_SIZE, OBJECT_BUFFER_SIZE);
        NetworkRegister.registerClasses(client.getKryo());
        return client;
    }
}
