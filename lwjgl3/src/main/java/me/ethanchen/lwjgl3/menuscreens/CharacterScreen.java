package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.List;
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
import me.ethanchen.lwjgl3.menuscreens.ui.InventoryPaging;
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
    /** Raised so the artifact half has room for name + up to 5 effect lines. */
    private static final float DIVIDER_Y = 610f;
    private static final int INV_COLUMNS = 15;
    private static final int INV_ROWS = 2;
    /** Packed grid (no gaps): slightly smaller tiles than the prior 100px / 3-row layout. */
    private static final UIGrid INVENTORY_GRID = UIGrid.designSquare(118, 275, INV_COLUMNS, 84, 0);
    private static final int MAX_EFFECT_LINES = 5;

    private final MenuScreen returnScreen;
    private final Supplier<Boolean> charactersEnabled;

    private final UIInventoryButton bigPortrait;
    private final UIText nameText;
    private final List<UIInventoryButton> characterButtons = new ArrayList<>();
    private final UIInventoryButton[] equipSlots = new UIInventoryButton[2];
    private final UIText highlightedStatsText;
    private final UIText hoveredStatsText;
    private final UIText warningText;

    private final List<UIInventoryButton> inventorySlots = new ArrayList<>();
    private final InventoryPaging paging;
    private int cachedItemCount = -1;

    /** Currently highlighted artifact id (inventory or equip-slot click). */
    private String highlightedId;

    public CharacterScreen(ClientApp app, MenuScreen returnScreen, Supplier<Boolean> charactersEnabled) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.returnScreen = returnScreen;
        this.charactersEnabled = charactersEnabled;

        elements.add(new UIText(DesignUi.nx(960), DesignUi.ny(1010), "Character & Artifacts", 2.5));
        elements.add(new UIButton(
                DesignUi.nx(140), DesignUi.ny(1020), DesignUi.nw(200), DesignUi.nh(64),
                "Back", () -> app.switchMenu(returnScreen)));

        // ---- Top half: character selection (compressed above the raised divider) ----
        bigPortrait = DesignUi.inventoryButton(340, 820, 230, null, null);
        nameText = new UIText(DesignUi.nx(340), DesignUi.ny(670), "", 1.3);
        elements.add(bigPortrait);
        elements.add(nameText);

        for (CharacterDef def : CharacterRegistry.ALL) {
            int id = def.id;
            int index = characterButtons.size();
            UIInventoryButton btn = DesignUi.inventoryButton(
                    580, 900 - index * 140, 110,
                    CharacterAssets.portraitFor(id), () -> selectCharacter(id));
            characterButtons.add(btn);
            elements.add(btn);
        }

        // ---- Bottom half: reference equip slots + Use/Remove + stats + inventory ----
        equipSlots[0] = DesignUi.inventoryButton(150, 530, 100, null, () -> highlightEquipSlot(0));
        equipSlots[1] = DesignUi.inventoryButton(280, 530, 100, null, () -> highlightEquipSlot(1));
        equipSlots[0].secondaryAction = () -> quickToggleEquipFromSlot(0);
        equipSlots[1].secondaryAction = () -> quickToggleEquipFromSlot(1);
        elements.add(equipSlots[0]);
        elements.add(equipSlots[1]);
        elements.add(new UIButton(
                DesignUi.nx(140), DesignUi.ny(430), DesignUi.nw(140), DesignUi.nh(48),
                "Use", this::useHighlighted));
        elements.add(new UIButton(
                DesignUi.nx(290), DesignUi.ny(430), DesignUi.nw(160), DesignUi.nh(48),
                "Remove", this::removeHighlighted));

        highlightedStatsText = new UIText(
                DesignUi.nx(460), DesignUi.ny(575), "Select an artifact", 1.35, UIText.TextAlign.TOP_LEFT);
        hoveredStatsText = new UIText(
                DesignUi.nx(1160), DesignUi.ny(575), "Hover an artifact", 1.35, UIText.TextAlign.TOP_LEFT);
        elements.add(highlightedStatsText);
        elements.add(hoveredStatsText);

        int pageSize = INV_COLUMNS * INV_ROWS;
        for (int i = 0; i < pageSize; i++) {
            UIInventoryButton slot = new UIInventoryButton(
                    INVENTORY_GRID.cellCenterX(i), INVENTORY_GRID.cellCenterY(i),
                    INVENTORY_GRID.cellW, INVENTORY_GRID.cellH, null, null);
            inventorySlots.add(slot);
            elements.add(slot);
        }
        // Page bar centred under the 15-col grid.
        paging = InventoryPaging.addTo(elements, INV_COLUMNS, INV_ROWS, 706, 50, this::changeInventoryPage);

        elements.add(new UIButton(
                DesignUi.nx(1780), DesignUi.ny(180), DesignUi.nw(220), DesignUi.nh(70),
                "Fusion", () -> app.switchMenu(new FusionScreen(app, this, charactersEnabled))));

        warningText = new UIText(DesignUi.nx(1600), DesignUi.ny(50), "", 0.75);
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

    private void changeInventoryPage(int delta) {
        if (delta < 0) paging.prev(cachedItemCount);
        else paging.next(cachedItemCount);
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

    /** Right-click / double-click on an inventory tile: equip if possible, else unequip. */
    private void quickToggleEquip(String artifactId) {
        highlightedId = artifactId;
        PlayerProfile profile = app.getProfile();
        if (profile == null || profile.findArtifact(artifactId) == null) return;
        if (isEquipped(profile, artifactId)) {
            for (int i = 0; i < profile.equippedArtifactIds.length; i++) {
                if (artifactId.equals(profile.equippedArtifactIds[i])) {
                    profile.equippedArtifactIds[i] = null;
                }
            }
            syncLoadout(profile);
            return;
        }
        for (int i = 0; i < profile.equippedArtifactIds.length; i++) {
            if (profile.equippedArtifactIds[i] == null) {
                profile.equippedArtifactIds[i] = artifactId;
                syncLoadout(profile);
                return;
            }
        }
    }

    /** Right-click / double-click on an equip slot: unequip that reference if present. */
    private void quickToggleEquipFromSlot(int index) {
        PlayerProfile profile = app.getProfile();
        if (profile == null) return;
        String id = profile.equippedArtifactIds[index];
        if (id == null) return;
        highlightedId = id;
        profile.equippedArtifactIds[index] = null;
        syncLoadout(profile);
    }

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
            bigPortrait.showItem(CharacterAssets.portraitFor(selected.id), null, null, null);
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
                slot.showArtifact(CharacterAssets.artifactIconFor(equipped), equipped);
            } else {
                slot.clearSlot("Empty");
            }
            slot.selected = equipped != null && equipped.id.equals(highlightedId);
            slot.grayscale = !enabled;
            slot.overlayColor = null;
        }

        refreshInventoryPage(profile, enabled);

        Artifact highlighted = profile != null ? profile.findArtifact(highlightedId) : null;
        highlightedStatsText.textin.set(highlighted != null
                ? highlighted.describeForUi(MAX_EFFECT_LINES) : "Select an artifact");

        Artifact hovered = findHoveredArtifact(profile);
        hoveredStatsText.textin.set(hovered != null
                ? hovered.describeForUi(MAX_EFFECT_LINES) : "Hover an artifact");
    }

    private void refreshInventoryPage(PlayerProfile profile, boolean enabled) {
        List<Artifact> items = profile != null ? profile.inventory : List.of();
        cachedItemCount = items.size();
        paging.updateLabel(cachedItemCount);

        int start = paging.page * paging.pageSize();
        for (int i = 0; i < inventorySlots.size(); i++) {
            UIInventoryButton slot = inventorySlots.get(i);
            int index = start + i;
            if (index < items.size()) {
                Artifact artifact = items.get(index);
                slot.showArtifact(CharacterAssets.artifactIconFor(artifact), artifact);
                slot.action = () -> highlightedId = artifact.id;
                slot.secondaryAction = () -> quickToggleEquip(artifact.id);
                slot.grayscale = !enabled;
                slot.selected = artifact.id.equals(highlightedId);
                slot.overlayColor = isEquipped(profile, artifact.id) ? UIInventoryButton.OVERLAY_EQUIPPED : null;
            } else {
                slot.clearSlot(null);
                slot.grayscale = !enabled;
                slot.selected = false;
                slot.overlayColor = null;
            }
        }
    }

    private Artifact findHoveredArtifact(PlayerProfile profile) {
        if (profile == null) return null;
        for (UIInventoryButton btn : inventorySlots) {
            if (btn.hovered && btn.boundId != null) return profile.findArtifact(btn.boundId);
        }
        for (UIInventoryButton slot : equipSlots) {
            if (slot.hovered && slot.boundId != null) return profile.findArtifact(slot.boundId);
        }
        return null;
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
