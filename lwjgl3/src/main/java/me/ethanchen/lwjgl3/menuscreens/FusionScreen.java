package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import me.ethanchen.game.progression.Artifact;
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
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.packets.s2c.FusionResultBroadcast;

/**
 * Artifact Fusion screen (implementation.md, Part 5): five reference-only fusion slots + a result
 * slot on the top half, and the fusable inventory (equipped artifacts excluded) on the bottom-left.
 * Laid out on a fixed 1920×1080 Aspect-locked canvas. Reached from {@link CharacterScreen}.
 */
public class FusionScreen extends AspectLockedMenuScreen {
    private static final int INV_COLUMNS = 9;
    private static final int INV_ROWS = 6;
    /** Packed grid filling the left bottom under Add/Remove; first-cell center at design px. */
    private static final UIGrid INVENTORY_GRID = UIGrid.designSquare(125, 640, INV_COLUMNS, 90, 0);

    private final CharacterScreen parent;
    private final Supplier<Boolean> charactersEnabled;

    private final UIInventoryButton[] fusionSlots = new UIInventoryButton[5];
    private final UIInventoryButton resultSlot;
    private final UIText statsText;
    private final UIText messageText;

    private final List<UIInventoryButton> inventorySlots = new ArrayList<>();
    private final InventoryPaging paging;
    private int cachedItemCount = -1;

    /** Owned artifact ids currently queued in the 5 fusion slots (nulls for empty). */
    private final String[] fusionIds = new String[5];
    private String highlightedId;
    private String resultId;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = new PacketDispatcher<ClientPacketWrapper>()
            .on(FusionResultBroadcast.class, w -> handleFusionResult((FusionResultBroadcast) w.packet));

    public FusionScreen(ClientApp app, CharacterScreen parent, Supplier<Boolean> charactersEnabled) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.parent = parent;
        this.charactersEnabled = charactersEnabled;

        elements.add(new UIText(DesignUi.nx(960), DesignUi.ny(1040), "Artifact Fusion", 2.5));
        elements.add(new UIButton(
                DesignUi.nx(140), DesignUi.ny(1020), DesignUi.nw(200), DesignUi.nh(64),
                "Back", () -> app.switchMenu(parent)));

        for (int i = 0; i < fusionSlots.length; i++) {
            final int idx = i;
            fusionSlots[i] = DesignUi.inventoryButton(
                    200 + i * 160, 860, 120, null, () -> highlightSlot(idx));
            fusionSlots[i].secondaryAction = () -> quickToggleFusionFromSlot(idx);
            elements.add(fusionSlots[i]);
        }
        elements.add(new UIButton(
                DesignUi.nx(200), DesignUi.ny(720), DesignUi.nw(260), DesignUi.nh(60),
                "Add to Fusion", this::addToFusion));
        elements.add(new UIButton(
                DesignUi.nx(520), DesignUi.ny(720), DesignUi.nw(300), DesignUi.nh(60),
                "Remove from Fusion", this::removeFromFusion));

        resultSlot = DesignUi.inventoryButton(1500, 860, 140, null, () -> {
            if (resultId != null) highlightedId = resultId;
        });
        elements.add(resultSlot);
        elements.add(new UIButton(
                DesignUi.nx(1500), DesignUi.ny(700), DesignUi.nw(260), DesignUi.nh(64),
                "Perform Fusion", this::performFusion));

        statsText = new UIText(
                DesignUi.nx(1180), DesignUi.ny(480), "Select an artifact", 1.0, UIText.TextAlign.TOP_LEFT);
        elements.add(statsText);

        int pageSize = INV_COLUMNS * INV_ROWS;
        for (int i = 0; i < pageSize; i++) {
            UIInventoryButton slot = new UIInventoryButton(
                    INVENTORY_GRID.cellCenterX(i), INVENTORY_GRID.cellCenterY(i),
                    INVENTORY_GRID.cellW, INVENTORY_GRID.cellH, null, null);
            inventorySlots.add(slot);
            elements.add(slot);
        }
        // Page bar centred under the 9-col grid (first centre 125, 9×90 wide).
        paging = InventoryPaging.addTo(elements, INV_COLUMNS, INV_ROWS, 485, 55, this::changeInventoryPage);

        messageText = new UIText(DesignUi.nx(960), DesignUi.ny(20), "", 0.9);
        elements.add(messageText);

        refresh();
    }

    private void changeInventoryPage(int delta) {
        if (delta < 0) paging.prev(cachedItemCount);
        else paging.next(cachedItemCount);
    }

    private void highlightSlot(int index) {
        String id = fusionIds[index];
        if (id != null) highlightedId = id;
    }

    private void addToFusion() {
        PlayerProfile profile = app.getProfile();
        if (profile == null || highlightedId == null) return;
        if (isEquipped(profile, highlightedId)) return;
        for (String id : fusionIds) if (highlightedId.equals(id)) return;
        for (int i = 0; i < fusionIds.length; i++) {
            if (fusionIds[i] == null) { fusionIds[i] = highlightedId; return; }
        }
        messageText.textin.set("All 5 fusion slots are full.");
    }

    private void removeFromFusion() {
        if (highlightedId == null) return;
        for (int i = 0; i < fusionIds.length; i++) {
            if (highlightedId.equals(fusionIds[i])) { fusionIds[i] = null; return; }
        }
    }

    /** Right-click / double-click on an inventory tile: queue for fusion or unqueue. */
    private void quickToggleFusion(String artifactId) {
        highlightedId = artifactId;
        PlayerProfile profile = app.getProfile();
        if (profile == null || isEquipped(profile, artifactId)) return;
        for (int i = 0; i < fusionIds.length; i++) {
            if (artifactId.equals(fusionIds[i])) {
                fusionIds[i] = null;
                return;
            }
        }
        for (int i = 0; i < fusionIds.length; i++) {
            if (fusionIds[i] == null) {
                fusionIds[i] = artifactId;
                return;
            }
        }
        messageText.textin.set("All 5 fusion slots are full.");
    }

    /** Right-click / double-click on a fusion slot: clear that reference. */
    private void quickToggleFusionFromSlot(int index) {
        String id = fusionIds[index];
        if (id == null) return;
        highlightedId = id;
        fusionIds[index] = null;
    }

    private void performFusion() {
        for (String id : fusionIds) {
            if (id == null) {
                messageText.textin.set("Select exactly 5 artifacts to fuse.");
                return;
            }
        }
        app.sendFusionRequest(fusionIds.clone());
    }

    private void handleFusionResult(FusionResultBroadcast p) {
        if (!p.success) {
            messageText.textin.set(p.reason != null ? p.reason : "Fusion failed.");
            return;
        }
        for (int i = 0; i < fusionIds.length; i++) fusionIds[i] = null;
        resultId = p.result != null ? p.result.id : null;
        highlightedId = resultId;
        messageText.textin.set("Fusion successful!");
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

        refreshInventoryPage(profile, enabled);

        for (int i = 0; i < fusionSlots.length; i++) {
            Artifact a = profile != null ? profile.findArtifact(fusionIds[i]) : null;
            UIInventoryButton slot = fusionSlots[i];
            if (a != null) {
                slot.showArtifact(CharacterAssets.artifactIconFor(a), a);
            } else {
                slot.clearSlot("Empty");
            }
            slot.selected = a != null && a.id.equals(highlightedId);
        }

        Artifact result = profile != null ? profile.findArtifact(resultId) : null;
        if (result != null) {
            resultSlot.showArtifact(CharacterAssets.artifactIconFor(result), result);
        } else {
            resultSlot.clearSlot("Result");
        }
        resultSlot.selected = result != null && result.id.equals(highlightedId);

        Artifact highlighted = profile != null ? profile.findArtifact(highlightedId) : null;
        statsText.textin.set(highlighted != null ? describe(highlighted) : "Select an artifact");
    }

    private void refreshInventoryPage(PlayerProfile profile, boolean enabled) {
        List<Artifact> items = new ArrayList<>();
        if (profile != null) {
            for (Artifact artifact : profile.inventory) {
                if (!isEquipped(profile, artifact.id)) items.add(artifact);
            }
        }
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
                slot.secondaryAction = () -> quickToggleFusion(artifact.id);
                slot.grayscale = !enabled;
                slot.selected = artifact.id.equals(highlightedId);
                boolean queued = false;
                for (String id : fusionIds) if (artifact.id.equals(id)) { queued = true; break; }
                slot.overlayColor = queued ? UIInventoryButton.OVERLAY_FUSION_QUEUED : null;
            } else {
                slot.clearSlot(null);
                slot.grayscale = !enabled;
                slot.selected = false;
                slot.overlayColor = null;
            }
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
        app.switchMenu(parent);
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        if (w.packet instanceof FusionResultBroadcast) {
            dispatcher.dispatch(w);
            return;
        }
        parent.passClientPacket(w);
    }
}
