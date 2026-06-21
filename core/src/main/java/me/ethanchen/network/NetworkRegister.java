package me.ethanchen.network;

import com.esotericsoftware.kryo.Kryo;

import me.ethanchen.game.GameMode;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.MoveType;
import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.PieceQueue;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.*;
import me.ethanchen.network.packets.other.*;
import me.ethanchen.network.packets.s2c.*;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeEndData;

public class NetworkRegister {
    public static final byte PROTOCOL_VERSION = 3;

    public static void registerClasses(Kryo kryo) {
        kryo.register(NetworkPacket.class);
        // Client to Server
        kryo.register(JoinRequest.class, 100); // constant ids so server can kick client out if protocol version is different without crashing client
        kryo.register(TextMessageRequest.class);
        kryo.register(StartGameRequest.class);
        kryo.register(MoveListRequest.class);
        // Server to Client
        kryo.register(JoinResponse.class, 200);
        kryo.register(TextMessageBroadcast.class);
        kryo.register(StartGameBroadcast.class);
        kryo.register(LightGameStateBroadcast.class);
        kryo.register(ParticleBroadcast.class);
        kryo.register(LobbyPlayerListBroadcast.class);
        kryo.register(EndGameBroadcast.class);
        // Other Packets
        kryo.register(DisconnectPacket.class);
        // Gamemode
        kryo.register(ScoreModeData.class);
        kryo.register(ScoreModeEndData.class);
        // Other Objects
        kryo.register(byte[].class);
        kryo.register(boolean[].class);
        kryo.register(int[].class);
        kryo.register(float[].class);
        kryo.register(String[].class);
        kryo.register(GameMode.class);
        kryo.register(MoveType.class);
        kryo.register(Board.NetBoardFull.class);
        kryo.register(Board.NetBoardLight.class);
        kryo.register(Board.NetBoardFull[].class);
        kryo.register(Board.NetBoardLight[].class);
        kryo.register(Piece.NetPiece.class);
        kryo.register(PieceQueue.NetQueue.class);
        kryo.register(Piece.NetPiece[].class);
        kryo.register(PieceQueue.NetQueue[].class);
        kryo.register(PieceQueue.BagTypes.class);
        kryo.register(float[].class);
        kryo.register(NetParticle.class);
        kryo.register(NetParticle[].class);
    }
}