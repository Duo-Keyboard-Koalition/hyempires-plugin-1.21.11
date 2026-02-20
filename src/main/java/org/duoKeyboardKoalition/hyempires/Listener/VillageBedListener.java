package org.duoKeyboardKoalition.hyempires.Listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;
import org.duoKeyboardKoalition.hyempires.scanners.VillagerJobScanner;

/**
 * Handles bed placement and destruction within villages.
 * Tracks beds and updates village population based on bed count.
 */
public class VillageBedListener implements Listener {
    private final HyEmpiresPlugin plugin;
    private final VillageManager villageManager;
    
    // All bed materials
    private static final Material[] BED_MATERIALS = {
        Material.RED_BED, Material.WHITE_BED, Material.BLACK_BED, Material.BLUE_BED,
        Material.BROWN_BED, Material.CYAN_BED, Material.GRAY_BED, Material.GREEN_BED,
        Material.LIGHT_BLUE_BED, Material.LIGHT_GRAY_BED, Material.LIME_BED,
        Material.MAGENTA_BED, Material.ORANGE_BED, Material.PINK_BED,
        Material.PURPLE_BED, Material.YELLOW_BED
    };
    
    public VillageBedListener(HyEmpiresPlugin plugin, VillageManager villageManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
    }
    
    /**
     * Check if a material is a bed.
     */
    private boolean isBed(Material material) {
        for (Material bed : BED_MATERIALS) {
            if (material == bed) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Handle bed placement: if within village (natural radius from bell), update population only. No pathfinding or chunk claiming.
     */
    @EventHandler
    public void onBedPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!isBed(block.getType())) return;

        Location bedLocation = block.getLocation();
        VillageManager.VillageData village = villageManager.getVillageContaining(bedLocation);

        if (village != null) {
            int count = plugin.getResidentCount(village);
            villageManager.setPopulationFromResidentCount(village, count);
            Player player = event.getPlayer();
            if (player != null) {
                player.sendMessage("§aBed placed! Village population: §e" + village.population + " (villagers with bed + workplace in village)");
            }
        }
    }

    /**
     * Handle bed destruction - find villager using it and notify.
     */
    @EventHandler
    public void onBedBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isBed(block.getType())) return;
        
        Location bedLocation = block.getLocation();
        VillageManager.VillageData village = villageManager.getVillageContaining(bedLocation);
        
        if (village != null) {
            // Find villager using this bed
            VillagerJobScanner scanner = plugin.getVillagerScanner();
            if (scanner != null) {
                Villager villagerUsingBed = findVillagerUsingBed(bedLocation, scanner);
                
                if (villagerUsingBed != null) {
                    // Get villager name
                    String villagerName = getVillagerName(villagerUsingBed, scanner);
                    
                    // Notify all nearby players
                    String message = "§cBed at §f" + bedLocation.getBlockX() + ", " + 
                                    bedLocation.getBlockY() + ", " + bedLocation.getBlockZ() + 
                                    " §cis broken. §e" + villagerName + " §7moved out of §6" + village.name;
                    
                    notifyNearbyPlayers(bedLocation, message);
                    
                    // Clear bed assignment from villager
                    scanner.assignBed(villagerUsingBed, null);
                } else {
                    // Bed was not assigned to any villager
                    String message = "§cBed at §f" + bedLocation.getBlockX() + ", " + 
                                    bedLocation.getBlockY() + ", " + bedLocation.getBlockZ() + 
                                    " §cis broken in §6" + village.name;
                    notifyNearbyPlayers(bedLocation, message);
                }
            }
            
            if (isBedPartHead(block)) {
                int count = plugin.getResidentCount(village);
                villageManager.setPopulationFromResidentCount(village, count);
            }
        }
    }

    /**
     * Bed destroyed by block explosion (e.g. bed in explosion radius).
     */
    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (!isBed(block.getType())) continue;
            VillageManager.VillageData village = villageManager.getVillageContaining(block.getLocation());
            if (village != null && isBedPartHead(block)) {
                int count = plugin.getResidentCount(village);
                villageManager.setPopulationFromResidentCount(village, count);
            }
        }
    }

    /**
     * Bed destroyed by entity explosion (TNT, creeper, etc.).
     */
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (!isBed(block.getType())) continue;
            VillageManager.VillageData village = villageManager.getVillageContaining(block.getLocation());
            if (village != null && isBedPartHead(block)) {
                int count = plugin.getResidentCount(village);
                villageManager.setPopulationFromResidentCount(village, count);
            }
        }
    }
    
    /**
     * True if this block is the HEAD (pillow) part of a bed.
     */
    private boolean isBedPartHead(Block block) {
        if (!isBed(block.getType())) return false;
        org.bukkit.block.data.BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Bed) {
            return ((org.bukkit.block.data.type.Bed) data).getPart() == org.bukkit.block.data.type.Bed.Part.HEAD;
        }
        return true;
    }
    
    /**
     * Find villager using a specific bed location.
     */
    private Villager findVillagerUsingBed(Location bedLocation, VillagerJobScanner scanner) {
        if (scanner == null || bedLocation == null || bedLocation.getWorld() == null) {
            return null;
        }
        
        // Check all villagers in the world
        for (Villager villager : bedLocation.getWorld().getEntitiesByClass(Villager.class)) {
            Location villagerBed = scanner.getVillagerBedLocation(villager);
            if (villagerBed != null && 
                villagerBed.getBlockX() == bedLocation.getBlockX() &&
                villagerBed.getBlockY() == bedLocation.getBlockY() &&
                villagerBed.getBlockZ() == bedLocation.getBlockZ() &&
                villagerBed.getWorld().getName().equals(bedLocation.getWorld().getName())) {
                return villager;
            }
        }
        
        return null;
    }
    
    /**
     * Get villager name.
     */
    private String getVillagerName(Villager villager, VillagerJobScanner scanner) {
        if (scanner != null) {
            VillagerJobScanner.VillagerData data = scanner.getVillagerData().get(villager.getUniqueId());
            if (data != null && data.name != null) {
                return data.name;
            }
        }
        return "A villager";
    }
    
    /**
     * Count beds in a village.
     * Counts HEAD and FOOT parts separately, then beds = min(heads, feet) / 2.
     */
    private int countBedsInVillage(VillageManager.VillageData village) {
        Location bellLoc = village.getAdminLocation();
        if (bellLoc == null || bellLoc.getWorld() == null) return 0;
        
        int[] headCount = new int[1];
        int[] footCount = new int[1];
        int radius = village.effectiveRadius;
        
        int chunkRadius = (radius / 16) + 1;
        org.bukkit.Chunk centerChunk = bellLoc.getChunk();
        
        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                org.bukkit.Chunk chunk = bellLoc.getWorld().getChunkAt(
                    centerChunk.getX() + cx, centerChunk.getZ() + cz);
                
                if (!chunk.isLoaded()) continue;
                
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int worldX = chunk.getX() * 16 + x;
                        int worldZ = chunk.getZ() * 16 + z;
                        double distSq = Math.pow(worldX - bellLoc.getX(), 2) + 
                                       Math.pow(worldZ - bellLoc.getZ(), 2);
                        if (distSq > radius * radius) continue;
                        
                        int minY = Math.max(bellLoc.getWorld().getMinHeight(), bellLoc.getBlockY() - 10);
                        int maxY = Math.min(bellLoc.getWorld().getMaxHeight(), bellLoc.getBlockY() + 10);
                        
                        for (int y = minY; y <= maxY; y++) {
                            Block block = chunk.getBlock(x, y, z);
                            countBedPart(block, headCount, footCount);
                        }
                    }
                }
            }
        }
        
        for (Location additionalBell : village.additionalBells) {
            if (additionalBell.getWorld() == null) continue;
            if (!additionalBell.getWorld().equals(bellLoc.getWorld())) continue;
            
            int addChunkRadius = 3;
            org.bukkit.Chunk addCenterChunk = additionalBell.getChunk();
            
            for (int cx = -addChunkRadius; cx <= addChunkRadius; cx++) {
                for (int cz = -addChunkRadius; cz <= addChunkRadius; cz++) {
                    org.bukkit.Chunk chunk = additionalBell.getWorld().getChunkAt(
                        addCenterChunk.getX() + cx, addCenterChunk.getZ() + cz);
                    if (!chunk.isLoaded()) continue;
                    
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int worldX = chunk.getX() * 16 + x;
                            int worldZ = chunk.getZ() * 16 + z;
                            double distSq = Math.pow(worldX - additionalBell.getX(), 2) + 
                                           Math.pow(worldZ - additionalBell.getZ(), 2);
                            if (distSq > 48 * 48) continue;
                            
                            int minY = Math.max(additionalBell.getWorld().getMinHeight(), additionalBell.getBlockY() - 10);
                            int maxY = Math.min(additionalBell.getWorld().getMaxHeight(), additionalBell.getBlockY() + 10);
                            for (int y = minY; y <= maxY; y++) {
                                Block block = chunk.getBlock(x, y, z);
                                countBedPart(block, headCount, footCount);
                            }
                        }
                    }
                }
            }
        }
        
        return Math.min(headCount[0], footCount[0]) / 2;
    }
    
    /** Increment head or foot count for a bed block. */
    private void countBedPart(Block block, int[] headCount, int[] footCount) {
        if (!isBed(block.getType())) return;
        org.bukkit.block.data.BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Bed) {
            if (((org.bukkit.block.data.type.Bed) data).getPart() == org.bukkit.block.data.type.Bed.Part.HEAD) {
                headCount[0]++;
            } else {
                footCount[0]++;
            }
        }
    }
    
    /**
     * Update village population based on bed count.
     */
    public void updateVillagePopulationFromBeds(VillageManager.VillageData village) {
        int bedCount = countBedsInVillage(village);
        village.population = bedCount;
        // Update population through VillageManager
        villageManager.updatePopulationFromBeds(village);
    }
    
    /**
     * Notify nearby players.
     */
    private void notifyNearbyPlayers(Location location, String message) {
        if (location.getWorld() == null) return;
        location.getWorld().getPlayers().forEach(player -> {
            if (player.getLocation().distance(location) <= 50) {
                player.sendMessage(message);
            }
        });
    }
}
