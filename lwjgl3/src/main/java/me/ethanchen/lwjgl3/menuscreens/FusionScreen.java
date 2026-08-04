package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.badlogic.gdx.graphics.Color;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.render.CharacterAssets;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIGrid;
import me.ethanchen.lwjgl3.menuscreens.ui.UIIconButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.packets.s2c.FusionResultBroadcast;

/**
 * Artifact Fusion screen (implementation.md, Part 5, last bullet): five reference-only fusion
 * slots + a result slot on the top half, and the fusable inventory (equipped artifacts excluded)
 * on the bottom-left. Reached from {@link CharacterScreen}'s "Fusion" button.
 */
public class FusionScreen extends MenuScreen {
    private static final UIGrid INVENTORY_GRID = new UIGrid(0.06, 0.42, 8, 0.05, 0.014);
    private static final Color FUSION_QUEUED_OVERLAY = new Color(1f, 0f, 0f, 0.35f);

    private final CharacterScreen parent;
    private final Supplier<Boolean> charactersEnabled;

    private final UIIconButton[] fusionSlots = new UIIconButton[5];
    private final UIIconButton resultSlot;
    private final UIText statsText;
    private final UIText messageText;

    private final List<UIIconButton> inventoryButtons = new ArrayList<>();
    private final Map<String, UIIconButton> inventoryButtonsById = new HashMap<>();
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

        elements.add(new UIText(0.5, 0.98, "Artifact Fusion", 2.5));
        elements.add(new UIButton(0.08, 0.95, 0.12, 0.06, "Back", () -> app.switchMenu(parent)));

        for (int i = 0; i < fusionSlots.length; i++) {
            final int idx = i;
            fusionSlots[i] = new UIIconButton(0.10 + i * 0.10, 0.80, 0.08, null, () -> highlightSlot(idx));
            elements.add(fusionSlots[i]);
        }
        elements.add(new UIButton(0.10, 0.68, 0.18, 0.055, "Add to Fusion", this::addToFusion));
        elements.add(new UIButton(0.32, 0.68, 0.22, 0.055, "Remove from Fusion", this::removeFromFusion));

        resultSlot = new UIIconButton(0.72, 0.80, 0.1, null, () -> {
            if (resultId != null) highlightedId = resultId;
        });
        elements.add(resultSlot);
        elements.add(new UIButton(0.72, 0.66, 0.16, 0.06, "Perform Fusion", this::performFusion));

        statsText = new UIText(0.62, 0.40, "Select an artifact", 0.85, UIText.TextAlign.TOP_LEFT);
        elements.add(statsText);

        messageText = new UIText(0.5, 0.03, "", 0.8);
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
        for (String id : fusionIds) if (highlightedId.equals(id)) return; // already queued
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
        for (Map.Entry<String, UIIconButton> entry : inventoryButtonsById.entrySet()) {
            UIIconButton btn = entry.getValue();
            btn.grayscale = !enabled;
            btn.selected = entry.getKey().equals(highlightedId);
            boolean queued = false;
            for (String id : fusionIds) if (entry.getKey().equals(id)) { queued = true; break; }
            btn.overlayColor = queued ? FUSION_QUEUED_OVERLAY : null;
        }

        for (int i = 0; i < fusionSlots.length; i++) {
            Artifact a = profile != null ? profile.findArtifact(fusionIds[i]) : null;
            fusionSlots[i].icon = CharacterAssets.artifactIconFor(a);
            fusionSlots[i].placeholderText = a == null ? "Empty" : null;
            fusionSlots[i].cornerLabel = a != null ? "Lv" + a.level : null;
            fusionSlots[i].selected = a != null && a.id.equals(highlightedId);
        }

        Artifact result = profile != null ? profile.findArtifact(resultId) : null;
        resultSlot.icon = CharacterAssets.artifactIconFor(result);
        resultSlot.placeholderText = result == null ? "Result" : null;
        resultSlot.cornerLabel = result != null ? "Lv" + result.level : null;
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
            if (isEquipped(profile, artifact.id)) continue; // implementation.md: can't fuse equipped artifacts
            int index = shown++;
            UIIconButton btn = new UIIconButton(
                    INVENTORY_GRID.cellCenterX(index), INVENTORY_GRID.cellCenterY(index), INVENTORY_GRID.cellSize,
                    CharacterAssets.artifactIconFor(artifact), () -> highlightedId = artifact.id);
            btn.cornerLabel = "Lv" + artifact.level;
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
