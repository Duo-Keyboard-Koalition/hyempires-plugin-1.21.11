package org.duoKeyboardKoalition.hyempires.systems;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import java.util.*;

/**
 * Scans for safe, standable blocks around a bell for villager rollcall positions.
 * Each returned location is a block center where a villager can pathfind to and stand.
 */
public class SafeBlockScanner {
    
    private static final int DEFAULT_SEARCH_RADIUS = 8;
    private static final int MAX_HEIGHT_DIFF = 3;
    
    // Blocks that are dangerous or unsuitable for standing
    private static final Set<Material> UNSAFE_GROUND = EnumSet.of(
        Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.CACTUS,
        Material.MAGMA_BLOCK, Material.HONEY_BLOCK, Material.POWDER_SNOW,
        Material.COBWEB, Material.SWEET_BERRY_BUSH
    );
    
    /**
     * Find all safe blocks around the bell that villagers can stand on.
     * Returns locations (block center + 1 block up for entity feet) sorted by distance from bell.
     */
    public static List<Location> scanSafeBlocksAroundBell(Location bellLocation) {
        return scanSafeBlocksAroundBell(bellLocation, DEFAULT_SEARCH_RADIUS);
    }
    
    /**
     * Find all safe blocks around the bell within the given radius.
     */
    public static List<Location> scanSafeBlocksAroundBell(Location bellLocation, int radius) {
        List<Location> safeBlocks = new ArrayList<>();
        
        if (bellLocation == null || bellLocation.getWorld() == null) {
            return safeBlocks;
        }
        
        int bellX = bellLocation.getBlockX();
        int bellY = bellLocation.getBlockY();
        int bellZ = bellLocation.getBlockZ();
        
        // Scan all blocks in radius
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = bellX + dx;
                int z = bellZ + dz;
                
                // Check multiple heights (ground might be at different levels)
                for (int dy = -MAX_HEIGHT_DIFF; dy <= MAX_HEIGHT_DIFF; dy++) {
                    int y = bellY + dy;
                    Location standLoc = findStandableAt(x, y, z, bellLocation.getWorld());
                    if (standLoc != null && !containsNearby(safeBlocks, standLoc, 1.5)) {
                        safeBlocks.add(standLoc);
                    }
                }
            }
        }
        
        // Sort by distance from bell (closest first)
        Location center = bellLocation.clone().add(0.5, 0, 0.5);
        safeBlocks.sort(Comparator.comparingDouble(loc -> loc.distanceSquared(center)));
        
        return safeBlocks;
    }
    
    /**
     * Check if block at (x, y, z) is a valid ground - block below is solid, block at y and y+1 are passable.
     * Returns the center location for entity placement, or null if not valid.
     */
    private static Location findStandableAt(int x, int y, int z, org.bukkit.World world) {
        Block ground = world.getBlockAt(x, y, z);
        Block feet = world.getBlockAt(x, y + 1, z);
        Block head = world.getBlockAt(x, y + 2, z);
        
        // Ground must be solid and safe
        if (!ground.getType().isSolid() || UNSAFE_GROUND.contains(ground.getType())) {
            return null;
        }
        
        // Check for full blocks - slabs and stairs are trickier
        if (isUnsafeGroundMaterial(ground.getType())) {
            return null;
        }
        
        // Feet and head space must be passable (villager can fit)
        if (!isPassable(feet.getType()) || !isPassable(head.getType())) {
            return null;
        }
        
        // Entity stands at block center, 1 block above ground
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }
    
    private static boolean isUnsafeGroundMaterial(Material m) {
        if (m == Material.POWDER_SNOW || m == Material.HONEY_BLOCK) return true;
        if (m == Material.LAVA || m == Material.WATER) return true;
        return false;
    }
    
    private static boolean isPassable(Material m) {
        if (m == Material.AIR) return true;
        if (m == Material.CAVE_AIR) return true;
        if (m == Material.VOID_AIR) return true;
        // Non-solid blocks that entities can pass through (includes plants, tall grass, etc.)
        if (!m.isSolid()) return true;
        return false;
    }
    
    private static boolean containsNearby(List<Location> list, Location loc, double minDist) {
        for (Location existing : list) {
            if (existing.getWorld().equals(loc.getWorld()) && existing.distance(loc) < minDist) {
                return true;
            }
        }
        return false;
    }
}
