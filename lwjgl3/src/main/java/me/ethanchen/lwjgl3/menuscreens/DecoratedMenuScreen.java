package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecorContext;
import me.ethanchen.lwjgl3.menuscreens.decorated.Decorated;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedElement;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedScrollableList;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedTextBox;
import me.ethanchen.lwjgl3.menuscreens.decorated.FocusGroup;
import me.ethanchen.lwjgl3.menuscreens.decorated.FocusNavigator;
import me.ethanchen.lwjgl3.menuscreens.decorated.Widget;
import me.ethanchen.lwjgl3.menuscreens.ui.AspectLockedViewport;
import me.ethanchen.lwjgl3.music.AudioManager;

/**
 * Aspect-locked menu that hosts only {@link Decorated} objects, in-screen {@link Widget}s,
 * keyboard/controller focus, and dual clocks ({@code menuElapsedMs}, {@code appElapsedMs}).
 * Legacy {@link me.ethanchen.lwjgl3.menuscreens.ui.UIElement} widgets are not accepted —
 * the inherited {@code elements} list stays empty.
 */
public abstract class DecoratedMenuScreen extends AspectLockedMenuScreen {
    private static final float STICK_DEADZONE = 0.55f;
    private static final int STICK_INITIAL_MS = 280;
    private static final int STICK_REPEAT_MS = 140;

    // SDL2 GameController fallbacks when Controller.getMapping() is null.
    private static final int SDL_A = 0;
    private static final int SDL_B = 1;
    private static final int SDL_DPAD_UP = 11;
    private static final int SDL_DPAD_DOWN = 12;
    private static final int SDL_DPAD_LEFT = 13;
    private static final int SDL_DPAD_RIGHT = 14;
    private static final int SDL_AXIS_LEFT_X = 0;
    private static final int SDL_AXIS_LEFT_Y = 1;

    protected final List<Decorated> decorated = new ArrayList<>();
    protected final ArrayDeque<Widget> widgets = new ArrayDeque<>();
    protected final FocusNavigator navigator = new FocusNavigator();

    private final long menuStartMs;
    private final ControllerAdapter controllerListener;

    private int stickHeldDx;
    private int stickHeldDy;
    private int stickTimerMs;
    private boolean stickRepeating;
    private boolean controllerBound = true;

    public DecoratedMenuScreen(ClientApp app, ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        super(app, shapes, sprites, font);
        this.menuStartMs = System.currentTimeMillis();
        this.controllerListener = new ControllerAdapter() {
            @Override
            public boolean buttonDown(Controller controller, int buttonIndex) {
                return handleControllerButton(controller, buttonIndex);
            }
        };
        Controllers.addListener(controllerListener);
    }

    protected void addDecorated(DecoratedElement element) {
        if (element == null) return;
        decorated.add(element);
    }

    public void openWidget(Widget widget) {
        if (widget == null) return;
        widget.beginOpen();
        widgets.addLast(widget);
        refreshFocusRing();
    }

    /** Starts the close animation on the top widget. Returns true if a widget was closing or started. */
    public boolean closeTopWidget() {
        Widget top = widgets.peekLast();
        if (top == null) return false;
        if (!top.isClosing()) top.beginClose();
        return true;
    }

    public boolean hasOpenWidget() {
        return !widgets.isEmpty();
    }

    protected long menuElapsedMs() {
        return System.currentTimeMillis() - menuStartMs;
    }

    protected long appElapsedMs() {
        return app.getAppElapsedMs();
    }

    /** Screen-specific logic after clocks, widgets, and stick have been ticked. */
    protected void updateScreen(long menuElapsedMs, long appElapsedMs) {}

    /** Full-window pass drawn before the letterbox (shaders, etc.). */
    protected void renderBackground(DecorContext ctx) {}

    /** Design-space chrome after the letterbox and before elements. */
    protected void drawDecorations(DecorContext ctx) {}

    /** Overlay after widgets. */
    protected void renderForeground(DecorContext ctx) {}

    /** Called first inside {@link #render()} so a screen can start an FBO capture. */
    protected void beginFrame(DecorContext ctx) {}

    /** Called last inside {@link #render()}, after InfoText, to end an FBO capture. */
    protected void endFrame(DecorContext ctx) {}

    /**
     * When true, keyboard, mouse, and controller input is swallowed this frame.
     * Used by FusionScreen to skip its animation on any interrupt.
     */
    protected boolean interceptInput() {
        return false;
    }

    /** When true, left-stick focus walking is paused (e.g. a playing animation). */
    protected boolean pauseStickFocus() {
        return false;
    }

    @Override
    public final void update() {
        long menuMs = menuElapsedMs();
        long appMs = appElapsedMs();
        float dtS = Gdx.graphics.getDeltaTime();
        int dtMs = Math.max(0, Math.round(dtS * 1000f));

        for (Decorated d : decorated) {
            if (d instanceof DecoratedElement) {
                ((DecoratedElement) d).updateDecorated(dtS);
            }
        }
        tickWidgets(dtS);
        withViewport(() -> {
            if (!pauseStickFocus()) pollStick(dtMs);
            refreshFocusRing();
            return true;
        });
        updateScreen(menuMs, appMs);
    }

    @Override
    public final void render() {
        viewport.update();
        AspectLockedViewport.push(viewport);
        DecorContext ctx = new DecorContext(
                shapes, sprites, font, viewport,
                menuElapsedMs(), appElapsedMs(), Gdx.graphics.getDeltaTime());
        DecorContext.push(ctx);
        try {
            beginFrame(ctx);
            renderBackground(ctx);
            drawLetterboxBars();
            drawDecorations(ctx);
            for (Decorated d : decorated) {
                d.renderDecorated(ctx);
            }
            for (Widget w : widgets) {
                w.render(ctx);
            }
            renderForeground(ctx);
            renderFocusCorners(ctx);
            renderInfoText(ctx);
            endFrame(ctx);
        } finally {
            DecorContext.pop();
            AspectLockedViewport.pop();
        }
    }

    @Override
    public void dispose() {
        if (controllerBound) {
            Controllers.removeListener(controllerListener);
            controllerBound = false;
        }
        super.dispose();
    }

    /**
     * Re-bind controller input when this screen is shown again after {@link #dispose()}
     * (e.g. Fusion / Loadout returning to a retained {@code CharacterScreen}).
     */
    @Override
    public void resumeInput() {
        if (controllerBound) return;
        Controllers.addListener(controllerListener);
        controllerBound = true;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (interceptInput()) return true;
        if (hasModalWidget() && widgets.peekLast().handleKeyDown(keycode)) {
            return true;
        }
        DecoratedTextBox textBox = focusedTextBox();
        if (textBox != null && textBox.handleKeyDown(keycode)) {
            return true;
        }

        if (keycode == Input.Keys.TAB) {
            boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            moveFocus(shift ? -1 : 1, 0, true);
            return true;
        }
        if (keycode == Input.Keys.DOWN) {
            moveFocus(0, -1, false);
            return true;
        }
        if (keycode == Input.Keys.UP) {
            moveFocus(0, 1, false);
            return true;
        }
        if (textBox == null && keycode == Input.Keys.RIGHT) {
            moveFocus(1, 0, false);
            return true;
        }
        if (textBox == null && keycode == Input.Keys.LEFT) {
            moveFocus(-1, 0, false);
            return true;
        }
        if (keycode == Input.Keys.ENTER) {
            if (textBox != null) {
                if (textBox.runOnEnter != null) {
                    textBox.runOnEnter.run();
                } else {
                    moveFocus(1, 0, true);
                }
                return true;
            }
            if (tryEnterFocusedGroup()) return true;
            navigator.activate();
            return true;
        }
        if (keycode == Input.Keys.SPACE) {
            if (textBox != null) return true;
            if (tryEnterFocusedGroup()) return true;
            navigator.activate();
            return true;
        }
        if (keycode == Input.Keys.ESCAPE) {
            if (exitEnteredGroup()) return true;
            if (closeTopWidget()) return true;
            onEscPressed();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return withViewport(() -> {
            if (hasModalWidget()) {
                widgets.peekLast().handleKeyTyped(character);
            } else {
                for (Decorated d : decorated) {
                    if (d instanceof DecoratedElement) {
                        ((DecoratedElement) d).handleKeyTyped(character);
                    }
                }
            }
            return true;
        });
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (interceptInput()) return true;
        return withViewport(() -> {
            if (hasModalWidget()) {
                Widget top = widgets.peekLast();
                DecoratedElement hit = top.hitFocusable(screenX, screenY);
                if (hit != null) {
                    navigator.focus(hit);
                } else {
                    navigator.clear();
                }
                top.handleClick(screenX, screenY, button);
                return true;
            }
            DecoratedElement hit = hitScreenFocusable(screenX, screenY);
            if (hit != null) {
                navigator.focus(hit);
            } else {
                navigator.clear();
            }
            for (Decorated d : decorated) {
                if (d instanceof DecoratedElement) {
                    ((DecoratedElement) d).handleClick(screenX, screenY, button);
                }
            }
            return true;
        });
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return withViewport(() -> {
            if (hasModalWidget()) {
                widgets.peekLast().handleDrag(screenX, screenY);
                return true;
            }
            for (Decorated d : decorated) {
                if (d instanceof DecoratedElement) {
                    ((DecoratedElement) d).handleDrag(screenX, screenY);
                }
            }
            return true;
        });
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return withViewport(() -> {
            if (hasModalWidget()) {
                widgets.peekLast().handleRelease(screenX, screenY);
                return true;
            }
            for (Decorated d : decorated) {
                if (d instanceof DecoratedElement) {
                    ((DecoratedElement) d).handleRelease(screenX, screenY);
                }
            }
            return true;
        });
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (hasModalWidget()) return false;
        if (amountY == 0f) return false;
        int dir = amountY > 0f ? 1 : -1;
        Decorated focused = navigator.getFocused();
        if (focused instanceof FocusGroup) {
            FocusGroup group = (FocusGroup) focused;
            if (group.hasPaging()) {
                group.changePage(dir);
                return true;
            }
        }
        int mx = Gdx.input.getX();
        int my = Gdx.input.getY();
        for (Decorated d : decorated) {
            if (!(d instanceof FocusGroup) || !(d instanceof DecoratedElement)) continue;
            FocusGroup group = (FocusGroup) d;
            if (group.hasPaging() && ((DecoratedElement) d).containsScreenPoint(mx, my)) {
                group.changePage(dir);
                return true;
            }
        }
        for (Decorated d : decorated) {
            if (d instanceof DecoratedScrollableList) {
                ((DecoratedScrollableList) d).scrollBy(dir);
                return true;
            }
        }
        return false;
    }

    /**
     * Focus corner marks are drawn after every element and widget so packed grids cannot
     * cover the brackets of a neighbor.
     */
    private void renderFocusCorners(DecorContext ctx) {
        for (DecoratedElement el : decoratedTree()) {
            el.renderFocusOverlay(ctx);
        }
    }

    /**
     * Draws at most one InfoText popup after all chrome so it is never covered.
     * A mouse-hovered host wins over keyboard/controller focus.
     */
    private void renderInfoText(DecorContext ctx) {
        int mx = Gdx.input.getX();
        int my = Gdx.input.getY();
        DecoratedElement mouseHost = null;
        DecoratedElement focusHost = null;
        for (DecoratedElement el : decoratedTree()) {
            if (el.infoText == null || !el.infoText.hasText()) continue;
            if (!el.visible || el.alpha <= 0.35f) continue;
            if (el.containsScreenPoint(mx, my)) {
                mouseHost = el;
            }
            if (el.focused && el.infoText.showOnFocus) {
                focusHost = el;
            }
        }
        if (mouseHost != null) {
            mouseHost.infoText.render(ctx, mouseHost, true);
        } else if (focusHost != null) {
            focusHost.infoText.render(ctx, focusHost, false);
        }
    }

    private List<DecoratedElement> decoratedTree() {
        List<DecoratedElement> out = new ArrayList<>();
        if (hasModalWidget()) {
            for (DecoratedElement el : widgets.peekLast().decoratedElements()) {
                out.add(el);
                out.addAll(el.nestedElements());
            }
        } else {
            for (Decorated d : decorated) {
                if (!(d instanceof DecoratedElement)) continue;
                DecoratedElement el = (DecoratedElement) d;
                out.add(el);
                out.addAll(el.nestedElements());
            }
        }
        return out;
    }

    private void tickWidgets(float dtS) {
        long now = System.currentTimeMillis();
        Iterator<Widget> it = widgets.iterator();
        while (it.hasNext()) {
            Widget w = it.next();
            w.updateDecorated(dtS);
            w.tick(now);
            if (w.isFinished()) it.remove();
        }
    }

    private void refreshFocusRing() {
        List<Decorated> ring = new ArrayList<>();
        if (hasModalWidget()) {
            ring.addAll(widgets.peekLast().focusables());
        } else {
            for (Decorated d : decorated) {
                if (d.isFocusable()) ring.add(d);
                if (d instanceof DecoratedElement) {
                    ring.addAll(((DecoratedElement) d).nestedFocusables());
                }
            }
        }
        navigator.setRing(ring);
    }

    private boolean hasModalWidget() {
        Widget top = widgets.peekLast();
        return top != null && top.modal && !top.isFinished();
    }

    private DecoratedTextBox focusedTextBox() {
        Decorated d = navigator.getFocused();
        return d instanceof DecoratedTextBox ? (DecoratedTextBox) d : null;
    }

    private DecoratedElement hitScreenFocusable(int screenX, int screenY) {
        for (int i = decorated.size() - 1; i >= 0; i--) {
            Decorated d = decorated.get(i);
            if (!(d instanceof DecoratedElement)) continue;
            DecoratedElement el = (DecoratedElement) d;
            if (el instanceof FocusGroup) {
                FocusGroup group = (FocusGroup) el;
                if (group.enterAt(screenX, screenY)) return el;
            }
            List<Decorated> nested = el.nestedFocusables();
            for (int j = nested.size() - 1; j >= 0; j--) {
                Decorated n = nested.get(j);
                if (!(n instanceof DecoratedElement)) continue;
                DecoratedElement child = (DecoratedElement) n;
                if (child.isFocusable() && child.containsScreenPoint(screenX, screenY)) return child;
            }
            if (el.isFocusable() && el.containsScreenPoint(screenX, screenY)) return el;
        }
        return null;
    }

    /**
     * Walks an entered {@link FocusGroup} in 2D, otherwise steps the outer ring.
     * Ring direction preserves existing menus: left/up = prev, right/down = next.
     */
    private void moveFocus(int dx, int dy, boolean wrapRows) {
        Decorated focused = navigator.getFocused();
        if (focused instanceof FocusGroup) {
            FocusGroup group = (FocusGroup) focused;
            if (group.isEntered()) {
                FocusGroup.Nav nav = group.navigate(dx, dy, wrapRows);
                if (nav == FocusGroup.Nav.HANDLED) {
                    AudioManager.getInstance().playMenuSelectSound();
                    return;
                }
                group.exitGroup();
                if (navigator.isEmpty()) refreshFocusRing();
                if (navigator.isEmpty()) return;
                if (nav == FocusGroup.Nav.EXIT_PREV) navigator.prev();
                else navigator.next();
                AudioManager.getInstance().playMenuSelectSound();
                return;
            }
        }
        if (navigator.isEmpty()) refreshFocusRing();
        if (navigator.isEmpty()) return;
        int dir = dx != 0 ? dx : -dy;
        if (dir == 0) return;
        if (dir > 0) navigator.next();
        else navigator.prev();
        AudioManager.getInstance().playMenuSelectSound();
    }

    private boolean tryEnterFocusedGroup() {
        Decorated focused = navigator.getFocused();
        if (!(focused instanceof FocusGroup)) return false;
        FocusGroup group = (FocusGroup) focused;
        if (group.isEntered()) return false;
        group.enterGroup();
        AudioManager.getInstance().playMenuPressSound();
        return true;
    }

    private boolean exitEnteredGroup() {
        Decorated focused = navigator.getFocused();
        if (!(focused instanceof FocusGroup)) return false;
        FocusGroup group = (FocusGroup) focused;
        if (!group.isEntered()) return false;
        group.exitGroup();
        return true;
    }

    private boolean handleControllerButton(Controller controller, int buttonIndex) {
        if (interceptInput()) return true;
        ControllerMapping map = controller != null ? controller.getMapping() : null;
        int up = map != null ? map.buttonDpadUp : SDL_DPAD_UP;
        int down = map != null ? map.buttonDpadDown : SDL_DPAD_DOWN;
        int left = map != null ? map.buttonDpadLeft : SDL_DPAD_LEFT;
        int right = map != null ? map.buttonDpadRight : SDL_DPAD_RIGHT;
        int a = map != null ? map.buttonA : SDL_A;
        int b = map != null ? map.buttonB : SDL_B;

        boolean textFocused = focusedTextBox() != null;
        if (buttonIndex == up) {
            moveFocus(0, 1, false);
            return true;
        }
        if (buttonIndex == down) {
            moveFocus(0, -1, false);
            return true;
        }
        if (!textFocused && buttonIndex == left) {
            moveFocus(-1, 0, false);
            return true;
        }
        if (!textFocused && buttonIndex == right) {
            moveFocus(1, 0, false);
            return true;
        }
        if (buttonIndex == a) {
            DecoratedTextBox box = focusedTextBox();
            if (box != null) {
                if (box.runOnEnter != null) {
                    box.runOnEnter.run();
                } else {
                    moveFocus(1, 0, true);
                }
                return true;
            }
            if (tryEnterFocusedGroup()) return true;
            navigator.activate();
            return true;
        }
        if (buttonIndex == b) {
            if (exitEnteredGroup()) return true;
            if (closeTopWidget()) return true;
            onEscPressed();
            return true;
        }
        return false;
    }

    private void pollStick(int dtMs) {
        int dx = 0;
        int dy = 0;
        for (Controller c : Controllers.getControllers()) {
            if (c == null) continue;
            ControllerMapping map = c.getMapping();
            int axisX = map != null ? map.axisLeftX : SDL_AXIS_LEFT_X;
            int axisY = map != null ? map.axisLeftY : SDL_AXIS_LEFT_Y;
            float x = c.getAxis(axisX);
            float y = c.getAxis(axisY);
            float ax = Math.abs(x);
            float ay = Math.abs(y);
            if (ax < STICK_DEADZONE && ay < STICK_DEADZONE) continue;
            if (ax >= ay) {
                if (focusedTextBox() != null) continue;
                dx = x < 0f ? -1 : 1;
            } else {
                dy = y < 0f ? 1 : -1; // up is typically negative
            }
            break;
        }

        if (dx == 0 && dy == 0) {
            stickHeldDx = 0;
            stickHeldDy = 0;
            stickTimerMs = 0;
            stickRepeating = false;
            return;
        }
        if (dx != stickHeldDx || dy != stickHeldDy) {
            stickHeldDx = dx;
            stickHeldDy = dy;
            stickTimerMs = 0;
            stickRepeating = false;
            moveFocus(dx, dy, false);
            return;
        }
        stickTimerMs += dtMs;
        int threshold = stickRepeating ? STICK_REPEAT_MS : STICK_INITIAL_MS;
        if (stickTimerMs >= threshold) {
            stickTimerMs -= threshold;
            stickRepeating = true;
            moveFocus(dx, dy, false);
        }
    }

    private void drawLetterboxBars() {
        float windowW = Gdx.graphics.getWidth();
        float windowH = Gdx.graphics.getHeight();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.BLACK);
        if (viewport.originX > 0.5f) {
            shapes.rect(0, 0, viewport.originX, windowH);
            shapes.rect(viewport.originX + viewport.viewW, 0,
                    windowW - viewport.originX - viewport.viewW, windowH);
        }
        if (viewport.originY > 0.5f) {
            shapes.rect(0, 0, windowW, viewport.originY);
            shapes.rect(0, viewport.originY + viewport.viewH, windowW,
                    windowH - viewport.originY - viewport.viewH);
        }
        shapes.end();
    }

    private boolean withViewport(BooleanSupplier action) {
        viewport.update();
        AspectLockedViewport.push(viewport);
        try {
            return action.getAsBoolean();
        } finally {
            AspectLockedViewport.pop();
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
