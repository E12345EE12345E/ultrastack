package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import me.ethanchen.game.GameMode;
import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.game.progression.CharacterRegistry;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedChat;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedListButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedScrollableList;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedText;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedTextBox;
import me.ethanchen.lwjgl3.menuscreens.decorated.Widget;
import me.ethanchen.lwjgl3.render.CharacterAssets;
import me.ethanchen.lwjgl3.render.MenuAssets;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.dto.LobbyPlayerInfo;
import me.ethanchen.network.packets.c2s.LobbySettingsRequest;
import me.ethanchen.network.packets.c2s.SpectateRequest;
import me.ethanchen.network.packets.c2s.StartGameRequest;
import me.ethanchen.network.packets.c2s.TextMessageRequest;
import me.ethanchen.network.packets.s2c.HostChangedBroadcast;
import me.ethanchen.network.packets.s2c.LobbyPlayerListBroadcast;
import me.ethanchen.network.packets.s2c.ProfileViewResponse;
import me.ethanchen.network.packets.s2c.RoomClosedBroadcast;
import me.ethanchen.network.packets.s2c.StartGameBroadcast;
import me.ethanchen.network.packets.s2c.TextMessageBroadcast;
import me.ethanchen.util.TextSanitizer;

public class MultiplayerLobby extends DecoratedMenuScreen {
    private static final float PLAYER_SLOT_W = 360f;
    private static final float PLAYER_SLOT_H = 56f;
    private static final int PLAYER_SLOT_COUNT = 8;
    private static final float PLAYER_SLOT_GAP = 8f;

    private final boolean gameInProgress;
    private boolean isHost;
    private GameMode lastBoundMode;

    private final DecoratedChat chat;
    private final DecoratedTextBox chatInput;
    private final DecoratedScrollableList<LobbyPlayerInfo> playerList;
    private final DecoratedButton startBtn;
    private final DecoratedButton scoreBtn;
    private final DecoratedButton puzzleBtn;
    private final DecoratedButton characterBtn;
    private final Widget roomWidget;
    private final ControllerConfigHub controllerHub;
    private PlayerProfileHub profileHub;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = new PacketDispatcher<ClientPacketWrapper>()
            .on(TextMessageBroadcast.class, w -> handleTextMessage((TextMessageBroadcast) w.packet))
            .on(StartGameBroadcast.class, w -> app.switchMenu(new GameScreen(app, (StartGameBroadcast) w.packet, app.isRoomHost())))
            .on(LobbyPlayerListBroadcast.class, w -> handlePlayerList((LobbyPlayerListBroadcast) w.packet))
            .on(ProfileViewResponse.class, w -> handleProfileView((ProfileViewResponse) w.packet))
            .on(RoomClosedBroadcast.class, w -> handleRoomClosed())
            .on(HostChangedBroadcast.class, w -> handleHostChanged((HostChangedBroadcast) w.packet));

    public MultiplayerLobby(ClientApp app, boolean isHost) {
        this(app, isHost, false);
    }

    public MultiplayerLobby(ClientApp app, boolean isHost, boolean gameInProgress) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        this.isHost = isHost;
        this.gameInProgress = gameInProgress;
        app.setRoomHost(isHost);

        DecoratedText title = new DecoratedText(960f, 1000f, "Room Lobby", 3.4f);
        DecoratedButton backBtn = new DecoratedButton(200f, 1000f, 200f, 68f, "Back", this::leaveRoom);
        backBtn.fontSize = 1.4f;

        DecoratedButton controllerBtn = new DecoratedButton(220f, 740f, 320f, 80f, "Controller",
                this::openControllerWidget);
        controllerBtn.fontSize = 1.4f;
        controllerBtn.info("Keyboard and controller input.");

        DecoratedButton loadoutBtn = new DecoratedButton(220f, 640f, 320f, 80f, "Character Loadout",
                this::openCharacterLoadout);
        loadoutBtn.fontSize = 1.25f;
        loadoutBtn.info("Choose a character and artifacts.");

        chat = new DecoratedChat(960f, 620f, 720f, 500f);
        for (ClientApp.LobbyChatLine line : app.copyLobbyChat()) {
            chat.append(line.sender, line.message);
        }
        chatInput = new DecoratedTextBox(960f, 280f, 720f, 84f);
        chatInput.sanitize(DecoratedTextBox.SANITIZE_CHAT);
        chatInput.onEnter(this::sendChat);

        playerList = new DecoratedScrollableList<>(1600f, 880f, PLAYER_SLOT_W, PLAYER_SLOT_H,
                PLAYER_SLOT_COUNT, PLAYER_SLOT_GAP, this::bindPlayer)
                .onSelect(this::openPlayerProfile);

        DecoratedButton roomBtn = new DecoratedButton(260f, 170f, 400f, 80f, "Room Settings",
                this::openRoomWidget);
        roomBtn.icon = MenuAssets.settingsIcon();
        roomBtn.fontSize = 1.25f;
        roomBtn.info("Room gamemode.");

        startBtn = new DecoratedButton(960f, 170f, 360f, 80f, "Start Game", this::startGame);
        startBtn.fill(0.95f, 0.32f, 0.68f);
        startBtn.fontSize = 1.45f;
        applyHostVisibility();

        addDecorated(title);
        addDecorated(backBtn);
        if (gameInProgress) {
            DecoratedButton spectateBtn = new DecoratedButton(420f, 1000f, 200f, 68f, "Spectate",
                    this::requestSpectate);
            spectateBtn.fontSize = 1.3f;
            addDecorated(spectateBtn);
        }
        addDecorated(controllerBtn);
        addDecorated(loadoutBtn);
        addDecorated(chat);
        addDecorated(chatInput);
        addDecorated(playerList);
        addDecorated(roomBtn);
        addDecorated(startBtn);

        roomWidget = new Widget(960f, 520f, 580f, 560f);
        roomWidget.add(new DecoratedText(0f, 0f, "Room", 2.2f), 0f, 220f);
        scoreBtn = new DecoratedButton(0f, 0f, 440f, 78f, "SCORE",
                () -> selectRoomMode(GameMode.MULTIPLAYER_SCORE));
        puzzleBtn = new DecoratedButton(0f, 0f, 440f, 78f, "PUZZLE",
                () -> selectRoomMode(GameMode.MULTIPLAYER_PUZZLE));
        characterBtn = new DecoratedButton(0f, 0f, 440f, 78f, "CHARACTER",
                () -> selectRoomMode(GameMode.CHARACTER_SCORE));
        scoreBtn.fontSize = 1.55f;
        puzzleBtn.fontSize = 1.55f;
        characterBtn.fontSize = 1.55f;
        DecoratedButton roomBack = new DecoratedButton(0f, 0f, 300f, 68f, "Back", this::closeTopWidget);
        roomBack.fontSize = 1.4f;
        roomWidget.add(scoreBtn, 0f, 110f);
        roomWidget.add(puzzleBtn, 0f, 10f);
        roomWidget.add(characterBtn, 0f, -90f);
        roomWidget.add(roomBack, 0f, -210f);
        refreshRoomModeButtons();

        controllerHub = ControllerConfigHub.create(app, this, app::sendLocalPlayerCount);
        lastBoundMode = app.getLobbySettings().gamemode;

        if (isHost) {
            sendPendingLobbySettings();
        }
        app.sendLocalPlayerCount();
    }

    private void applyHostVisibility() {
        startBtn.visible = isHost;
        startBtn.focusable = isHost;
    }

    private void openControllerWidget() {
        if (hasOpenWidget()) return;
        openWidget(controllerHub.widget);
    }

    private void openCharacterLoadout() {
        app.switchMenu(new CharacterScreen(app, new MultiplayerLobby(app, isHost, gameInProgress),
                () -> app.getLobbySettings().gamemode.supportsCharacters()));
    }

    private void openRoomWidget() {
        if (hasOpenWidget()) return;
        refreshRoomModeButtons();
        openWidget(roomWidget);
    }

    private void selectRoomMode(GameMode mode) {
        if (!isHost) return;
        app.getLobbySettings().gamemode = mode;
        app.getLobbySettings().pveLevelId = 0;
        app.getLobbySettings().pveDifficulty = 0;
        sendPendingLobbySettings();
        refreshRoomModeButtons();
        playerList.refresh();
    }

    private void refreshRoomModeButtons() {
        GameMode mode = app.getLobbySettings().gamemode;
        scoreBtn.selected = mode == GameMode.MULTIPLAYER_SCORE;
        puzzleBtn.selected = mode == GameMode.MULTIPLAYER_PUZZLE;
        characterBtn.selected = mode == GameMode.CHARACTER_SCORE;
        scoreBtn.interactable = isHost;
        puzzleBtn.interactable = isHost;
        characterBtn.interactable = isHost;
    }

    private void startGame() {
        if (!isHost) return;
        StartGameRequest p = new StartGameRequest();
        p.gamemode = app.getLobbySettings().gamemode;
        app.sendTCP(p);
    }

    private void sendChat() {
        TextMessageRequest t = new TextMessageRequest();
        t.message = TextSanitizer.sanitizeChat(chatInput.get());
        if (t.message == null || t.message.isEmpty()) return;
        if (app.sendTCP(t)) {
            chatInput.set("");
        }
    }

    private void bindPlayer(DecoratedListButton slot, LobbyPlayerInfo player) {
        if (player == null) {
            slot.text = "";
            slot.icon = null;
            slot.fill(1f, 1f, 1f);
            return;
        }
        slot.text = player.name != null ? player.name : "";
        slot.icon = null;
        if (player.spectating) {
            slot.fill(0.12f, 0.12f, 0.14f, 0.50f);
        } else {
            slot.fill(1f, 1f, 1f);
            boolean hasAccount = player.accountUuid != null && !player.accountUuid.isEmpty();
            if (hasAccount && app.getLobbySettings().gamemode.supportsCharacters()) {
                CharacterDef def = CharacterRegistry.byId(player.characterId);
                if (def != null) {
                    slot.icon = CharacterAssets.portraitFor(def.id);
                }
            }
        }
    }

    private void openPlayerProfile(LobbyPlayerInfo player) {
        if (hasOpenWidget()) return;
        if (player == null || player.accountUuid == null || player.accountUuid.isEmpty()) return;
        profileHub = PlayerProfileHub.create(app, this, player.accountUuid);
        openWidget(profileHub.widget);
    }

    private void handleProfileView(ProfileViewResponse res) {
        if (profileHub != null) profileHub.apply(res);
    }

    private void handleHostChanged(HostChangedBroadcast p) {
        isHost = p.youAreHost;
        app.setRoomHost(p.youAreHost);
        applyHostVisibility();
        if (p.youAreHost) {
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
        app.clearLobbyChat();
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
    protected void updateScreen(long menuElapsedMs, long appElapsedMs) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            debugSetScenario();
        }
        controllerHub.setEnabled(app.getLobbySettings().gamemode != GameMode.PVE);
        controllerHub.tick();
        refreshRoomModeButtons();
        GameMode mode = app.getLobbySettings().gamemode;
        if (mode != lastBoundMode) {
            lastBoundMode = mode;
            playerList.refresh();
        }
    }

    /** Host-only debug: Scenario (PvE) level 0, Normal. */
    private void debugSetScenario() {
        if (!isHost) return;
        app.getLobbySettings().gamemode = GameMode.PVE;
        app.getLobbySettings().pveLevelId = 0;
        app.getLobbySettings().pveDifficulty = 0;
        sendPendingLobbySettings();
        refreshRoomModeButtons();
        playerList.refresh();
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        dispatcher.dispatch(w);
    }

    private void handleTextMessage(TextMessageBroadcast p) {
        chat.append(p.sender, p.message);
    }

    private void handlePlayerList(LobbyPlayerListBroadcast p) {
        if (p.players == null || p.players.length == 0) {
            playerList.setItems(List.of());
            return;
        }
        playerList.setItems(new ArrayList<>(Arrays.asList(p.players)));
    }

    private void handleRoomClosed() {
        app.setRoomHost(false);
        app.clearLobbyChat();
        if (app.isLanMode()) {
            app.disconnect();
            app.switchMenu(new LanMenu(app));
        } else {
            app.switchMenu(new RoomBrowserMenu(app));
        }
    }
}
