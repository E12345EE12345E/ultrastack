package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.Align;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.ArtifactFusion;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.decorated.Anim;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecorContext;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedGrid;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedPager;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedSlot;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedText;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIFont;
import me.ethanchen.lwjgl3.render.CharacterAssets;
import me.ethanchen.lwjgl3.render.shader.ShockwaveRenderer;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.packets.s2c.FusionResultBroadcast;

/**
 * Decorated artifact fusion: five floating reference slots, a centered inventory grid, and a
 * skippable converge / shockwave / reveal animation. The server fuses instantly; this screen
 * only animates the already-committed result.
 */
public class FusionScreen extends DecoratedMenuScreen {
    private static final int INV_COLUMNS = 14;
    private static final int INV_ROWS = 3;
    private static final float INV_CELL = 96f;
    private static final float INV_GAP = 6f;
    private static final int MAX_EFFECT_LINES = 6;

    private static final float SLOT_SIZE = 120f;
    private static final float SLOT_Y = 600f;
    private static final float[] SLOT_X = {640f, 800f, 960f, 1120f, 1280f};
    private static final long SLOT_BOX_FADE_MS = 180L;

    private static final float POINT_A_X = 820f;
    private static final float POINT_A_Y = 800f;
    private static final float RESULT_SIZE = 140f;
    private static final float OUTWARD = 140f;

    private static final float STAT_X = 950f;
    private static final float STAT_Y0 = 880f;
    private static final float STAT_LINE_H = 36f;
    private static final float STAT_FONT = 1.35f;

    private static final long CONVERGE_MS = 900L;
    private static final long IMPACT_SCALE_MS = 250L;
    private static final long LINE_STAGGER_MS = 140L;
    private static final long LINE_FADE_MS = 180L;
    private static final long REVEAL_HOLD_MS = 600L;
    private static final long FADE_OUT_MS = 350L;
    private static final float FUSE_FADE_S = 0.18f;

    private enum Phase { IDLE, CONVERGE, IMPACT, REVEAL, FADE_OUT }

    private final CharacterScreen parent;
    private final Supplier<Boolean> charactersEnabled;

    private final DecoratedSlot[] fusionSlots = new DecoratedSlot[5];
    private final DecoratedButton addBtn;
    private final DecoratedButton removeBtn;
    private final DecoratedButton fuseButton;
    private final DecoratedText reasonText;
    private final DecoratedGrid inventoryGrid;
    private final DecoratedPager paging;

    private final String[] fusionIds = new String[5];
    private String highlightedId;
    private int cachedItemCount = -1;

    private Phase phase = Phase.IDLE;
    private long phaseStartMs;
    private boolean skipRequested;
    private boolean shockwaveSpawned;
    private boolean capturingShockwave;

    private final Flyer[] flying = new Flyer[5];
    private Artifact pendingResult;
    private String pendingFailureReason;
    private String pendingResultId;

    private ShockwaveRenderer shockwave;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = new PacketDispatcher<ClientPacketWrapper>()
            .on(FusionResultBroadcast.class, w -> handleFusionResult((FusionResultBroadcast) w.packet));

    public FusionScreen(ClientApp app, CharacterScreen parent, Supplier<Boolean> charactersEnabled) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.parent = parent;
        this.charactersEnabled = charactersEnabled;

        addDecorated(new DecoratedButton(140, 1020, 200, 64, "Back",
                () -> app.switchMenu(parent)));
        addDecorated(new DecoratedText(960, 1040, "Artifact Fusion", 2.5f));

        fuseButton = new DecoratedButton(960, 800, 320, 90, "Fusion", this::performFusion);
        fuseButton.fontSize = 2.0f;
        fuseButton.alpha = 0f;
        fuseButton.interactable = false;
        fuseButton.focusable = false;
        addDecorated(fuseButton);

        reasonText = new DecoratedText(960, 800, "", 1.4f);
        addDecorated(reasonText);

        for (int i = 0; i < fusionSlots.length; i++) {
            DecoratedSlot slot = new DecoratedSlot(SLOT_X[i], SLOT_Y, SLOT_SIZE);
            slot.focusable = true;
            slot.action = () -> {
                if (slot.boundId != null) highlightArtifact(slot.boundId);
            };
            slot.secondaryAction = () -> {
                if (slot.boundId != null) quickToggleFusion(slot.boundId);
            };
            fusionSlots[i] = slot;
            addDecorated(slot);
        }

        addBtn = new DecoratedButton(740, 430, 260, 56, "Add to Fusion", this::addToFusion);
        removeBtn = new DecoratedButton(1060, 430, 300, 56, "Remove from Fusion", this::removeFromFusion);
        addBtn.fontSize = 1.25f;
        removeBtn.fontSize = 1.25f;
        addBtn.outlineDesignPx = 3f;
        removeBtn.outlineDesignPx = 3f;
        addDecorated(addBtn);
        addDecorated(removeBtn);

        inventoryGrid = DecoratedGrid.centered(960, 340, INV_COLUMNS, INV_ROWS, INV_CELL, INV_GAP);
        inventoryGrid.onPageChange(this::changeInventoryPage);
        addDecorated(inventoryGrid);

        paging = new DecoratedPager(INV_COLUMNS, INV_ROWS, 960, 40, this::changeInventoryPage);
        addDecorated(paging);

        refresh();
    }

    @Override
    protected void updateScreen(long menuElapsedMs, long appElapsedMs) {
        float dtS = Math.max(0f, Gdx.graphics.getDeltaTime());
        if (shockwave != null) shockwave.update(dtS);

        if (phase != Phase.CONVERGE && phase != Phase.IMPACT) {
            for (int i = 0; i < fusionSlots.length; i++) {
                fusionSlots[i].offsetYDesign = 12f * (float) Math.sin(menuElapsedMs / 620.0 + i * 0.9);
            }
        }

        tickPhase(menuElapsedMs);
        refresh();
        tickFuseButton(dtS);
        applySlotBoxAlpha(menuElapsedMs);
    }

    private void applySlotBoxAlpha(long menuElapsedMs) {
        float a = 1f;
        long elapsed = menuElapsedMs - phaseStartMs;
        if (phase == Phase.CONVERGE) {
            a = 1f - Anim.clamp01(elapsed / (float) SLOT_BOX_FADE_MS);
        } else if (phase == Phase.IMPACT || phase == Phase.REVEAL) {
            a = 0f;
        } else if (phase == Phase.FADE_OUT) {
            a = Anim.clamp01(elapsed / (float) FADE_OUT_MS);
        }
        for (DecoratedSlot slot : fusionSlots) {
            slot.alpha = a;
            slot.visible = true;
        }
    }

    private void tickFuseButton(float dtS) {
        boolean show = phase == Phase.IDLE && currentValidateReason() == null && allSlotsFilled();
        float target = show ? 1f : 0f;
        float step = dtS / FUSE_FADE_S;
        if (fuseButton.alpha < target) fuseButton.alpha = Math.min(target, fuseButton.alpha + step);
        else fuseButton.alpha = Math.max(target, fuseButton.alpha - step);
        fuseButton.interactable = show && fuseButton.alpha > 0.55f;
        fuseButton.focusable = fuseButton.interactable;
        fuseButton.visible = fuseButton.alpha > 0.02f;

        String reason = currentValidateReason();
        boolean showReason = phase == Phase.IDLE && allSlotsFilled() && reason != null;
        reasonText.text = showReason ? reason : "";
        reasonText.visible = showReason;
        reasonText.alpha = showReason ? 1f : 0f;
    }

    private void tickPhase(long menuElapsedMs) {
        if (phase == Phase.IDLE) return;
        long elapsed = menuElapsedMs - phaseStartMs;

        if (skipRequested && phase != Phase.FADE_OUT) {
            tryFinishSkip();
            if (phase == Phase.IDLE || phase == Phase.FADE_OUT) return;
        }

        if (phase == Phase.CONVERGE && elapsed >= CONVERGE_MS) {
            enterImpact(menuElapsedMs);
        } else if (phase == Phase.IMPACT) {
            if (pendingFailureReason != null) {
                failToIdle();
                return;
            }
            if (pendingResult == null) return;
            if (!shockwaveSpawned) {
                spawnImpactShockwave();
                phaseStartMs = menuElapsedMs;
                elapsed = 0L;
            }
            if (elapsed >= IMPACT_SCALE_MS) enterReveal(menuElapsedMs);
        } else if (phase == Phase.REVEAL) {
            long revealDur = revealDurationMs() + REVEAL_HOLD_MS;
            if (elapsed >= revealDur) enterFadeOut(menuElapsedMs);
        } else if (phase == Phase.FADE_OUT) {
            if (elapsed >= FADE_OUT_MS) finishNow();
        }
    }

    private long revealDurationMs() {
        int n = resultLines().length;
        if (n <= 0) return LINE_FADE_MS;
        return (n - 1) * LINE_STAGGER_MS + LINE_FADE_MS;
    }

    private void enterImpact(long menuElapsedMs) {
        phase = Phase.IMPACT;
        phaseStartMs = menuElapsedMs;
        if (pendingFailureReason != null) {
            failToIdle();
            return;
        }
        if (pendingResult != null) spawnImpactShockwave();
    }

    private void enterReveal(long menuElapsedMs) {
        phase = Phase.REVEAL;
        phaseStartMs = menuElapsedMs;
    }

    private void enterFadeOut(long menuElapsedMs) {
        phase = Phase.FADE_OUT;
        phaseStartMs = menuElapsedMs;
    }

    private void spawnImpactShockwave() {
        if (shockwaveSpawned) return;
        shockwaveSpawned = true;
        if (shockwave == null) shockwave = new ShockwaveRenderer();
        float sx = viewport.toScreenX((float) DesignUi.nx(POINT_A_X));
        float sy = viewport.toScreenY((float) DesignUi.ny(POINT_A_Y));
        shockwave.spawn(sx, sy, ShockwaveRenderer.AMPLITUDE_NORMAL, ShockwaveRenderer.SPEED_FAST);
    }

    private void changeInventoryPage(int delta) {
        if (phase != Phase.IDLE) return;
        if (delta < 0) paging.prev(cachedItemCount);
        else paging.next(cachedItemCount);
    }

    private void highlightArtifact(String artifactId) {
        if (phase != Phase.IDLE) return;
        highlightedId = artifactId;
        PlayerProfile profile = app.getProfile();
        if (profile == null) return;
        List<Artifact> items = fusableInventory(profile);
        for (int i = 0; i < items.size(); i++) {
            if (artifactId.equals(items.get(i).id)) {
                paging.showIndex(i, items.size());
                return;
            }
        }
    }

    private List<Artifact> fusableInventory(PlayerProfile profile) {
        List<Artifact> items = new ArrayList<>();
        for (Artifact artifact : profile.inventory) {
            if (isEquipped(profile, artifact.id)) continue;
            if (pendingResultId != null && pendingResultId.equals(artifact.id)) continue;
            items.add(artifact);
        }
        return items;
    }

    private void addToFusion() {
        if (phase != Phase.IDLE) return;
        PlayerProfile profile = app.getProfile();
        if (profile == null || highlightedId == null) return;
        if (isEquipped(profile, highlightedId)) return;
        for (String id : fusionIds) if (highlightedId.equals(id)) return;
        for (int i = 0; i < fusionIds.length; i++) {
            if (fusionIds[i] == null) { fusionIds[i] = highlightedId; return; }
        }
        reasonText.text = "All 5 fusion slots are full.";
        reasonText.visible = true;
        reasonText.alpha = 1f;
    }

    private void removeFromFusion() {
        if (phase != Phase.IDLE) return;
        if (highlightedId == null) return;
        for (int i = 0; i < fusionIds.length; i++) {
            if (highlightedId.equals(fusionIds[i])) { fusionIds[i] = null; return; }
        }
    }

    private void quickToggleFusion(String artifactId) {
        if (phase != Phase.IDLE) return;
        highlightArtifact(artifactId);
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
        reasonText.text = "All 5 fusion slots are full.";
        reasonText.visible = true;
        reasonText.alpha = 1f;
    }

    private void performFusion() {
        if (phase != Phase.IDLE) return;
        if (currentValidateReason() != null || !allSlotsFilled()) return;
        PlayerProfile profile = app.getProfile();
        if (profile == null) return;

        for (int i = 0; i < flying.length; i++) {
            Artifact a = profile.findArtifact(fusionIds[i]);
            Flyer f = new Flyer();
            f.artifact = a;
            f.icon = CharacterAssets.artifactIconFor(a);
            f.startX = SLOT_X[i];
            f.startY = SLOT_Y + fusionSlots[i].offsetYDesign;
            flying[i] = f;
            fusionIds[i] = null;
        }

        pendingResult = null;
        pendingFailureReason = null;
        pendingResultId = null;
        skipRequested = false;
        shockwaveSpawned = false;
        app.sendFusionRequest(idsSnapshot(flying));
        phase = Phase.CONVERGE;
        phaseStartMs = menuElapsedMs();
    }

    private static String[] idsSnapshot(Flyer[] flyers) {
        String[] ids = new String[flyers.length];
        for (int i = 0; i < flyers.length; i++) {
            ids[i] = flyers[i] != null && flyers[i].artifact != null ? flyers[i].artifact.id : null;
        }
        return ids;
    }

    private void handleFusionResult(FusionResultBroadcast p) {
        if (!p.success) {
            pendingFailureReason = p.reason != null ? p.reason : "Fusion failed.";
            pendingResult = null;
            pendingResultId = null;
            tryFinishSkip();
            if (phase != Phase.IDLE) failToIdle();
            return;
        }
        pendingFailureReason = null;
        pendingResult = p.result;
        pendingResultId = p.result != null ? p.result.id : null;
        tryFinishSkip();
    }

    @Override
    protected boolean interceptInput() {
        if (phase == Phase.IDLE) return false;
        if (canSkip()) skipAnimation();
        return true;
    }

    private boolean canSkip() {
        return shockwaveSpawned && phase != Phase.IDLE;
    }

    @Override
    protected boolean pauseStickFocus() {
        return phase != Phase.IDLE;
    }

    private void skipAnimation() {
        if (!canSkip()) return;
        skipRequested = true;
        tryFinishSkip();
    }

    private void tryFinishSkip() {
        if (!skipRequested) return;
        if (pendingFailureReason != null) {
            failToIdle();
            return;
        }
        if (pendingResult == null) return;
        if (phase == Phase.FADE_OUT) {
            finishNow();
            return;
        }
        enterFadeOut(menuElapsedMs());
    }

    private void failToIdle() {
        if (phase == Phase.IDLE) return;
        for (int i = 0; i < fusionSlots.length; i++) {
            fusionSlots[i].visible = true;
            fusionSlots[i].alpha = 1f;
            fusionIds[i] = flying[i] != null && flying[i].artifact != null ? flying[i].artifact.id : null;
            flying[i] = null;
        }
        String reason = pendingFailureReason != null ? pendingFailureReason : "Fusion failed.";
        reasonText.text = reason;
        reasonText.visible = true;
        reasonText.alpha = 1f;
        pendingResult = null;
        pendingResultId = null;
        skipRequested = false;
        shockwaveSpawned = false;
        phase = Phase.IDLE;
    }

    private void finishNow() {
        Artifact result = pendingResult;
        pendingResultId = null;
        pendingResult = null;
        pendingFailureReason = null;
        skipRequested = false;
        shockwaveSpawned = false;
        for (int i = 0; i < fusionSlots.length; i++) {
            fusionSlots[i].visible = true;
            fusionSlots[i].alpha = 1f;
            fusionIds[i] = null;
            flying[i] = null;
        }
        phase = Phase.IDLE;
        if (result != null) highlightArtifact(result.id);
    }

    private boolean allSlotsFilled() {
        for (String id : fusionIds) if (id == null) return false;
        return true;
    }

    private String currentValidateReason() {
        if (!allSlotsFilled()) return "Select exactly 5 artifacts to fuse.";
        PlayerProfile profile = app.getProfile();
        if (profile == null) return "Select exactly 5 artifacts to fuse.";
        List<Artifact> inputs = new ArrayList<>(5);
        for (String id : fusionIds) {
            Artifact a = profile.findArtifact(id);
            if (a == null) return "Select exactly 5 artifacts to fuse.";
            inputs.add(a);
        }
        return ArtifactFusion.validate(inputs);
    }

    private boolean isEquipped(PlayerProfile profile, String id) {
        return id.equals(profile.equippedArtifactIds[0]) || id.equals(profile.equippedArtifactIds[1]);
    }

    private void refresh() {
        PlayerProfile profile = app.getProfile();
        boolean enabled = Boolean.TRUE.equals(charactersEnabled.get());
        boolean idle = phase == Phase.IDLE;

        addBtn.interactable = idle;
        removeBtn.interactable = idle;

        refreshInventoryPage(profile, enabled);

        for (int i = 0; i < fusionSlots.length; i++) {
            DecoratedSlot slot = fusionSlots[i];
            if (!idle) {
                slot.clearSlot("Empty");
                continue;
            }
            Artifact a = profile != null ? profile.findArtifact(fusionIds[i]) : null;
            if (a != null) {
                slot.showArtifact(CharacterAssets.artifactIconFor(a), a);
                slot.info(a.describeForUi(MAX_EFFECT_LINES));
            } else {
                slot.clearSlot("Empty");
            }
            slot.selected = a != null && a.id.equals(highlightedId);
        }
    }

    private void refreshInventoryPage(PlayerProfile profile, boolean enabled) {
        List<Artifact> items = profile != null ? fusableInventory(profile) : List.of();
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
                slot.secondaryAction = () -> quickToggleFusion(artifact.id);
                slot.grayscale = !enabled;
                slot.selected = artifact.id.equals(highlightedId);
                boolean queued = false;
                for (String id : fusionIds) if (artifact.id.equals(id)) { queued = true; break; }
                slot.overlayColor = queued ? DecoratedSlot.OVERLAY_FUSION_QUEUED : null;
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
    protected void beginFrame(DecorContext ctx) {
        capturingShockwave = shockwave != null && shockwave.hasActive();
        if (capturingShockwave) shockwave.begin();
    }

    @Override
    protected void endFrame(DecorContext ctx) {
        if (capturingShockwave && shockwave != null) {
            shockwave.end();
            capturingShockwave = false;
        }
    }

    @Override
    protected void renderForeground(DecorContext ctx) {
        if (phase == Phase.IDLE) return;
        float elapsed = menuElapsedMs() - phaseStartMs;
        float fade = 1f;
        if (phase == Phase.FADE_OUT) fade = 1f - Anim.clamp01(elapsed / (float) FADE_OUT_MS);

        if (phase == Phase.CONVERGE || (phase == Phase.IMPACT && pendingResult == null)) {
            float t = phase == Phase.CONVERGE
                    ? Anim.clamp01(elapsed / (float) CONVERGE_MS)
                    : 1f;
            if (skipRequested) t = 1f;
            float te = Anim.easeInCubic(t);
            for (Flyer f : flying) {
                if (f == null || f.icon == null) continue;
                float[] xy = bezier(f.startX, f.startY, te);
                drawFlyingIcon(ctx, f.icon, xy[0], xy[1], SLOT_SIZE, fade);
            }
        }

        if (pendingResult != null && (phase == Phase.IMPACT || phase == Phase.REVEAL || phase == Phase.FADE_OUT)) {
            float scaleT = 1f;
            if (phase == Phase.IMPACT) {
                scaleT = Anim.easeOutCubic(Anim.clamp01(elapsed / (float) IMPACT_SCALE_MS));
            }
            Texture icon = CharacterAssets.artifactIconFor(pendingResult);
            if (icon != null) {
                drawFlyingIcon(ctx, icon, POINT_A_X, POINT_A_Y, RESULT_SIZE * Math.max(0.15f, scaleT), fade);
            }
        }

        if (pendingResult != null && (phase == Phase.REVEAL || phase == Phase.FADE_OUT)) {
            drawStatLines(ctx, elapsed, fade);
        }
    }

    private void drawStatLines(DecorContext ctx, float elapsed, float fade) {
        String[] lines = resultLines();
        float[] saved = UIFont.saveAndSetScale(ctx.font, STAT_FONT);
        boolean prevMarkup = ctx.font.getData().markupEnabled;
        ctx.font.getData().markupEnabled = true;
        ctx.sprites.begin();
        for (int i = 0; i < lines.length; i++) {
            float lineT = 1f;
            if (phase == Phase.REVEAL) {
                lineT = Anim.clamp01((elapsed - i * LINE_STAGGER_MS) / (float) LINE_FADE_MS);
            }
            if (lineT <= 0f) continue;
            float a = Anim.clamp01(lineT) * fade;
            GlyphLayout layout = new GlyphLayout();
            layout.setText(ctx.font, lines[i], Color.WHITE, MenuScreen.toScreenWidth((float) DesignUi.nw(520f)),
                    Align.left, true);
            float x = viewport.toScreenX((float) DesignUi.nx(STAT_X));
            float y = viewport.toScreenY((float) DesignUi.ny(STAT_Y0 - i * STAT_LINE_H));
            ctx.font.setColor(1f, 1f, 1f, a);
            ctx.font.draw(ctx.sprites, layout, x, y);
        }
        ctx.sprites.end();
        ctx.font.setColor(Color.WHITE);
        ctx.font.getData().markupEnabled = prevMarkup;
        UIFont.restoreScale(ctx.font, saved);
    }

    private String[] resultLines() {
        if (pendingResult == null) return new String[0];
        String block = pendingResult.describeForUi(MAX_EFFECT_LINES);
        if (block == null || block.isEmpty()) return new String[0];
        return block.split("\n");
    }

    private void drawFlyingIcon(DecorContext ctx, Texture icon, float designX, float designY,
                                float designSize, float alpha) {
        float size = viewport.toScreenW((float) DesignUi.nw(designSize));
        float x = viewport.toScreenX((float) DesignUi.nx(designX)) - size * 0.5f;
        float y = viewport.toScreenY((float) DesignUi.ny(designY)) - size * 0.5f;
        ctx.sprites.begin();
        ctx.sprites.setColor(1f, 1f, 1f, Anim.clamp01(alpha));
        ctx.sprites.draw(icon, x, y, size, size);
        ctx.sprites.setColor(Color.WHITE);
        ctx.sprites.end();
    }

    private static float[] bezier(float startX, float startY, float t) {
        float dx = startX - POINT_A_X;
        float dy = startY - POINT_A_Y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) {
            dx = 0f;
            dy = 1f;
            len = 1f;
        }
        float cx = startX + dx / len * OUTWARD;
        float cy = startY + dy / len * OUTWARD;
        float u = 1f - t;
        float x = u * u * startX + 2f * u * t * cx + t * t * POINT_A_X;
        float y = u * u * startY + 2f * u * t * cy + t * t * POINT_A_Y;
        return new float[] {x, y};
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

    @Override
    public void dispose() {
        if (shockwave != null) {
            shockwave.dispose();
            shockwave = null;
        }
        super.dispose();
    }

    private static final class Flyer {
        Artifact artifact;
        Texture icon;
        float startX;
        float startY;
    }
}
