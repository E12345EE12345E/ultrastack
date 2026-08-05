package me.ethanchen.lwjgl3.menuscreens.ui;

import java.util.ArrayList;

/**
 * Prev / page-label / next controls for a paged inventory grid. Page index is 0-based;
 * the label shows 1-based {@code current/total} (e.g. {@code 1/3}).
 */
public final class InventoryPaging {
    public final int columns;
    public final int rows;
    public final UIButton prevButton;
    public final UIButton nextButton;
    public final UIText pageText;

    /** 0-based current page. */
    public int page;

    public InventoryPaging(int columns, int rows,
                           UIButton prevButton, UIText pageText, UIButton nextButton) {
        this.columns = columns;
        this.rows = rows;
        this.prevButton = prevButton;
        this.pageText = pageText;
        this.nextButton = nextButton;
    }

    public int pageSize() {
        return columns * rows;
    }

    public int pageCount(int itemCount) {
        return Math.max(1, (itemCount + pageSize() - 1) / pageSize());
    }

    public void clamp(int itemCount) {
        int max = pageCount(itemCount) - 1;
        if (page > max) page = max;
        if (page < 0) page = 0;
    }

    public void prev(int itemCount) {
        clamp(itemCount);
        if (page > 0) page--;
    }

    public void next(int itemCount) {
        clamp(itemCount);
        if (page < pageCount(itemCount) - 1) page++;
    }

    public void updateLabel(int itemCount) {
        clamp(itemCount);
        pageText.textin.set((page + 1) + "/" + pageCount(itemCount));
    }

    /**
     * Builds {@code <} / {@code n/m} / {@code >} centred at design-pixel {@code (barCenterX, barCenterY)}
     * and adds them to {@code elements}.
     */
    public static InventoryPaging addTo(ArrayList<UIElement> elements,
                                        int columns, int rows,
                                        double barCenterXPx, double barCenterYPx,
                                        IntConsumer onPageChange) {
        UIButton prev = new UIButton(
                DesignUi.nx(barCenterXPx - 110), DesignUi.ny(barCenterYPx),
                DesignUi.nw(70), DesignUi.nh(48),
                "<", () -> onPageChange.accept(-1));
        UIText label = new UIText(DesignUi.nx(barCenterXPx), DesignUi.ny(barCenterYPx), "1/1", 1.1);
        UIButton next = new UIButton(
                DesignUi.nx(barCenterXPx + 110), DesignUi.ny(barCenterYPx),
                DesignUi.nw(70), DesignUi.nh(48),
                ">", () -> onPageChange.accept(1));
        elements.add(prev);
        elements.add(label);
        elements.add(next);
        return new InventoryPaging(columns, rows, prev, label, next);
    }

    @FunctionalInterface
    public interface IntConsumer {
        void accept(int delta);
    }
}
