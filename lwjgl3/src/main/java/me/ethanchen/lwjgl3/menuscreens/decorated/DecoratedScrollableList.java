package me.ethanchen.lwjgl3.menuscreens.decorated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.badlogic.gdx.graphics.Color;

import me.ethanchen.game.GameMode;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.network.dto.RoomInfo;

/**
 * Discrete-slot list. Slot buttons stay in fixed design positions; scrolling only rebinds
 * which item each slot shows via {@link SlotBinder}.
 */
public class DecoratedScrollableList<T> extends DecoratedElement {
    @FunctionalInterface
    public interface SlotBinder<T> {
        void bind(DecoratedListButton slot, T item);
    }

    private final DecoratedListButton[] slots;
    private final int slotCount;
    private final SlotBinder<T> binder;
    private final List<T> items = new ArrayList<>();
    private int offset;
    private Consumer<T> onSelect;

    public DecoratedScrollableList(float designCenterX, float topSlotCenterY,
                                   float slotW, float slotH, int slotCount, float gap,
                                   SlotBinder<T> binder) {
        super(
                DesignUi.nx(designCenterX),
                DesignUi.ny(listCenterY(topSlotCenterY, slotH, slotCount, gap)),
                DesignUi.nw(slotW),
                DesignUi.nh(listHeight(slotH, slotCount, gap)));
        this.slotCount = Math.max(1, slotCount);
        this.binder = binder;
        this.slots = new DecoratedListButton[this.slotCount];
        this.focusable = false;
        for (int i = 0; i < this.slotCount; i++) {
            float slotY = topSlotCenterY - i * (slotH + gap);
            DecoratedListButton slot = new DecoratedListButton(designCenterX, slotY, slotW, slotH, "", null);
            slot.visible = false;
            slot.focusable = false;
            slots[i] = slot;
        }
        rebind();
    }

    public static DecoratedScrollableList<RoomInfo> rooms(float designCenterX, float topSlotCenterY,
                                                         float slotW, float slotH, int slotCount, float gap) {
        return new DecoratedScrollableList<>(designCenterX, topSlotCenterY, slotW, slotH, slotCount, gap,
                DecoratedScrollableList::bindRoom);
    }

    public DecoratedScrollableList<T> onSelect(Consumer<T> onSelect) {
        this.onSelect = onSelect;
        rebind();
        return this;
    }

    public void setItems(List<T> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        clampOffset();
        rebind();
    }

    public void refresh() {
        rebind();
    }

    public void scrollBy(int delta) {
        if (delta == 0 || items.size() <= slotCount) return;
        offset += delta;
        clampOffset();
        rebind();
    }

    public boolean canScroll() {
        return items.size() > slotCount;
    }

    public int offset() {
        return offset;
    }

    public int maxOffset() {
        return Math.max(0, items.size() - slotCount);
    }

    public int slotCount() {
        return slotCount;
    }

    public int itemCount() {
        return items.size();
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    @Override
    public List<Decorated> nestedFocusables() {
        List<Decorated> out = new ArrayList<>();
        for (DecoratedListButton slot : slots) {
            if (slot.isFocusable()) out.add(slot);
        }
        return out;
    }

    @Override
    public List<DecoratedElement> nestedElements() {
        List<DecoratedElement> out = new ArrayList<>(slotCount);
        Collections.addAll(out, slots);
        return out;
    }

    @Override
    public void updateDecorated(float dtS) {
        super.updateDecorated(dtS);
        for (DecoratedListButton slot : slots) {
            slot.updateDecorated(dtS);
        }
    }

    @Override
    public void renderDecorated(DecorContext ctx) {
        for (DecoratedListButton slot : slots) {
            slot.renderDecorated(ctx);
        }
    }

    @Override
    public void handleClick(int screenX, int screenY, int button) {
        for (DecoratedListButton slot : slots) {
            slot.handleClick(screenX, screenY, button);
        }
    }

    @Override
    public void handleDrag(int screenX, int screenY) {
        for (DecoratedListButton slot : slots) {
            slot.handleDrag(screenX, screenY);
        }
    }

    @Override
    public void handleRelease(int screenX, int screenY) {
        for (DecoratedListButton slot : slots) {
            slot.handleRelease(screenX, screenY);
        }
    }

    private void clampOffset() {
        int max = Math.max(0, items.size() - slotCount);
        if (offset < 0) offset = 0;
        if (offset > max) offset = max;
    }

    private void rebind() {
        for (int i = 0; i < slotCount; i++) {
            int index = offset + i;
            DecoratedListButton slot = slots[i];
            if (index >= items.size()) {
                clearSlot(slot);
                continue;
            }
            T item = items.get(index);
            slot.visible = true;
            slot.focusable = true;
            slot.icon = null;
            slot.text = "";
            slot.infoText = null;
            slot.fill(1f, 1f, 1f);
            if (binder != null) {
                binder.bind(slot, item);
            }
            slot.action = () -> {
                if (onSelect != null) onSelect.accept(item);
            };
        }
    }

    private static void clearSlot(DecoratedListButton slot) {
        slot.visible = false;
        slot.focusable = false;
        slot.text = "";
        slot.icon = null;
        slot.action = null;
        slot.infoText = null;
        slot.focused = false;
        slot.fill(1f, 1f, 1f);
    }

    private static float listHeight(float slotH, int slotCount, float gap) {
        int n = Math.max(1, slotCount);
        return n * slotH + Math.max(0, n - 1) * gap;
    }

    private static float listCenterY(float topSlotCenterY, float slotH, int slotCount, float gap) {
        return topSlotCenterY - (listHeight(slotH, slotCount, gap) - slotH) * 0.5f;
    }

    private static void bindRoom(DecoratedListButton slot, RoomInfo room) {
        slot.text = formatLabel(room);
        slot.info(formatInfo(room));
        applyRoomTint(slot, room.roomId);
    }

    /** Deterministic HSV tint so the same room id always gets the same hue. */
    static void applyRoomTint(DecoratedListButton slot, String roomId) {
        float hue = hueFromKey(roomId);
        Color c = new Color().fromHsv(hue * 360f, 0.55f, 0.95f);
        slot.fill(c.r, c.g, c.b);
    }

    static float hueFromKey(String key) {
        int h = key == null ? 0 : key.hashCode();
        h ^= (h >>> 16);
        h *= 0x7feb352d;
        h ^= (h >>> 15);
        h *= 0x846ca68b;
        h ^= (h >>> 16);
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    static String formatLabel(RoomInfo room) {
        if (room == null) return "";
        String id = room.roomId != null ? room.roomId : "?";
        String host = room.hostName != null && !room.hostName.isEmpty() ? room.hostName : "?";
        StringBuilder sb = new StringBuilder();
        sb.append(id).append("  ").append(host).append("  ").append(room.playerCount).append("p");
        if (room.inProgress) sb.append("  [IN PROGRESS]");
        return sb.toString();
    }

    static String formatInfo(RoomInfo room) {
        if (room == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(formatGamemode(room.gamemode));
        sb.append('\n');
        if (room.playerNames == null || room.playerNames.length == 0) {
            sb.append("No players");
        } else {
            boolean first = true;
            for (String name : room.playerNames) {
                if (name == null || name.isEmpty()) continue;
                if (!first) sb.append(", ");
                sb.append(name);
                first = false;
            }
            if (first) sb.append("No players");
        }
        return sb.toString();
    }

    static String formatGamemode(GameMode mode) {
        if (mode == null || mode == GameMode.NONE) return "Mode: —";
        switch (mode) {
            case MULTIPLAYER_SCORE:
                return "Mode: Score";
            case MULTIPLAYER_PUZZLE:
                return "Mode: Puzzle";
            case CHARACTER_SCORE:
                return "Mode: Character Score";
            case PVE:
                return "Mode: PvE";
            default:
                return "Mode: " + mode.name();
        }
    }
}
