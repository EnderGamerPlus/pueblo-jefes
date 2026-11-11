package me.putindeer.pueblojefes;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Pueblo Jefes: jefes con fases y ataques anunciados (estilo Dark Souls).
 * Se invocan con /jefes invocar [jefe]; Pueblo Eventos lo hace solo según su horario semanal.
 */
public final class PuebloJefes extends JavaPlugin {
    private Text text;
    private LootTables loot;
    private BossManager bosses;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        text = new Text(this);
        loot = new LootTables(this);
        bosses = new BossManager(this);
        getServer().getPluginManager().registerEvents(bosses, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands ->
                commands.registrar().register("jefes", "Invoca y gestiona los jefes.", List.of("jefe"), new JefesCommand(this)));
    }

    @Override
    public void onDisable() {
        if (bosses != null) bosses.stop(false);
    }

    public Text text() {
        return text;
    }

    public LootTables loot() {
        return loot;
    }

    public BossManager bosses() {
        return bosses;
    }
}
