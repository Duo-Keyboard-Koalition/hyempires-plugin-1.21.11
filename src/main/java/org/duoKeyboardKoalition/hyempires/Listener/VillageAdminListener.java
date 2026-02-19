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
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;

/**
 * Handles events related to village administration blocks.
 * Players can place a village admin block (using a bell) to establish
 * administrative control over a village.
 */
public class VillageAdminListener implements Listener {
    private final JavaPlugin plugin;
    private final VillageManager villageManager;

    public VillageAdminListener(JavaPlugin plugin, VillageManager villageManager) {
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
            // Create a village after a short delay to ensure block is fully placed
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                VillageManager.VillageData village = villageManager.createVillage(
                        block.getLocation(), player, null);
                
                if (village != null) {
                    player.sendMessage("§aVillage '" + village.name + "' has been established!");
                    player.sendMessage("§ePopulation: " + village.population + " villagers");
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
                // Check if player owns this village
                if (village.owner != null && !village.owner.equals(player.getUniqueId()) && !player.isOp()) {
                    player.sendMessage("§cYou can only break your own village admin blocks!");
                    event.setCancelled(true);
                    return;
                }

                // Remove the village
                villageManager.removeVillage(block.getLocation());
                player.sendMessage("§6Village administration has been abandoned.");
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

            // Show village info
            player.sendMessage(villageManager.getVillageInfo(village));

            // If owner, show additional options
            if (villageManager.canAdminister(player, village)) {
                player.sendMessage("§7=== Administration Options ===");
                player.sendMessage("§e[Left-click] §7Abandon village");
                player.sendMessage("§e[Shift+Right-click] §7Refresh population count");
            }
        }
    }

    /**
     * Handles left-clicking to abandon village.
     */
    @EventHandler
    public void onPlayerInteractLeft(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BELL) return;

        Player player = event.getPlayer();
        VillageManager.VillageData village = villageManager.getVillageAt(block.getLocation());

        if (village != null && villageManager.canAdminister(player, village)) {
            villageManager.removeVillage(block.getLocation());
            player.sendMessage("§cVillage administration has been abandoned.");
            event.setCancelled(true);
        }
    }

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
        if (village != null && village.owner != null) {
            if (!village.owner.equals(player.getUniqueId()) && !player.isOp()) {
                // Allow building only if player has permission
                org.bukkit.Location adminLoc = village.getAdminLocation();
                if (adminLoc != null && block.getLocation().distance(adminLoc) < 16) {
                    player.sendMessage("§cYou cannot build near another player's village center!");
                    event.setCancelled(true);
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
        if (village != null && village.owner != null) {
            if (!village.owner.equals(player.getUniqueId()) && !player.isOp()) {
                org.bukkit.Location adminLoc = village.getAdminLocation();
                if (adminLoc != null && block.getLocation().distance(adminLoc) < 16) {
                    player.sendMessage("§cYou cannot destroy near another player's village center!");
                    event.setCancelled(true);
                }
            }
        }
    }
}
