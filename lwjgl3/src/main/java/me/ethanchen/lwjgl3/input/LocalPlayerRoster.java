package me.ethanchen.lwjgl3.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.ethanchen.game.GameConstants;

/**
 * Immutable snapshot of how keyboard/controllers map to local players for the current
 * {@link LocalPlayerMode} and {@link ControllerRoster} state.
 */
public final class LocalPlayerRoster {

    public enum InputSource {
        /** Keyboard only (controller events go to other local players). */
        KEYBOARD,
        /** Keyboard and every controller drive this single player ("K | C"). */
        KEYBOARD_AND_ANY_CONTROLLER,
        /** A specific controller slot drives this player. */
        CONTROLLER
    }

    public static final class Entry {
        public final InputSource source;
        /** Controller slot index when {@link #source} is {@link InputSource#CONTROLLER}; otherwise -1. */
        public final int controllerSlot;

        public Entry(InputSource source, int controllerSlot) {
            this.source = source;
            this.controllerSlot = controllerSlot;
        }
    }

    private final List<Entry> entries;

    private LocalPlayerRoster(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public static LocalPlayerRoster compute(LocalPlayerMode mode, ControllerRoster roster) {
        List<Entry> list = new ArrayList<>();
        if (mode == null) mode = LocalPlayerMode.KEYBOARD_OR_CONTROLLER;
        if (roster == null) {
            list.add(new Entry(InputSource.KEYBOARD_AND_ANY_CONTROLLER, -1));
            return new LocalPlayerRoster(list);
        }

        switch (mode) {
            case KEYBOARD_OR_CONTROLLER:
                list.add(new Entry(InputSource.KEYBOARD_AND_ANY_CONTROLLER, -1));
                break;
            case KEYBOARD_PLUS_CONTROLLERS:
                list.add(new Entry(InputSource.KEYBOARD, -1));
                for (int slot : roster.getOccupiedSlotsAscending()) {
                    if (list.size() >= GameConstants.MAX_PLAYERS) break;
                    list.add(new Entry(InputSource.CONTROLLER, slot));
                }
                break;
            case CONTROLLERS_ONLY:
                for (int slot : roster.getOccupiedSlotsAscending()) {
                    if (list.size() >= GameConstants.MAX_PLAYERS) break;
                    list.add(new Entry(InputSource.CONTROLLER, slot));
                }
                break;
            default:
                list.add(new Entry(InputSource.KEYBOARD_AND_ANY_CONTROLLER, -1));
                break;
        }
        return new LocalPlayerRoster(list);
    }
}
