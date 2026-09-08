package me.ethanchen.lwjgl3.input;

/**
 * Session-only setting controlling how keyboard and controllers map to local players.
 * Not persisted to settings.json.
 */
public enum LocalPlayerMode {
    /** Keyboard and all controllers drive a single shared local player (legacy behaviour). */
    KEYBOARD_OR_CONTROLLER("K | C",
            "Both keyboard and controller input to player 1 simultaneously"),
    /** Keyboard is the main player; each connected controller is an additional local player. */
    KEYBOARD_PLUS_CONTROLLERS("K + C",
            "Keyboard controls player 1, additional controllers input to local players 2, 3, 4"),
    /** Each connected controller is a local player; keyboard input is ignored. */
    CONTROLLERS_ONLY("C",
            "First controller controls player 1, additional controllers input to local players 2, 3, 4");

    private final String label;
    private final String description;

    LocalPlayerMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
