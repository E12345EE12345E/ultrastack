package me.ethanchen.lwjgl3.input;

/**
 * Session-only setting controlling how keyboard and controllers map to local players.
 * Not persisted to settings.json.
 */
public enum LocalPlayerMode {
    /** Keyboard and all controllers drive a single shared local player (legacy behaviour). */
    KEYBOARD_OR_CONTROLLER("K | C"),
    /** Keyboard is the main player; each connected controller is an additional local player. */
    KEYBOARD_PLUS_CONTROLLERS("K + C"),
    /** Each connected controller is a local player; keyboard input is ignored. */
    CONTROLLERS_ONLY("C");

    private final String label;

    LocalPlayerMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
