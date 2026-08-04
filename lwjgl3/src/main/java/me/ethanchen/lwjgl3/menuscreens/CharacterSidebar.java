package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.function.Supplier;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.game.progression.CharacterRegistry;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.render.CharacterAssets;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIElement;
import me.ethanchen.lwjgl3.menuscreens.ui.UIImage;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;

/**
 * Left-side sidebar showing the local player's loadout as plain images, with a
 * {@code Character Loadout} button underneath that opens {@link CharacterScreen}
 * (implementation.md, Part 5). Shared by the room browser and both lobby screens.
 *
 * <p>{@code charactersEnabled} reports whether the current room's gamemode supports characters;
 * when false, the images render desaturated. Non-host clients can't currently see the host's
 * pending gamemode choice before a game starts, so this only reflects the host's local
 * {@code LobbySettings} — see {@link ClientApp#getLobbySettings()}.
 */
public class CharacterSidebar {
    private final ClientApp app;
    private final Supplier<Boolean> charactersEnabled;
    private final UIImage portrait;
    private final UIImage artifactA;
    private final UIImage artifactB;
    private final UIText nameText;

    public CharacterSidebar(ClientApp app, ArrayList<UIElement> elements, MenuScreen returnScreen,
                             Supplier<Boolean> charactersEnabled) {
        this.app = app;
        this.charactersEnabled = charactersEnabled;

        double x = 0.14;
        Runnable open = () -> app.switchMenu(new CharacterScreen(app, returnScreen, charactersEnabled));

        // Square images scale height from width, so leave extra vertical gap on wide windows.
        portrait = new UIImage(x, 0.70, 0.11);
        nameText = new UIText(x, 0.595, "", 0.75);
        artifactA = new UIImage(x - 0.06, 0.48, 0.055);
        artifactB = new UIImage(x + 0.06, 0.48, 0.055);

        elements.add(portrait);
        elements.add(nameText);
        elements.add(artifactA);
        elements.add(artifactB);
        elements.add(new UIButton(x, 0.375, 0.20, 0.07, "Character Loadout", open, 0.7f));

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
        portrait.texture = character != null ? CharacterAssets.portraitFor(character.id) : null;
        portrait.grayscale = !enabled;
        nameText.textin.set(character != null ? character.name : "No character");

        Artifact a = profile != null ? profile.findArtifact(profile.equippedArtifactIds[0]) : null;
        Artifact b = profile != null ? profile.findArtifact(profile.equippedArtifactIds[1]) : null;
        artifactA.texture = CharacterAssets.artifactIconFor(a);
        artifactA.grayscale = !enabled;
        artifactB.texture = CharacterAssets.artifactIconFor(b);
        artifactB.grayscale = !enabled;
    }
}
