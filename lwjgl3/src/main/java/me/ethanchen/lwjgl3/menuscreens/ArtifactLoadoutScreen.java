package me.ethanchen.lwjgl3.menuscreens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

import me.ethanchen.game.board.Piece;
import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.ArtifactEffect;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.AspectLockedViewport;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.lwjgl3.render.BoardRenderer;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.server.ActiveLoadout;

/**
 * Read-only summary of equipped artifact bonuses per tetromino (plus a MISC card for
 * non-piece-specific bonuses). Character passives are intentionally excluded.
 */
public class ArtifactLoadoutScreen extends AspectLockedMenuScreen {
    private static final byte[] PIECE_TYPES = {
        Piece.I, Piece.J, Piece.L, Piece.O, Piece.S, Piece.T, Piece.Z
    };
    private static final int PIECE_COUNT = PIECE_TYPES.length;
    private static final int CARD_COUNT = PIECE_COUNT + 1; // + MISC
    private static final int MISC_INDEX = PIECE_COUNT;
    private static final int COLS = 4;
    private static final int ROWS = 2;

    private static final float MARGIN_X = 48f;
    private static final float GAP_X = 28f;
    private static final float GAP_Y = 28f;
    private static final float GRID_TOP = 940f;
    private static final float GRID_BOTTOM = 80f;
    private static final float CARD_W =
            (DesignUi.DESIGN_W - 2f * MARGIN_X - (COLS - 1) * GAP_X) / COLS;
    private static final float CARD_H =
            (GRID_TOP - GRID_BOTTOM - (ROWS - 1) * GAP_Y) / ROWS;

    private static final float PREVIEW_OFFSET_Y = 130f;
    private static final float STATS_OFFSET_Y = 220f;
    private static final float MISC_LABEL_OFFSET_Y = 130f;
    private static final float TILE_SIZE_DESIGN = 36f;
    private static final float STATS_TEXT_SIZE = 1.15f;
    private static final float MISC_LABEL_SIZE = 2.0f;

    private final CharacterScreen parent;
    private final UIText[] statsTexts = new UIText[CARD_COUNT];

    public ArtifactLoadoutScreen(ClientApp app, CharacterScreen parent) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.parent = parent;

        elements.add(new UIText(DesignUi.nx(960), DesignUi.ny(1015), "Artifact Loadout Bonuses", 2.5));
        elements.add(new UIButton(
                DesignUi.nx(140), DesignUi.ny(1020), DesignUi.nw(200), DesignUi.nh(64),
                "Back", () -> app.switchMenu(parent)));

        elements.add(new UIText(
                DesignUi.nx(cardCenterX(MISC_INDEX)),
                DesignUi.ny(cardTop(MISC_INDEX) - MISC_LABEL_OFFSET_Y),
                "MISC", MISC_LABEL_SIZE));

        for (int i = 0; i < CARD_COUNT; i++) {
            statsTexts[i] = new UIText(
                    DesignUi.nx(cardLeft(i) + 28),
                    DesignUi.ny(cardTop(i) - STATS_OFFSET_Y),
                    "", STATS_TEXT_SIZE, UIText.TextAlign.TOP_LEFT);
            elements.add(statsTexts[i]);
        }

        refresh();
    }

    @Override
    protected void drawDesignDecorations() {
        float thickness = Math.max(2f, viewport.scale * 2.5f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.WHITE);
        for (int i = 0; i < CARD_COUNT; i++) {
            float left = viewport.toScreenX((float) DesignUi.nx(cardLeft(i)));
            float right = viewport.toScreenX((float) DesignUi.nx(cardLeft(i) + CARD_W));
            float bottom = viewport.toScreenY((float) DesignUi.ny(cardBottom(i)));
            float top = viewport.toScreenY((float) DesignUi.ny(cardTop(i)));
            shapes.rect(left, bottom, right - left, thickness);
            shapes.rect(left, top - thickness, right - left, thickness);
            shapes.rect(left, bottom, thickness, top - bottom);
            shapes.rect(right - thickness, bottom, thickness, top - bottom);
        }
        shapes.end();
        shapes.setColor(Color.WHITE);
    }

    @Override
    public void render() {
        super.render();
        viewport.update();
        AspectLockedViewport.push(viewport);
        try {
            renderPiecePreviews();
        } finally {
            AspectLockedViewport.pop();
        }
    }

    private void renderPiecePreviews() {
        float tileSize = viewport.toScreenW((float) DesignUi.nw(TILE_SIZE_DESIGN));
        BoardRenderer renderer = BoardRenderer.getInstance();
        sprites.begin();

        for (int i = 0; i < PIECE_COUNT; i++) {
            byte type = PIECE_TYPES[i];
            Piece p = Piece.defaultPiece(type);
            Vector2[] tiles = p.tiles;
            byte[] states = p.tileconnectionstates;
            Vector2 loc = p.location;

            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (Vector2 t : tiles) {
                float wx = loc.x + t.x;
                float wy = loc.y + t.y;
                if (wx < minX) minX = wx;
                if (wx > maxX) maxX = wx;
                if (wy < minY) minY = wy;
                if (wy > maxY) maxY = wy;
            }

            float colCenterX = viewport.toScreenX((float) DesignUi.nx(cardCenterX(i)));
            float centerY = viewport.toScreenY((float) DesignUi.ny(cardTop(i) - PREVIEW_OFFSET_Y));
            float pieceW = (maxX - minX + 1) * tileSize;
            float pieceH = (maxY - minY + 1) * tileSize;
            float originX = colCenterX - pieceW * 0.5f - minX * tileSize;
            float originY = centerY - pieceH * 0.5f - minY * tileSize;

            for (int j = 0; j < tiles.length; j++) {
                float tx = originX + (loc.x + tiles[j].x) * tileSize;
                float ty = originY + (loc.y + tiles[j].y) * tileSize;
                renderer.drawTileBgPreview(sprites, tx, ty, tileSize, type);
                renderer.drawTilePreview(sprites, tx, ty, tileSize, type, states[j]);
            }
        }

        sprites.end();
    }

    @Override
    public void update() {
        refresh();
    }

    private void refresh() {
        PlayerProfile profile = app.getProfile();
        Artifact a = profile != null ? profile.findArtifact(profile.equippedArtifactIds[0]) : null;
        Artifact b = profile != null ? profile.findArtifact(profile.equippedArtifactIds[1]) : null;
        ActiveLoadout loadout = new ActiveLoadout(null, a, b);

        for (int i = 0; i < PIECE_COUNT; i++) {
            byte type = PIECE_TYPES[i];
            statsTexts[i].textin.set(pieceStatsBlock(
                    loadout.clearScoreBonusPercent(type),
                    loadout.clearMeterBonusPercent(type),
                    loadout.spinScoreBonusPercent(type),
                    loadout.spinMeterBonusPercent(type)));
        }

        statsTexts[MISC_INDEX].textin.set(miscStatsBlock(
                loadout.equippedPassiveFillBonusPercent(),
                loadout.equippedScoreBonusPercent(true, false),
                loadout.equippedMeterBonusPercent(true, false),
                loadout.equippedScoreBonusPercent(false, true),
                loadout.equippedMeterBonusPercent(false, true)));
    }

    private static String pieceStatsBlock(float clearScore, float clearMeter,
                                          float spinScore, float spinMeter) {
        return "Clear Score: " + ArtifactEffect.formatBonusPercent(clearScore)
                + "\nClear Meter: " + ArtifactEffect.formatBonusPercent(clearMeter)
                + "\nSpin Score: " + ArtifactEffect.formatBonusPercent(spinScore)
                + "\nSpin Meter: " + ArtifactEffect.formatBonusPercent(spinMeter);
    }

    private static String miscStatsBlock(float passive, float clearScore, float clearMeter,
                                         float spinScore, float spinMeter) {
        return "Passive Meter Fill: " + ArtifactEffect.formatBonusPercent(passive)
                + "\nClear Score: " + ArtifactEffect.formatBonusPercent(clearScore)
                + "\nClear Meter: " + ArtifactEffect.formatBonusPercent(clearMeter)
                + "\nSpin Score: " + ArtifactEffect.formatBonusPercent(spinScore)
                + "\nSpin Meter: " + ArtifactEffect.formatBonusPercent(spinMeter);
    }

    private static int cardCol(int index) {
        return index % COLS;
    }

    private static int cardRow(int index) {
        return index / COLS;
    }

    private static float cardLeft(int index) {
        return MARGIN_X + cardCol(index) * (CARD_W + GAP_X);
    }

    private static float cardTop(int index) {
        return GRID_TOP - cardRow(index) * (CARD_H + GAP_Y);
    }

    private static float cardBottom(int index) {
        return cardTop(index) - CARD_H;
    }

    private static float cardCenterX(int index) {
        return cardLeft(index) + CARD_W * 0.5f;
    }

    @Override
    protected void onEscPressed() {
        app.switchMenu(parent);
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        parent.passClientPacket(w);
    }
}
