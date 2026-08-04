package me.ethanchen.server;

import org.junit.jupiter.api.Test;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.minlog.Log;

import me.ethanchen.network.NetEndpoints;

/** TEMPORARY scratch harness: times the local LAN connect handshake. */
class LanConnectTimingScratch {

    private static long t0;

    private static void mark() {
        t0 = System.nanoTime();
    }

    private static void report(String what) {
        System.out.println(">>> " + what + " took " + ((System.nanoTime() - t0) / 1_000_000) + "ms");
    }

    /** Mirrors ClientApp: ONE Client instance, reused across repeated connects. */
    @Test
    void reusedClientReconnectTiming() throws Exception {
        Log.set(Log.LEVEL_INFO);
        int port = 7799;

        Client client = NetEndpoints.createClient();
        mark();
        client.start();
        report("client.start()");

        for (int round = 0; round < 4; round++) {
            System.out.println("=== round " + round + " ===");
            ServerCore server = new ServerCore(0, 4);
            mark();
            server.start(port);
            report("server.start()");

            mark();
            try {
                client.connect(5000, "127.0.0.1", port, port);
                report("client.connect() OK connected=" + client.isConnected());
            } catch (Exception e) {
                report("client.connect() FAILED " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }

            mark();
            client.close();
            report("client.close()");

            mark();
            server.stop();
            report("server.stop()");
        }
        client.stop();
    }

    /** The reported bug's shape: connected elsewhere, host a LAN server, connect to it. */
    @Test
    void switchDestinationTiming() throws Exception {
        Log.set(Log.LEVEL_INFO);
        int portA = 7801;
        int portB = 7802;

        ServerCore serverA = new ServerCore(0, 4);
        serverA.start(portA);

        Client client = NetEndpoints.createClient();
        client.start();
        client.connect(5000, "127.0.0.1", portA, portA);
        System.out.println(">>> connected to A = " + client.isConnected());

        ServerCore serverB = new ServerCore(0, 4);
        mark();
        serverB.start(portB);
        report("serverB.start()");

        mark();
        try {
            client.connect(5000, "127.0.0.1", portB, portB);
            report("switch connect() OK connected=" + client.isConnected());
        } catch (Exception e) {
            report("switch connect() FAILED " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        client.close();
        client.stop();
        serverA.stop();
        serverB.stop();
    }
}
