package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.GachaTables;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.decorated.Anim;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecorContext;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedElement;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedText;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.music.AudioManager;
import me.ethanchen.lwjgl3.render.CharacterAssets;
import me.ethanchen.lwjgl3.render.GachaAssets;
import me.ethanchen.lwjgl3.render.LightningRenderer;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.packets.s2c.DealerResultBroadcast;

/** Token-funded Card Dealer with server-authoritative results and unskippable reveal animation. */
public class CardDealerScreen extends DecoratedMenuScreen {
    private static final int MAX_CARDS = 10;
    private static final int MAX_EFFECT_LINES = 6;

    private static final float CARD_W = 170f;
    private static final float CARD_H = 245f;
    private static final float ICON_SIZE = 100f;
    private static final float SPAWN_X = 960f;
    private static final float SPAWN_Y = 1250f;

    private static final long UI_FADE_MS = 200L;
    private static final long PACKET_TIMEOUT_MS = 5000L;
    private static final long DEAL_CARD_MS = 600L;
    private static final long DEAL_STAGGER_MS = 80L;
    private static final long FLASH_MS = 200L;
    private static final long FLIP_BACK_MS = 75L;
    private static final long FLIP_MID_MS = 100L;
    private static final long FLIP_FACE_MS = 75L;
    private static final long FLIP_STAGGER_MS = 500L;

    private enum Phase { IDLE, FADE_UI, DEAL_IN, ROULETTE, FLASH, FLIP, RESULT }

    private final CharacterScreen parent;
    private final Random animationRng = new Random();
    private final LightningRenderer lightningRenderer = new LightningRenderer();
    private final DecoratedButton backButton;
    private final DecoratedButton dealOneButton;
    private final DecoratedButton dealTenButton;
    private final DecoratedButton dealAgainButton;
    private final DecoratedText titleText;
    private final DecoratedText tokenText;
    private final DecoratedText statusText;
    private final ArtifactHitbox[] artifactHitboxes = new ArtifactHitbox[MAX_CARDS];

    private Phase phase = Phase.IDLE;
    private long phaseStartMs;
    private int requestedCount;
    private byte[] rarities;
    private byte[] displayRarities;
    private Artifact[] artifacts;

    private int upgradeTarget = -1;
    private int arrowIndex;
    private int rouletteHops;
    private int rouletteTotalHops;
    private long nextRouletteHopMs;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher =
            new PacketDispatcher<ClientPacketWrapper>()
                    .on(DealerResultBroadcast.class,
                            w -> handleDealerResult((DealerResultBroadcast) w.packet));

    public CardDealerScreen(ClientApp app, CharacterScreen parent) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.parent = parent;

        backButton = new DecoratedButton(140, 1020, 200, 64, "Back",
                () -> app.switchMenu(parent));
        titleText = new DecoratedText(960, 1040, "Card Dealer", 2.5f);
        tokenText = new DecoratedText(960, 205, "Tokens: 0", 1.65f);
        statusText = new DecoratedText(960, 265, "", 1.35f);

        dealOneButton = new DecoratedButton(750, 105, 320, 82, "Deal 1x",
                () -> requestDeal(1));
        dealTenButton = new DecoratedButton(1170, 105, 320, 82, "Deal 10x",
                () -> requestDeal(10));
        dealOneButton.info("Token Cost: " + GachaTables.COST_1X);
        dealTenButton.info("Token Cost: " + GachaTables.COST_10X);
        dealOneButton.fontSize = 1.7f;
        dealTenButton.fontSize = 1.7f;

        dealAgainButton = new DecoratedButton(960, 105, 340, 82, "Deal Again",
                this::resetToIdle);
        dealAgainButton.fontSize = 1.6f;
        dealAgainButton.visible = false;

        addDecorated(backButton);
        addDecorated(titleText);
        addDecorated(tokenText);
        addDecorated(statusText);
        addDecorated(dealOneButton);
        addDecorated(dealTenButton);
        addDecorated(dealAgainButton);

        for (int i = 0; i < artifactHitboxes.length; i++) {
            ArtifactHitbox hitbox = new ArtifactHitbox(i);
            hitbox.visible = false;
            artifactHitboxes[i] = hitbox;
            addDecorated(hitbox);
        }
        applyUiState(0L);
    }

    @Override
    protected void updateScreen(long menuElapsedMs, long appElapsedMs) {
        updateTokenText();
        tickPhase(menuElapsedMs);
        applyUiState(menuElapsedMs);
        updateArtifactHitboxes();
    }

    private void updateTokenText() {
        PlayerProfile profile = app.getProfile();
        long tokens = profile == null ? 0L : profile.tokenBalance();
        tokenText.text = "Tokens: " + tokens;
        boolean idle = phase == Phase.IDLE;
        boolean available = idle && !app.isProfileReadOnly();
        dealOneButton.interactable = available && tokens >= GachaTables.COST_1X;
        dealTenButton.interactable = available && tokens >= GachaTables.COST_10X;
        if (idle && app.isProfileReadOnly() && statusText.text.isEmpty()) {
            statusText.text = "The Card Dealer is not available in LAN mode.";
        }
    }

    private void requestDeal(int count) {
        if (phase != Phase.IDLE) return;
        long balance = app.getProfile() == null ? 0L : app.getProfile().tokenBalance();
        long cost = GachaTables.costFor(count);
        if (balance < cost) return;

        requestedCount = count;
        rarities = null;
        displayRarities = null;
        artifacts = null;
        upgradeTarget = -1;
        statusText.text = "";
        phase = Phase.FADE_UI;
        phaseStartMs = menuElapsedMs();
        if (!app.sendDealerRequest(count)) failToIdle("Unable to contact the card dealer.");
    }

    private void tickPhase(long nowMs) {
        long elapsed = nowMs - phaseStartMs;
        switch (phase) {
            case FADE_UI:
                if (rarities != null && elapsed >= UI_FADE_MS) {
                    phase = Phase.DEAL_IN;
                    phaseStartMs = nowMs;
                } else if (elapsed >= PACKET_TIMEOUT_MS) {
                    failToIdle("Card Dealer request timed out.");
                }
                break;
            case DEAL_IN:
                if (elapsed >= dealDurationMs()) {
                    if (requestedCount == 10) enterRoulette(nowMs);
                    else enterFlip(nowMs);
                }
                break;
            case ROULETTE:
                tickRoulette(nowMs);
                break;
            case FLASH:
                if (elapsed >= FLASH_MS) {
                    displayRarities[upgradeTarget] = rarities[upgradeTarget];
                    enterFlip(nowMs);
                }
                break;
            case FLIP:
                if (elapsed >= flipDurationMs()) {
                    phase = Phase.RESULT;
                    phaseStartMs = nowMs;
                }
                break;
            default:
                break;
        }
    }

    private long dealDurationMs() {
        return DEAL_CARD_MS + Math.max(0, requestedCount - 1) * DEAL_STAGGER_MS;
    }

    private long flipDurationMs() {
        return Math.max(0, requestedCount - 1) * FLIP_STAGGER_MS
                + FLIP_BACK_MS + FLIP_MID_MS + FLIP_FACE_MS;
    }

    private void enterRoulette(long nowMs) {
        arrowIndex = 0;
        rouletteHops = 0;
        int cycles = animationRng.nextBoolean() ? 3 : 4;
        rouletteTotalHops = cycles * requestedCount + upgradeTarget;
        if (rouletteTotalHops < 32) rouletteTotalHops += requestedCount;
        nextRouletteHopMs = nowMs + rouletteIntervalMs(0);
        phase = Phase.ROULETTE;
        phaseStartMs = nowMs;
    }

    private void tickRoulette(long nowMs) {
        while (rouletteHops < rouletteTotalHops && nowMs >= nextRouletteHopMs) {
            rouletteHops++;
            arrowIndex = rouletteHops % requestedCount;
            if (rouletteHops >= rouletteTotalHops) {
                arrowIndex = upgradeTarget;
                phase = Phase.FLASH;
                phaseStartMs = nowMs;
                AudioManager.getInstance().playLightningSound();
                return;
            }
            nextRouletteHopMs += rouletteIntervalMs(rouletteHops);
        }
    }

    private long rouletteIntervalMs(int completedHops) {
        int remaining = rouletteTotalHops - completedHops;
        if (remaining > 8) return 45L + animationRng.nextInt(26);
        float progress = (8 - Math.max(0, remaining)) / 8f;
        return Math.round(90f + 330f * progress * progress);
    }

    private void enterFlip(long nowMs) {
        phase = Phase.FLIP;
        phaseStartMs = nowMs;
    }

    private void handleDealerResult(DealerResultBroadcast result) {
        if (phase != Phase.FADE_UI) return;
        if (!result.success) {
            failToIdle(result.reason == null || result.reason.isEmpty()
                    ? "Card Dealer request failed." : result.reason);
            return;
        }
        if (result.rarities == null || result.artifacts == null
                || result.rarities.length != requestedCount
                || result.artifacts.length != requestedCount) {
            failToIdle("The card dealer returned an invalid result.");
            return;
        }

        rarities = result.rarities.clone();
        artifacts = result.artifacts.clone();
        displayRarities = rarities.clone();
        if (requestedCount == 10) {
            List<Integer> eligible = new ArrayList<>();
            for (int i = 0; i < rarities.length; i++) {
                if (rarities[i] >= GachaTables.EPIC) eligible.add(i);
            }
            if (eligible.isEmpty()) {
                failToIdle("The card dealer guarantee was not fulfilled.");
                return;
            }
            upgradeTarget = eligible.get(animationRng.nextInt(eligible.size()));
            displayRarities[upgradeTarget] = (byte) (rarities[upgradeTarget] - 1);
        }
    }

    private void failToIdle(String reason) {
        phase = Phase.IDLE;
        requestedCount = 0;
        rarities = null;
        displayRarities = null;
        artifacts = null;
        upgradeTarget = -1;
        statusText.text = reason;
        phaseStartMs = menuElapsedMs();
    }

    private void resetToIdle() {
        failToIdle("");
    }

    private void applyUiState(long nowMs) {
        float staticAlpha;
        if (phase == Phase.IDLE) {
            staticAlpha = 1f;
        } else if (phase == Phase.FADE_UI) {
            staticAlpha = 1f - Anim.clamp01((nowMs - phaseStartMs) / (float) UI_FADE_MS);
        } else {
            staticAlpha = 0f;
        }

        titleText.alpha = staticAlpha;
        tokenText.alpha = staticAlpha;
        statusText.alpha = staticAlpha;
        dealOneButton.alpha = staticAlpha;
        dealTenButton.alpha = staticAlpha;
        backButton.alpha = phase == Phase.RESULT ? 1f : staticAlpha;

        boolean result = phase == Phase.RESULT;
        dealAgainButton.visible = result;
        dealAgainButton.alpha = result
                ? Anim.smoothstep(Anim.clamp01((nowMs - phaseStartMs) / 180f)) : 0f;
        dealAgainButton.interactable = result && dealAgainButton.alpha > 0.55f;
    }

    private void updateArtifactHitboxes() {
        for (int i = 0; i < artifactHitboxes.length; i++) {
            ArtifactHitbox hitbox = artifactHitboxes[i];
            boolean faceUp = artifacts != null && i < requestedCount && isFaceUp(i);
            hitbox.visible = faceUp;
            hitbox.focusable = false;
            Artifact artifact = faceUp ? artifacts[i] : null;
            if (hitbox.artifact != artifact) {
                hitbox.artifact = artifact;
                hitbox.info(artifact == null ? null : artifact.describeForUi(MAX_EFFECT_LINES));
            }
            hitbox.moveTo(targetX(i), targetY(i));
            if (!faceUp) hitbox.hovered = false;
        }
    }

    private boolean isFaceUp(int index) {
        if (phase == Phase.RESULT) return true;
        if (phase != Phase.FLIP) return false;
        long local = menuElapsedMs() - phaseStartMs - index * FLIP_STAGGER_MS;
        return local >= FLIP_BACK_MS + FLIP_MID_MS;
    }

    @Override
    protected void renderForeground(DecorContext ctx) {
        if (artifacts == null || displayRarities == null) return;
        if (phase == Phase.DEAL_IN) {
            drawDealingCards(ctx);
        } else if (phase == Phase.ROULETTE || phase == Phase.FLASH) {
            drawSettledBacks(ctx);
            drawArrow(ctx, phase == Phase.FLASH ? upgradeTarget : arrowIndex);
            if (phase == Phase.FLASH) {
                drawFlash(ctx, upgradeTarget);
                drawLightning(ctx, upgradeTarget);
            }
        } else if (phase == Phase.FLIP || phase == Phase.RESULT) {
            drawFlippingCards(ctx);
        }
    }

    private void drawDealingCards(DecorContext ctx) {
        long elapsed = menuElapsedMs() - phaseStartMs;
        for (int i = 0; i < requestedCount; i++) {
            float t = Anim.easeOutCubic(Anim.clamp01(
                    (elapsed - i * DEAL_STAGGER_MS) / (float) DEAL_CARD_MS));
            if (t <= 0f) continue;
            float x = Anim.lerp(SPAWN_X, targetX(i), t);
            float y = Anim.lerp(SPAWN_Y, targetY(i), t);
            drawCard(ctx, displayRarities[i], GachaAssets.BACK, x, y, 1f);
        }
    }

    private void drawSettledBacks(DecorContext ctx) {
        for (int i = 0; i < requestedCount; i++) {
            drawCard(ctx, displayRarities[i], GachaAssets.BACK, targetX(i), targetY(i), 1f);
        }
    }

    private void drawFlippingCards(DecorContext ctx) {
        long elapsed = phase == Phase.RESULT ? Long.MAX_VALUE : menuElapsedMs() - phaseStartMs;
        for (int i = 0; i < requestedCount; i++) {
            long local = elapsed - i * FLIP_STAGGER_MS;
            int frame = GachaAssets.BACK;
            float xScale = 1f;
            if (local >= FLIP_BACK_MS + FLIP_MID_MS + FLIP_FACE_MS) {
                frame = GachaAssets.FACE;
            } else if (local >= FLIP_BACK_MS + FLIP_MID_MS) {
                frame = GachaAssets.FACE;
                xScale = Anim.clamp01((local - FLIP_BACK_MS - FLIP_MID_MS)
                        / (float) FLIP_FACE_MS);
            } else if (local >= FLIP_BACK_MS) {
                frame = GachaAssets.MID_FLIP;
                float p = (local - FLIP_BACK_MS) / (float) FLIP_MID_MS;
                xScale = 0.12f + 0.35f * (float) Math.sin(Math.PI * p);
            } else if (local >= 0L) {
                xScale = 1f - 0.88f * Anim.clamp01(local / (float) FLIP_BACK_MS);
            }
            drawCard(ctx, rarities[i], frame, targetX(i), targetY(i), xScale);
            if (local >= FLIP_BACK_MS + FLIP_MID_MS) drawArtifactIcon(ctx, i);
        }
    }

    private void drawCard(DecorContext ctx, byte rarity, int frame,
                          float centerX, float centerY, float xScale) {
        Texture texture = GachaAssets.cardTexture(rarity, frame);
        float width = viewport.toScreenW((float) DesignUi.nw(CARD_W * Math.max(0.08f, xScale)));
        float height = viewport.toScreenH((float) DesignUi.nh(CARD_H));
        float x = viewport.toScreenX((float) DesignUi.nx(centerX)) - width * 0.5f;
        float y = viewport.toScreenY((float) DesignUi.ny(centerY)) - height * 0.5f;
        ctx.sprites.begin();
        ctx.sprites.setColor(Color.WHITE);
        ctx.sprites.draw(texture, x, y, width, height);
        ctx.sprites.end();
    }

    private void drawArtifactIcon(DecorContext ctx, int index) {
        Artifact artifact = artifacts[index];
        Texture icon = artifact == null ? null : CharacterAssets.artifactIconFor(artifact);
        if (icon == null) return;
        float size = viewport.toScreenW((float) DesignUi.nw(ICON_SIZE));
        float x = viewport.toScreenX((float) DesignUi.nx(targetX(index))) - size * 0.5f;
        float y = viewport.toScreenY((float) DesignUi.ny(targetY(index))) - size * 0.5f;
        ctx.sprites.begin();
        ctx.sprites.setColor(Color.WHITE);
        ctx.sprites.draw(icon, x, y, size, size);
        ctx.sprites.end();
    }

    private void drawArrow(DecorContext ctx, int index) {
        float cx = viewport.toScreenX((float) DesignUi.nx(targetX(index)));
        float cardTop = viewport.toScreenY((float) DesignUi.ny(targetY(index) + CARD_H * 0.5f));
        float halfW = viewport.toScreenW((float) DesignUi.nw(18f));
        float height = viewport.toScreenH((float) DesignUi.nh(28f));
        float gap = viewport.toScreenH((float) DesignUi.nh(18f));
        ctx.shapes.begin(ShapeRenderer.ShapeType.Filled);
        ctx.shapes.setColor(Color.WHITE);
        ctx.shapes.triangle(cx - halfW, cardTop + gap + height,
                cx + halfW, cardTop + gap + height, cx, cardTop + gap);
        ctx.shapes.end();
    }

    private void drawFlash(DecorContext ctx, int index) {
        float width = viewport.toScreenW((float) DesignUi.nw(CARD_W));
        float height = viewport.toScreenH((float) DesignUi.nh(CARD_H));
        float x = viewport.toScreenX((float) DesignUi.nx(targetX(index))) - width * 0.5f;
        float y = viewport.toScreenY((float) DesignUi.ny(targetY(index))) - height * 0.5f;
        ctx.shapes.begin(ShapeRenderer.ShapeType.Filled);
        ctx.shapes.setColor(Color.WHITE);
        ctx.shapes.rect(x, y, width, height);
        ctx.shapes.end();
    }

    private void drawLightning(DecorContext ctx, int index) {
        float targetX = viewport.toScreenX((float) DesignUi.nx(targetX(index)));
        float targetY = viewport.toScreenY((float) DesignUi.ny(targetY(index)));
        long elapsed = menuElapsedMs() - phaseStartMs;
        lightningRenderer.renderFromTop(
                ctx.shapes, targetX, targetY, elapsed, 0x434152444445414cL ^ index);
    }

    private float targetX(int index) {
        if (requestedCount == 1) return 960f;
        return 500f + (index % 5) * 230f;
    }

    private float targetY(int index) {
        if (requestedCount == 1) return 590f;
        return index < 5 ? 690f : 405f;
    }

    @Override
    protected boolean interceptInput() {
        return phase != Phase.IDLE && phase != Phase.RESULT;
    }

    @Override
    protected boolean pauseStickFocus() {
        return phase != Phase.IDLE && phase != Phase.RESULT;
    }

    @Override
    protected void onEscPressed() {
        app.switchMenu(parent);
    }

    @Override
    public void passClientPacket(ClientPacketWrapper wrapper) {
        if (wrapper.packet instanceof DealerResultBroadcast) {
            dispatcher.dispatch(wrapper);
            return;
        }
        parent.passClientPacket(wrapper);
    }

    private final class ArtifactHitbox extends DecoratedElement {
        private final int index;
        private Artifact artifact;

        private ArtifactHitbox(int index) {
            super(DesignUi.nx(0), DesignUi.ny(0), DesignUi.nw(ICON_SIZE), DesignUi.nh(ICON_SIZE));
            this.index = index;
        }

        private void moveTo(float designX, float designY) {
            centerX = DesignUi.nx(designX);
            centerY = DesignUi.ny(designY);
        }

        @Override
        public void renderDecorated(DecorContext ctx) {
            if (!visible || artifact == null) {
                hovered = false;
                return;
            }
            float mouseX = Gdx.input.getX();
            float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
            hovered = mouseX >= pxX() && mouseX <= pxX() + pxW()
                    && mouseY >= pxY() && mouseY <= pxY() + pxH();
        }

        @Override
        public boolean isFocusable() {
            return false;
        }

        @Override
        public String toString() {
            return "CardArtifactHitbox[" + index + "]";
        }
    }
}
