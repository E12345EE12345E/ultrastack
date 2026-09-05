package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayDeque;

import me.ethanchen.game.GameMode;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.*;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.packets.c2s.LobbySettingsRequest;
import me.ethanchen.network.packets.c2s.SpectateRequest;
import me.ethanchen.network.packets.c2s.StartGameRequest;
import me.ethanchen.network.packets.c2s.TextMessageRequest;
import me.ethanchen.network.packets.s2c.HostChangedBroadcast;
import me.ethanchen.network.packets.s2c.LobbyPlayerListBroadcast;
import me.ethanchen.network.packets.s2c.RoomClosedBroadcast;
import me.ethanchen.network.packets.s2c.StartGameBroadcast;
import me.ethanchen.network.packets.s2c.TextMessageBroadcast;
import me.ethanchen.util.TextSanitizer;

public class MultiplayerLobby extends MenuScreen {
    private static final int MAX_CHAT_LINES = 10;

    private TextBoxOutput chatoutput;
    private TextInput chat;
    private TextInput playerNameList;
    private ArrayDeque<String> chatLines;
    private boolean isHost;
    private boolean hostButtonsAdded;
    private final LocalPlayerSidebar sidebar;
    private final CharacterSidebar characterSidebar;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = new PacketDispatcher<ClientPacketWrapper>()
            .on(TextMessageBroadcast.class, w -> handleTextMessage((TextMessageBroadcast) w.packet))
            .on(StartGameBroadcast.class, w -> app.switchMenu(new GameScreen(app, (StartGameBroadcast) w.packet, app.isRoomHost())))
            .on(LobbyPlayerListBroadcast.class, w -> handlePlayerList((LobbyPlayerListBroadcast) w.packet))
            .on(RoomClosedBroadcast.class, w -> handleRoomClosed())
            .on(HostChangedBroadcast.class, w -> handleHostChanged((HostChangedBroadcast) w.packet));

    public MultiplayerLobby(ClientApp app, boolean isHost) {
        this(app, isHost, false);
    }

    public MultiplayerLobby(ClientApp app, boolean isHost, boolean gameInProgress) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        this.isHost = isHost;
        app.setRoomHost(isHost);
        chatLines = new ArrayDeque<String>();

        elements.add(new UIText(0.5, 0.8, "Lobby", 4));

        // Leave button — top-left corner
        elements.add(new UIButton(0.08, 0.93, 0.12, 0.07, "Leave", this::leaveRoom));
        if (gameInProgress) {
            elements.add(new UIButton(0.88, 0.93, 0.16, 0.07, "Spectate", this::requestSpectate));
        }

        chatoutput = new TextBoxOutput();
        chat = new TextInput();
        playerNameList = new TextInput();
        elements.add(new UIText(0.2, 0.35, chat, 2, UIText.TextAlign.BOTTOM_LEFT));
        elements.add(new UIText(0.68, 0.6, playerNameList, 1, UIText.TextAlign.TOP_LEFT));
        UITextBox chatInput = new UITextBox(0.5, 0.25, 0.6, 0.08, chatoutput, null, null);
        chatInput.runOnEnter = () -> {
            TextMessageRequest t = new TextMessageRequest();
            t.message = TextSanitizer.sanitizeChat(chatoutput.get());
            if (app.sendTCP(t)) {
                chatInput.text = "";
                chatoutput.set("");
            }
        };
        chatInput.sanitize = 1;
        elements.add(chatInput);
        if (isHost) {
            addHostButtons();
            // Sync host's local pending settings into the room so peers (and late joiners) match.
            sendPendingLobbySettings();
        }

        sidebar = new LocalPlayerSidebar(app, elements, app::sendLocalPlayerCount);
        sidebar.setEnabled(app.getLobbySettings().gamemode != GameMode.PVE);
        app.sendLocalPlayerCount();

        characterSidebar = new CharacterSidebar(app, elements, this,
                () -> app.getLobbySettings().gamemode.supportsCharacters());
    }

    private void addHostButtons() {
        if (hostButtonsAdded) return;
        hostButtonsAdded = true;
        elements.add(new UIButton(0.18, 0.125, 0.28, 0.1, "Settings",
                () -> app.switchMenu(new LobbySettingsScreen(app, this))));

        elements.add(new UIButton(0.5, 0.125, 0.3, 0.1, "Start Game", () -> {
            StartGameRequest p = new StartGameRequest();
            p.gamemode = app.getLobbySettings().gamemode;
            app.sendTCP(p);
        }));
    }

    private void handleHostChanged(HostChangedBroadcast p) {
        isHost = p.youAreHost;
        app.setRoomHost(p.youAreHost);
        if (p.youAreHost) {
            addHostButtons();
            sendPendingLobbySettings();
        }
    }

    private void sendPendingLobbySettings() {
        LobbySettingsRequest req = new LobbySettingsRequest();
        req.gamemode = app.getLobbySettings().gamemode;
        req.pveLevelId = app.getLobbySettings().pveLevelId;
        req.pveDifficulty = app.getLobbySettings().pveDifficulty;
        app.sendTCP(req);
    }

    private void requestSpectate() {
        app.sendTCP(new SpectateRequest());
    }

    private void leaveRoom() {
        app.setRoomHost(false);
        app.sendLeaveRoomRequest();
        if (app.isLanMode()) {
            app.stopLanServer();
            app.disconnect();
            app.switchMenu(new LanMenu(app));
        } else {
            app.switchMenu(new RoomBrowserMenu(app));
        }
    }

    @Override
    protected void onEscPressed() {
        leaveRoom();
    }

    @Override
    public void update() {
        sidebar.setEnabled(app.getLobbySettings().gamemode != GameMode.PVE);
        sidebar.tick();
        characterSidebar.tick();
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        dispatcher.dispatch(w);
    }

    private void handleTextMessage(TextMessageBroadcast p) {
        chatLines.add("[" + p.sender + "] " + p.message + " ");
        while (chatLines.size() > MAX_CHAT_LINES) {
            chatLines.removeFirst();
        }
        chat.set(String.join("\n", chatLines));
    }

    private void handlePlayerList(LobbyPlayerListBroadcast p) {
        StringBuilder sb = new StringBuilder();
        if (p.playerNames != null) {
            for (int i = 0; i < p.playerNames.length; i++) {
                sb.append("p").append(i + 1).append(": ").append(p.playerNames[i]).append("\n");
            }
        }
        if (p.spectatorNames != null) {
            for (String name : p.spectatorNames) {
                sb.append("(Spectator) ").append(name).append("\n");
            }
        }
        playerNameList.set(sb.toString());
    }

    private void handleRoomClosed() {
        app.setRoomHost(false);
        // Host left the lobby — return to the room browser (stay connected online).
        if (app.isLanMode()) {
            app.disconnect();
            app.switchMenu(new LanMenu(app));
        } else {
            app.switchMenu(new RoomBrowserMenu(app));
        }
    }
}
