package me.ethanchen.network;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Server;

public final class NetEndpoints {
    public static Server createServer() {
        Server server = new Server();
        NetworkRegister.registerClasses(server.getKryo());
        return server;
    }
    public static Client createClient() {
        Client client = new Client();
        NetworkRegister.registerClasses(client.getKryo());
        return client;
    }
}