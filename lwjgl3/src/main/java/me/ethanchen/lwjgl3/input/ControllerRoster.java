package me.ethanchen.lwjgl3.input;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.Controllers;

import me.ethanchen.game.GameConstants;

/**
 * Tracks up to {@link GameConstants#MAX_PLAYERS} controller slots, keyed by
 * {@link Controller#getUniqueId()} so a disconnect/reconnect resumes the same slot.
 */
public class ControllerRoster extends ControllerAdapter {

    private final String[] slotUniqueIds = new String[GameConstants.MAX_PLAYERS];
    private final Controller[] slotControllers = new Controller[GameConstants.MAX_PLAYERS];

    public ControllerRoster() {}

    /** Call once after the Controllers backend is ready (e.g. from {@code ClientApp.create()}). */
    public void seedFromConnected() {
        for (Controller c : Controllers.getControllers()) {
            connected(c);
        }
    }

    @Override
    public void connected(Controller controller) {
        if (controller == null) return;
        String uid = controller.getUniqueId();
        if (uid != null) {
            for (int i = 0; i < slotUniqueIds.length; i++) {
                if (uid.equals(slotUniqueIds[i])) {
                    slotControllers[i] = controller;
                    return;
                }
            }
        }
        // Prefer an empty slot (no uniqueId reserved).
        for (int i = 0; i < slotControllers.length; i++) {
            if (slotControllers[i] == null && slotUniqueIds[i] == null) {
                slotControllers[i] = controller;
                slotUniqueIds[i] = uid;
                return;
            }
        }
        // Reclaim a vacated slot (uniqueId retained but controller null).
        for (int i = 0; i < slotControllers.length; i++) {
            if (slotControllers[i] == null) {
                slotControllers[i] = controller;
                slotUniqueIds[i] = uid;
                return;
            }
        }
        // All four slots occupied by live controllers — ignore.
    }

    @Override
    public void disconnected(Controller controller) {
        if (controller == null) return;
        for (int i = 0; i < slotControllers.length; i++) {
            if (slotControllers[i] == controller) {
                slotControllers[i] = null;
                // Keep slotUniqueIds[i] so a reconnect resumes this slot.
                return;
            }
        }
    }

    /** Returns the slot index for {@code controller}, or {@code -1} if unknown. */
    public int slotOf(Controller controller) {
        if (controller == null) return -1;
        for (int i = 0; i < slotControllers.length; i++) {
            if (slotControllers[i] == controller) return i;
        }
        String uid = controller.getUniqueId();
        if (uid != null) {
            for (int i = 0; i < slotUniqueIds.length; i++) {
                if (uid.equals(slotUniqueIds[i])) return i;
            }
        }
        return -1;
    }

    public int getConnectedCount() {
        int n = 0;
        for (Controller c : slotControllers) {
            if (c != null) n++;
        }
        return n;
    }

    /** Occupied (live) slots in ascending order. */
    public int[] getOccupiedSlotsAscending() {
        int count = getConnectedCount();
        int[] out = new int[count];
        int j = 0;
        for (int i = 0; i < slotControllers.length; i++) {
            if (slotControllers[i] != null) out[j++] = i;
        }
        return out;
    }
}
