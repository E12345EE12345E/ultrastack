package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayDeque;

import me.ethanchen.game.GameMode;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.*;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.packets.c2s.StartGameRequest;
import me.ethanchen.network.packets.c2s.TextMessageRequest;
import me.ethanchen.network.packets.s2c.LobbyPlayerListBroadcast;
import me.ethanchen.network.packets.s2c.StartGameBroadcast;
import me.ethanchen.network.packets.s2c.TextMessageBroadcast;
import me.ethanchen.util.TextSanitizer;

public class MultiplayerLobby extends MenuScreen {
    private static final int MAX_CHAT_LINES = 10;

    private TextBoxOutput chatoutput;
    private TextInput chat;
    private TextInput playerNameList;
    private ArrayDeque<String> chatLines;

    public MultiplayerLobby(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        chatLines = new ArrayDeque<String>();

        elements.add(new UIText(0.5, 0.8, "Lobby", 4));

        chatoutput = new TextBoxOutput();
        chat = new TextInput();
        playerNameList = new TextInput();
        elements.add(new UIText(0.2, 0.35, chat, 2, UIText.TextAlign.BOTTOM_LEFT));
        elements.add(new UIText(0.75, 0.6, playerNameList, 1, UIText.TextAlign.TOP_LEFT));
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
        elements.add(new UIButton(0.5, 0.125, 0.3, 0.1, "Start Game", () -> {
            StartGameRequest p = new StartGameRequest();
            p.gamemode = GameMode.MULTIPLAYER_SCORE;
            app.sendTCP(p);
        }));
    }

    @Override
    public void update() {
        
    }

    @Override
    public void render() {
        elements.forEach(element -> {
            element.render(shapes, sprites, font);
        });
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        if (w.packet instanceof TextMessageBroadcast) {
            System.out.println(w.packet);
            TextMessageBroadcast p = (TextMessageBroadcast) w.packet;
            chatLines.add("[" + p.sender + "] " + p.message + " ");
            while (chatLines.size() > MAX_CHAT_LINES) {
                chatLines.removeFirst();
            }
            chat.set(String.join("\n", chatLines));
        }
        if (w.packet instanceof StartGameBroadcast) {
            app.switchMenu(new GameScreen(app, (StartGameBroadcast)w.packet));
        }
        if (w.packet instanceof LobbyPlayerListBroadcast) {
            LobbyPlayerListBroadcast p = (LobbyPlayerListBroadcast) w.packet;
            String playerListing = "";
            for (int i=0; i<p.playerNames.length; i++) {
                playerListing += "p" + (i+1) + ": " + p.playerNames[i] + "\n";
            }
            playerNameList.set(playerListing);
        }
    }
}
