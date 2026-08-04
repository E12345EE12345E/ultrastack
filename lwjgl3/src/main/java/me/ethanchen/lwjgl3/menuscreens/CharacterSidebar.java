package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.function.Supplier;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.game.progression.CharacterRegistry;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.render.CharacterAssets;
import me.ethanchen.lwjgl3.menuscreens.ui.UIElement;
import me.ethanchen.lwjgl3.menuscreens.ui.UIIconButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;

/**
 * Left-side sidebar showing the local player's currently selected character and two equipped
 * artifacts; clicking any part opens {@link CharacterScreen} (implementation.md, Part 5). Shared
 * by the room browser and both lobby screens.
 *
 * <p>{@code charactersEnabled} reports whether the current room's gamemode supports characters;
 * when false, the whole sidebar renders desaturated (still clickable). Non-host clients can't
 * currently see the host's pending gamemode choice before a game starts, so this only reflects
 * the host's local {@code LobbySettings} -- see {@link ClientApp#getLobbySettings()}.
 */
public class CharacterSidebar {
    private final ClientApp app;
    private final Supplier<Boolean> charactersEnabled;
    private final UIIconButton portrait;
    private final UIIconButton artifactA;
    private final UIIconButton artifactB;
    private final UIText nameText;

    public CharacterSidebar(ClientApp app, ArrayList<UIElement> elements, MenuScreen returnScreen,
                             Supplier<Boolean> charactersEnabled) {
        this.app = app;
        this.charactersEnabled = charactersEnabled;

        double x = 0.08;
        Runnable open = () -> app.switchMenu(new CharacterScreen(app, returnScreen, charactersEnabled));

        portrait = new UIIconButton(x, 0.78, 0.11, null, open);
        artifactA = new UIIconButton(x - 0.045, 0.65, 0.06, null, open);
        artifactB = new UIIconButton(x + 0.045, 0.65, 0.06, null, open);
        nameText = new UIText(x, 0.71, "", 0.75);

        elements.add(portrait);
        elements.add(nameText);
        elements.add(artifactA);
        elements.add(artifactB);

        refresh();
    }

    /** Call once per frame; cheap enough (a handful of field writes) to not bother diffing. */
    public void tick() {
        refresh();
    }

    private void refresh() {
        PlayerProfile profile = app.getProfile();
        boolean enabled = Boolean.TRUE.equals(charactersEnabled.get());

        CharacterDef character = profile != null ? CharacterRegistry.byId(profile.selectedCharacterId) : null;
        portrait.icon = character != null ? CharacterAssets.portraitFor(character.id) : null;
        portrait.grayscale = !enabled;
        nameText.textin.set(character != null ? character.name : "No character");

        Artifact a = profile != null ? profile.findArtifact(profile.equippedArtifactIds[0]) : null;
        Artifact b = profile != null ? profile.findArtifact(profile.equippedArtifactIds[1]) : null;
        artifactA.icon = CharacterAssets.artifactIconFor(a);
        artifactA.grayscale = !enabled;
        artifactA.placeholderText = a == null ? "-" : null;
        artifactB.icon = CharacterAssets.artifactIconFor(b);
        artifactB.grayscale = !enabled;
        artifactB.placeholderText = b == null ? "-" : null;
    }
}
