package me.putindeer.pueblojefes.boss;

import me.putindeer.pueblojefes.PuebloJefes;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Combate en curso contra un jefe: la máquina de estados (perseguir → aviso → ataque → recuperación),
 * las fases según la vida, y quién hizo cuánto daño. También trae utilidades para escribir ataques.
 */
public class BossFight {
    public enum State {CHASE, WINDUP, ACTIVE, RECOVER}

    private final PuebloJefes plugin;
    public final Boss boss;
    public final Mob entity;
    public final Location arena;
    private final ConfigurationSection config;
    private final Map<UUID, Double> damageByPlayer = new HashMap<>();
    private final Map<BossAttack, Integer> cooldowns = new HashMap<>();
    private final List<Double> phaseThresholds;

    private State state = State.CHASE;
    private BossAttack current;
    private int stateTick;
    private int phase = 1;
    private int globalCooldown = 40;
    private Player target;
    private Location attackPoint;
    private boolean dealingDamage;

    public BossFight(PuebloJefes plugin, Boss boss, Mob entity, Location arena, ConfigurationSection config) {
        this.plugin = plugin;
        this.boss = boss;
        this.entity = entity;
        this.arena = arena;
        this.config = config;
        List<Double> thresholds = new ArrayList<>(config.getDoubleList("phase-thresholds"));
        thresholds.sort(Comparator.reverseOrder());
        this.phaseThresholds = thresholds;
    }

    // ============================================================ consultas

    public State state() {
        return state;
    }

    public int phase() {
        return phase;
    }

    public Player target() {
        return target;
    }

    public ConfigurationSection config() {
        return config;
    }

    public PuebloJefes plugin() {
        return plugin;
    }

    /** Punto guardado por el ataque actual (ej. dónde caerá el golpe). */
    public Location attackPoint() {
        return attackPoint;
    }

    public void attackPoint(Location point) {
        this.attackPoint = point;
    }

    /** En fases avanzadas los avisos son más cortos: fase 2 = 85%, fase 3 = 70%... */
    public double speedFactor() {
        return Math.max(0.5, 1.0 - 0.15 * (phase - 1));
    }

    public double maxHealth() {
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        return health != null ? health.getValue() : entity.getHealth();
    }

    public Map<UUID, Double> damageByPlayer() {
        return damageByPlayer;
    }

    /** true mientras un ataque del jefe está haciendo daño (para no confundirlo con su golpe vanilla). */
    public boolean isDealingDamage() {
        return dealingDamage;
    }

    public boolean isVulnerable() {
        return state == State.RECOVER;
    }

    public void recordDamage(Player player, double amount) {
        damageByPlayer.merge(player.getUniqueId(), amount, Double::sum);
    }

    public double arenaRadius() {
        return config.getDouble("arena-radius", 40);
    }

    /** Jugadores en supervivencia dentro de la arena. */
    public List<Player> players() {
        List<Player> result = new ArrayList<>();
        double r2 = arenaRadius() * arenaRadius();
        for (Player player : arena.getWorld().getPlayers()) {
            if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                    && player.getLocation().distanceSquared(arena) <= r2) result.add(player);
        }
        return result;
    }

    // ================================================================= tick

    public void tick() {
        if (!entity.isValid() || entity.isDead()) return;
        updatePhase();
        chooseTarget();
        boss.tick(this);

        switch (state) {
            case CHASE -> {
                if (target != null) entity.getPathfinder().moveTo(target, config.getDouble("chase-speed", 1.1));
                if (globalCooldown > 0) globalCooldown--;
                cooldowns.replaceAll((a, t) -> Math.max(0, t - 1));
                if (globalCooldown <= 0 && target != null) pickAttack();
            }
            case WINDUP -> {
                current.telegraph(this, stateTick);
                if (++stateTick >= scaled(current.windupTicks())) switchTo(State.ACTIVE);
            }
            case ACTIVE -> {
                current.active(this, stateTick);
                if (++stateTick >= Math.max(1, current.activeTicks())) switchTo(State.RECOVER);
            }
            case RECOVER -> {
                if (stateTick % 5 == 0) {
                    entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().add(0, entity.getHeight(), 0), 6, 0.4, 0.2, 0.4, 0.05);
                }
                if (++stateTick >= scaled(current.recoverTicks())) {
                    cooldowns.put(current, current.cooldownTicks());
                    current = null;
                    globalCooldown = scaled(config.getInt("time-between-attacks", 20));
                    switchTo(State.CHASE);
                }
            }
        }
    }

    private int scaled(int ticks) {
        return Math.max(1, (int) Math.round(ticks * speedFactor()));
    }

    private void switchTo(State next) {
        state = next;
        stateTick = 0;
        boolean frozen = next != State.CHASE;
        entity.setAware(!frozen);
        if (frozen) entity.getPathfinder().stopPathfinding();
    }

    private void updatePhase() {
        double ratio = entity.getHealth() / maxHealth();
        int newPhase = 1;
        for (double threshold : phaseThresholds) {
            if (ratio <= threshold) newPhase++;
        }
        if (newPhase > phase) {
            phase = newPhase;
            boss.onPhaseChange(this, phase);
        }
    }

    private void chooseTarget() {
        Player closest = null;
        double best = Double.MAX_VALUE;
        for (Player player : players()) {
            double d = player.getLocation().distanceSquared(entity.getLocation());
            if (d < best) {
                best = d;
                closest = player;
            }
        }
        target = closest;
    }

    private void pickAttack() {
        double distance = target.getLocation().distance(entity.getLocation());
        Map<BossAttack, Integer> options = new LinkedHashMap<>();
        for (BossAttack attack : boss.attacks()) {
            if (attack.minPhase() > phase || cooldowns.getOrDefault(attack, 0) > 0) continue;
            if (distance < attack.minRange() || distance > attack.maxRange()) continue;
            options.put(attack, attack.weight());
        }
        if (options.isEmpty()) return;
        int total = options.values().stream().mapToInt(Integer::intValue).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (Map.Entry<BossAttack, Integer> option : options.entrySet()) {
            roll -= option.getValue();
            if (roll < 0) {
                current = option.getKey();
                break;
            }
        }
        attackPoint = target.getLocation();
        switchTo(State.WINDUP);
        current.start(this);
    }

    // ===================================================== utilidades para ataques

    /** Daña y empuja a los jugadores dentro del radio. Devuelve cuántos alcanzó. */
    public int hitPlayers(Location center, double radius, double damage, double knockback, double lift) {
        int hits = 0;
        for (Player player : players()) {
            if (player.getLocation().distanceSquared(center) > radius * radius) continue;
            dealingDamage = true;
            try {
                if (damage > 0) player.damage(damage * config.getDouble("damage-multiplier", 1.0), entity);
            } finally {
                dealingDamage = false;
            }
            Vector push = player.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (push.lengthSquared() < 0.01) push = new Vector(0, 0, 0.1);
            player.setVelocity(push.normalize().multiply(knockback).setY(lift));
            hits++;
        }
        return hits;
    }

    /** Anillo de partículas en el suelo (para avisar de un área). */
    public void ring(Location center, double radius, Particle particle) {
        int points = Math.max(12, (int) (radius * 8));
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Location p = center.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
            center.getWorld().spawnParticle(particle, p, 1, 0, 0, 0, 0);
        }
    }
}
