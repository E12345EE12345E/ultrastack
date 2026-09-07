package me.ethanchen.testclient;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.esotericsoftware.minlog.Log;

import me.ethanchen.network.NetConfig;

public class TestClientLauncher {
    public static void main(String[] args) {
        Log.INFO();

        String host = NetConfig.HOST;
        int port = NetConfig.PORT;
        if (args.length >= 1 && !args[0].isBlank()) {
            host = args[0].trim();
        }
        if (args.length >= 2) {
            port = Integer.parseInt(args[1].trim());
        }

        // Load-harness knobs (optional): -Dultrastack.rooms=25 -Dultrastack.botsPerRoom=1
        // -Dultrastack.idleMs=30000 -Dultrastack.quiet=true
        // On the dedicated server: -Dultrastack.tickStats=true
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        config.updatesPerSecond = -1;
        new HeadlessApplication(new TestClientApp(host, port), config);
    }
}
