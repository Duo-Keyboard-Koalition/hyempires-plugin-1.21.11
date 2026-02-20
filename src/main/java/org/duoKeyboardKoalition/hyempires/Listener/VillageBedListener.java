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
     * Handle bed placement: if within village (natural radius from bell). No pathfinding or chunk claiming.
     */
    @EventHandler
    public void onBedPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!isBed(block.getType())) return;
        // Optional: could notify player that bed is in village
        VillageManager.VillageData village = villageManager.getVillageContaining(block.getLocation());
        if (village != null) {
            Player player = event.getPlayer();
            if (player != null) {
                player.sendMessage("§aBed placed in §6" + village.name + "§a.");
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
                // Bed destroyed in village - no population to update
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
                // Bed destroyed in village
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
