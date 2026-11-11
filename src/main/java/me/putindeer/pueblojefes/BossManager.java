package me.putindeer.pueblojefes;

import me.putindeer.pueblojefes.boss.Boss;
import me.putindeer.pueblojefes.boss.BossFight;
import me.putindeer.pueblojefes.boss.BossRegistry;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Lleva el combate contra un jefe: aviso previo, aparición en la arena, máquina de estados del jefe
 * (ver {@link BossFight}), barra de vida, tiempo máximo y botín para quienes le hicieron daño.
 * Solo puede haber un jefe a la vez.
 */
public class BossManager implements Listener {
    private enum Outcome {VICTORY, RETREAT}

    private final PuebloJefes plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<Chunk> tickets = new ArrayList<>();
    private BossFight fight;
    private BossBar bar;
    private String bossName;
    private boolean active;

    public BossManager(PuebloJefes plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection config() {
        return plugin.getConfig();
    }

    private String msg(String key) {
        return config().getString("messages." + key, "");
    }

    private ConfigurationSection bossConfig(String id) {
        ConfigurationSection section = config().getConfigurationSection("bosses." + id);
        return section != null ? section : new MemoryConfiguration();
    }

    public boolean isActive() {
        return active;
    }

    // ================================================================ inicio

    /** Empieza el aviso y, después, hace aparecer al jefe. Devuelve un error o null si todo va bien. */
    public String start(String requestedId) {
        if (active) return msg("already-active");
        String id = requestedId != null ? requestedId : config().getString("default-boss", "guardian");
        Boss boss = BossRegistry.get(id);
        World world = Bukkit.getWorld(config().getString("arena.world", "world"));
        if (boss == null) return Text.fill(msg("unknown-boss"), "boss", id, "bosses", String.join(", ", BossRegistry.ids()));
        if (world == null) return msg("no-world");

        active = true;
        bossName = bossConfig(id).getString("name", id);
        int warning = Math.max(0, config().getInt("warning-seconds", 60));
        plugin.text().broadcast(Text.fill(msg("warning"), "boss", bossName, "seconds", warning));
        later(() -> spawn(boss, id, world), warning * 20L);
        return null;
    }

    private void spawn(Boss boss, String id, World world) {
        Location arena = new Location(world, config().getDouble("arena.x"), config().getDouble("arena.y", 80), config().getDouble("arena.z"));
        ConfigurationSection settings = bossConfig(id);

        int chunkRadius = (int) Math.ceil(settings.getDouble("arena-radius", 40) / 16.0);
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Chunk chunk = world.getChunkAt((arena.getBlockX() >> 4) + dx, (arena.getBlockZ() >> 4) + dz);
                chunk.addPluginChunkTicket(plugin);
                tickets.add(chunk);
            }
        }

        Entity spawned = world.spawnEntity(arena, boss.entityType(), CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!(spawned instanceof Mob mob)) {
            spawned.remove();
            plugin.getLogger().warning("El jefe '" + id + "' no es un mob válido.");
            finish(Outcome.RETREAT, false);
            return;
        }
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.customName(plugin.text().parse(bossName));
        mob.setCustomNameVisible(true);
        boss.setup(mob, settings);
        AttributeInstance health = mob.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) health.setBaseValue(settings.getDouble("health", 600));
        mob.setHealth(health != null ? health.getValue() : mob.getHealth());

        fight = new BossFight(plugin, boss, mob, arena, settings);
        bar = BossBar.bossBar(plugin.text().parse(bossName), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_20);

        world.strikeLightningEffect(arena);
        world.playSound(arena, Sound.ENTITY_ENDER_DRAGON_GROWL, 5f, 0.6f);
        plugin.text().titleAll(Text.fill(msg("start-title"), "boss", bossName), msg("start-subtitle"), Sound.ENTITY_WITHER_SPAWN);
        plugin.text().broadcast(Text.fill(msg("start"), "boss", bossName, "x", arena.getBlockX(), "y", arena.getBlockY(), "z", arena.getBlockZ()));

        repeat(this::tick, 1L, 1L);
        int duration = Math.max(60, config().getInt("duration-seconds", 1200));
        later(() -> finish(Outcome.RETREAT, true), duration * 20L);
    }

    private void tick() {
        if (fight == null || !fight.entity.isValid()) return;
        fight.tick();

        // Si sale de la arena, vuelve.
        double radius = fight.arenaRadius();
        if (fight.entity.getLocation().distanceSquared(fight.arena) > radius * radius) fight.entity.teleport(fight.arena);

        if (fight.entity.getTicksLived() % 5 == 0 && bar != null) {
            bar.progress((float) Math.max(0, Math.min(1, fight.entity.getHealth() / fight.maxHealth())));
            bar.name(plugin.text().parse(Text.fill(msg("bossbar"), "boss", bossName, "phase", fight.phase())));
            double show = radius * 1.5;
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean near = player.getWorld().equals(fight.arena.getWorld())
                        && player.getLocation().distanceSquared(fight.arena) < show * show;
                if (near) player.showBossBar(bar);
                else player.hideBossBar(bar);
            }
        }
    }

    // ================================================================= final

    /** Termina el combate (al apagar el servidor, sin anunciar). */
    public void stop(boolean announce) {
        if (active) finish(Outcome.RETREAT, announce);
    }

    private void finish(Outcome outcome, boolean announce) {
        if (!active) return;
        active = false;
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        if (bar != null) Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bar));
        bar = null;
        if (fight != null && fight.entity.isValid()) fight.entity.remove();
        fight = null;
        tickets.forEach(c -> c.removePluginChunkTicket(plugin));
        tickets.clear();
        if (announce) plugin.text().broadcast(Text.fill(msg(outcome == Outcome.VICTORY ? "victory" : "retreat"), "boss", bossName));
    }

    private void later(Runnable task, long ticks) {
        tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin, task, Math.max(0, ticks)));
    }

    private void repeat(Runnable task, long delay, long period) {
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, task, delay, Math.max(1, period)));
    }

    // =============================================================== eventos

    private boolean isBoss(Entity entity) {
        return fight != null && entity.equals(fight.entity);
    }

    /** Su golpe vanilla no cuenta: solo hace daño con sus ataques anunciados. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBossHits(EntityDamageByEntityEvent event) {
        if (isBoss(event.getDamager()) && !fight.isDealingDamage() && config().getBoolean("cancel-vanilla-attacks", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamaged(EntityDamageEvent event) {
        if (!isBoss(event.getEntity())) return;
        switch (event.getCause()) {
            case FALL, SUFFOCATION, DROWNING, FIRE, FIRE_TICK, LAVA, CRAMMING -> {
                event.setCancelled(true);
                return;
            }
            default -> {
            }
        }
        // Ventana de castigo: después de atacar recibe más daño.
        if (fight.isVulnerable()) event.setDamage(event.getDamage() * config().getDouble("vulnerable-multiplier", 1.5));

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = null;
            if (byEntity.getDamager() instanceof Player player) attacker = player;
            else if (byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) attacker = shooter;
            if (attacker != null) fight.recordDamage(attacker, Math.min(event.getFinalDamage(), fight.entity.getHealth()));
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (!isBoss(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(config().getInt("xp", 500));
        reward();
        plugin.getServer().getScheduler().runTask(plugin, () -> finish(Outcome.VICTORY, true));
    }

    private void reward() {
        double max = fight.maxHealth();
        double minPercent = config().getDouble("min-damage-percent", 5) / 100.0;
        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(fight.damageByPlayer().entrySet());
        ranking.sort(Map.Entry.<UUID, Double>comparingByValue().reversed());

        plugin.text().broadcast(msg("ranking-title"));
        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(ranking.get(i).getKey());
            plugin.text().broadcast(Text.fill(msg("ranking-line"), "pos", i + 1, "player", player.getName(),
                    "percent", Math.round(ranking.get(i).getValue() / max * 100)));
        }

        for (Map.Entry<UUID, Double> entry : ranking) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || entry.getValue() < max * minPercent) continue;
            for (ItemStack item : plugin.loot().roll("jefe", config().getConfigurationSection("reward"))) {
                player.getInventory().addItem(item).values()
                        .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            }
            for (String command : config().getStringList("reward.commands")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Text.fill(command, "player", player.getName()));
            }
            plugin.text().send(player, msg("reward"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }
}
