package me.ethanchen.testclient;

import java.util.Arrays;

import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.network.dto.RoomInfo;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.CreateRoomRequest;
import me.ethanchen.network.packets.c2s.JoinRequest;
import me.ethanchen.network.packets.c2s.JoinRoomRequest;
import me.ethanchen.network.packets.c2s.LeaveRoomRequest;
import me.ethanchen.network.packets.c2s.LoadoutRequest;
import me.ethanchen.network.packets.c2s.LocalPlayerCountRequest;
import me.ethanchen.network.packets.c2s.LoginRequest;
import me.ethanchen.network.packets.c2s.ProfileViewRequest;
import me.ethanchen.network.packets.c2s.RegisterRequest;
import me.ethanchen.network.packets.c2s.StartGameRequest;
import me.ethanchen.network.packets.s2c.AbilityActivateBroadcast;
import me.ethanchen.network.packets.s2c.ArtifactGrantBroadcast;
import me.ethanchen.network.packets.s2c.AuthResponse;
import me.ethanchen.network.packets.s2c.BumpSoundBroadcast;
import me.ethanchen.network.packets.s2c.EndGameBroadcast;
import me.ethanchen.network.packets.s2c.FusionResultBroadcast;
import me.ethanchen.network.packets.s2c.HardDropEffectsBroadcast;
import me.ethanchen.network.packets.s2c.HoldSoundBroadcast;
import me.ethanchen.network.packets.s2c.HostChangedBroadcast;
import me.ethanchen.network.packets.s2c.JoinResponse;
import me.ethanchen.network.packets.s2c.LightGameStateBroadcast;
import me.ethanchen.network.packets.s2c.LobbyPlayerListBroadcast;
import me.ethanchen.network.packets.s2c.LobbySettingsBroadcast;
import me.ethanchen.network.packets.s2c.ParticleBroadcast;
import me.ethanchen.network.packets.s2c.PieceSwapBroadcast;
import me.ethanchen.network.packets.s2c.ProfileSyncBroadcast;
import me.ethanchen.network.packets.s2c.ProfileViewResponse;
import me.ethanchen.network.packets.s2c.RoomClosedBroadcast;
import me.ethanchen.network.packets.s2c.RoomJoinResponse;
import me.ethanchen.network.packets.s2c.RoomListBroadcast;
import me.ethanchen.network.packets.s2c.StartGameBroadcast;
import me.ethanchen.network.packets.s2c.TextMessageBroadcast;

final class PacketSummarizer {
    private PacketSummarizer() {}

    static boolean isHighFrequency(Object packet) {
        return packet instanceof LightGameStateBroadcast
                || packet instanceof ParticleBroadcast
                || packet instanceof HardDropEffectsBroadcast
                || packet instanceof HoldSoundBroadcast
                || packet instanceof BumpSoundBroadcast
                || packet instanceof PieceSwapBroadcast
                || packet instanceof AbilityActivateBroadcast;
    }

    static String summarize(Object packet) {
        if (packet == null) return "null";
        String name = packet.getClass().getSimpleName();
        if (!(packet instanceof NetworkPacket)) {
            return name;
        }
        if (packet instanceof RegisterRequest) {
            RegisterRequest p = (RegisterRequest) packet;
            return name + " username=" + p.username + " passcode=" + p.passcode
                    + " protocolVersion=" + p.protocolVersion;
        }
        if (packet instanceof LoginRequest) {
            LoginRequest p = (LoginRequest) packet;
            return name + " username=" + p.username + " passcode=" + p.passcode
                    + " protocolVersion=" + p.protocolVersion;
        }
        if (packet instanceof CreateRoomRequest) {
            CreateRoomRequest p = (CreateRoomRequest) packet;
            return name + " localPlayers=" + (p.localPlayers & 0xFF);
        }
        if (packet instanceof JoinRoomRequest) {
            JoinRoomRequest p = (JoinRoomRequest) packet;
            return name + " roomId=" + p.roomId + " localPlayers=" + (p.localPlayers & 0xFF);
        }
        if (packet instanceof LeaveRoomRequest) {
            return name;
        }
        if (packet instanceof StartGameRequest) {
            StartGameRequest p = (StartGameRequest) packet;
            return name + " gamemode=" + p.gamemode;
        }
        if (packet instanceof JoinRequest) {
            JoinRequest p = (JoinRequest) packet;
            return name + " playerName=" + p.playerName + " localPlayers=" + (p.localPlayers & 0xFF)
                    + " protocolVersion=" + p.protocolVersion;
        }
        if (packet instanceof LocalPlayerCountRequest) {
            LocalPlayerCountRequest p = (LocalPlayerCountRequest) packet;
            return name + " count=" + (p.count & 0xFF);
        }
        if (packet instanceof LoadoutRequest) {
            LoadoutRequest p = (LoadoutRequest) packet;
            return name + " characterId=" + p.characterId
                    + " artifactIdA=" + p.artifactIdA + " artifactIdB=" + p.artifactIdB;
        }
        if (packet instanceof AuthResponse) {
            AuthResponse p = (AuthResponse) packet;
            return name + " success=" + p.success + " reason=" + quote(p.reason)
                    + " accountUuid=" + p.accountUuid;
        }
        if (packet instanceof ProfileSyncBroadcast) {
            ProfileSyncBroadcast p = (ProfileSyncBroadcast) packet;
            return name + " readOnly=" + p.readOnly + " " + profileSummary(p.profile);
        }
        if (packet instanceof ProfileViewRequest) {
            return name + " accountUuid=" + ((ProfileViewRequest) packet).accountUuid;
        }
        if (packet instanceof ProfileViewResponse) {
            ProfileViewResponse p = (ProfileViewResponse) packet;
            String score = p.bestScore == null ? "null" : p.bestScore.displayScore;
            return name + " found=" + p.found + " accountUuid=" + p.accountUuid
                    + " username=" + p.username + " xp=" + p.xp + " bestScore=" + score;
        }
        if (packet instanceof RoomJoinResponse) {
            RoomJoinResponse p = (RoomJoinResponse) packet;
            return name + " success=" + p.success + " reason=" + quote(p.reason)
                    + " roomId=" + p.roomId + " isHost=" + p.isHost
                    + " gameInProgress=" + p.gameInProgress + " spectatorOnly=" + p.spectatorOnly;
        }
        if (packet instanceof StartGameBroadcast) {
            StartGameBroadcast p = (StartGameBroadcast) packet;
            return name + " mode=" + p.mode
                    + " totalPlayers=" + (p.totalPlayers & 0xFF)
                    + " boards=" + len(p.boards)
                    + " localPlayerIds=" + Arrays.toString(p.localPlayerIds)
                    + " slotBoardIndex=" + Arrays.toString(p.slotBoardIndex)
                    + " slotSeatIndex=" + Arrays.toString(p.slotSeatIndex)
                    + " msUntilStart=" + p.msUntilStart
                    + " playerNames=" + Arrays.toString(p.playerNames)
                    + " spectatorJoin=" + p.spectatorJoin;
        }
        if (packet instanceof LobbyPlayerListBroadcast) {
            LobbyPlayerListBroadcast p = (LobbyPlayerListBroadcast) packet;
            return name + " playerNames=" + Arrays.toString(p.playerNames)
                    + " spectatorNames=" + Arrays.toString(p.spectatorNames);
        }
        if (packet instanceof LobbySettingsBroadcast) {
            LobbySettingsBroadcast p = (LobbySettingsBroadcast) packet;
            return name + " gamemode=" + p.gamemode + " pveLevelId=" + p.pveLevelId
                    + " pveDifficulty=" + p.pveDifficulty;
        }
        if (packet instanceof RoomListBroadcast) {
            return name + " " + roomListSummary((RoomListBroadcast) packet);
        }
        if (packet instanceof EndGameBroadcast) {
            EndGameBroadcast p = (EndGameBroadcast) packet;
            String score = p.scoreModeEnd == null ? "null"
                    : "finalScore=" + p.scoreModeEnd.finalScore
                    + " timeSurvivedMs=" + p.scoreModeEnd.timeSurvivedMs
                    + " boardScore=" + Arrays.toString(p.scoreModeEnd.boardScore);
            return name + " mode=" + p.mode + " win=" + p.win + " disconnected=" + p.disconnected
                    + " playerNames=" + Arrays.toString(p.playerNames) + " scoreModeEnd={" + score + "}";
        }
        if (packet instanceof LightGameStateBroadcast) {
            LightGameStateBroadcast p = (LightGameStateBroadcast) packet;
            long score = p.scoreMode == null ? -1L : p.scoreMode.totalScore;
            return name + " boards=" + len(p.boards)
                    + " ackMoveIds=" + Arrays.toString(p.ackMoveIds)
                    + " gravity=" + p.gravity
                    + " gameEnded=" + p.gameEnded
                    + " totalScore=" + score;
        }
        if (packet instanceof ParticleBroadcast) {
            ParticleBroadcast p = (ParticleBroadcast) packet;
            return name + " particles=" + len(p.particles) + " spawners=" + len(p.spawners);
        }
        if (packet instanceof HardDropEffectsBroadcast) {
            HardDropEffectsBroadcast p = (HardDropEffectsBroadcast) packet;
            return name + " effects=" + len(p.effects);
        }
        if (packet instanceof HoldSoundBroadcast) {
            HoldSoundBroadcast p = (HoldSoundBroadcast) packet;
            return name + " playerId=" + p.playerId + " boardIndex=" + p.boardIndex
                    + " success=" + p.success;
        }
        if (packet instanceof BumpSoundBroadcast) {
            BumpSoundBroadcast p = (BumpSoundBroadcast) packet;
            return name + " playerId=" + p.playerId + " otherPlayerId=" + p.otherPlayerId
                    + " blocked=" + p.blocked;
        }
        if (packet instanceof PieceSwapBroadcast) {
            PieceSwapBroadcast p = (PieceSwapBroadcast) packet;
            return name + " playerId=" + p.playerId + " pieceType=" + p.pieceType
                    + " boardIndex=" + p.boardIndex;
        }
        if (packet instanceof AbilityActivateBroadcast) {
            AbilityActivateBroadcast p = (AbilityActivateBroadcast) packet;
            return name + " playerId=" + p.playerId + " boardIndex=" + p.boardIndex;
        }
        if (packet instanceof HostChangedBroadcast) {
            HostChangedBroadcast p = (HostChangedBroadcast) packet;
            return name + " youAreHost=" + p.youAreHost + " hostName=" + p.hostName;
        }
        if (packet instanceof RoomClosedBroadcast) {
            return name + " reason=" + quote(((RoomClosedBroadcast) packet).reason);
        }
        if (packet instanceof TextMessageBroadcast) {
            TextMessageBroadcast p = (TextMessageBroadcast) packet;
            return name + " sender=" + p.sender + " message=" + quote(p.message);
        }
        if (packet instanceof JoinResponse) {
            JoinResponse p = (JoinResponse) packet;
            return name + " accepted=" + p.accepted + " playerId=" + p.playerId
                    + " reason=" + quote(p.reason) + " gameInProgress=" + p.gameInProgress
                    + " spectatorOnly=" + p.spectatorOnly;
        }
        if (packet instanceof ArtifactGrantBroadcast) {
            ArtifactGrantBroadcast p = (ArtifactGrantBroadcast) packet;
            String id = p.artifact == null ? "null" : p.artifact.id;
            return name + " artifactId=" + id;
        }
        if (packet instanceof FusionResultBroadcast) {
            FusionResultBroadcast p = (FusionResultBroadcast) packet;
            String id = p.result == null ? "null" : p.result.id;
            return name + " success=" + p.success + " reason=" + quote(p.reason) + " result=" + id;
        }
        return name;
    }

    private static String profileSummary(PlayerProfile profile) {
        if (profile == null) return "profile=null";
        int inv = profile.inventory == null ? 0 : profile.inventory.size();
        return "selectedCharacterId=" + profile.selectedCharacterId
                + " tokens=" + profile.tokens
                + " inventorySize=" + inv
                + " equipped=" + Arrays.toString(profile.equippedArtifactIds)
                + " pveUnlockedLevels=" + profile.pveUnlockedLevels;
    }

    private static String roomListSummary(RoomListBroadcast p) {
        if (p.rooms == null) return "rooms=null";
        StringBuilder sb = new StringBuilder("rooms=").append(p.rooms.length).append(" [");
        for (int i = 0; i < p.rooms.length; i++) {
            if (i > 0) sb.append(", ");
            RoomInfo r = p.rooms[i];
            if (r == null) {
                sb.append("null");
                continue;
            }
            sb.append("{id=").append(r.roomId)
                    .append(" host=").append(r.hostName)
                    .append(" players=").append(r.playerCount)
                    .append(" spectators=").append(r.spectatorCount)
                    .append(" inProgress=").append(r.inProgress)
                    .append(" gamemode=").append(r.gamemode)
                    .append("}");
        }
        return sb.append(']').toString();
    }

    private static int len(Object[] arr) {
        return arr == null ? 0 : arr.length;
    }

    private static String quote(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }
}
