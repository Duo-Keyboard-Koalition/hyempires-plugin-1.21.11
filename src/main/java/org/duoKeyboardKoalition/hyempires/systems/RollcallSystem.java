package org.duoKeyboardKoalition.hyempires.systems;

import org.bukkit.Location;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;

import java.util.*;

/**
 * Rollcall system using finite automata and artificial life approaches.
 * Each villager behaves uniquely based on their personality traits.
 */
public class RollcallSystem {
    private final HyEmpiresPlugin plugin;
    private final Map<UUID, VillagerBehaviorState> activeRollcalls = new HashMap<>();
    private final Map<UUID, BukkitTask> rollcallTasks = new HashMap<>();
    
    public RollcallSystem(HyEmpiresPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Start a rollcall for a village.
     * All villagers will be summoned to the bell with unique behaviors.
     */
    public void startRollcall(VillageManager.VillageData village, Location bellLocation) {
        // Cancel any existing rollcall for this village
        stopRollcall(village);
        
        // Find all villagers in the village
        List<Villager> villagers = getVillagersInVillage(village, bellLocation);
        
        if (villagers.isEmpty()) {
            return;
        }
        
        // Create behavior states for each villager
        Map<UUID, Location> originalLocations = new HashMap<>();
        for (Villager villager : villagers) {
            UUID uuid = villager.getUniqueId();
            originalLocations.put(uuid, villager.getLocation().clone());
            VillagerBehaviorState state = new VillagerBehaviorState(uuid, villager.getLocation());
            activeRollcalls.put(uuid, state);
        }
        
        // Start the behavior update task
        BukkitTask task = new BukkitRunnable() {
            private int tickCount = 0;
            private final Map<Integer, Location> formationPositions = calculateFormationPositions(bellLocation, villagers.size());
            
            @Override
            public void run() {
                tickCount++;
                
                // Update each villager's behavior
                int villagersInFormation = 0;
                for (Villager villager : villagers) {
                    if (!villager.isValid()) continue;
                    
                    UUID uuid = villager.getUniqueId();
                    VillagerBehaviorState state = activeRollcalls.get(uuid);
                    if (state == null) continue;
                    
                    // Count villagers in formation
                    if (state.getCurrentState() == VillagerBehaviorState.State.FORMATION) {
                        villagersInFormation++;
                    }
                }
                
                // Update each villager
                for (Villager villager : villagers) {
                    if (!villager.isValid()) continue;
                    
                    UUID uuid = villager.getUniqueId();
                    VillagerBehaviorState state = activeRollcalls.get(uuid);
                    if (state == null) continue;
                    
                    VillagerBehaviorState.State currentState = state.update(villager, bellLocation, villagersInFormation);
                    Location targetLoc = getTargetLocationForState(state, bellLocation, formationPositions, villagersInFormation);
                    
                    if (targetLoc != null) {
                        moveVillagerToLocation(villager, targetLoc, state);
                    }
                }
                
                // Check if all villagers have returned
                boolean allReturned = true;
                for (Villager villager : villagers) {
                    if (!villager.isValid()) continue;
                    VillagerBehaviorState state = activeRollcalls.get(villager.getUniqueId());
                    if (state != null && state.getCurrentState() != VillagerBehaviorState.State.IDLE &&
                        state.getCurrentState() != VillagerBehaviorState.State.RETURNING) {
                        allReturned = false;
                        break;
                    }
                }
                
                // Stop after 60 seconds or if all returned
                if (tickCount > 1200 || allReturned) { // 60 seconds at 20 ticks/second
                    stopRollcall(village);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick
        
        rollcallTasks.put(UUID.randomUUID(), task); // Use village name as key would be better, but UUID works
    }
    
    /**
     * Stop rollcall for a village.
     */
    public void stopRollcall(VillageManager.VillageData village) {
        // Return all villagers to their original locations
        for (Map.Entry<UUID, VillagerBehaviorState> entry : activeRollcalls.entrySet()) {
            Villager villager = (Villager) plugin.getServer().getEntity(entry.getKey());
            if (villager != null && villager.isValid()) {
                Location originalLoc = entry.getValue().getOriginalLocation();
                if (originalLoc != null) {
                    villager.teleport(originalLoc);
                }
            }
        }
        
        activeRollcalls.clear();
        
        // Cancel all tasks
        for (BukkitTask task : rollcallTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        rollcallTasks.clear();
    }
    
    /**
     * Get target location for villager's current state.
     */
    private Location getTargetLocationForState(VillagerBehaviorState state, Location bellLocation,
                                               Map<Integer, Location> formationPositions, int villagersInFormation) {
        VillagerBehaviorState.State currentState = state.getCurrentState();
        
        switch (currentState) {
            case APPROACHING:
                return bellLocation.clone().add(
                    (state.getPrecision() - 0.5) * 2.0, // Small random offset based on precision
                    0,
                    (state.getPrecision() - 0.5) * 2.0
                );
                
            case ARRIVED:
                return bellLocation.clone();
                
            case CIRCLE:
                // Circle around bell based on curiosity and time
                double angle = (System.currentTimeMillis() / 100.0) % (Math.PI * 2);
                double radius = 3.0 + state.getCuriosity() * 2.0;
                // Add offset based on villager UUID for unique circling pattern
                long uuidHash = state.getVillagerUUID().hashCode();
                angle += (uuidHash % 100) / 50.0; // Unique starting angle
                return bellLocation.clone().add(
                    Math.cos(angle) * radius,
                    0,
                    Math.sin(angle) * radius
                );
                
            case FORMATION:
                // Form a circle around the bell
                int position = state.getFormationPreference() % formationPositions.size();
                Location formationLoc = formationPositions.get(position);
                if (formationLoc != null) {
                    return formationLoc.clone();
                }
                return bellLocation.clone();
                
            case RETURNING:
                return state.getOriginalLocation();
                
            default:
                return null;
        }
    }
    
    /**
     * Move villager towards target location using their speed trait.
     */
    private void moveVillagerToLocation(Villager villager, Location target, VillagerBehaviorState state) {
        Location current = villager.getLocation();
        double distance = current.distance(target);
        
        if (distance < 0.5) {
            return; // Close enough
        }
        
        // Calculate direction
        double dx = target.getX() - current.getX();
        double dy = target.getY() - current.getY();
        double dz = target.getZ() - current.getZ();
        
        // Normalize and apply speed multiplier
        double speed = 0.3 * state.getSpeedMultiplier(); // Base speed * personality multiplier
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if (length > 0) {
            dx = (dx / length) * speed;
            dy = (dy / length) * speed;
            dz = (dz / length) * speed;
        }
        
        // Move villager
        Location newLoc = current.clone().add(dx, dy, dz);
        newLoc.setYaw((float) Math.toDegrees(Math.atan2(-dx, -dz))); // Face movement direction
        
        villager.teleport(newLoc);
    }
    
    /**
     * Calculate formation positions around the bell.
     */
    private Map<Integer, Location> calculateFormationPositions(Location center, int villagerCount) {
        Map<Integer, Location> positions = new HashMap<>();
        int positionsToCreate = Math.min(villagerCount, 8); // Max 8 positions
        
        for (int i = 0; i < positionsToCreate; i++) {
            double angle = (2 * Math.PI * i) / positionsToCreate;
            double radius = 4.0;
            Location pos = center.clone().add(
                Math.cos(angle) * radius,
                0,
                Math.sin(angle) * radius
            );
            positions.put(i, pos);
        }
        
        return positions;
    }
    
    /**
     * Get all villagers in a village.
     */
    private List<Villager> getVillagersInVillage(VillageManager.VillageData village, Location bellLocation) {
        List<Villager> villagers = new ArrayList<>();
        
        if (bellLocation == null || bellLocation.getWorld() == null) {
            return villagers;
        }
        
        // Find villagers within effective radius
        Collection<Villager> nearbyVillagers = new ArrayList<>();
        for (org.bukkit.entity.Entity entity : bellLocation.getWorld()
            .getNearbyEntities(bellLocation, village.effectiveRadius, 256, village.effectiveRadius)) {
            if (entity instanceof Villager) {
                nearbyVillagers.add((Villager) entity);
            }
        }
        
        villagers.addAll(nearbyVillagers);
        return villagers;
    }
    
}
