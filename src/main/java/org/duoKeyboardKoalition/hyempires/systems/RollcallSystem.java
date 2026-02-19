package org.duoKeyboardKoalition.hyempires.systems;

import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;

import java.util.*;

/**
 * Rollcall system: villagers pathfind to assigned safe spots around the bell.
 * Uses pathfinding (no wall phasing). Villagers make way when players step through.
 */
public class RollcallSystem {
    private final HyEmpiresPlugin plugin;
    private final Map<UUID, VillagerBehaviorState> activeRollcalls = new HashMap<>();
    private final Map<UUID, Location> villagerAssignedSpots = new HashMap<>();
    private final Map<String, BukkitTask> villageRollcallTasks = new HashMap<>();
    
    private static final double ARRIVAL_DISTANCE = 1.2;
    private static final double MAKE_WAY_RANGE = 2.5;
    private static final long ROLLCALL_DURATION_MS = 30000;
    private static final double RETREAT_DISTANCE = 3.0;
    
    public RollcallSystem(HyEmpiresPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Start a rollcall. Scans for safe blocks, assigns villagers, and uses pathfinding.
     */
    public void startRollcall(VillageManager.VillageData village, Location bellLocation) {
        stopRollcall(village);
        
        List<Villager> villagers = getVillagersInVillage(village, bellLocation);
        if (villagers.isEmpty()) return;
        
        // Scan for safe blocks around the bell
        List<Location> safeSpots = SafeBlockScanner.scanSafeBlocksAroundBell(bellLocation);
        if (safeSpots.isEmpty()) {
            plugin.getLogger().warning("No safe blocks found around bell for rollcall!");
            return;
        }
        
        // Assign each villager to a spot (1:1)
        Map<UUID, Location> assignments = new HashMap<>();
        List<Location> usedSpots = new ArrayList<>();
        
        for (int i = 0; i < villagers.size(); i++) {
            Villager v = villagers.get(i);
            Location spot = i < safeSpots.size() ? safeSpots.get(i) : safeSpots.get(i % safeSpots.size());
            assignments.put(v.getUniqueId(), spot.clone());
            usedSpots.add(spot);
        }
        
        villagerAssignedSpots.clear();
        villagerAssignedSpots.putAll(assignments);
        
        // Create behavior states
        for (Villager villager : villagers) {
            UUID uuid = villager.getUniqueId();
            VillagerBehaviorState state = new VillagerBehaviorState(uuid, villager.getLocation().clone());
            state.setAssignedSpot(assignments.get(uuid));
            state.transitionTo(VillagerBehaviorState.State.APPROACHING);
            activeRollcalls.put(uuid, state);
        }
        
        long rollcallStartTime = System.currentTimeMillis();
        
        BukkitTask task = new BukkitRunnable() {
            int tickCount = 0;
            int pathRefreshTick = 0;
            
            @Override
            public void run() {
                tickCount++;
                pathRefreshTick++;
                long elapsed = System.currentTimeMillis() - rollcallStartTime;
                
                for (Villager villager : villagers) {
                    if (!villager.isValid()) continue;
                    
                    UUID uuid = villager.getUniqueId();
                    VillagerBehaviorState state = activeRollcalls.get(uuid);
                    Location assignedSpot = villagerAssignedSpots.get(uuid);
                    if (state == null || assignedSpot == null) continue;
                    
                    // Check if a player is near this villager's spot (make-way)
                    boolean playerNearSpot = isPlayerNearLocation(assignedSpot, MAKE_WAY_RANGE, villager);
                    
                    state.update(villager, bellLocation, playerNearSpot, ROLLCALL_DURATION_MS);
                    
                    // Apply pathfinding based on state
                    Location target = getTargetForState(state, assignedSpot, bellLocation, playerNearSpot, villager);
                    if (villager instanceof Mob) {
                        Mob mob = (Mob) villager;
                        if (target != null) {
                            // When at position and no player blocking, stop pathfinding to avoid wobble
                            if (state.getCurrentState() == VillagerBehaviorState.State.AT_POSITION && !playerNearSpot) {
                                if (villager.getLocation().distance(assignedSpot) < ARRIVAL_DISTANCE) {
                                    mob.getPathfinder().stopPathfinding();
                                } else {
                                    pathfindTo(mob, target, state.getSpeedMultiplier(), pathRefreshTick);
                                }
                            } else {
                                pathfindTo(mob, target, state.getSpeedMultiplier(), pathRefreshTick);
                            }
                        } else if (state.getCurrentState() == VillagerBehaviorState.State.IDLE) {
                            mob.getPathfinder().stopPathfinding();
                        }
                    }
                }
                
                // End rollcall after duration or when all returned
                boolean allIdleOrReturning = activeRollcalls.values().stream()
                    .allMatch(s -> s.getCurrentState() == VillagerBehaviorState.State.IDLE
                        || s.getCurrentState() == VillagerBehaviorState.State.RETURNING);
                
                if (elapsed > ROLLCALL_DURATION_MS + 5000 || (elapsed > ROLLCALL_DURATION_MS && allIdleOrReturning)) {
                    // Signal all to return
                    for (VillagerBehaviorState s : activeRollcalls.values()) {
                        if (s.getCurrentState() != VillagerBehaviorState.State.RETURNING
                            && s.getCurrentState() != VillagerBehaviorState.State.IDLE) {
                            s.transitionTo(VillagerBehaviorState.State.RETURNING);
                        }
                    }
                }
                
                if (tickCount > 2000 || (elapsed > ROLLCALL_DURATION_MS + 60000)) {
                    stopRollcall(village);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
        
        villageRollcallTasks.put(village.name, task);
    }
    
    /**
     * Get the target location for the villager's current state.
     */
    private Location getTargetForState(VillagerBehaviorState state, Location assignedSpot,
                                       Location bellLocation, boolean playerNearSpot, Villager villager) {
        switch (state.getCurrentState()) {
            case APPROACHING:
                return assignedSpot;
            case AT_POSITION:
                return assignedSpot;
            case STEPPED_BACK:
                return computeRetreatSpot(assignedSpot, villager);
            case RETURNING:
                return state.getOriginalLocation();
            default:
                return null;
        }
    }
    
    /**
     * Compute a retreat spot (make way for player).
     */
    private Location computeRetreatSpot(Location assignedSpot, Villager villager) {
        List<Player> nearby = new ArrayList<>(assignedSpot.getWorld().getPlayers());
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Player p : nearby) {
            double d = p.getLocation().distance(assignedSpot);
            if (d < MAKE_WAY_RANGE && d < minDist) {
                minDist = d;
                nearest = p;
            }
        }
        if (nearest == null) return assignedSpot;
        
        // Move away from player: retreat in opposite direction
        Location playerLoc = nearest.getLocation();
        double dx = assignedSpot.getX() - playerLoc.getX();
        double dz = assignedSpot.getZ() - playerLoc.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.1) return assignedSpot;
        
        dx = (dx / len) * RETREAT_DISTANCE;
        dz = (dz / len) * RETREAT_DISTANCE;
        
        Location retreat = assignedSpot.clone().add(dx, 0, dz);
        retreat.setY(assignedSpot.getY());
        
        // Clamp to same block height, find valid ground
        retreat = findNearestStandable(retreat);
        return retreat != null ? retreat : assignedSpot;
    }
    
    private Location findNearestStandable(Location loc) {
        org.bukkit.World w = loc.getWorld();
        if (w == null) return null;
        int x = loc.getBlockX(), z = loc.getBlockZ();
        for (int dy = 2; dy >= -2; dy--) {
            int y = loc.getBlockY() + dy;
            Block b = w.getBlockAt(x, y, z);
            Block above = w.getBlockAt(x, y + 1, z);
            Block above2 = w.getBlockAt(x, y + 2, z);
            if (b.getType().isSolid() && !b.getType().toString().contains("LAVA")
                && (above.getType().isAir() || !above.getType().isSolid())
                && (above2.getType().isAir() || !above2.getType().isSolid())) {
                return new Location(w, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }
    
    private boolean isPlayerNearLocation(Location loc, double range, Villager excludeVillager) {
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distance(loc) < range) return true;
        }
        return false;
    }
    
    /**
     * Use Paper pathfinder to move entity to target. Refreshes path periodically.
     */
    private void pathfindTo(Mob mob, Location target, double speedMult, int tick) {
        if (target == null || target.getWorld() == null || !target.getWorld().equals(mob.getWorld())) return;
        
        Pathfinder pf = mob.getPathfinder();
        double speed = 0.5 * speedMult;
        
        if (tick % 20 == 0) {
            pf.moveTo(target, speed);
        }
    }
    
    public void stopRollcall(VillageManager.VillageData village) {
        for (Map.Entry<UUID, VillagerBehaviorState> entry : new HashMap<>(activeRollcalls).entrySet()) {
            Villager v = (Villager) plugin.getServer().getEntity(entry.getKey());
            if (v != null && v.isValid()) {
                if (v instanceof Mob) {
                    ((Mob) v).getPathfinder().stopPathfinding();
                }
                Location orig = entry.getValue().getOriginalLocation();
                if (orig != null) {
                    v.teleport(orig);
                }
            }
        }
        
        activeRollcalls.clear();
        villagerAssignedSpots.clear();
        
        BukkitTask task = villageRollcallTasks.remove(village.name);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
    
    private List<Villager> getVillagersInVillage(VillageManager.VillageData village, Location bellLocation) {
        List<Villager> list = new ArrayList<>();
        if (bellLocation == null || bellLocation.getWorld() == null) return list;
        
        for (org.bukkit.entity.Entity e : bellLocation.getWorld()
            .getNearbyEntities(bellLocation, village.effectiveRadius, 256, village.effectiveRadius)) {
            if (e instanceof Villager) list.add((Villager) e);
        }
        return list;
    }
}
