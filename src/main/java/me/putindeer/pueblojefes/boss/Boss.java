package me.putindeer.pueblojefes.boss;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;

import java.util.List;

/**
 * Definición de un jefe. Para crear uno nuevo: extender esta clase (ver FallenGuardianBoss como
 * ejemplo), registrarlo en BossRegistry y añadir su sección en config.yml (bosses.&lt;id&gt;).
 */
public abstract class Boss {
    public final String id;

    protected Boss(String id) {
        this.id = id;
    }

    /** Mob base que se usa como cuerpo del jefe. */
    public abstract EntityType entityType();

    /** Ataques disponibles (cada uno indica desde qué fase se usa). */
    public abstract List<BossAttack> attacks();

    /** Equipamiento, efectos, tamaño... al aparecer. */
    public void setup(Mob entity, ConfigurationSection config) {
    }

    /** Al entrar en una fase nueva (2, 3...): rugido, invocaciones, cambio de arma... */
    public void onPhaseChange(BossFight fight, int phase) {
    }

    /** Cada tick mientras está vivo (efectos de ambiente). */
    public void tick(BossFight fight) {
    }
}
