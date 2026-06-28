package me.ethanchen.headless;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

public class HeadlessLauncher {
    public static void main(String[] args) throws Exception {
        DedicatedServer server = new DedicatedServer();
        server.start();
        // Keep JVM alive; use a minimal HeadlessApplication as the app container
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        config.updatesPerSecond = -1; // no render loop needed
        new HeadlessApplication(new ApplicationAdapter() {}, config);
    }
}
