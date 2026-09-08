package me.ethanchen.lwjgl3.menuscreens.decorated;

/**
 * A focusable container that can be entered so keyboard/controller input walks its children
 * instead of the outer focus ring. Tab highlights the group as a whole; Enter/A drills in;
 * Escape/B (or walking past an edge) leaves.
 */
public interface FocusGroup {
    enum Nav {
        HANDLED,
        EXIT_PREV,
        EXIT_NEXT
    }

    boolean isEntered();

    void enterGroup();

    void exitGroup();

    /**
     * {@code dx}: +1 right, -1 left. {@code dy}: +1 up, -1 down.
     * Tab passes {@code dx = ±1} with {@code wrapRows = true}.
     */
    Nav navigate(int dx, int dy, boolean wrapRows);

    /**
     * If a child sits under the screen point, enter the group (if needed) and place the
     * internal cursor on that child. Returns {@code true} when a child was hit.
     */
    boolean enterAt(int screenX, int screenY);

    default boolean hasPaging() {
        return false;
    }

    default void changePage(int delta) {}
}
