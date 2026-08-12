package me.ethanchen.game.pve.boss;

import java.util.HashMap;
import java.util.Map;

import me.ethanchen.game.pve.GarbageStyle;

/** Code-defined boss lookup by id (implementation.md, Part 3 / Part 4). */
public final class BossRegistry {
    private static final Map<Integer, BossDef> BY_ID = new HashMap<>();

    static {
        // Boss 0: 10k HP; idle 5s, windup 2s / attack 1s adding 4 garbage, interrupt at 800 score.
        register(new BossDef(0, 10_000, 2000L, new BossAttack[]{
                BossAttack.addGarbage(5000, 2000, 1000, 800, 4, GarbageStyle.DEFAULT)
        }));
    }

    private BossRegistry() {}

    public static void register(BossDef def) {
        if (def == null) return;
        BY_ID.put(def.id, def);
    }

    public static BossDef byId(int id) {
        return BY_ID.get(id);
    }
}
