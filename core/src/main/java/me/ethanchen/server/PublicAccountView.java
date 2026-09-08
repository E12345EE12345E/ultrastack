package me.ethanchen.server;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.BestGameRecord;

/** Server-side public profile loaded by account uuid for {@code ProfileViewResponse}. */
public class PublicAccountView {
    public String accountUuid;
    public String username;
    public long xp;
    public int selectedCharacterId;
    public Artifact equippedA;
    public Artifact equippedB;
    public BestGameRecord bestScore;
    public BestGameRecord bestPuzzle;
    public BestGameRecord bestCharacterScore;
}
