package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.*;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.packets.s2c.RoomJoinResponse;
import me.ethanchen.network.packets.s2c.RoomListBroadcast;

public class RoomBrowserMenu extends MenuScreen {
    private static final int ROOM_LIST_INTERVAL = 120;

    private int tickCount;
    private TextInput roomListText;
    private TextInput messageText;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = new PacketDispatcher<ClientPacketWrapper>()
            .on(RoomListBroadcast.class, w -> handleRoomList((RoomListBroadcast) w.packet))
            .on(RoomJoinResponse.class, w -> handleRoomJoinResponse((RoomJoinResponse) w.packet));

    public RoomBrowserMenu(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        tickCount = ROOM_LIST_INTERVAL - 1; // fires on first tick

        roomListText = new TextInput();
        roomListText.set("Fetching rooms...");
        messageText = new TextInput();

        TextBoxOutput joinIdOutput = new TextBoxOutput();

        elements.add(new UIText(0.5, 0.88, "Room Browser", 4));

        elements.add(new UIText(0.5, 0.77, roomListText, 1, UIText.TextAlign.TOP_LEFT));

        elements.add(new UIText(0.5, 0.38, "Join by Room ID", 1));
        elements.add(new UITextBox(0.5, 0.31, 0.35, 0.08, joinIdOutput));

        elements.add(new UIText(0.5, 0.22, messageText, 1));

        elements.add(new UIButton(0.3, 0.125, 0.25, 0.1, "Join", () -> {
            String roomId = joinIdOutput.get().trim();
            if (roomId.isEmpty()) {
                messageText.set("Enter a room ID.");
                return;
            }
            app.sendJoinRoomRequest(roomId);
        }));

        elements.add(new UIButton(0.7, 0.125, 0.3, 0.1, "Create Room", () -> {
            app.sendCreateRoomRequest();
        }));

        elements.add(new UIButton(0.5, 0.04, 0.3, 0.07, "Disconnect", () -> {
            app.sendLeaveRoomRequest();
            app.disconnect();
            app.switchMenu(new MainMenu(app));
        }));
    }

    @Override
    protected void onEscPressed() {
        app.sendLeaveRoomRequest();
        app.disconnect();
        app.switchMenu(new MainMenu(app));
    }

    @Override
    public void update() {
        tickCount++;
        if (tickCount % ROOM_LIST_INTERVAL == 0) {
            app.sendRoomListRequest();
        }
    }


    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        dispatcher.dispatch(w);
    }

    private void handleRoomList(RoomListBroadcast p) {
        if (p.rooms == null || p.rooms.length == 0) {
            roomListText.set("No rooms available.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (me.ethanchen.network.dto.RoomInfo r : p.rooms) {
            if (r.inProgress) {
                sb.append(r.roomId).append(" | [IN PROGRESS]\n");
            } else {
                sb.append(r.roomId)
                  .append(" | host: ").append(r.hostName != null ? r.hostName : "?")
                  .append(" | players: ").append(r.playerCount)
                  .append("\n");
            }
        }
        roomListText.set(sb.toString().trim());
    }

    private void handleRoomJoinResponse(RoomJoinResponse res) {
        if (res.success) {
            app.switchMenu(new MultiplayerLobby(app, res.isHost));
        } else {
            messageText.set(res.reason != null && !res.reason.isEmpty() ? res.reason : "Could not join room.");
        }
    }
}
