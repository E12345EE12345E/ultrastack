package me.ethanchen.lwjgl3.menuscreens.decorated;

/**
 * Capability required of every object hosted by a {@link me.ethanchen.lwjgl3.menuscreens.DecoratedMenuScreen}.
 * Legacy {@link me.ethanchen.lwjgl3.menuscreens.ui.UIElement} widgets that do not implement this
 * are not accepted on decorated screens.
 */
public interface Decorated {
    void renderDecorated(DecorContext ctx);

    boolean isFocusable();

    void setFocused(boolean focused);

    boolean isFocused();

    /** Confirm / click — keyboard Enter, controller A, or mouse press. */
    void activate();
}
