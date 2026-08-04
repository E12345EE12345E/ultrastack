package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.game.progression.CharacterRegistry;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.render.CharacterAssets;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIGrid;
import me.ethanchen.lwjgl3.menuscreens.ui.UIIconButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.network.ClientPacketWrapper;

/**
 * Character selection (top half) and artifact loadout management (bottom half), per
 * implementation.md, Part 5. Reached from {@link CharacterSidebar} in the room browser or either
 * lobby screen; all network traffic (packets, and getting pulled into a starting game) is
 * forwarded to {@code returnScreen} exactly like {@link LobbySettingsScreen} does for its chat
 * screen, so nothing about the underlying lobby session is lost while this screen is open.
 */
public class CharacterScreen extends MenuScreen {
    private static final UIGrid INVENTORY_GRID = new UIGrid(0.58, 0.30, 6, 0.05, 0.014);

    private final MenuScreen returnScreen;
    private final Supplier<Boolean> charactersEnabled;

    private final UIIconButton bigPortrait;
    private final UIText nameText;
    private final List<UIIconButton> characterButtons = new ArrayList<>();
    private final UIIconButton[] equipSlots = new UIIconButton[2];
    private final UIText equipStatsText;
    private final UIText inventoryStatsText;
    private final UIText warningText;
    private final List<UIIconButton> inventoryButtons = new ArrayList<>();
    private int lastInventorySize = -1;

    private int highlightedEquipIndex = -1;
    private String highlightedInventoryId = null;

    public CharacterScreen(ClientApp app, MenuScreen returnScreen, Supplier<Boolean> charactersEnabled) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.returnScreen = returnScreen;
        this.charactersEnabled = charactersEnabled;

        elements.add(new UIText(0.5, 0.98, "Character & Artifacts", 2.5));
        elements.add(new UIButton(0.08, 0.95, 0.12, 0.06, "Back", () -> app.switchMenu(returnScreen)));

        // ---- Top half: character selection ----
        bigPortrait = new UIIconButton(0.22, 0.78, 0.22, null, null);
        nameText = new UIText(0.22, 0.63, "", 1.2);
        elements.add(bigPortrait);
        elements.add(nameText);

        for (CharacterDef def : CharacterRegistry.ALL) {
            int id = def.id;
            UIIconButton btn = new UIIconButton(0.62, 0.85 - characterButtons.size() * 0.11, 0.08,
                    CharacterAssets.portraitFor(id), () -> selectCharacter(id));
            characterButtons.add(btn);
            elements.add(btn);
        }

        // ---- Bottom half: artifact loadout ----
        equipSlots[0] = new UIIconButton(0.66, 0.50, 0.09, null, () -> toggleEquipHighlight(0));
        equipSlots[1] = new UIIconButton(0.78, 0.50, 0.09, null, () -> toggleEquipHighlight(1));
        elements.add(equipSlots[0]);
        elements.add(equipSlots[1]);
        elements.add(new UIButton(0.72, 0.40, 0.14, 0.05, "Swap Out", this::performSwap));

        equipStatsText = new UIText(0.06, 0.50, "Select an artifact", 0.85, UIText.TextAlign.TOP_LEFT);
        inventoryStatsText = new UIText(0.06, 0.26, "Select an artifact", 0.85, UIText.TextAlign.TOP_LEFT);
        elements.add(equipStatsText);
        elements.add(inventoryStatsText);

        elements.add(new UIButton(0.5, 0.05, 0.22, 0.06, "Fusion",
                () -> app.switchMenu(new FusionScreen(app, this, charactersEnabled))));

        warningText = new UIText(0.5, 0.115, "", 0.7);
        elements.add(warningText);

        refresh();
    }

    private void selectCharacter(int characterId) {
        PlayerProfile profile = app.getProfile();
        if (profile == null || !profile.isCharacterUnlocked(characterId)) return;
        profile.selectedCharacterId = characterId;
        app.sendLoadoutRequest(characterId, profile.equippedArtifactIds[0], profile.equippedArtifactIds[1]);
    }

    private void toggleEquipHighlight(int index) {
        highlightedEquipIndex = (highlightedEquipIndex == index) ? -1 : index;
    }

    private void toggleInventoryHighlight(String artifactId) {
        highlightedInventoryId = artifactId.equals(highlightedInventoryId) ? null : artifactId;
    }

    private void performSwap() {
        PlayerProfile profile = app.getProfile();
        if (profile == null || highlightedEquipIndex < 0) return;
        profile.equippedArtifactIds[highlightedEquipIndex] = highlightedInventoryId; // may be null (unequip)
        app.sendLoadoutRequest(profile.selectedCharacterId, profile.equippedArtifactIds[0], profile.equippedArtifactIds[1]);
        highlightedEquipIndex = -1;
        highlightedInventoryId = null;
    }

    @Override
    public void update() {
        refresh();
    }

    private void refresh() {
        PlayerProfile profile = app.getProfile();
        boolean enabled = Boolean.TRUE.equals(charactersEnabled.get());
        warningText.textin.set(enabled ? "" : "The Character System is not available for this gamemode "
                + "and selected effects will not be active");

        CharacterDef selected = profile != null ? CharacterRegistry.byId(profile.selectedCharacterId) : null;
        bigPortrait.icon = selected != null ? CharacterAssets.portraitFor(selected.id) : null;
        bigPortrait.grayscale = !enabled;
        nameText.textin.set(selected != null ? selected.name : "");

        for (int i = 0; i < characterButtons.size(); i++) {
            CharacterDef def = CharacterRegistry.ALL[i];
            UIIconButton btn = characterButtons.get(i);
            boolean unlocked = profile != null && profile.isCharacterUnlocked(def.id);
            btn.grayscale = !unlocked || !enabled;
            btn.selected = profile != null && profile.selectedCharacterId == def.id;
        }

        Artifact equippedA = profile != null ? profile.findArtifact(profile.equippedArtifactIds[0]) : null;
        Artifact equippedB = profile != null ? profile.findArtifact(profile.equippedArtifactIds[1]) : null;
        Artifact[] equipped = { equippedA, equippedB };
        for (int i = 0; i < 2; i++) {
            UIIconButton slot = equipSlots[i];
            slot.icon = CharacterAssets.artifactIconFor(equipped[i]);
            slot.placeholderText = equipped[i] == null ? "Empty" : null;
            slot.cornerLabel = equipped[i] != null ? "Lv" + equipped[i].level : null;
            slot.selected = highlightedEquipIndex == i;
            slot.grayscale = !enabled;
        }

        rebuildInventoryIfNeeded(profile);
        for (UIIconButton btn : inventoryButtons) {
            btn.grayscale = !enabled;
        }
        for (int i = 0; i < inventoryButtons.size() && profile != null && i < profile.inventory.size(); i++) {
            inventoryButtons.get(i).selected = profile.inventory.get(i).id.equals(highlightedInventoryId);
        }

        Artifact highlightedEquipArtifact = highlightedEquipIndex >= 0 ? equipped[highlightedEquipIndex] : null;
        equipStatsText.textin.set(describe(highlightedEquipArtifact));
        Artifact highlightedInventoryArtifact = profile != null ? profile.findArtifact(highlightedInventoryId) : null;
        inventoryStatsText.textin.set(describe(highlightedInventoryArtifact));
    }

    private void rebuildInventoryIfNeeded(PlayerProfile profile) {
        int size = profile != null ? profile.inventory.size() : 0;
        if (size == lastInventorySize) return;
        lastInventorySize = size;

        elements.removeAll(inventoryButtons);
        inventoryButtons.clear();
        if (profile == null) return;
        for (int i = 0; i < profile.inventory.size(); i++) {
            Artifact artifact = profile.inventory.get(i);
            UIIconButton btn = new UIIconButton(
                    INVENTORY_GRID.cellCenterX(i), INVENTORY_GRID.cellCenterY(i), INVENTORY_GRID.cellSize,
                    CharacterAssets.artifactIconFor(artifact), () -> toggleInventoryHighlight(artifact.id));
            btn.cornerLabel = "Lv" + artifact.level;
            inventoryButtons.add(btn);
            elements.add(btn);
        }
    }

    private String describe(Artifact artifact) {
        if (artifact == null) return "Select an artifact";
        StringBuilder sb = new StringBuilder(artifact.displayName());
        for (var effect : artifact.effects) {
            sb.append('\n').append(effect.describe());
        }
        return sb.toString();
    }

    @Override
    protected void onEscPressed() {
        app.switchMenu(returnScreen);
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        // Delegate so the underlying lobby's dispatcher (StartGameBroadcast, RoomClosedBroadcast,
        // ProfileSyncBroadcast, etc.) keeps working, including pulling the player into the game
        // immediately if it starts while this screen is open (implementation.md, Part 5).
        returnScreen.passClientPacket(w);
    }
}
