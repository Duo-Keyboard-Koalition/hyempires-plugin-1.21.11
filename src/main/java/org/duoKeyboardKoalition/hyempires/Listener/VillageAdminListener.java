package org.duoKeyboardKoalition.hyempires.Listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;

/**
 * Handles events related to village administration blocks.
 * Players can place a village admin block (using a bell) to establish
 * administrative control over a village.
 */
public class VillageAdminListener implements Listener {
    private final HyEmpiresPlugin plugin;
    private final VillageManager villageManager;

    public VillageAdminListener(HyEmpiresPlugin plugin, VillageManager villageManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
    }

    /**
     * Handles placing of village admin blocks.
     * For now, we use a bell as the village administration marker.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Check if placing a bell (village admin block)
        if (block.getType() == Material.BELL) {
            // Create or expand village after a short delay to ensure block is fully placed
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                VillageManager.VillageData village = villageManager.createVillage(
                        block.getLocation(), player, null);
                
                if (village != null) {
                    // Check if this was an expansion or new village
                    boolean isExpansion = village.additionalBells.stream()
                            .anyMatch(loc -> loc.getBlockX() == block.getX() && 
                                           loc.getBlockY() == block.getY() && 
                                           loc.getBlockZ() == block.getZ());
                    
                    if (!isExpansion && village.adminX == block.getX() && 
                        village.adminY == block.getY() && village.adminZ == block.getZ()) {
                        // New village
                        player.sendMessage("§aVillage '" + village.name + "' has been established!");
                        player.sendMessage("§ePopulation: " + village.population + " villagers");
                    }
                    // Expansion message is handled in expandVillage()
                } else {
                    // Bell already exists at this location
                    player.sendMessage("§cA bell already exists at this location!");
                }
            }, 5L);
        }
    }

    /**
     * Handles breaking of village admin blocks.
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() == Material.BELL) {
            VillageManager.VillageData village = villageManager.getVillageAt(block.getLocation());
            if (village != null) {
                // Only OPs can break village bells (for admin purposes)
                if (!player.isOp()) {
                    player.sendMessage("§cOnly administrators can remove village bells!");
                    event.setCancelled(true);
                    return;
                }

                // Remove the village (admin only)
                villageManager.removeVillage(block.getLocation(), player);
                player.sendMessage("§6Village has been removed by admin.");
            }
        }
    }

    /**
     * Handles right-clicking on village admin blocks.
     */
    @EventHandler
    public void onPlayerInteractRight(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BELL) return;

        Player player = event.getPlayer();
        VillageManager.VillageData village = villageManager.getVillageAt(block.getLocation());

        if (village != null) {
            event.setCancelled(true);
            
            // Check if player is holding paper - give them admin token
            org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
            if (item != null && item.getType() == Material.PAPER) {
                // Check if they already have a token for this village
                boolean hasToken = false;
                for (org.bukkit.inventory.ItemStack invItem : player.getInventory().getContents()) {
                    if (invItem != null && org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.isToken(invItem)) {
                        String tokenVillage = org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.getVillageName((HyEmpiresPlugin) plugin, invItem);
                        if (village.name.equals(tokenVillage)) {
                            hasToken = true;
                            break;
                        }
                    }
                }
                
                if (!hasToken) {
                    // Give admin token
                    org.bukkit.inventory.ItemStack token = org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.createToken((HyEmpiresPlugin) plugin, village.name);
                    
                    // Try to add to inventory
                    java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> overflow = player.getInventory().addItem(token);
                    if (overflow.isEmpty()) {
                        // Consume one paper
                        if (item.getAmount() > 1) {
                            item.setAmount(item.getAmount() - 1);
                        } else {
                            player.getInventory().setItemInMainHand(null);
                        }
                        
                        player.sendMessage("§a§lVillage Administration Token Received!");
                        player.sendMessage("§7Right-click the bell with this token to open the administration menu.");
                    } else {
                        player.sendMessage("§cYour inventory is full! Make space and try again.");
                    }
                } else {
                    player.sendMessage("§eYou already have an administration token for this village!");
                }
                return;
            }

            // Show village info (normal right-click)
            player.sendMessage(villageManager.getVillageInfo(village, player));

            // If can administer, show options
            if (villageManager.canAdminister(player, village)) {
                player.sendMessage("§7=== Administration Options ===");
                player.sendMessage("§e[Right-click with Paper] §7Get administration token");
                player.sendMessage("§e[Shift+Right-click] §7Refresh population count");
            }
            
            // Update activity for influence system
            if (plugin.getInfluenceManager() != null) {
                plugin.getInfluenceManager().updateActivity(village.name, player.getUniqueId());
            }
        }
    }

    // Removed: Abandon village functionality - villages cannot be abandoned
    // Ownership is now managed through the influence system

    /**
     * Handles shift right-click to refresh population.
     */
    @EventHandler
    public void onPlayerInteractShift(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!event.getPlayer().isSneaking()) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BELL) return;

        Player player = event.getPlayer();
        VillageManager.VillageData village = villageManager.getVillageAt(block.getLocation());

        if (village != null && villageManager.canAdminister(player, village)) {
            villageManager.updatePopulation(village);
            player.sendMessage("§aPopulation updated: " + village.population + " villagers");
            event.setCancelled(true);
        }
    }

    /**
     * Handles placing blocks near village admin blocks.
     * Prevents non-owners from building near village centers.
     */
    @EventHandler
    public void onBlockPlaceNearVillage(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Check if placing within 16 blocks of a village admin block
        VillageManager.VillageData village = villageManager.getVillageContaining(block.getLocation());
        if (village != null) {
            org.bukkit.Location adminLoc = village.getAdminLocation();
            if (adminLoc != null && block.getLocation().distance(adminLoc) < 16) {
                // Use influence system to check permissions
                if (!villageManager.canAdminister(player, village)) {
                    player.sendMessage("§cYou need more influence in this village to build near the center!");
                    event.setCancelled(true);
                } else {
                    // Player can build - gain small influence
                    if (plugin.getInfluenceManager() != null) {
                        plugin.getInfluenceManager().addInfluence(village.name, player.getUniqueId(), 0.5, "Building");
                    }
                }
            }
        }
    }

    /**
     * Handles breaking blocks near village admin blocks.
     * Prevents non-owners from destroying near village centers.
     */
    @EventHandler
    public void onBlockBreakNearVillage(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Check if breaking within 16 blocks of a village admin block
        VillageManager.VillageData village = villageManager.getVillageContaining(block.getLocation());
        if (village != null) {
            org.bukkit.Location adminLoc = village.getAdminLocation();
            if (adminLoc != null && block.getLocation().distance(adminLoc) < 16) {
                // Use influence system to check permissions
                if (!villageManager.canAdminister(player, village)) {
                    player.sendMessage("§cYou need more influence in this village to destroy near the center!");
                    event.setCancelled(true);
                }
            }
        }
    }
}
