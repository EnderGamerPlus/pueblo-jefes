package me.putindeer.pueblojefes.boss;

import me.putindeer.pueblojefes.boss.bosses.FallenGuardianBoss;

import java.util.*;

/** Jefes disponibles. Para agregar uno nuevo: crear su clase en boss/bosses y añadirla aquí. */
public final class BossRegistry {
    private static final Map<String, Boss> BOSSES = new LinkedHashMap<>();

    static {
        register(new FallenGuardianBoss());
    }

    private BossRegistry() {
    }

    private static void register(Boss boss) {
        BOSSES.put(boss.id, boss);
    }

    public static Boss get(String id) {
        return id == null ? null : BOSSES.get(id.toLowerCase(Locale.ROOT));
    }

    public static Collection<String> ids() {
        return BOSSES.keySet();
    }
}
