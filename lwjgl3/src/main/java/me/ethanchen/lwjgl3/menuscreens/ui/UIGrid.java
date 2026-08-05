package me.ethanchen.lwjgl3.menuscreens.ui;

/**
 * Pure layout helper for a Minecraft-style inventory grid (implementation.md, Part 5): computes
 * each cell's center in relative coordinates from a top-left origin, column count, and
 * per-cell size/spacing. Not a {@link UIElement} itself — callers place their own
 * {@link UIInventoryButton} (or similar) at the coordinates this returns.
 *
 * <p>For Aspect-locked 16:9 layouts, prefer {@link #designSquare} so cells stay square in design
 * pixels. The single-size constructors assume equal relative width and height (Simple UI).
 */
public class UIGrid {
    public final double originX;
    public final double originY;
    public final int columns;
    public final double cellW;
    public final double cellH;
    public final double spacingX;
    public final double spacingY;

    /** Alias of {@link #cellW} for Simple UI callers that used a single cell size. */
    public final double cellSize;
    /** Alias of {@link #spacingX} for Simple UI callers that used a single spacing. */
    public final double spacing;

    public UIGrid(double originX, double originY, int columns, double cellSize, double spacing) {
        this(originX, originY, columns, cellSize, cellSize, spacing, spacing);
    }

    public UIGrid(double originX, double originY, int columns,
                  double cellW, double cellH, double spacingX, double spacingY) {
        this.originX = originX;
        this.originY = originY;
        this.columns = columns;
        this.cellW = cellW;
        this.cellH = cellH;
        this.spacingX = spacingX;
        this.spacingY = spacingY;
        this.cellSize = cellW;
        this.spacing = spacingX;
    }

    /**
     * Square-cell grid in 1920×1080 design pixels. {@code originXPx}/{@code originYPx} are the
     * center of the first (top-left) cell.
     */
    public static UIGrid designSquare(float originXPx, float originYPx, int columns,
                                      float cellPx, float spacingPx) {
        return new UIGrid(
                DesignUi.nx(originXPx), DesignUi.ny(originYPx), columns,
                DesignUi.nw(cellPx), DesignUi.nh(cellPx),
                DesignUi.nw(spacingPx), DesignUi.nh(spacingPx));
    }

    public double cellCenterX(int index) {
        int col = index % columns;
        return originX + col * (cellW + spacingX);
    }

    public double cellCenterY(int index) {
        int row = index / columns;
        return originY - row * (cellH + spacingY);
    }

    /** Number of rows needed to display {@code itemCount} items at this grid's column count. */
    public int rowsFor(int itemCount) {
        return (itemCount + columns - 1) / columns;
    }
}
