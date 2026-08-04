package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.render.CharacterAssets;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
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
    private static final UIGrid INVENTORY_GRID = UIGrid.designSquare(160, 420, 8, 88, 14);

    private final CharacterScreen parent;
    private final Supplier<Boolean> charactersEnabled;

    private final UIInventoryButton[] fusionSlots = new UIInventoryButton[5];
    private final UIInventoryButton resultSlot;
    private final UIText statsText;
    private final UIText messageText;

    private final List<UIInventoryButton> inventoryButtons = new ArrayList<>();
    private final Map<String, UIInventoryButton> inventoryButtonsById = new HashMap<>();
    private int lastInventorySize = -1;

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

        messageText = new UIText(DesignUi.nx(960), DesignUi.ny(40), "", 0.9);
        elements.add(messageText);

        refresh();
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

        rebuildInventoryIfNeeded(profile);
        for (Map.Entry<String, UIInventoryButton> entry : inventoryButtonsById.entrySet()) {
            UIInventoryButton btn = entry.getValue();
            btn.grayscale = !enabled;
            btn.selected = entry.getKey().equals(highlightedId);
            boolean queued = false;
            for (String id : fusionIds) if (entry.getKey().equals(id)) { queued = true; break; }
            btn.overlayColor = queued ? UIInventoryButton.OVERLAY_FUSION_QUEUED : null;
        }

        for (int i = 0; i < fusionSlots.length; i++) {
            Artifact a = profile != null ? profile.findArtifact(fusionIds[i]) : null;
            UIInventoryButton slot = fusionSlots[i];
            if (a != null) {
                slot.showItem(CharacterAssets.artifactIconFor(a), a.id, "Lv" + a.level);
            } else {
                slot.clearSlot("Empty");
            }
            slot.selected = a != null && a.id.equals(highlightedId);
        }

        Artifact result = profile != null ? profile.findArtifact(resultId) : null;
        if (result != null) {
            resultSlot.showItem(CharacterAssets.artifactIconFor(result), result.id, "Lv" + result.level);
        } else {
            resultSlot.clearSlot("Result");
        }
        resultSlot.selected = result != null && result.id.equals(highlightedId);

        Artifact highlighted = profile != null ? profile.findArtifact(highlightedId) : null;
        statsText.textin.set(highlighted != null ? describe(highlighted) : "Select an artifact");
    }

    private void rebuildInventoryIfNeeded(PlayerProfile profile) {
        int size = profile != null ? profile.inventory.size() : 0;
        if (size == lastInventorySize) return;
        lastInventorySize = size;

        elements.removeAll(inventoryButtons);
        inventoryButtons.clear();
        inventoryButtonsById.clear();
        if (profile == null) return;

        int shown = 0;
        for (Artifact artifact : profile.inventory) {
            if (isEquipped(profile, artifact.id)) continue;
            int index = shown++;
            UIInventoryButton btn = new UIInventoryButton(
                    INVENTORY_GRID.cellCenterX(index), INVENTORY_GRID.cellCenterY(index),
                    INVENTORY_GRID.cellW, INVENTORY_GRID.cellH,
                    CharacterAssets.artifactIconFor(artifact), () -> highlightedId = artifact.id);
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
