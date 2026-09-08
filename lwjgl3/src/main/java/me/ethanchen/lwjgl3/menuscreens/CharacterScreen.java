package me.ethanchen.lwjgl3.menuscreens;

import java.util.List;
import java.util.function.Supplier;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.game.progression.CharacterRegistry;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecorContext;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedGrid;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedPager;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedSlot;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedText;
import me.ethanchen.lwjgl3.menuscreens.decorated.Widget;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.lwjgl3.render.CharacterAssets;
import me.ethanchen.network.ClientPacketWrapper;

/**
 * Character selection (top half) and artifact loadout management (bottom half).
 *
 * <p>Equip slots are reference-only: they point at inventory artifacts, which stay in the
 * grid with a lime overlay while equipped. {@code Use}/{@code Remove} add or clear those
 * references. Hovered artifact stats use the cursor-following {@code InfoText} popup.
 */
public class CharacterScreen extends DecoratedMenuScreen {
    private static final float DIVIDER_Y = 610f;
    private static final int INV_COLUMNS = 10;
    private static final int INV_ROWS = 4;
    private static final float INV_CELL = 110f;
    private static final float INV_GAP = 8f;
    private static final int MAX_EFFECT_LINES = 6;
    private static final int CHAR_ROWS = 2;
    private static final float CHAR_SIZE = 115f;

    private final MenuScreen returnScreen;
    private final Supplier<Boolean> charactersEnabled;

    private final DecoratedSlot bigPortrait;
    private final DecoratedText nameText;
    private final DecoratedGrid characterGrid;
    private final DecoratedText abilityBody;
    private final DecoratedText descriptionBody;
    private final DecoratedSlot[] equipSlots = new DecoratedSlot[2];
    private final DecoratedText highlightedStatsText;
    private final DecoratedText warningText;

    private final DecoratedGrid inventoryGrid;
    private final DecoratedPager paging;
    private int cachedItemCount = -1;

    /** Currently highlighted artifact id (inventory or equip-slot click). */
    private String highlightedId;

    public CharacterScreen(ClientApp app, MenuScreen returnScreen, Supplier<Boolean> charactersEnabled) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.returnScreen = returnScreen;
        this.charactersEnabled = charactersEnabled;

        addDecorated(new DecoratedButton(140, 1020, 200, 64, "Back",
                () -> app.switchMenu(returnScreen)));
        addDecorated(new DecoratedText(960, 1010, "Character & Artifacts", 2.5f));

        final float bigPortraitX = 340f;
        final float bigPortraitY = 820f;
        final float bigPortraitSize = 230f;
        final float charStartX = 580f;
        final float charTopCenterY = bigPortraitY + bigPortraitSize * 0.5f - CHAR_SIZE * 0.5f;
        int charCols = Math.max(1, (CharacterRegistry.ALL.length + CHAR_ROWS - 1) / CHAR_ROWS);

        bigPortrait = new DecoratedSlot(bigPortraitX, bigPortraitY, bigPortraitSize);
        nameText = new DecoratedText(bigPortraitX, 670, "", 1.3f);
        addDecorated(bigPortrait);
        addDecorated(nameText);

        characterGrid = new DecoratedGrid(charStartX, charTopCenterY, charCols, CHAR_ROWS, CHAR_SIZE, 0f);
        characterGrid.populatedCount = CharacterRegistry.ALL.length;
        for (int i = 0; i < characterGrid.slotCount(); i++) {
            DecoratedSlot slot = characterGrid.slot(i);
            if (i >= CharacterRegistry.ALL.length) {
                slot.visible = false;
                continue;
            }
            int id = CharacterRegistry.ALL[i].id;
            slot.showItem(CharacterAssets.portraitFor(id), String.valueOf(id), null);
            slot.action = () -> selectCharacter(id);
        }
        addDecorated(characterGrid);

        final float blurbX = 850f;
        DecoratedText abilityHeading = new DecoratedText(blurbX, 935, "Ability Description", 1.2f)
                .align(UIText.TextAlign.TOP_LEFT);
        abilityBody = new DecoratedText(blurbX, 900, "", 1.0f)
                .align(UIText.TextAlign.TOP_LEFT)
                .wrap(1000f);
        DecoratedText descriptionHeading = new DecoratedText(blurbX, 790, "Description", 1.2f)
                .align(UIText.TextAlign.TOP_LEFT);
        descriptionBody = new DecoratedText(blurbX, 755, "", 1.0f)
                .align(UIText.TextAlign.TOP_LEFT)
                .wrap(1000f);
        addDecorated(abilityHeading);
        addDecorated(abilityBody);
        addDecorated(descriptionHeading);
        addDecorated(descriptionBody);

        equipSlots[0] = new DecoratedSlot(150, 530, 100);
        equipSlots[1] = new DecoratedSlot(280, 530, 100);
        for (DecoratedSlot slot : equipSlots) {
            slot.focusable = true;
            slot.action = () -> {
                if (slot.boundId != null) highlightArtifact(slot.boundId);
            };
            slot.secondaryAction = () -> {
                if (slot.boundId != null) quickToggleEquip(slot.boundId);
            };
            addDecorated(slot);
        }
        DecoratedButton useBtn = new DecoratedButton(140, 430, 140, 48, "Use", this::useHighlighted);
        DecoratedButton removeBtn = new DecoratedButton(290, 430, 160, 48, "Remove", this::removeHighlighted);
        useBtn.fontSize = 1.35f;
        removeBtn.fontSize = 1.35f;
        useBtn.outlineDesignPx = 3f;
        removeBtn.outlineDesignPx = 3f;
        addDecorated(useBtn);
        addDecorated(removeBtn);

        highlightedStatsText = new DecoratedText(100, 350, "Select an artifact", 1.2f)
                .align(UIText.TextAlign.TOP_LEFT)
                .wrap(430f);
        addDecorated(highlightedStatsText);

        inventoryGrid = DecoratedGrid.centered(1210, 530, INV_COLUMNS, INV_ROWS, INV_CELL, INV_GAP);
        inventoryGrid.onPageChange(this::changeInventoryPage);
        addDecorated(inventoryGrid);

        paging = new DecoratedPager(INV_COLUMNS, INV_ROWS, 1210, 70, this::changeInventoryPage);
        addDecorated(paging);

        DecoratedButton manageBtn = new DecoratedButton(1720, 70, 340, 48, "Manage Artifacts",
                this::openManageWidget);
        manageBtn.fontSize = 1.25f;
        manageBtn.outlineDesignPx = 3f;
        addDecorated(manageBtn);

        warningText = new DecoratedText(1720, 125, "", 0.75f);
        addDecorated(warningText);

        refresh();
    }

    @Override
    protected void drawDecorations(DecorContext ctx) {
        float x0 = viewport.toScreenX((float) DesignUi.nx(80));
        float x1 = viewport.toScreenX((float) DesignUi.nx(1840));
        float y = viewport.toScreenY((float) DesignUi.ny(DIVIDER_Y));
        float thickness = Math.max(2f, viewport.scale * 3f);

        ctx.shapes.begin(ShapeRenderer.ShapeType.Filled);
        ctx.shapes.setColor(1f, 1f, 1f, 1f);
        ctx.shapes.rect(x0, y - thickness * 0.5f, x1 - x0, thickness);
        ctx.shapes.end();
        ctx.shapes.setColor(Color.WHITE);
    }

    @Override
    protected void updateScreen(long menuElapsedMs, long appElapsedMs) {
        refresh();
    }

    private void changeInventoryPage(int delta) {
        if (delta < 0) paging.prev(cachedItemCount);
        else paging.next(cachedItemCount);
    }

    private void openManageWidget() {
        if (hasOpenWidget()) return;
        openWidget(createManageWidget());
    }

    private Widget createManageWidget() {
        Widget w = new Widget(960f, 520f, 580f, 480f);
        w.add(new DecoratedText(0f, 0f, "Manage Artifacts", 2.2f), 0f, 168f);

        DecoratedButton fusion = new DecoratedButton(0f, 0f, 440f, 78f, "Fusion",
                () -> app.switchMenu(new FusionScreen(app, this, charactersEnabled)));
        DecoratedButton effects = new DecoratedButton(0f, 0f, 440f, 78f, "Artifact Effects",
                () -> app.switchMenu(new ArtifactLoadoutScreen(app, this)));
        DecoratedButton back = new DecoratedButton(0f, 0f, 300f, 68f, "Back", this::closeTopWidget);
        fusion.fontSize = 1.55f;
        effects.fontSize = 1.55f;
        back.fontSize = 1.4f;

        float btnH = 78f;
        float backH = 68f;
        float gap = 32f;
        float fusionY = 70f;
        float effectsY = fusionY - btnH - gap;
        float backY = effectsY - btnH * 0.5f - gap - backH * 0.5f;
        w.add(fusion, 0f, fusionY);
        w.add(effects, 0f, effectsY);
        w.add(back, 0f, backY);
        return w;
    }

    private void selectCharacter(int characterId) {
        PlayerProfile profile = app.getProfile();
        if (profile == null || !profile.isCharacterUnlocked(characterId)) return;
        profile.selectedCharacterId = characterId;
        syncLoadout(profile);
    }

    /** Select an artifact and reveal its inventory tile (same as clicking that inventory slot). */
    private void highlightArtifact(String artifactId) {
        highlightedId = artifactId;
        PlayerProfile profile = app.getProfile();
        if (profile == null) return;
        List<Artifact> items = profile.inventory;
        for (int i = 0; i < items.size(); i++) {
            if (artifactId.equals(items.get(i).id)) {
                paging.showIndex(i, items.size());
                return;
            }
        }
    }

    /** Right-click / double-click on an inventory (or equip-slot shortcut) tile: equip or unequip. */
    private void quickToggleEquip(String artifactId) {
        highlightArtifact(artifactId);
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

    private void refresh() {
        PlayerProfile profile = app.getProfile();
        boolean enabled = Boolean.TRUE.equals(charactersEnabled.get());
        warningText.text = enabled ? "" : "The Character System is not available for this gamemode "
                + "and selected effects will not be active";

        CharacterDef selected = profile != null ? CharacterRegistry.byId(profile.selectedCharacterId) : null;
        if (selected != null) {
            bigPortrait.showItem(CharacterAssets.portraitFor(selected.id), null, null);
        } else {
            bigPortrait.clearSlot(null);
        }
        bigPortrait.grayscale = !enabled;
        nameText.text = selected != null ? selected.name : "";
        abilityBody.text = selected != null ? selected.abilityDescription : "";
        descriptionBody.text = selected != null ? selected.description : "";

        int selectedCharIndex = -1;
        for (int i = 0; i < CharacterRegistry.ALL.length && i < characterGrid.slotCount(); i++) {
            CharacterDef def = CharacterRegistry.ALL[i];
            DecoratedSlot slot = characterGrid.slot(i);
            boolean unlocked = profile != null && profile.isCharacterUnlocked(def.id);
            slot.grayscale = !unlocked || !enabled;
            slot.selected = profile != null && profile.selectedCharacterId == def.id;
            if (slot.selected) selectedCharIndex = i;
        }
        characterGrid.selectedIndex = selectedCharIndex;
        characterGrid.populatedCount = CharacterRegistry.ALL.length;

        for (int i = 0; i < equipSlots.length; i++) {
            Artifact equipped = profile != null ? profile.findArtifact(profile.equippedArtifactIds[i]) : null;
            DecoratedSlot slot = equipSlots[i];
            if (equipped != null) {
                slot.showArtifact(CharacterAssets.artifactIconFor(equipped), equipped);
                slot.info(equipped.describeForUi(MAX_EFFECT_LINES));
            } else {
                slot.clearSlot("Empty");
            }
            slot.selected = equipped != null && equipped.id.equals(highlightedId);
            slot.grayscale = !enabled;
            slot.overlayColor = null;
        }

        refreshInventoryPage(profile, enabled);

        Artifact highlighted = profile != null ? profile.findArtifact(highlightedId) : null;
        highlightedStatsText.text = highlighted != null
                ? highlighted.describeForUi(MAX_EFFECT_LINES) : "Select an artifact";
    }

    private void refreshInventoryPage(PlayerProfile profile, boolean enabled) {
        List<Artifact> items = profile != null ? profile.inventory : List.of();
        cachedItemCount = items.size();
        paging.updateLabel(cachedItemCount);

        int start = paging.page * paging.pageSize();
        int populated = 0;
        int selectedOnPage = -1;
        for (int i = 0; i < inventoryGrid.slotCount(); i++) {
            DecoratedSlot slot = inventoryGrid.slot(i);
            int index = start + i;
            if (index < items.size()) {
                Artifact artifact = items.get(index);
                slot.showArtifact(CharacterAssets.artifactIconFor(artifact), artifact);
                slot.info(artifact.describeForUi(MAX_EFFECT_LINES));
                slot.action = () -> highlightArtifact(artifact.id);
                slot.secondaryAction = () -> quickToggleEquip(artifact.id);
                slot.grayscale = !enabled;
                slot.selected = artifact.id.equals(highlightedId);
                slot.overlayColor = isEquipped(profile, artifact.id) ? DecoratedSlot.OVERLAY_EQUIPPED : null;
                populated++;
                if (slot.selected) selectedOnPage = i;
            } else {
                slot.clearSlot(null);
                slot.action = null;
                slot.secondaryAction = null;
                slot.grayscale = !enabled;
                slot.selected = false;
                slot.overlayColor = null;
            }
        }
        inventoryGrid.populatedCount = populated;
        inventoryGrid.selectedIndex = selectedOnPage;
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
