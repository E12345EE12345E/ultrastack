package me.ethanchen.network.packets.s2c;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.BestGameRecord;
import me.ethanchen.network.packets.NetworkPacket;

/** Public profile for {@link #accountUuid}. {@link #found} is false when the account is unknown. */
public class ProfileViewResponse extends NetworkPacket {
    public String accountUuid;
    public boolean found;
    public String username;
    public long xp;
    public int selectedCharacterId;
    public Artifact equippedA;
    public Artifact equippedB;
    public BestGameRecord bestScore;
    public BestGameRecord bestPuzzle;
    public BestGameRecord bestCharacterScore;
}
