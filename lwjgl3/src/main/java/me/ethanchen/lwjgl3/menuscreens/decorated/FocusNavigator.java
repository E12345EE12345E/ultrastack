package me.ethanchen.lwjgl3.menuscreens.decorated;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered focus ring for keyboard / controller navigation. Arrow keys and the stick
 * walk the same ring as Tab. Mouse hover is a separate state and does not write this ring.
 */
public class FocusNavigator {
    private final List<Decorated> ring = new ArrayList<>();
    private int index = -1;

    public void setRing(List<Decorated> items) {
        Decorated prev = getFocused();
        ring.clear();
        if (items != null) {
            for (Decorated d : items) {
                if (d != null && d.isFocusable()) ring.add(d);
            }
        }
        if (ring.isEmpty()) {
            index = -1;
            if (prev != null) prev.setFocused(false);
            return;
        }
        int restored = prev != null ? ring.indexOf(prev) : -1;
        index = restored >= 0 ? restored : -1;
        if (prev != null && index < 0) prev.setFocused(false);
        apply();
    }

    public void next() {
        if (ring.isEmpty()) return;
        index = index < 0 ? 0 : (index + 1) % ring.size();
        apply();
    }

    public void prev() {
        if (ring.isEmpty()) return;
        index = index < 0 ? 0 : (index - 1 + ring.size()) % ring.size();
        apply();
    }

    public void focus(Decorated target) {
        if (target == null) return;
        int i = ring.indexOf(target);
        if (i < 0) return;
        index = i;
        apply();
    }

    public void clear() {
        index = -1;
        apply();
    }

    public void activate() {
        Decorated d = getFocused();
        if (d != null) d.activate();
    }

    public Decorated getFocused() {
        if (index < 0 || index >= ring.size()) return null;
        return ring.get(index);
    }

    public boolean isEmpty() {
        return ring.isEmpty();
    }

    private void apply() {
        for (int i = 0; i < ring.size(); i++) {
            ring.get(i).setFocused(i == index);
        }
    }
}
