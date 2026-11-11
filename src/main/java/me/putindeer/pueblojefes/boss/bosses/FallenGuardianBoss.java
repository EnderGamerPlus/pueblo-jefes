package me.putindeer.pueblojefes.boss.bosses;

import me.putindeer.pueblojefes.boss.Boss;
import me.putindeer.pueblojefes.boss.BossAttack;
import me.putindeer.pueblojefes.boss.BossFight;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Jefe de EJEMPLO: el Guardián Caído (esqueleto wither gigante). Sirve de plantilla para crear otros.
 * <ul>
 *   <li>Fase 1: Golpe sísmico (cerca) y Embestida (lejos).</li>
 *   <li>Fase 2 (60% de vida): añade Lluvia de fuego sobre los jugadores.</li>
 *   <li>Fase 3 (30%): invoca escoltas y ataca más rápido (los avisos se acortan solos).</li>
 * </ul>
 */
public class FallenGuardianBoss extends Boss {
    private final List<BossAttack> attacks = List.of(new Slam(), new Charge(), new FireRain());

    public FallenGuardianBoss() {
        super("guardian");
    }

    @Override
    public EntityType entityType() {
        return EntityType.WITHER_SKELETON;
    }

    @Override
    public List<BossAttack> attacks() {
        return attacks;
    }

    @Override
    public void setup(Mob entity, ConfigurationSection config) {
        setAttribute(entity, Attribute.SCALE, config.getDouble("scale", 2.0));
        setAttribute(entity, Attribute.KNOCKBACK_RESISTANCE, 1.0);
        setAttribute(entity, Attribute.MOVEMENT_SPEED, 0.28);
        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            equipment.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            equipment.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            equipment.setItemInMainHandDropChance(0f);
            equipment.setHelmetDropChance(0f);
            equipment.setChestplateDropChance(0f);
        }
        if (entity instanceof WitherSkeleton skeleton) skeleton.setShouldBurnInDay(false);
    }

    private static void setAttribute(Mob entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    @Override
    public void onPhaseChange(BossFight fight, int phase) {
        Location at = fight.entity.getLocation();
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_WITHER_SPAWN, 3f, 0.6f);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0, 2, 0), 120, 2, 2, 2, 0.1);
        // Onda expansiva que aparta a todos (sin daño).
        fight.hitPlayers(at, 7, 0, 1.6, 0.5);

        if (phase >= 3) {
            int adds = fight.config().getInt("phase3-adds", 3);
            for (int i = 0; i < adds; i++) {
                Location spot = at.clone().add(ThreadLocalRandom.current().nextDouble(-4, 4), 0, ThreadLocalRandom.current().nextDouble(-4, 4));
                world.spawnEntity(spot, EntityType.WITHER_SKELETON);
                world.spawnParticle(Particle.LARGE_SMOKE, spot, 20, 0.3, 1, 0.3, 0.02);
            }
        }
    }

    @Override
    public void tick(BossFight fight) {
        if (fight.phase() >= 2 && ThreadLocalRandom.current().nextInt(4) == 0) {
            fight.entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, fight.entity.getLocation().add(0, 1.5, 0),
                    2, 0.4, 0.8, 0.4, 0.01);
        }
    }

    // ============================================================== ataques

    /** Levanta el arma y golpea el suelo: daño en área alrededor suyo. Aviso: anillo rojo que se cierra. */
    private static final class Slam implements BossAttack {
        private static final Particle.DustOptions RED = new Particle.DustOptions(Color.RED, 1.5f);

        public String name() {
            return "Golpe sísmico";
        }

        public double minRange() {
            return 0;
        }

        public double maxRange() {
            return 6;
        }

        public int windupTicks() {
            return 30;
        }

        public int recoverTicks() {
            return 30;
        }

        public int cooldownTicks() {
            return 60;
        }

        public void start(BossFight fight) {
            fight.attackPoint(fight.entity.getLocation());
            fight.entity.getWorld().playSound(fight.entity.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.7f);
        }

        public void telegraph(BossFight fight, int tick) {
            double radius = 5.5 - 4.0 * tick / Math.max(1, windupTicks());
            Location center = fight.attackPoint();
            int points = 40;
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                center.getWorld().spawnParticle(Particle.DUST, center.clone().add(Math.cos(angle) * 5.5, 0.1, Math.sin(angle) * 5.5), 1, RED);
            }
            fight.ring(center, Math.max(0.5, radius), Particle.CRIT);
        }

        public void active(BossFight fight, int tick) {
            Location center = fight.attackPoint();
            center.getWorld().spawnParticle(Particle.EXPLOSION, center, 6, 2, 0.2, 2);
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
            fight.hitPlayers(center, 5.5, fight.config().getDouble("attacks.slam-damage", 10), 1.2, 0.6);
        }
    }

    /** Se agacha apuntando a un jugador y embiste en línea recta. Aviso: línea de partículas hacia el objetivo. */
    private static final class Charge implements BossAttack {
        private final Set<UUID> hit = new HashSet<>();
        private Vector direction;

        public String name() {
            return "Embestida";
        }

        public double minRange() {
            return 6;
        }

        public double maxRange() {
            return 22;
        }

        public int windupTicks() {
            return 25;
        }

        public int activeTicks() {
            return 14;
        }

        public int recoverTicks() {
            return 35;
        }

        public int cooldownTicks() {
            return 80;
        }

        public void start(BossFight fight) {
            hit.clear();
            Location from = fight.entity.getLocation();
            Location to = fight.target() != null ? fight.target().getLocation() : from.clone().add(from.getDirection());
            direction = to.toVector().subtract(from.toVector()).setY(0);
            if (direction.lengthSquared() < 0.01) direction = new Vector(1, 0, 0);
            direction.normalize();
            Location facing = from.clone().setDirection(direction);
            fight.entity.teleport(facing);
            from.getWorld().playSound(from, Sound.ENTITY_HORSE_ANGRY, 2f, 0.5f);
        }

        public void telegraph(BossFight fight, int tick) {
            Location from = fight.entity.getLocation().add(0, 0.2, 0);
            for (double d = 1; d <= 18; d += 0.75) {
                from.getWorld().spawnParticle(Particle.FLAME, from.clone().add(direction.clone().multiply(d)), 1, 0.05, 0, 0.05, 0);
            }
        }

        public void active(BossFight fight, int tick) {
            fight.entity.setVelocity(direction.clone().multiply(1.3).setY(-0.1));
            Location at = fight.entity.getLocation();
            at.getWorld().spawnParticle(Particle.CLOUD, at, 6, 0.4, 0.2, 0.4, 0.01);
            for (Player player : fight.players()) {
                if (hit.contains(player.getUniqueId()) || player.getLocation().distanceSquared(at) > 2.5 * 2.5) continue;
                hit.add(player.getUniqueId());
                fight.hitPlayers(player.getLocation(), 0.5, fight.config().getDouble("attacks.charge-damage", 12), 1.8, 0.7);
            }
        }
    }

    /** Fase 2+: marca el suelo bajo cada jugador y hace caer fuego. Aviso: círculos de llamas. */
    private static final class FireRain implements BossAttack {
        private final List<Location> spots = new ArrayList<>();

        public String name() {
            return "Lluvia de fuego";
        }

        public int minPhase() {
            return 2;
        }

        public double minRange() {
            return 0;
        }

        public double maxRange() {
            return 30;
        }

        public int weight() {
            return 8;
        }

        public int windupTicks() {
            return 40;
        }

        public int recoverTicks() {
            return 40;
        }

        public int cooldownTicks() {
            return 140;
        }

        public void start(BossFight fight) {
            spots.clear();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (Player player : fight.players()) {
                spots.add(player.getLocation());
                spots.add(player.getLocation().add(random.nextDouble(-4, 4), 0, random.nextDouble(-4, 4)));
            }
            fight.entity.getWorld().playSound(fight.entity.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 3f, 0.5f);
        }

        public void telegraph(BossFight fight, int tick) {
            if (tick % 2 != 0) return;
            for (Location spot : spots) {
                fight.ring(spot, 2.5, Particle.FLAME);
                spot.getWorld().spawnParticle(Particle.LAVA, spot.clone().add(0, 6, 0), 1, 0.5, 0.5, 0.5);
            }
        }

        public void active(BossFight fight, int tick) {
            for (Location spot : spots) {
                spot.getWorld().spawnParticle(Particle.EXPLOSION, spot, 2, 0.5, 0.2, 0.5);
                spot.getWorld().spawnParticle(Particle.FLAME, spot, 40, 1.2, 0.3, 1.2, 0.05);
                spot.getWorld().playSound(spot, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.7f);
                for (Player player : fight.players()) {
                    if (player.getLocation().distanceSquared(spot) <= 2.5 * 2.5) player.setFireTicks(Math.max(player.getFireTicks(), 80));
                }
                fight.hitPlayers(spot, 2.5, fight.config().getDouble("attacks.fire-rain-damage", 8), 0.4, 0.4);
            }
        }
    }
}
