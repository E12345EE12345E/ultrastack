package me.ethanchen.headless;

import me.ethanchen.network.NetConfig;
import me.ethanchen.server.ServerCore;

public class DedicatedServer {
    public void start() throws Exception {
        AccountStore accountStore = new AccountStore("accounts.db");
        AccountAuthProvider authProvider = new AccountAuthProvider(accountStore);
        GameResultStore resultStore = new GameResultStore("results.db");
        ServerCore serverCore = new ServerCore(authProvider, resultStore, accountStore, accountStore, 4); // 4-digit room IDs
        serverCore.start(NetConfig.PORT);
        System.out.println("[DedicatedServer] Running on port " + NetConfig.PORT);
    }
}
