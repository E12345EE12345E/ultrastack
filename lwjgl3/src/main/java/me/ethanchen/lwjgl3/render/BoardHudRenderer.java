package me.ethanchen.lwjgl3.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.Tile;

/**
 * Stateless HUD draw helpers extracted from {@link BoardRenderer}: hold box, timer boxes.
 *
 * <p>All methods are package-private statics — they are only meant to be called by
 * {@link BoardRenderer}, which owns the GL resources and the public API surface.
 */
final class BoardHudRenderer {
    private BoardHudRenderer() {}

    private static final Color HOLD_GRAYSCALE = new Color(0.45f, 0.45f, 0.45f, 1f);

    // -------------------------------------------------------------------------
    // Hold box
    // -------------------------------------------------------------------------

    /**
     * Draws the shared hold box to the left of the board.
     * The box is a white-outline rectangle with a "HOLD" label; if a piece is held it is drawn
     * centred inside — full colour when {@code available}, grayscale otherwise.
     *
     * <p>Caller must NOT have an open SpriteBatch or ShapeRenderer begin/end.
     *
     * @param renderer used solely to draw the held piece's tiles (tiles textures live there)
     */
    static void drawHoldBox(byte heldType, boolean available,
                            float x, float y, float boxSize, float tileSize,
                            ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font,
                            BoardRenderer renderer) {
        drawOutlinedBox("HOLD", x, y, boxSize, tileSize, shapes, sprites, font);

        if (heldType == 0) return;

        Piece piece = Piece.defaultPiece(heldType);
        float minTX = Float.MAX_VALUE, maxTX = -Float.MAX_VALUE;
        float minTY = Float.MAX_VALUE, maxTY = -Float.MAX_VALUE;
        for (Vector2 t : piece.tiles) {
            minTX = Math.min(minTX, t.x);
            maxTX = Math.max(maxTX, t.x + 1f);
            minTY = Math.min(minTY, t.y);
            maxTY = Math.max(maxTY, t.y + 1f);
        }
        float centerX = x + boxSize * 0.5f;
        float centerY = y + boxSize * 0.35f;
        float offsetX = centerX - (minTX + (maxTX - minTX) * 0.5f) * tileSize;
        float offsetY = centerY - (minTY + (maxTY - minTY) * 0.5f) * tileSize;

        Color tint = available ? PieceTints.forType(heldType) : HOLD_GRAYSCALE;
        sprites.begin();
        for (int i = 0; i < piece.tiles.length; i++) {
            float sx = offsetX + piece.tiles[i].x * tileSize;
            float sy = offsetY + piece.tiles[i].y * tileSize;
            byte connection = (piece.tileconnectionstates != null && i < piece.tileconnectionstates.length)
                    ? piece.tileconnectionstates[i] : Tile.SINGLE_TILE;
            Color bg = available ? PieceTints.forTileBackground(heldType)
                                 : new Color(0.2f, 0.2f, 0.2f, 1f);
            sprites.setColor(bg);
            sprites.draw(renderer.tileBackground, sx, sy, tileSize, tileSize);
            sprites.setColor(tint);
            sprites.draw(renderer.tileRegions[connection & 0xF], sx, sy, tileSize, tileSize);
        }
        sprites.setColor(Color.WHITE);
        sprites.end();
    }

    // -------------------------------------------------------------------------
    // Timer boxes
    // -------------------------------------------------------------------------

    /**
     * Draws a countdown timer + score box (score mode).
     * Shows a "TIME" label at the top, the remaining MM:SS, and the current score near the bottom.
     */
    static void drawTimerBox(long endTargetMs, long score,
                             float x, float y, float boxSize, float tileSize,
                             ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        float lh = drawOutlinedBox("TIME", x, y, boxSize, tileSize, shapes, sprites, font);

        float savedX = font.getScaleX(), savedY = font.getScaleY();
        GlyphLayout layout = new GlyphLayout();

        long remaining = Math.min(GameConstants.SCORE_MODE_DURATION_MS,
                Math.max(0, endTargetMs - System.currentTimeMillis()));
        String timeText = formatMmSs(remaining);
        float timeFs = 0.8f * (tileSize / lh);
        font.getData().setScale(timeFs);
        layout.setText(font, timeText);
        float timeX = x + (boxSize - layout.width) * 0.5f;
        float timeY = y + boxSize * 0.62f + layout.height * 0.5f;
        sprites.begin();
        font.setColor(Color.WHITE);
        font.draw(sprites, timeText, timeX, timeY);
        sprites.end();

        String scoreText = String.valueOf(score);
        float scoreFs = 0.65f * (tileSize / lh);
        font.getData().setScale(scoreFs);
        layout.setText(font, scoreText);
        float scoreX = x + (boxSize - layout.width) * 0.5f;
        float scoreY = y + boxSize * 0.28f + layout.height * 0.5f;
        sprites.begin();
        font.setColor(Color.WHITE);
        font.draw(sprites, scoreText, scoreX, scoreY);
        sprites.end();

        font.getData().setScale(savedX, savedY);
    }

    /**
     * Draws the PvE section HUD: section index label, section time, session score, and optional
     * objective / criteria lines. When {@code timeoutMs < 0} the time line shows elapsed count-up;
     * otherwise it shows remaining time until the section times out.
     *
     * <p>{@code boxHeight} may exceed {@code boxWidth} when many objective lines are present.
     */
    static void drawPveSectionBox(int sectionIndex, long elapsedMs, long timeoutMs, long totalScore,
                                  String[] objectiveLines,
                                  float x, float y, float boxWidth, float boxHeight, float tileSize,
                                  ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        String label = "SECT " + Math.max(0, sectionIndex + 1);
        float lh = drawOutlinedBox(label, x, y, boxWidth, boxHeight, tileSize, shapes, sprites, font);

        float savedX = font.getScaleX(), savedY = font.getScaleY();
        GlyphLayout layout = new GlyphLayout();

        String timeText;
        if (timeoutMs >= 0) {
            timeText = formatMmSs(Math.max(0, timeoutMs - elapsedMs));
        } else {
            timeText = formatMmSs(Math.max(0, elapsedMs));
        }

        // Content stacked under the label: time, score, then objectives.
        float padTop = boxHeight * 0.22f;
        float contentTop = y + boxHeight - padTop;
        float timeFs = 0.75f * (tileSize / lh);
        font.getData().setScale(timeFs);
        layout.setText(font, timeText);
        float lineGap = layout.height * 1.25f;
        float cursorY = contentTop;

        sprites.begin();
        font.setColor(Color.WHITE);
        font.draw(sprites, timeText, x + (boxWidth - layout.width) * 0.5f, cursorY);
        cursorY -= lineGap;

        String scoreText = String.valueOf(totalScore);
        float scoreFs = 0.6f * (tileSize / lh);
        font.getData().setScale(scoreFs);
        layout.setText(font, scoreText);
        font.draw(sprites, scoreText, x + (boxWidth - layout.width) * 0.5f, cursorY);
        cursorY -= lineGap * 1.1f;

        if (objectiveLines != null && objectiveLines.length > 0) {
            float objFs = 0.38f * (tileSize / lh);
            font.getData().setScale(objFs);
            for (String line : objectiveLines) {
                if (line == null || line.isEmpty()) continue;
                layout.setText(font, line);
                if (cursorY - layout.height < y + boxHeight * 0.06f) break;
                font.draw(sprites, line, x + (boxWidth - layout.width) * 0.5f, cursorY);
                cursorY -= layout.height * 1.35f;
            }
        }
        sprites.end();

        font.getData().setScale(savedX, savedY);
    }

    /**
     * Draws a count-up timer box (puzzle mode). Shows a "TIME" label and MM:SS elapsed (uncapped).
     */
    static void drawCountUpTimerBox(long elapsedMs,
                                    float x, float y, float boxSize, float tileSize,
                                    ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        float lh = drawOutlinedBox("TIME", x, y, boxSize, tileSize, shapes, sprites, font);

        float savedX = font.getScaleX(), savedY = font.getScaleY();
        GlyphLayout layout = new GlyphLayout();

        String timeText = formatMmSs(Math.max(0, elapsedMs));
        float timeFs = 0.8f * (tileSize / lh);
        font.getData().setScale(timeFs);
        layout.setText(font, timeText);
        float timeX = x + (boxSize - layout.width) * 0.5f;
        float timeY = y + boxSize * 0.5f + layout.height * 0.5f;
        sprites.begin();
        font.setColor(Color.WHITE);
        font.draw(sprites, timeText, timeX, timeY);
        sprites.end();

        font.getData().setScale(savedX, savedY);
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /**
     * Draws a white-outline square at {@code (x, y, boxSize × boxSize)} and a centred
     * {@code label} near the top of the box.
     *
     * @return the unscaled {@code lineHeight} of the font, so callers can use the same
     *         base measurement for their own content without re-measuring.
     */
    private static float drawOutlinedBox(String label,
                                         float x, float y, float boxSize, float tileSize,
                                         ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        return drawOutlinedBox(label, x, y, boxSize, boxSize, tileSize, shapes, sprites, font);
    }

    /**
     * Draws a white-outline rectangle at {@code (x, y, width × height)} and a centred
     * {@code label} near the top.
     *
     * @return the unscaled {@code lineHeight} of the font, so callers can use the same
     *         base measurement for their own content without re-measuring.
     */
    private static float drawOutlinedBox(String label,
                                         float x, float y, float width, float height, float tileSize,
                                         ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.WHITE);
        shapes.rect(x, y, width, height);
        shapes.end();

        GlyphLayout layout = new GlyphLayout();
        float savedX = font.getScaleX(), savedY = font.getScaleY();
        font.getData().setScale(1f);
        float lh = font.getData().lineHeight;
        // Slightly smaller label scale for longer titles like "SECT 1".
        float fs = (label != null && label.length() > 4 ? 0.5f : 0.6f) * (tileSize / lh);
        font.getData().setScale(fs);
        layout.setText(font, label);
        float labelX = x + (width - layout.width) * 0.5f;
        // Baseline sits just under the top edge so the glyph body stays inside the box.
        float labelY = y + height - layout.height * 0.35f;
        sprites.begin();
        font.setColor(Color.WHITE);
        font.draw(sprites, label, labelX, labelY);
        sprites.end();
        font.getData().setScale(savedX, savedY);
        return lh;
    }

    private static String formatMmSs(long ms) {
        long mins = ms / 60000;
        long secs = (ms % 60000) / 1000;
        return mins + ":" + String.format("%02d", secs);
    }
}
