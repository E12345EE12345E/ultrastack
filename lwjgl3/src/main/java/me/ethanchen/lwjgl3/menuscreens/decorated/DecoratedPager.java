package me.ethanchen.lwjgl3.menuscreens.decorated;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;

/**
 * Prev / page-label / next controls for a paged inventory grid. Page index is 0-based;
 * the label shows 1-based {@code current/total} (e.g. {@code 1/3}).
 */
public class DecoratedPager extends DecoratedElement {
    public final int columns;
    public final int rows;
    public final DecoratedButton prevButton;
    public final DecoratedButton nextButton;
    public final DecoratedText pageText;

    /** 0-based current page. */
    public int page;

    public DecoratedPager(int columns, int rows, float barCenterX, float barCenterY,
                          IntConsumer onPageChange) {
        super(DesignUi.nx(barCenterX), DesignUi.ny(barCenterY), DesignUi.nw(290), DesignUi.nh(48));
        this.columns = columns;
        this.rows = rows;
        this.focusable = false;
        this.prevButton = new DecoratedButton(barCenterX - 110, barCenterY, 70, 48, "<",
                () -> onPageChange.accept(-1));
        this.pageText = new DecoratedText(barCenterX, barCenterY, "1/1", 1.1f);
        this.nextButton = new DecoratedButton(barCenterX + 110, barCenterY, 70, 48, ">",
                () -> onPageChange.accept(1));
        prevButton.fontSize = 1.5f;
        nextButton.fontSize = 1.5f;
        prevButton.outlineDesignPx = 3f;
        nextButton.outlineDesignPx = 3f;
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

    /** Jumps so {@code itemIndex} (0-based in the full list) is on the current page. */
    public void showIndex(int itemIndex, int itemCount) {
        if (itemIndex < 0 || itemCount <= 0) return;
        page = itemIndex / pageSize();
        clamp(itemCount);
    }

    public void updateLabel(int itemCount) {
        clamp(itemCount);
        pageText.text = (page + 1) + "/" + pageCount(itemCount);
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    @Override
    public List<Decorated> nestedFocusables() {
        List<Decorated> out = new ArrayList<>(2);
        if (prevButton.isFocusable()) out.add(prevButton);
        if (nextButton.isFocusable()) out.add(nextButton);
        return out;
    }

    @Override
    public List<DecoratedElement> nestedElements() {
        List<DecoratedElement> out = new ArrayList<>(3);
        out.add(prevButton);
        out.add(pageText);
        out.add(nextButton);
        return out;
    }

    @Override
    public void updateDecorated(float dtS) {
        super.updateDecorated(dtS);
        prevButton.updateDecorated(dtS);
        nextButton.updateDecorated(dtS);
        pageText.updateDecorated(dtS);
    }

    @Override
    public void handleClick(int screenX, int screenY, int button) {
        prevButton.handleClick(screenX, screenY, button);
        nextButton.handleClick(screenX, screenY, button);
    }

    @Override
    public void handleDrag(int screenX, int screenY) {
        prevButton.handleDrag(screenX, screenY);
        nextButton.handleDrag(screenX, screenY);
    }

    @Override
    public void handleRelease(int screenX, int screenY) {
        prevButton.handleRelease(screenX, screenY);
        nextButton.handleRelease(screenX, screenY);
    }

    @Override
    public void renderDecorated(DecorContext ctx) {
        prevButton.renderDecorated(ctx);
        pageText.renderDecorated(ctx);
        nextButton.renderDecorated(ctx);
    }
}
