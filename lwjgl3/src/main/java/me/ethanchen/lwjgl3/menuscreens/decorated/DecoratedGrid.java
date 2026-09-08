package me.ethanchen.lwjgl3.menuscreens.decorated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

import com.badlogic.gdx.graphics.Color;

import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;

/**
 * Fixed-size 2D slot grid. The container is one outer-ring focus target; Enter drills into
 * individual slots, and walking past a left/top (or right/bottom) edge leaves toward the
 * previous (or next) ring element.
 */
public class DecoratedGrid extends DecoratedElement implements FocusGroup {
    private static final float PAD_DESIGN = 8f;
    private static final Color FILL = new Color(1f, 1f, 1f, 0.10f);
    private static final Color OUTLINE = new Color(1f, 1f, 1f, 0.85f);
    private static final Color OUTLINE_ENTERED = new Color(1f, 1f, 1f, 0.35f);

    private final int cols;
    private final int rows;
    private final List<DecoratedSlot> slots;
    private final List<DecoratedElement> nested;

    public int selectedIndex = -1;
    public int cursorIndex;
    public int populatedCount;

    private boolean entered;
    private IntConsumer onPageChange;

    public DecoratedGrid(float originX, float topRowCenterY, int cols, int rows,
                         float cellPx, float gapPx) {
        super(
                DesignUi.nx(gridCenterX(originX, cols, cellPx, gapPx)),
                DesignUi.ny(gridCenterY(topRowCenterY, rows, cellPx, gapPx)),
                DesignUi.nw(gridWidth(cols, cellPx, gapPx) + PAD_DESIGN * 2f),
                DesignUi.nh(gridHeight(rows, cellPx, gapPx) + PAD_DESIGN * 2f));
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        int count = this.cols * this.rows;
        this.slots = new ArrayList<>(count);
        this.nested = new ArrayList<>(count);
        this.populatedCount = count;
        this.focusable = true;
        for (int i = 0; i < count; i++) {
            int col = i % this.cols;
            int row = i / this.cols;
            float x = originX + col * (cellPx + gapPx);
            float y = topRowCenterY - row * (cellPx + gapPx);
            DecoratedSlot slot = new DecoratedSlot(x, y, cellPx);
            slot.focusable = false;
            slots.add(slot);
            nested.add(slot);
        }
    }

    public static DecoratedGrid centered(float gridCenterX, float topRowCenterY,
                                         int cols, int rows, float cellPx, float gapPx) {
        float innerW = gridWidth(cols, cellPx, gapPx);
        float originX = gridCenterX - innerW * 0.5f + cellPx * 0.5f;
        return new DecoratedGrid(originX, topRowCenterY, cols, rows, cellPx, gapPx);
    }

    public DecoratedGrid onPageChange(IntConsumer onPageChange) {
        this.onPageChange = onPageChange;
        return this;
    }

    @Override
    public boolean hasPaging() {
        return onPageChange != null;
    }

    @Override
    public void changePage(int delta) {
        if (onPageChange != null && delta != 0) onPageChange.accept(delta);
    }

    public List<DecoratedSlot> slots() {
        return slots;
    }

    public DecoratedSlot slot(int index) {
        return slots.get(index);
    }

    public int slotCount() {
        return slots.size();
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    @Override
    public boolean isEntered() {
        return entered;
    }

    @Override
    public void enterGroup() {
        entered = true;
        int max = Math.max(0, populatedCount - 1);
        if (cursorIndex < 0 || cursorIndex > max) {
            int seed = selectedIndex;
            if (seed < 0 || seed > max) seed = 0;
            cursorIndex = seed;
        }
        applyCursorFocus();
    }

    @Override
    public void exitGroup() {
        entered = false;
        applyCursorFocus();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) exitGroup();
    }

    @Override
    public Nav navigate(int dx, int dy, boolean wrapRows) {
        int populated = Math.max(0, populatedCount);
        if (populated <= 0) {
            if (dx < 0 || dy > 0) return Nav.EXIT_PREV;
            return Nav.EXIT_NEXT;
        }
        if (wrapRows) {
            int next = cursorIndex + dx;
            if (next < 0) return Nav.EXIT_PREV;
            if (next >= populated) return Nav.EXIT_NEXT;
            cursorIndex = next;
            applyCursorFocus();
            return Nav.HANDLED;
        }
        int col = cursorIndex % cols;
        int row = cursorIndex / cols;
        col += dx;
        row -= dy;
        if (col < 0 || row < 0) return Nav.EXIT_PREV;
        if (col >= cols || row >= rows) return Nav.EXIT_NEXT;
        int index = row * cols + col;
        if (index >= populated) return Nav.EXIT_NEXT;
        cursorIndex = index;
        applyCursorFocus();
        return Nav.HANDLED;
    }

    @Override
    public boolean enterAt(int screenX, int screenY) {
        int limit = Math.min(slots.size(), Math.max(populatedCount, 0));
        for (int i = 0; i < limit; i++) {
            DecoratedSlot slot = slots.get(i);
            if (slot.containsScreenPoint(screenX, screenY)) {
                cursorIndex = i;
                entered = true;
                applyCursorFocus();
                return true;
            }
        }
        return false;
    }

    @Override
    public void activate() {
        if (!entered) {
            enterGroup();
            return;
        }
        if (cursorIndex < 0 || cursorIndex >= slots.size()) return;
        slots.get(cursorIndex).activate();
    }

    @Override
    public List<Decorated> nestedFocusables() {
        return Collections.emptyList();
    }

    @Override
    public List<DecoratedElement> nestedElements() {
        return nested;
    }

    @Override
    public void updateDecorated(float dtS) {
        super.updateDecorated(dtS);
        if (entered) {
            int max = Math.max(0, populatedCount - 1);
            if (cursorIndex > max) cursorIndex = max;
            if (cursorIndex < 0) cursorIndex = 0;
            applyCursorFocus();
        }
        for (DecoratedSlot slot : slots) {
            slot.updateDecorated(dtS);
        }
    }

    @Override
    public void handleClick(int screenX, int screenY, int button) {
        for (DecoratedSlot slot : slots) {
            slot.handleClick(screenX, screenY, button);
        }
    }

    @Override
    public void handleDrag(int screenX, int screenY) {
        for (DecoratedSlot slot : slots) {
            slot.handleDrag(screenX, screenY);
        }
    }

    @Override
    public void handleRelease(int screenX, int screenY) {
        for (DecoratedSlot slot : slots) {
            slot.handleRelease(screenX, screenY);
        }
    }

    @Override
    public void renderDecorated(DecorContext ctx) {
        if (!visible || alpha <= 0.01f) return;
        float a = Anim.clamp01(alpha);
        Color outline = entered ? OUTLINE_ENTERED : OUTLINE;
        boolean hot = focused && !entered;
        drawFramedPanel(ctx, FILL, outline, a, hot, 4f);

        for (DecoratedSlot slot : slots) {
            slot.renderDecorated(ctx);
        }
    }

    @Override
    protected boolean shouldDrawFocusCorners() {
        return focused && !entered;
    }

    private void applyCursorFocus() {
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).setFocused(entered && i == cursorIndex);
        }
    }

    private static float gridWidth(int cols, float cellPx, float gapPx) {
        int n = Math.max(1, cols);
        return n * cellPx + Math.max(0, n - 1) * gapPx;
    }

    private static float gridHeight(int rows, float cellPx, float gapPx) {
        int n = Math.max(1, rows);
        return n * cellPx + Math.max(0, n - 1) * gapPx;
    }

    private static float gridCenterX(float originX, int cols, float cellPx, float gapPx) {
        return originX + (gridWidth(cols, cellPx, gapPx) - cellPx) * 0.5f;
    }

    private static float gridCenterY(float topRowCenterY, int rows, float cellPx, float gapPx) {
        return topRowCenterY - (gridHeight(rows, cellPx, gapPx) - cellPx) * 0.5f;
    }
}
