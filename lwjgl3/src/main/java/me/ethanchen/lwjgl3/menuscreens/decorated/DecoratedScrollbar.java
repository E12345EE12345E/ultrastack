package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.menuscreens.MenuScreen;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;

/**
 * Visual-only scroll track. Clicks are ignored; thumb position is derived from a
 * {@link DecoratedScrollableList}.
 */
public class DecoratedScrollbar extends DecoratedElement {
    private static final float TRACK_DESIGN_PX = 6f;
    private static final float MIN_THUMB_DESIGN_PX = 40f;
    private static final Color TRACK_ACTIVE = new Color(1f, 1f, 1f, 1f);
    private static final Color TRACK_IDLE = new Color(0.45f, 0.45f, 0.45f, 1f);
    private static final Color THUMB_FILL = new Color(0.45f, 0.45f, 0.45f, 1f);
    private static final Color THUMB_OUTLINE = new Color(1f, 1f, 1f, 1f);

    private final DecoratedScrollableList<?> list;

    public DecoratedScrollbar(float designCenterX, float designCenterY,
                              float designW, float designH, DecoratedScrollableList<?> list) {
        super(DesignUi.nx(designCenterX), DesignUi.ny(designCenterY),
                DesignUi.nw(designW), DesignUi.nh(designH));
        this.list = list;
        this.focusable = false;
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    @Override
    public void handleClick(int screenX, int screenY, int button) {}

    @Override
    public void handleDrag(int screenX, int screenY) {}

    @Override
    public void handleRelease(int screenX, int screenY) {}

    @Override
    public void renderDecorated(DecorContext ctx) {
        if (!visible || alpha <= 0.01f || list == null) return;

        float a = Anim.clamp01(alpha);
        float x = pxX();
        float y = pxY();
        float w = pxW();
        float h = pxH();
        float trackW = Math.max(1f, MenuScreen.toScreenWidth((float) DesignUi.nw(TRACK_DESIGN_PX)));
        float trackX = x + (w - trackW) * 0.5f;

        enableBlend();
        ctx.shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (!list.canScroll()) {
            ctx.shapes.setColor(TRACK_IDLE.r, TRACK_IDLE.g, TRACK_IDLE.b, TRACK_IDLE.a * a);
            ctx.shapes.rect(trackX, y, trackW, h);
            ctx.shapes.end();
            return;
        }

        ctx.shapes.setColor(TRACK_ACTIVE.r, TRACK_ACTIVE.g, TRACK_ACTIVE.b, TRACK_ACTIVE.a * a);
        ctx.shapes.rect(trackX, y, trackW, h);

        int itemCount = Math.max(1, list.itemCount());
        float visible = list.slotCount() / (float) itemCount;
        float minThumb = MenuScreen.toScreenHeight((float) DesignUi.nh(MIN_THUMB_DESIGN_PX));
        float thumbH = Math.min(h, Math.max(minThumb, visible * h));
        int max = list.maxOffset();
        float progress = max <= 0 ? 0f : list.offset() / (float) max;
        float thumbY = y + (1f - progress) * (h - thumbH);
        float outline = Math.max(1f, MenuScreen.toScreenWidth((float) DesignUi.nw(2f)));

        ctx.shapes.setColor(THUMB_FILL.r, THUMB_FILL.g, THUMB_FILL.b, THUMB_FILL.a * a);
        ctx.shapes.rect(x, thumbY, w, thumbH);
        ctx.shapes.setColor(THUMB_OUTLINE.r, THUMB_OUTLINE.g, THUMB_OUTLINE.b, THUMB_OUTLINE.a * a);
        drawAxisOutline(ctx.shapes, x, thumbY, w, thumbH, outline);
        ctx.shapes.end();
    }
}
