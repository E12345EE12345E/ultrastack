package me.ethanchen.lwjgl3.menuscreens.decorated;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;

/**
 * In-screen sub-panel. Children are laid out in widget-local design pixels (origin at the
 * panel center) so the same widget can be re-anchored. Open/close is a fade+scale driven
 * by a local clock. A modal widget dims the rest of the screen and owns the focus ring.
 */
public class Widget {
    public static final long ANIM_MS = 180L;

    public float designCenterX;
    public float designCenterY;
    public float designW;
    public float designH;
    public boolean modal = true;

    private final List<Child> children = new ArrayList<>();
    private boolean closing;
    private long animStartMs = System.currentTimeMillis();
    private boolean finished;

    public Widget(float designCenterX, float designCenterY, float designW, float designH) {
        this.designCenterX = designCenterX;
        this.designCenterY = designCenterY;
        this.designW = designW;
        this.designH = designH;
    }

    public <T extends DecoratedElement> T add(T element, float localDesignX, float localDesignY) {
        children.add(new Child(element, localDesignX, localDesignY, element.scaleMult));
        return element;
    }

    public void beginOpen() {
        closing = false;
        finished = false;
        animStartMs = System.currentTimeMillis();
    }

    public void beginClose() {
        if (closing) return;
        float open = openAmount(System.currentTimeMillis());
        closing = true;
        // Start the close from the current visual amount so a mid-open dismiss doesn't pop.
        animStartMs = System.currentTimeMillis() - (long) ((1f - open) * ANIM_MS);
    }

    public boolean isClosing() {
        return closing;
    }

    public boolean isFinished() {
        return finished;
    }

    public float openAmount(long nowMs) {
        float t = Anim.smoothstep(Anim.progress(nowMs, animStartMs, ANIM_MS));
        return closing ? 1f - t : t;
    }

    public void tick(long nowMs) {
        if (closing && nowMs - animStartMs >= ANIM_MS) {
            finished = true;
        }
    }

    public void applyLayout(long nowMs) {
        float open = openAmount(nowMs);
        float scale = Anim.lerp(0.92f, 1f, Anim.easeOutCubic(open));
        float alpha = open;
        for (Child c : children) {
            c.element.centerX = DesignUi.nx(designCenterX + c.localX * scale);
            c.element.centerY = DesignUi.ny(designCenterY + c.localY * scale);
            c.element.scaleMult = c.baseScale * scale;
            c.element.alpha = alpha;
            c.element.visible = open > 0.01f;
        }
    }

    public void render(DecorContext ctx) {
        long now = System.currentTimeMillis();
        float open = openAmount(now);
        applyLayout(now);
        if (open <= 0.01f) return;

        if (modal) {
            drawDim(ctx, 0.55f * open);
        }
        drawPanel(ctx, open);
        for (Child c : children) {
            c.element.renderDecorated(ctx);
        }
    }

    public void updateDecorated(float dtS) {
        for (Child c : children) {
            c.element.updateDecorated(dtS);
        }
    }

    public void handleClick(int screenX, int screenY, int button) {
        applyLayout(System.currentTimeMillis());
        for (Child c : children) {
            c.element.handleClick(screenX, screenY, button);
        }
    }

    public void handleDrag(int screenX, int screenY) {
        applyLayout(System.currentTimeMillis());
        for (Child c : children) {
            c.element.handleDrag(screenX, screenY);
        }
    }

    public void handleRelease(int screenX, int screenY) {
        applyLayout(System.currentTimeMillis());
        for (Child c : children) {
            c.element.handleRelease(screenX, screenY);
        }
    }

    public void handleKeyTyped(char key) {
        for (Child c : children) {
            c.element.handleKeyTyped(key);
        }
    }

    public boolean handleKeyDown(int keycode) {
        for (Child c : children) {
            if (c.element instanceof DecoratedTextBox) {
                if (((DecoratedTextBox) c.element).handleKeyDown(keycode)) return true;
            }
        }
        return false;
    }

    public List<DecoratedElement> decoratedElements() {
        List<DecoratedElement> out = new ArrayList<>();
        for (Child c : children) {
            out.add(c.element);
        }
        return out;
    }

    public List<Decorated> focusables() {
        List<Decorated> out = new ArrayList<>();
        for (Child c : children) {
            if (c.element.isFocusable()) out.add(c.element);
        }
        return out;
    }

    public DecoratedElement hitFocusable(int screenX, int screenY) {
        applyLayout(System.currentTimeMillis());
        for (int i = children.size() - 1; i >= 0; i--) {
            DecoratedElement el = children.get(i).element;
            if (el.isFocusable() && el.containsScreenPoint(screenX, screenY)) return el;
        }
        return null;
    }

    private void drawDim(DecorContext ctx, float a) {
        float windowW = Gdx.graphics.getWidth();
        float windowH = Gdx.graphics.getHeight();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        ctx.shapes.begin(ShapeRenderer.ShapeType.Filled);
        ctx.shapes.setColor(0f, 0f, 0f, a);
        ctx.shapes.rect(0, 0, windowW, windowH);
        ctx.shapes.end();
    }

    private void drawPanel(DecorContext ctx, float open) {
        float scale = Anim.lerp(0.92f, 1f, Anim.easeOutCubic(open));
        float w = (float) DesignUi.nw(designW * scale);
        float h = (float) DesignUi.nh(designH * scale);
        float pxW = me.ethanchen.lwjgl3.menuscreens.MenuScreen.toScreenWidth(w);
        float pxH = me.ethanchen.lwjgl3.menuscreens.MenuScreen.toScreenHeight(h);
        float pxX = me.ethanchen.lwjgl3.menuscreens.MenuScreen.convertFromRelCoordsX(
                (float) DesignUi.nx(designCenterX)) - 0.5f * pxW;
        float pxY = me.ethanchen.lwjgl3.menuscreens.MenuScreen.toScreenYBottom(
                (float) DesignUi.ny(designCenterY)) - 0.5f * pxH;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        ctx.shapes.begin(ShapeRenderer.ShapeType.Filled);
        DecoratedElement.fillRoundRect(ctx.shapes, pxX, pxY, pxW, pxH, 18f,
                new Color(0.06f, 0.07f, 0.12f, 0.92f * open));
        ctx.shapes.end();
        ctx.shapes.begin(ShapeRenderer.ShapeType.Line);
        ctx.shapes.setColor(0.45f, 0.85f, 1f, 0.75f * open);
        ctx.shapes.rect(pxX + 1f, pxY + 1f, pxW - 2f, pxH - 2f);
        ctx.shapes.end();
    }

    private static final class Child {
        final DecoratedElement element;
        final float localX;
        final float localY;
        final float baseScale;

        Child(DecoratedElement element, float localX, float localY, float baseScale) {
            this.element = element;
            this.localX = localX;
            this.localY = localY;
            this.baseScale = baseScale;
        }
    }
}
