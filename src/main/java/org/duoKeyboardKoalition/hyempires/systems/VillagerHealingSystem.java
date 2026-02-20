package org.duoKeyboardKoalition.hyempires.systems;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.managers.ChunkTerritoryManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profession-based healing: smiths (armorer/weaponsmith) heal iron golems;
 * clerics throw splash healing when health is critical and splash regeneration when health >= 90%.
 * Targets villagers and iron golems in the same village.
 */
public class VillagerHealingSystem {
    private final HyEmpiresPlugin plugin;
    private final ChunkTerritoryManager chunkTerritoryManager;

    private static final double SMITH_HEAL_RANGE = 6.0;
    private static final double CLERIC_RANGE = 10.0;
    private static final double CRITICAL_HEALTH_RATIO = 0.25;   // below 25% = critical
    private static final double REGEN_HEALTH_RATIO = 0.90;      // at or above 90% = throw regen
    private static final double SMITH_HEAL_AMOUNT = 10.0;       // HP per tick when smith "repairs" golem
    private static final long SMITH_HEAL_COOLDOWN_MS = 8_000;   // 8 seconds between heals per golem
    private static final long CLERIC_POTION_COOLDOWN_MS = 6_000; // 6 seconds between potion throws per cleric
    private static final long TASK_INTERVAL_TICKS = 60L;        // run every 3 seconds

    /** Golem UUID -> last time a smith healed it */
    private final Map<UUID, Long> golemSmithHealCooldown = new ConcurrentHashMap<>();
    /** Cleric UUID -> last time they threw a potion */
    private final Map<UUID, Long> clericPotionCooldown = new ConcurrentHashMap<>();

    public VillagerHealingSystem(HyEmpiresPlugin plugin) {
        this.plugin = plugin;
        this.chunkTerritoryManager = plugin.getChunkTerritoryManager();
        startTask();
    }

    private void startTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TASK_INTERVAL_TICKS, TASK_INTERVAL_TICKS);
    }

    private void tick() {
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (!villager.isValid() || !villager.isAdult()) continue;
                Villager.Profession prof = villager.getProfession();
                if (prof == null) continue;
                String village = chunkTerritoryManager.getVillageForLocation(villager.getLocation());
                if (village == null) continue;

                if (prof == Villager.Profession.ARMORER || prof == Villager.Profession.WEAPONSMITH) {
                    trySmithHealGolem(villager, village, world);
                } else if (prof == Villager.Profession.CLERIC) {
                    tryClericThrowPotion(villager, village, world);
                }
            }
        }
    }

    /** Smiths heal nearby damaged iron golems in the same village. */
    private void trySmithHealGolem(Villager smith, String village, World world) {
        Location smithLoc = smith.getLocation();
        for (Entity entity : smith.getNearbyEntities(SMITH_HEAL_RANGE, SMITH_HEAL_RANGE, SMITH_HEAL_RANGE)) {
            if (!(entity instanceof IronGolem golem)) continue;
            if (!golem.isValid() || golem.isDead()) continue;
            String golemVillage = chunkTerritoryManager.getVillageForLocation(golem.getLocation());
            if (!village.equals(golemVillage)) continue;

            double maxHp = golem.getAttribute(Attribute.MAX_HEALTH) != null
                ? golem.getAttribute(Attribute.MAX_HEALTH).getValue()
                : 100.0;
            double current = golem.getHealth();
            if (current >= maxHp) continue;

            UUID golemId = golem.getUniqueId();
            long now = System.currentTimeMillis();
            if (now - golemSmithHealCooldown.getOrDefault(golemId, 0L) < SMITH_HEAL_COOLDOWN_MS) continue;
            golemSmithHealCooldown.put(golemId, now);

            double newHp = Math.min(maxHp, current + SMITH_HEAL_AMOUNT);
            golem.setHealth(newHp);
            world.playSound(smithLoc, Sound.BLOCK_ANVIL_USE, 0.4f, 1.2f);
        }
    }

    /** Clerics throw splash healing when target health is critical; splash regeneration when health >= 90%. */
    private void tryClericThrowPotion(Villager cleric, String village, World world) {
        UUID clericId = cleric.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - clericPotionCooldown.getOrDefault(clericId, 0L) < CLERIC_POTION_COOLDOWN_MS) return;

        LivingEntity criticalTarget = null;  // below 25% -> splash healing
        LivingEntity regenTarget = null;    // >= 90% -> splash regeneration

        for (Entity entity : cleric.getNearbyEntities(CLERIC_RANGE, CLERIC_RANGE, CLERIC_RANGE)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living.isDead() || !living.isValid()) continue;
            if (living instanceof Player) continue;
            boolean isVillager = living instanceof Villager;
            boolean isGolem = living instanceof IronGolem;
            if (!isVillager && !isGolem) continue;

            String targetVillage = chunkTerritoryManager.getVillageForLocation(living.getLocation());
            if (!village.equals(targetVillage)) continue;

            double maxHp = living.getAttribute(Attribute.MAX_HEALTH) != null
                ? living.getAttribute(Attribute.MAX_HEALTH).getValue()
                : (living instanceof IronGolem ? 100.0 : 20.0);
            double current = living.getHealth();
            double ratio = current / maxHp;

            if (ratio < CRITICAL_HEALTH_RATIO) {
                criticalTarget = living;
                break; // prefer healing first
            }
            if (ratio >= REGEN_HEALTH_RATIO && regenTarget == null) {
                regenTarget = living;
            }
        }

        if (criticalTarget != null) {
            throwSplashPotion(cleric, criticalTarget, world, true);
            clericPotionCooldown.put(clericId, now);
        } else if (regenTarget != null) {
            throwSplashPotion(cleric, regenTarget, world, false);
            clericPotionCooldown.put(clericId, now);
        }
    }

    private void throwSplashPotion(Villager cleric, LivingEntity target, World world, boolean healing) {
        Location from = cleric.getEyeLocation();
        Location to = target.getLocation().add(0, target.getHeight() * 0.5, 0);
        Vector dir = to.toVector().subtract(from.toVector()).normalize();
        double speed = 0.6;

        ItemStack potionItem = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) potionItem.getItemMeta();
        if (meta == null) return;
        if (healing) {
            meta.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 1, true, true), true);
        } else {
            meta.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 22, 0, true, true), true);
        }
        potionItem.setItemMeta(meta);

        ThrownPotion thrown = (ThrownPotion) world.spawnEntity(from, EntityType.SPLASH_POTION);
        thrown.setItem(potionItem);
        thrown.setShooter(cleric);
        thrown.setVelocity(dir.multiply(speed));
    }
}
