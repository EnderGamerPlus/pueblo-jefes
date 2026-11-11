package me.putindeer.pueblojefes;

import me.putindeer.pueblojefes.PuebloJefes;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tablas de botín. Cada tabla junta dos fuentes:
 * <ul>
 *   <li>Ítems vanilla escritos en la config del evento: {@code "MATERIAL peso min-max"}.</li>
 *   <li>Ítems agregados en el juego con /jefes loot agregar (guardados en loot.yml).
 *   Sirve para cualquier ítem especial de otros plugins: se guarda tal cual.</li>
 * </ul>
 */
public class LootTables {
    public record Entry(ItemStack item, int weight, int min, int max) {
    }

    private final PuebloJefes plugin;
    private final File file;
    private YamlConfiguration data;

    public LootTables(PuebloJefes plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "loot.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    // ------------------------------------------------------- ítems agregados

    public List<Entry> custom(String table) {
        List<Entry> entries = new ArrayList<>();
        for (Map<?, ?> raw : data.getMapList("tables." + table)) {
            if (!(raw.get("item") instanceof ItemStack item)) continue;
            entries.add(new Entry(item, toInt(raw.get("weight"), 1), toInt(raw.get("min"), 1), toInt(raw.get("max"), 1)));
        }
        return entries;
    }

    public void add(String table, ItemStack item, int weight, int min, int max) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Entry e : custom(table)) list.add(serialize(e));
        ItemStack copy = item.clone();
        copy.setAmount(1);
        list.add(serialize(new Entry(copy, weight, min, max)));
        data.set("tables." + table, list);
        save();
    }

    /** Quita la entrada número index (empezando en 1). */
    public boolean remove(String table, int index) {
        List<Entry> entries = custom(table);
        if (index < 1 || index > entries.size()) return false;
        entries.remove(index - 1);
        data.set("tables." + table, entries.stream().map(LootTables::serialize).toList());
        save();
        return true;
    }

    private static Map<String, Object> serialize(Entry e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("item", e.item());
        map.put("weight", e.weight());
        map.put("min", e.min());
        map.put("max", e.max());
        return map;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar loot.yml: " + ex.getMessage());
        }
    }

    public void reload() {
        data = YamlConfiguration.loadConfiguration(file);
    }

    // ---------------------------------------------------------------- tirar

    /**
     * Genera el botín: "rolls" tiradas (entre rolls-min y rolls-max de la sección) sobre los ítems vanilla
     * de section.items más los agregados en la tabla.
     */
    public List<ItemStack> roll(String table, ConfigurationSection section) {
        List<Entry> entries = new ArrayList<>(custom(table));
        if (section != null) {
            for (String line : section.getStringList("items")) {
                Entry entry = parseLine(line);
                if (entry != null) entries.add(entry);
            }
        }
        List<ItemStack> result = new ArrayList<>();
        int total = entries.stream().mapToInt(e -> Math.max(0, e.weight())).sum();
        if (total <= 0) return result;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int rollsMin = section != null ? section.getInt("rolls-min", 5) : 5;
        int rollsMax = Math.max(rollsMin, section != null ? section.getInt("rolls-max", 9) : 9);
        int rolls = random.nextInt(rollsMin, rollsMax + 1);

        for (int i = 0; i < rolls; i++) {
            int pick = random.nextInt(total);
            for (Entry entry : entries) {
                pick -= Math.max(0, entry.weight());
                if (pick >= 0) continue;
                ItemStack item = entry.item().clone();
                int amount = entry.max() <= entry.min() ? entry.min() : random.nextInt(entry.min(), entry.max() + 1);
                item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
                result.add(item);
                break;
            }
        }
        return result;
    }

    /** "DIAMOND 10 1-3" -> diamante, peso 10, de 1 a 3. */
    static Entry parseLine(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0) return null;
        Material material = Material.matchMaterial(parts[0]);
        if (material == null || !material.isItem()) return null;
        int weight = parts.length > 1 ? toInt(parts[1], 1) : 1;
        int min = 1, max = 1;
        if (parts.length > 2) {
            String[] range = parts[2].split("-", 2);
            min = toInt(range[0], 1);
            max = range.length > 1 ? toInt(range[1], min) : min;
        }
        return new Entry(new ItemStack(material), weight, min, Math.max(min, max));
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
