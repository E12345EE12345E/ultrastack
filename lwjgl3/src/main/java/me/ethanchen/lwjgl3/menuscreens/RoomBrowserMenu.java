package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecorContext;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedScrollableList;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedText;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedTextBox;
import me.ethanchen.lwjgl3.render.shader.AuroraBackgroundRenderer;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.dto.RoomInfo;
import me.ethanchen.network.packets.s2c.RoomJoinResponse;
import me.ethanchen.network.packets.s2c.RoomListBroadcast;
import me.ethanchen.network.packets.s2c.StartGameBroadcast;

/**
 * Decorated room browser MVP: centered list, join-by-id, create room.
 */
public class RoomBrowserMenu extends DecoratedMenuScreen {
    private static final int ROOM_LIST_INTERVAL = 120;
    private static final int SLOT_COUNT = 6;

    private int tickCount;
    private final DecoratedScrollableList roomList;
    private final DecoratedTextBox joinIdBox;
    private final DecoratedText statusText;
    private final AuroraBackgroundRenderer aurora;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = new PacketDispatcher<ClientPacketWrapper>()
            .on(RoomListBroadcast.class, w -> handleRoomList((RoomListBroadcast) w.packet))
            .on(RoomJoinResponse.class, w -> handleRoomJoinResponse((RoomJoinResponse) w.packet))
            .on(StartGameBroadcast.class, w -> app.switchMenu(new GameScreen(app, (StartGameBroadcast) w.packet, false)));

    public RoomBrowserMenu(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        tickCount = ROOM_LIST_INTERVAL - 1;

        DecoratedText title = new DecoratedText(960f, 1000f, "Multiplayer", 3.4f);
        addDecorated(title);

        roomList = new DecoratedScrollableList(960f, 880f, 720f, 72f, SLOT_COUNT, 12f)
                .onSelect(this::joinRoom);
        addDecorated(roomList);

        DecoratedText joinLabel = new DecoratedText(850f, 360f, "Join by Room ID", 1.35f);
        joinIdBox = new DecoratedTextBox(850f, 280f, 480f, 84f);
        joinIdBox.onEnter(this::joinByTypedId);

        DecoratedButton upBtn = new DecoratedButton(1170f, 280f, 72f, 72f, "^", () -> roomList.scrollBy(-2));
        upBtn.fontSize = 1.6f;
        DecoratedButton downBtn = new DecoratedButton(1260f, 280f, 72f, 72f, "v", () -> roomList.scrollBy(2));
        downBtn.fontSize = 1.6f;

        DecoratedButton createBtn = new DecoratedButton(960f, 170f, 360f, 80f, "Create Room", this::createRoom);
        createBtn.fill(0.95f, 0.32f, 0.68f);
        createBtn.fontSize = 1.45f;
        createBtn.info("Host a new room.");

        statusText = new DecoratedText(960f, 90f, "Fetching rooms...", 1.2f);

        DecoratedButton backBtn = new DecoratedButton(200f, 80f, 200f, 68f, "Back", this::leaveToMain);
        backBtn.fontSize = 1.4f;

        addDecorated(joinLabel);
        addDecorated(joinIdBox);
        addDecorated(upBtn);
        addDecorated(downBtn);
        addDecorated(createBtn);
        addDecorated(statusText);
        addDecorated(backBtn);

        aurora = new AuroraBackgroundRenderer();
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
