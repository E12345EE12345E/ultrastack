package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.game.GameMode;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.*;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.packets.s2c.RoomJoinResponse;
import me.ethanchen.network.packets.s2c.RoomListBroadcast;

public class RoomBrowserMenu extends MenuScreen {
    private static final int ROOM_LIST_INTERVAL = 120;

    private int tickCount;
    private TextInput roomListText;
    private TextInput messageText;

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
            app.sendCreateRoomRequest(GameMode.MULTIPLAYER_SCORE);
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
    public void render() {
        elements.forEach(element -> element.render(shapes, sprites, font));
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        if (w.packet instanceof RoomListBroadcast) {
            RoomListBroadcast p = (RoomListBroadcast) w.packet;
            if (p.roomIds == null || p.roomIds.length == 0) {
                roomListText.set("No rooms available.");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < p.roomIds.length; i++) {
                    if (p.inProgress != null && i < p.inProgress.length && p.inProgress[i]) {
                        sb.append(p.roomIds[i]).append(" | [IN PROGRESS]\n");
                    } else {
                        String host = (p.hostNames != null && i < p.hostNames.length) ? p.hostNames[i] : "?";
                        int count = (p.playerCounts != null && i < p.playerCounts.length) ? p.playerCounts[i] : 0;
                        sb.append(p.roomIds[i])
                          .append(" | host: ").append(host)
                          .append(" | players: ").append(count)
                          .append("\n");
                    }
                }
                roomListText.set(sb.toString().trim());
            }
        }
        if (w.packet instanceof RoomJoinResponse) {
            RoomJoinResponse res = (RoomJoinResponse) w.packet;
            if (res.success) {
                app.switchMenu(new MultiplayerLobby(app, res.isHost));
            } else {
                messageText.set(res.reason != null && !res.reason.isEmpty() ? res.reason : "Could not join room.");
            }
        }
    }
}
