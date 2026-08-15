package me.ethanchen.game.pve.boss;

import java.util.HashMap;
import java.util.Map;

import me.ethanchen.game.pve.GarbageStyle;

/** Code-defined boss lookup by id (implementation.md, Part 3 / Part 4). */
public final class BossRegistry {
    private static final Map<Integer, BossDef> BY_ID = new HashMap<>();

    static {
        register(
            new BossDef(
                0,
                BossIntroAnim.FLOAT_IN,
                new BossPhaseDef(
                    6_000,
                    2000L,
                    new BossAttack[]{
                        BossAttack.addGarbage(
                            4000,
                            3000,
                            800,
                            600,
                            4,
                            GarbageStyle.DEFAULT,
                            true
                        )
                    },
                    8f,
                    48f
                ),
                new BossPhaseDef(
                    6_000,
                    2000L,
                    new BossAttack[]{
                        BossAttack.addGarbage(
                            4000,
                            3000,
                            800,
                            600,
                            4,
                            GarbageStyle.DEFAULT,
                            true
                        ),
                        BossAttack.addGarbage(
                            1000,
                            2000,
                            400,
                            200,
                            2,
                            GarbageStyle.DEFAULT,
                            false
                        ),
                        BossAttack.addGarbage(
                            1000,
                            2000,
                            400,
                            200,
                            2,
                            GarbageStyle.DEFAULT,
                            false
                        )
                    },
                    8f,
                    48f
                )
            )
        );
        register(
            new BossDef(
                1,
                BossIntroAnim.FLOAT_IN,
                new BossPhaseDef(
                    8_000,
                    2000L,
                    new BossAttack[]{
                        BossAttack.addGarbage(
                            4000,
                            2000,
                            800,
                            600,
                            4,
                            GarbageStyle.DEFAULT,
                            true
                        )
                    },
                    8f,
                    48f
                ),
                new BossPhaseDef(
                    8_000,
                    2000L,
                    new BossAttack[]{
                        BossAttack.addGarbage(
                            4000,
                            2000,
                            800,
                            600,
                            4,
                            GarbageStyle.DEFAULT,
                            true
                        ),
                        BossAttack.addGarbage(
                            1000,
                            1000,
                            400,
                            200,
                            2,
                            GarbageStyle.DEFAULT,
                            false
                        ),
                        BossAttack.addGarbage(
                            1000,
                            1000,
                            400,
                            200,
                            2,
                            GarbageStyle.DEFAULT,
                            false
                        )
                    },
                    8f,
                    48f
                )
            )
        );
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
