package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.Gdx;

import me.ethanchen.lwjgl3.AppLinks;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecorContext;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedAccountButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedScrollableList;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedScrollbar;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedText;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedTextBox;
import me.ethanchen.lwjgl3.menuscreens.decorated.Widget;
import me.ethanchen.lwjgl3.render.MenuAssets;
import me.ethanchen.lwjgl3.render.shader.AuroraBackgroundRenderer;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.dto.RoomInfo;
import me.ethanchen.network.packets.s2c.ProfileViewResponse;
import me.ethanchen.network.packets.s2c.RoomJoinResponse;
import me.ethanchen.network.packets.s2c.RoomListBroadcast;
import me.ethanchen.network.packets.s2c.StartGameBroadcast;

/**
 * Decorated room browser MVP: centered list, join-by-id, create room.
 */
public class RoomBrowserMenu extends DecoratedMenuScreen {
    private static final int ROOM_LIST_INTERVAL = 120;
    private static final int SLOT_COUNT = 6;
    private static final float LIST_CX = 960f;
    private static final float LIST_TOP_SLOT_Y = 880f;
    private static final float SLOT_W = 720f;
    private static final float SLOT_H = 72f;
    private static final float SLOT_GAP = 12f;
    private static final float SCROLL_BTN = 64f;
    private static final float SCROLL_GAP = 20f;
    private static final float SCROLL_THUMB_W = 22f;
    /** Gap between each scroll button's inner edge and the track (also clears the button outline). */
    private static final float TRACK_INSET = 12f;
    private static final float ICON_BTN = 96f;

    private int tickCount;
    private final DecoratedScrollableList roomList;
    private final DecoratedTextBox joinIdBox;
    private final DecoratedText statusText;
    private final Widget settingsWidget;
    private final ControllerConfigHub controllerHub;
    private PlayerProfileHub profileHub;
    private final AuroraBackgroundRenderer aurora;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = new PacketDispatcher<ClientPacketWrapper>()
            .on(RoomListBroadcast.class, w -> handleRoomList((RoomListBroadcast) w.packet))
            .on(RoomJoinResponse.class, w -> handleRoomJoinResponse((RoomJoinResponse) w.packet))
            .on(ProfileViewResponse.class, w -> handleProfileView((ProfileViewResponse) w.packet))
            .on(StartGameBroadcast.class, w -> app.switchMenu(new GameScreen(app, (StartGameBroadcast) w.packet, false)));

    public RoomBrowserMenu(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        tickCount = ROOM_LIST_INTERVAL - 1;

        DecoratedText title = new DecoratedText(960f, 1000f, "Multiplayer", 3.4f);

        roomList = new DecoratedScrollableList(LIST_CX, LIST_TOP_SLOT_Y, SLOT_W, SLOT_H, SLOT_COUNT, SLOT_GAP)
                .onSelect(this::joinRoom);

        float listH = SLOT_COUNT * SLOT_H + (SLOT_COUNT - 1) * SLOT_GAP;
        float listTop = LIST_TOP_SLOT_Y + SLOT_H * 0.5f;
        float listBottom = listTop - listH;
        float scrollX = LIST_CX + SLOT_W * 0.5f + SCROLL_GAP + SCROLL_BTN * 0.5f;
        float upY = listTop - SCROLL_BTN * 0.5f;
        float downY = listBottom + SCROLL_BTN * 0.5f;
        float trackTop = upY - SCROLL_BTN * 0.5f - TRACK_INSET;
        float trackBottom = downY + SCROLL_BTN * 0.5f + TRACK_INSET;
        float trackH = trackTop - trackBottom;
        float trackY = (trackTop + trackBottom) * 0.5f;

        DecoratedButton upBtn = DecoratedButton.icon(scrollX, upY, SCROLL_BTN,
                MenuAssets.upArrowIcon(), () -> roomList.scrollBy(-2));
        DecoratedButton downBtn = DecoratedButton.icon(scrollX, downY, SCROLL_BTN,
                MenuAssets.upArrowIcon(), () -> roomList.scrollBy(2));
        downBtn.iconRotationDeg = 180f;
        DecoratedScrollbar scrollbar = new DecoratedScrollbar(scrollX, trackY, SCROLL_THUMB_W, trackH, roomList);

        DecoratedText joinLabel = new DecoratedText(960f, 360f, "Join by Room ID", 1.35f);
        joinIdBox = new DecoratedTextBox(960f, 280f, 480f, 84f);
        joinIdBox.onEnter(this::joinByTypedId);

        DecoratedButton createBtn = new DecoratedButton(960f, 170f, 360f, 80f, "Create Room", this::createRoom);
        createBtn.fill(0.95f, 0.32f, 0.68f);
        createBtn.fontSize = 1.45f;
        createBtn.info("Host a new room.");

        statusText = new DecoratedText(960f, 90f, "Fetching rooms...", 1.2f);

        DecoratedButton controllerBtn = new DecoratedButton(220f, 740f, 320f, 80f, "Controller",
                this::openControllerWidget);
        controllerBtn.fontSize = 1.4f;
        controllerBtn.info("Keyboard and controller input.");

        DecoratedButton loadoutBtn = new DecoratedButton(220f, 640f, 320f, 80f, "Character Loadout",
                this::openCharacterLoadout);
        loadoutBtn.fontSize = 1.25f;
        loadoutBtn.info("Choose a character and artifacts.");

        DecoratedButton backBtn = new DecoratedButton(200f, 80f, 200f, 68f, "Back", this::leaveToMain);
        backBtn.fontSize = 1.4f;

        DecoratedButton settingsBtn = DecoratedButton.icon(1712f, 80f, ICON_BTN, MenuAssets.settingsIcon(),
                this::openSettingsWidget);
        DecoratedButton helpBtn = DecoratedButton.icon(1832f, 80f, ICON_BTN, MenuAssets.wikiIcon(),
                () -> Gdx.net.openURI(AppLinks.WIKI_URL));
        settingsBtn.fill(0.95f, 0.70f, 0.22f);
        helpBtn.fill(0.35f, 0.78f, 1.00f);
        settingsBtn.info("Settings");
        helpBtn.info("Wiki");

        String username = app.getSettings().lastUsername;
        if (username == null || username.isEmpty()) username = "Player";
        DecoratedAccountButton accountBtn = new DecoratedAccountButton(1720f, 1000f, 360f, 72f,
                username, this::openProfileWidget);
        accountBtn.info("Profile");

        // Add order is the focus ring order (nested list slots follow their list), so keep this
        // reading top-to-bottom: room list and its scroll controls, account, left column,
        // join / create, bottom bar.
        addDecorated(title);
        addDecorated(roomList);
        addDecorated(upBtn);
        addDecorated(scrollbar);
        addDecorated(downBtn);
        addDecorated(accountBtn);
        addDecorated(controllerBtn);
        addDecorated(loadoutBtn);
        addDecorated(joinLabel);
        addDecorated(joinIdBox);
        addDecorated(createBtn);
        addDecorated(statusText);
        addDecorated(backBtn);
        addDecorated(settingsBtn);
        addDecorated(helpBtn);

        settingsWidget = SettingsHub.createWidget(app, this, () -> new RoomBrowserMenu(app));
        controllerHub = ControllerConfigHub.create(app, this, null);
        aurora = new AuroraBackgroundRenderer();
    }

    private void openSettingsWidget() {
        if (hasOpenWidget()) return;
        openWidget(settingsWidget);
    }

    private void openControllerWidget() {
        if (hasOpenWidget()) return;
        openWidget(controllerHub.widget);
    }

    private void openCharacterLoadout() {
        // Switching away disposes this screen (and its aurora), so hand the loadout screen a
        // fresh browser to return to rather than this soon-to-be-dead instance.
        app.switchMenu(new CharacterScreen(app, new RoomBrowserMenu(app), () -> true));
    }

    private void openProfileWidget() {
        if (hasOpenWidget()) return;
        String uuid = app.getAccountUuid();
        if (uuid == null || uuid.isEmpty()) return;
        profileHub = PlayerProfileHub.create(app, this, uuid);
        openWidget(profileHub.widget);
    }

    private void handleProfileView(ProfileViewResponse res) {
        if (profileHub != null) profileHub.apply(res);
    }

    private void joinByTypedId() {
        String roomId = joinIdBox.get().trim();
        if (roomId.isEmpty()) {
            setStatus("Enter a room ID.");
            return;
        }
        joinRoomId(roomId);
    }

    private void joinRoom(RoomInfo room) {
        if (room == null || room.roomId == null || room.roomId.isEmpty()) return;
        joinRoomId(room.roomId);
    }

    private void joinRoomId(String roomId) {
        setStatus("Joining...");
        app.sendJoinRoomRequest(roomId);
    }

    private void createRoom() {
        setStatus("Creating room...");
        app.sendCreateRoomRequest();
    }

    private void setStatus(String message) {
        statusText.text = message != null ? message : "";
    }

    private void leaveToMain() {
        app.sendLeaveRoomRequest();
        app.disconnect();
        app.switchMenu(new MainMenu(app));
    }

    @Override
    protected void onEscPressed() {
        leaveToMain();
    }

    @Override
    protected void updateScreen(long menuElapsedMs, long appElapsedMs) {
        tickCount++;
        if (tickCount % ROOM_LIST_INTERVAL == 0) {
            app.sendRoomListRequest();
        }
        if (hasOpenWidget()) {
            controllerHub.tick();
        }
    }

    @Override
    protected void renderBackground(DecorContext ctx) {
        aurora.draw(ctx.appElapsedMs / 1000f, 1f);
    }

    @Override
    public void dispose() {
        aurora.dispose();
        super.dispose();
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        dispatcher.dispatch(w);
    }

    private void handleRoomList(RoomListBroadcast p) {
        if (p.rooms == null || p.rooms.length == 0) {
            roomList.setItems(List.of());
            setStatus("No rooms available.");
            return;
        }
        List<RoomInfo> rooms = new ArrayList<>(Arrays.asList(p.rooms));
        roomList.setItems(rooms);
        if ("Fetching rooms...".equals(statusText.text) || "No rooms available.".equals(statusText.text)) {
            setStatus("");
        }
    }

    private void handleRoomJoinResponse(RoomJoinResponse res) {
        if (res.success) {
            if (res.gameInProgress) {
                setStatus(res.spectatorOnly ? "Joining as spectator..." : "Joining...");
            }
            app.switchMenu(new MultiplayerLobby(app, res.isHost, res.gameInProgress));
        } else {
            setStatus(res.reason != null && !res.reason.isEmpty() ? res.reason : "Could not join room.");
        }
    }
}
