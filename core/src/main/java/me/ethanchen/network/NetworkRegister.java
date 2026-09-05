package me.ethanchen.network;

import com.esotericsoftware.kryo.Kryo;

import me.ethanchen.game.GameMode;
import me.ethanchen.game.board.MoveType;
import me.ethanchen.game.board.PieceQueue;
import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.ArtifactEffect;
import me.ethanchen.game.progression.ArtifactEffectType;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.network.dto.HardDropEffect;
import me.ethanchen.network.dto.NetBoardFull;
import me.ethanchen.network.dto.NetBoardLight;
import me.ethanchen.network.dto.NetFallingColumn;
import me.ethanchen.network.dto.NetPiece;
import me.ethanchen.network.dto.NetQueue;
import me.ethanchen.network.dto.RoomInfo;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.*;
import me.ethanchen.network.packets.other.*;
import me.ethanchen.network.packets.s2c.*;
import me.ethanchen.network.packets.s2c.gamemode.CharacterModeData;
import me.ethanchen.network.packets.s2c.gamemode.PuzzleModeData;
import me.ethanchen.network.packets.s2c.gamemode.PuzzleModeEndData;
import me.ethanchen.game.pve.PveBoardDisplay;
import me.ethanchen.network.packets.s2c.gamemode.PveModeData;
import me.ethanchen.network.packets.s2c.gamemode.PveModeEndData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeEndData;

import java.util.ArrayList;

public class NetworkRegister {
    // RULES FOR UPDATING: bump this value when modifying the protocol. This is used to ensure that the client and server are using the same protocol version.
    public static final byte PROTOCOL_VERSION = 30;

    public static void registerClasses(Kryo kryo) {
        kryo.register(NetworkPacket.class);
        // Client to Server
        kryo.register(JoinRequest.class, 100); // constant ids so server can kick client out if protocol version is different without crashing client
        kryo.register(TextMessageRequest.class);
        kryo.register(StartGameRequest.class);
        kryo.register(LobbySettingsRequest.class);
        kryo.register(MoveListRequest.class);
        kryo.register(LocalPlayerCountRequest.class);
        // Server to Client
        kryo.register(JoinResponse.class, 200);
        kryo.register(TextMessageBroadcast.class);
        kryo.register(StartGameBroadcast.class);
        kryo.register(LightGameStateBroadcast.class);
        kryo.register(ParticleBroadcast.class);
        kryo.register(HardDropEffectsBroadcast.class);
        kryo.register(HoldSoundBroadcast.class);
        kryo.register(BumpSoundBroadcast.class);
        kryo.register(PieceSwapBroadcast.class);
        kryo.register(AbilityActivateBroadcast.class);
        kryo.register(LobbyPlayerListBroadcast.class);
        kryo.register(LobbySettingsBroadcast.class);
        // New c2s auth (fixed IDs)
        kryo.register(LoginRequest.class, 101);
        kryo.register(RegisterRequest.class, 102);
        // New c2s room
        kryo.register(RoomListRequest.class);
        kryo.register(CreateRoomRequest.class);
        kryo.register(JoinRoomRequest.class);
        kryo.register(LeaveRoomRequest.class);
        // New s2c auth
        kryo.register(AuthResponse.class, 201);
        // New s2c room
        kryo.register(RoomListBroadcast.class);
        kryo.register(RoomJoinResponse.class);
        // Other Packets
        kryo.register(DisconnectPacket.class);
        kryo.register(ConnectionEstablishedPacket.class);
        kryo.register(ConnectFailedPacket.class);
        // Gamemode
        kryo.register(ScoreModeData.class);
        kryo.register(ScoreModeEndData.class);
        kryo.register(PuzzleModeData.class);
        kryo.register(PuzzleModeEndData.class);
        kryo.register(PveModeData.class);
        kryo.register(PveModeEndData.class);
        kryo.register(PveBoardDisplay.class);
        // End game
        kryo.register(EndGameBroadcast.class);
        // Other Objects
        kryo.register(byte[].class);
        kryo.register(boolean[].class);
        kryo.register(int[].class);
        kryo.register(long[].class);
        kryo.register(float[].class);
        kryo.register(String[].class);
        kryo.register(GameMode.class);
        kryo.register(MoveType.class);
        kryo.register(NetBoardFull.class);
        kryo.register(NetBoardLight.class);
        kryo.register(NetBoardFull[].class);
        kryo.register(NetBoardLight[].class);
        kryo.register(NetPiece.class);
        kryo.register(NetQueue.class);
        kryo.register(NetPiece[].class);
        kryo.register(NetQueue[].class);
        kryo.register(NetFallingColumn.class);
        kryo.register(NetFallingColumn[].class);
        kryo.register(PieceQueue.BagTypes.class);
        kryo.register(NetParticle.class);
        kryo.register(NetParticle[].class);
        kryo.register(ParticleSpawner.class);
        kryo.register(ParticleSpawner[].class);
        kryo.register(HardDropEffect.class);
        kryo.register(HardDropEffect[].class);
        // Room lifecycle
        kryo.register(RoomClosedBroadcast.class);
        kryo.register(HostChangedBroadcast.class);
        kryo.register(RoomInfo.class);
        kryo.register(RoomInfo[].class);
        // Character and leveling system (implementation.md)
        kryo.register(ArrayList.class);
        kryo.register(ArtifactEffectType.class);
        kryo.register(ArtifactEffect.class);
        kryo.register(ArtifactEffect[].class);
        kryo.register(Artifact.class);
        kryo.register(Artifact[].class);
        kryo.register(PlayerProfile.class);
        kryo.register(ProfileSyncBroadcast.class);
        kryo.register(LoadoutRequest.class);
        kryo.register(ArtifactGrantBroadcast.class);
        kryo.register(FusionRequest.class);
        kryo.register(FusionResultBroadcast.class);
        kryo.register(AbilityRequest.class);
        kryo.register(CharacterModeData.class);
        kryo.register(SpectateRequest.class);
    }
}