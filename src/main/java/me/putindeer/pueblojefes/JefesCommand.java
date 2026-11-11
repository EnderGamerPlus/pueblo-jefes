package me.putindeer.pueblojefes;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.putindeer.pueblojefes.boss.BossRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /jefes invocar [jefe] | parar | lista | loot agregar|lista|quitar | recargar */
public class JefesCommand implements BasicCommand {
    private static final List<String> SUBCOMMANDS = List.of("invocar", "parar", "lista", "loot", "recargar");
    private final PuebloJefes plugin;

    public JefesCommand(PuebloJefes plugin) {
        this.plugin = plugin;
    }

    @Override
    public String permission() {
        return "pueblojefes.admin";
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, String @NotNull [] args) {
        CommandSender sender = source.getSender();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "invocar" -> {
                String error = plugin.bosses().start(args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : null);
                if (error != null) plugin.text().send(sender, error);
                else plugin.text().send(sender, "<green>Jefe invocado.");
            }
            case "parar" -> {
                if (!plugin.bosses().isActive()) {
                    plugin.text().send(sender, "<gray>No hay ningún jefe activo.");
                    return;
                }
                plugin.bosses().stop(true);
                plugin.text().send(sender, "<green>Jefe retirado.");
            }
            case "lista" -> plugin.text().send(sender, "<yellow>Jefes: <white>" + String.join(", ", BossRegistry.ids()));
            case "loot" -> loot(sender, args);
            case "recargar" -> {
                plugin.reloadConfig();
                plugin.loot().reload();
                plugin.text().send(sender, "<green>Configuración recargada.");
            }
            default -> plugin.text().send(sender, "<red>Uso: /jefes <" + String.join("|", SUBCOMMANDS) + ">");
        }
    }

    /** /jefes loot agregar <peso> [min] [max] | lista | quitar <n> (tabla "jefe") */
    private void loot(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "agregar" -> {
                if (!(sender instanceof Player player)) {
                    plugin.text().send(sender, "<red>Solo un jugador puede agregar (usa el ítem de la mano).");
                    return;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    plugin.text().send(sender, "<red>Ten en la mano el ítem que quieres agregar.");
                    return;
                }
                int weight = args.length > 2 ? parse(args[2], 10) : 10;
                int min = args.length > 3 ? parse(args[3], 1) : 1;
                int max = args.length > 4 ? parse(args[4], min) : min;
                plugin.loot().add("jefe", hand, Math.max(1, weight), Math.max(1, min), Math.max(min, max));
                plugin.text().send(sender, "<green>Agregado al botín del jefe (peso " + weight + ", cantidad " + min + "-" + max + ").");
            }
            case "lista" -> {
                List<LootTables.Entry> entries = plugin.loot().custom("jefe");
                plugin.text().send(sender, "<yellow>Ítems agregados al botín: " + entries.size() + " <gray>(los vanilla están en config.yml)");
                for (int i = 0; i < entries.size(); i++) {
                    LootTables.Entry e = entries.get(i);
                    plugin.text().send(sender, "<white>" + (i + 1) + ".</white> " + e.item().getType().name().toLowerCase(Locale.ROOT)
                            + " <gray>peso " + e.weight() + ", " + e.min() + "-" + e.max());
                }
            }
            case "quitar" -> {
                int index = args.length > 2 ? parse(args[2], -1) : -1;
                if (plugin.loot().remove("jefe", index)) plugin.text().send(sender, "<green>Entrada " + index + " quitada.");
                else plugin.text().send(sender, "<red>Número inválido. Mira /jefes loot lista.");
            }
            default -> plugin.text().send(sender, "<red>Uso: /jefes loot <agregar <peso> [min] [max]|lista|quitar <n>>");
        }
    }

    private static int parse(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, String @NotNull [] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invocar")) return new ArrayList<>(BossRegistry.ids());
        if (args.length == 2 && args[0].equalsIgnoreCase("loot")) return List.of("agregar", "lista", "quitar");
        return List.of();
    }
}
