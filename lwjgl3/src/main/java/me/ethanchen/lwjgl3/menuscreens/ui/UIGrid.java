package me.ethanchen.lwjgl3.menuscreens.ui;

/**
 * Pure layout helper for a Minecraft-style inventory grid (implementation.md, Part 5): computes
 * each cell's center in relative screen coordinates from a top-left origin, column count, and
 * per-cell size/spacing. Not a {@link UIElement} itself -- callers place their own
 * {@link UIIconButton} (or similar) at the coordinates this returns.
 */
public class UIGrid {
    public final double originX;
    public final double originY;
    public final int columns;
    public final double cellSize;
    public final double spacing;

    public UIGrid(double originX, double originY, int columns, double cellSize, double spacing) {
        this.originX = originX;
        this.originY = originY;
        this.columns = columns;
        this.cellSize = cellSize;
        this.spacing = spacing;
    }

    public double cellCenterX(int index) {
        int col = index % columns;
        return originX + col * (cellSize + spacing);
    }

    public double cellCenterY(int index) {
        int row = index / columns;
        return originY - row * (cellSize + spacing);
    }

    /** Number of rows needed to display {@code itemCount} items at this grid's column count. */
    public int rowsFor(int itemCount) {
        return (itemCount + columns - 1) / columns;
    }
}
