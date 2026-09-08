package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.game.progression.BestGameRecord;
import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.game.progression.CharacterRegistry;
import me.ethanchen.game.progression.Levels;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedIconSlot;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedLevelBadge;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedStatBox;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedText;
import me.ethanchen.lwjgl3.menuscreens.decorated.Widget;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.lwjgl3.render.CharacterAssets;
import me.ethanchen.network.packets.s2c.ProfileViewResponse;

/**
 * Modal public-profile overlay. Always requests {@code accountUuid} from the server; children
 * bind the matching {@link ProfileViewResponse} and do not cache other players.
 */
public final class PlayerProfileHub {
    public final Widget widget;
    public final String boundUuid;

    private final DecoratedText username;
    private final DecoratedLevelBadge levelBadge;
    private final DecoratedStatBox scoreBox;
    private final DecoratedStatBox puzzleBox;
    private final DecoratedStatBox characterScoreBox;
    private final DecoratedIconSlot portrait;
    private final DecoratedIconSlot artifactA;
    private final DecoratedIconSlot artifactB;

    private PlayerProfileHub(Widget widget, String boundUuid,
                             DecoratedText username, DecoratedLevelBadge levelBadge,
                             DecoratedStatBox scoreBox, DecoratedStatBox puzzleBox,
                             DecoratedStatBox characterScoreBox,
                             DecoratedIconSlot portrait, DecoratedIconSlot artifactA,
                             DecoratedIconSlot artifactB) {
        this.widget = widget;
        this.boundUuid = boundUuid;
        this.username = username;
        this.levelBadge = levelBadge;
        this.scoreBox = scoreBox;
        this.puzzleBox = puzzleBox;
        this.characterScoreBox = characterScoreBox;
        this.portrait = portrait;
        this.artifactA = artifactA;
        this.artifactB = artifactB;
    }

    public static PlayerProfileHub create(ClientApp app, DecoratedMenuScreen host, String accountUuid) {
        Widget w = new Widget(960f, 520f, 1040f, 600f);

        DecoratedText username = new DecoratedText(0f, 0f, "...", 2.0f);
        username.align = UIText.TextAlign.CENTER_LEFT;
        username.bounds(520f, 80f);
        w.add(username, -220f, 200f);

        DecoratedLevelBadge badge = new DecoratedLevelBadge(0f, 0f, 80f, 80f);
        w.add(badge, 420f, 200f);

        DecoratedStatBox score = new DecoratedStatBox(0f, 0f, 300f, 170f, "Score");
        DecoratedStatBox puzzle = new DecoratedStatBox(0f, 0f, 300f, 170f, "Puzzle");
        DecoratedStatBox characterScore = new DecoratedStatBox(0f, 0f, 300f, 170f, "Character Score");
        w.add(score, -336f, 40f);
        w.add(puzzle, 0f, 40f);
        w.add(characterScore, 336f, 40f);

        DecoratedText loadoutLabel = new DecoratedText(0f, 0f, "Character Loadout", 1.55f);
        loadoutLabel.align = UIText.TextAlign.CENTER;
        loadoutLabel.bounds(360f, 80f);
        w.add(loadoutLabel, -280f, -130f);

        DecoratedIconSlot portrait = new DecoratedIconSlot(0f, 0f, 120f);
        DecoratedIconSlot artA = new DecoratedIconSlot(0f, 0f, 68f);
        DecoratedIconSlot artB = new DecoratedIconSlot(0f, 0f, 68f);
        w.add(portrait, 140f, -130f);
        w.add(artA, 280f, -130f);
        w.add(artB, 370f, -130f);

        DecoratedButton back = new DecoratedButton(0f, 0f, 300f, 68f, "Back", host::closeTopWidget);
        back.fontSize = 1.4f;
        w.add(back, 0f, -250f);

        app.sendProfileViewRequest(accountUuid);
        return new PlayerProfileHub(w, accountUuid, username, badge, score, puzzle, characterScore,
                portrait, artA, artB);
    }

    public void apply(ProfileViewResponse res) {
        if (res == null || boundUuid == null || !boundUuid.equals(res.accountUuid)) return;
        if (!res.found) {
            username.text = "Unknown";
            levelBadge.level = 1;
            levelBadge.info(null);
            scoreBox.value = "—";
            scoreBox.info(null);
            puzzleBox.value = "—";
            puzzleBox.info(null);
            characterScoreBox.value = "—";
            characterScoreBox.info(null);
            portrait.texture = null;
            portrait.info(null);
            artifactA.texture = null;
            artifactB.texture = null;
            return;
        }
        username.text = res.username != null && !res.username.isEmpty() ? res.username : "Player";
        int level = Levels.levelFromXp(res.xp);
        levelBadge.level = level;
        levelBadge.info("Level: " + level + "\nXP: " + res.xp);
        scoreBox.value = displayOf(res.bestScore);
        scoreBox.info(hoverOf(res.bestScore));
        puzzleBox.value = displayOf(res.bestPuzzle);
        puzzleBox.info(hoverOf(res.bestPuzzle));
        characterScoreBox.value = displayOf(res.bestCharacterScore);
        characterScoreBox.info(hoverOf(res.bestCharacterScore));

        CharacterDef def = CharacterRegistry.byId(res.selectedCharacterId);
        portrait.texture = def != null ? CharacterAssets.portraitFor(def.id) : null;
        portrait.info(def != null ? def.name : "No character");
        artifactA.texture = CharacterAssets.artifactIconFor(res.equippedA);
        artifactB.texture = CharacterAssets.artifactIconFor(res.equippedB);
    }

    private static String displayOf(BestGameRecord record) {
        if (record == null || record.displayScore == null || record.displayScore.isEmpty()) return "—";
        return record.displayScore;
    }

    private static String hoverOf(BestGameRecord record) {
        return record == null ? null : record.hoverText();
    }
}
