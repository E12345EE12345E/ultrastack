package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.game.progression.CharacterRegistry;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.render.CharacterAssets;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIGrid;
import me.ethanchen.lwjgl3.menuscreens.ui.UIInventoryButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.network.ClientPacketWrapper;

/**
 * Character selection (top half) and artifact loadout management (bottom half), per
 * implementation.md, Part 5. Laid out on a fixed 1920×1080 Aspect-locked canvas.
 *
 * <p>Equip slots are reference-only (like fusion slots): they point at inventory artifacts, which
 * stay in the grid with a lime overlay while equipped. {@code Use}/{@code Remove} add or clear
 * those references. Stats show the highlighted artifact on the left and the hovered artifact on
 * the right.
 */
public class CharacterScreen extends AspectLockedMenuScreen {
    private static final float DIVIDER_Y = 560f;
    private static final UIGrid INVENTORY_GRID = UIGrid.designSquare(130, 300, 14, 100, 12);

    private final MenuScreen returnScreen;
    private final Supplier<Boolean> charactersEnabled;

    private final UIInventoryButton bigPortrait;
    private final UIText nameText;
    private final List<UIInventoryButton> characterButtons = new ArrayList<>();
    private final UIInventoryButton[] equipSlots = new UIInventoryButton[2];
    private final UIText highlightedStatsText;
    private final UIText hoveredStatsText;
    private final UIText warningText;

    private final List<UIInventoryButton> inventoryButtons = new ArrayList<>();
    private final Map<String, UIInventoryButton> inventoryButtonsById = new HashMap<>();
    private int lastInventorySize = -1;

    /** Currently highlighted artifact id (inventory or equip-slot click). */
    private String highlightedId;

    public CharacterScreen(ClientApp app, MenuScreen returnScreen, Supplier<Boolean> charactersEnabled) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.returnScreen = returnScreen;
        this.charactersEnabled = charactersEnabled;

        elements.add(new UIText(DesignUi.nx(960), DesignUi.ny(990), "Character & Artifacts", 2.5));
        elements.add(new UIButton(
                DesignUi.nx(140), DesignUi.ny(1000), DesignUi.nw(200), DesignUi.nh(64),
                "Back", () -> app.switchMenu(returnScreen)));

        // ---- Top half: character selection ----
        bigPortrait = DesignUi.inventoryButton(340, 760, 260, null, null);
        nameText = new UIText(DesignUi.nx(340), DesignUi.ny(600), "", 1.4);
        elements.add(bigPortrait);
        elements.add(nameText);

        for (CharacterDef def : CharacterRegistry.ALL) {
            int id = def.id;
            int index = characterButtons.size();
            UIInventoryButton btn = DesignUi.inventoryButton(
                    580, 860 - index * 150, 120,
                    CharacterAssets.portraitFor(id), () -> selectCharacter(id));
            characterButtons.add(btn);
            elements.add(btn);
        }

        // ---- Bottom half: reference equip slots + Use/Remove + stats + inventory ----
        equipSlots[0] = DesignUi.inventoryButton(150, 480, 110, null, () -> highlightEquipSlot(0));
        equipSlots[1] = DesignUi.inventoryButton(290, 480, 110, null, () -> highlightEquipSlot(1));
        elements.add(equipSlots[0]);
        elements.add(equipSlots[1]);
        elements.add(new UIButton(
                DesignUi.nx(140), DesignUi.ny(380), DesignUi.nw(140), DesignUi.nh(52),
                "Use", this::useHighlighted));
        elements.add(new UIButton(
                DesignUi.nx(300), DesignUi.ny(380), DesignUi.nw(160), DesignUi.nh(52),
                "Remove", this::removeHighlighted));

        highlightedStatsText = new UIText(
                DesignUi.nx(480), DesignUi.ny(520), "Select an artifact", 1.45, UIText.TextAlign.TOP_LEFT);
        hoveredStatsText = new UIText(
                DesignUi.nx(1180), DesignUi.ny(520), "Hover an artifact", 1.45, UIText.TextAlign.TOP_LEFT);
        elements.add(highlightedStatsText);
        elements.add(hoveredStatsText);

        elements.add(new UIButton(
                DesignUi.nx(1780), DesignUi.ny(180), DesignUi.nw(220), DesignUi.nh(70),
                "Fusion", () -> app.switchMenu(new FusionScreen(app, this, charactersEnabled))));

        warningText = new UIText(DesignUi.nx(960), DesignUi.ny(40), "", 0.85);
        elements.add(warningText);

        refresh();
    }

    @Override
    protected void drawDesignDecorations() {
        float x0 = viewport.toScreenX((float) DesignUi.nx(80));
        float x1 = viewport.toScreenX((float) DesignUi.nx(1840));
        float y = viewport.toScreenY((float) DesignUi.ny(DIVIDER_Y));
        float thickness = Math.max(2f, viewport.scale * 3f);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.45f, 0.45f, 0.48f, 1f);
        shapes.rect(x0, y - thickness * 0.5f, x1 - x0, thickness);
        shapes.end();
        shapes.setColor(Color.WHITE);
    }

    private void selectCharacter(int characterId) {
        PlayerProfile profile = app.getProfile();
        if (profile == null || !profile.isCharacterUnlocked(characterId)) return;
        profile.selectedCharacterId = characterId;
        syncLoadout(profile);
    }

    private void highlightEquipSlot(int index) {
        PlayerProfile profile = app.getProfile();
        if (profile == null) return;
        String id = profile.equippedArtifactIds[index];
        if (id != null) highlightedId = id;
    }

    private void toggleInventoryHighlight(String artifactId) {
        highlightedId = artifactId.equals(highlightedId) ? null : artifactId;
    }

    /** Places the highlighted inventory artifact into the first empty equip slot (reference). */
    private void useHighlighted() {
        PlayerProfile profile = app.getProfile();
        if (profile == null || highlightedId == null) return;
        if (profile.findArtifact(highlightedId) == null) return;
        if (isEquipped(profile, highlightedId)) return;
        for (int i = 0; i < profile.equippedArtifactIds.length; i++) {
            if (profile.equippedArtifactIds[i] == null) {
                profile.equippedArtifactIds[i] = highlightedId;
                syncLoadout(profile);
                return;
            }
        }
    }

    /** Clears the highlighted artifact from whichever equip slot references it. */
    private void removeHighlighted() {
        PlayerProfile profile = app.getProfile();
        if (profile == null || highlightedId == null) return;
        boolean changed = false;
        for (int i = 0; i < profile.equippedArtifactIds.length; i++) {
            if (highlightedId.equals(profile.equippedArtifactIds[i])) {
                profile.equippedArtifactIds[i] = null;
                changed = true;
            }
        }
        if (changed) syncLoadout(profile);
    }

    private void syncLoadout(PlayerProfile profile) {
        app.sendLoadoutRequest(profile.selectedCharacterId,
                profile.equippedArtifactIds[0], profile.equippedArtifactIds[1]);
    }

    private boolean isEquipped(PlayerProfile profile, String id) {
        return id.equals(profile.equippedArtifactIds[0]) || id.equals(profile.equippedArtifactIds[1]);
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
        if (selected != null) {
            bigPortrait.showItem(CharacterAssets.portraitFor(selected.id), null, null);
        } else {
            bigPortrait.clearSlot(null);
        }
        bigPortrait.grayscale = !enabled;
        nameText.textin.set(selected != null ? selected.name : "");

        for (int i = 0; i < characterButtons.size(); i++) {
            CharacterDef def = CharacterRegistry.ALL[i];
            UIInventoryButton btn = characterButtons.get(i);
            boolean unlocked = profile != null && profile.isCharacterUnlocked(def.id);
            btn.grayscale = !unlocked || !enabled;
            btn.selected = profile != null && profile.selectedCharacterId == def.id;
        }

        for (int i = 0; i < equipSlots.length; i++) {
            Artifact equipped = profile != null ? profile.findArtifact(profile.equippedArtifactIds[i]) : null;
            UIInventoryButton slot = equipSlots[i];
            if (equipped != null) {
                slot.showItem(CharacterAssets.artifactIconFor(equipped), equipped.id, "Lv" + equipped.level);
            } else {
                slot.clearSlot("Empty");
            }
            slot.selected = equipped != null && equipped.id.equals(highlightedId);
            slot.grayscale = !enabled;
            slot.overlayColor = null;
        }

        rebuildInventoryIfNeeded(profile);
        for (Map.Entry<String, UIInventoryButton> entry : inventoryButtonsById.entrySet()) {
            UIInventoryButton btn = entry.getValue();
            btn.grayscale = !enabled;
            btn.selected = entry.getKey().equals(highlightedId);
            boolean equipped = profile != null && isEquipped(profile, entry.getKey());
            btn.overlayColor = equipped ? UIInventoryButton.OVERLAY_EQUIPPED : null;
        }

        Artifact highlighted = profile != null ? profile.findArtifact(highlightedId) : null;
        highlightedStatsText.textin.set(highlighted != null ? describe(highlighted) : "Select an artifact");

        Artifact hovered = findHoveredArtifact(profile);
        hoveredStatsText.textin.set(hovered != null ? describe(hovered) : "Hover an artifact");
    }

    private Artifact findHoveredArtifact(PlayerProfile profile) {
        if (profile == null) return null;
        for (UIInventoryButton btn : inventoryButtons) {
            if (btn.hovered && btn.boundId != null) return profile.findArtifact(btn.boundId);
        }
        for (UIInventoryButton slot : equipSlots) {
            if (slot.hovered && slot.boundId != null) return profile.findArtifact(slot.boundId);
        }
        return null;
    }

    private void rebuildInventoryIfNeeded(PlayerProfile profile) {
        int size = profile != null ? profile.inventory.size() : 0;
        if (size == lastInventorySize) return;
        lastInventorySize = size;

        elements.removeAll(inventoryButtons);
        inventoryButtons.clear();
        inventoryButtonsById.clear();
        if (profile == null) return;
        for (int i = 0; i < profile.inventory.size(); i++) {
            Artifact artifact = profile.inventory.get(i);
            UIInventoryButton btn = new UIInventoryButton(
                    INVENTORY_GRID.cellCenterX(i), INVENTORY_GRID.cellCenterY(i),
                    INVENTORY_GRID.cellW, INVENTORY_GRID.cellH,
                    CharacterAssets.artifactIconFor(artifact), () -> toggleInventoryHighlight(artifact.id));
            btn.showItem(CharacterAssets.artifactIconFor(artifact), artifact.id, "Lv" + artifact.level);
            inventoryButtons.add(btn);
            inventoryButtonsById.put(artifact.id, btn);
            elements.add(btn);
        }
    }

    private String describe(Artifact artifact) {
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
        returnScreen.passClientPacket(w);
    }
}
