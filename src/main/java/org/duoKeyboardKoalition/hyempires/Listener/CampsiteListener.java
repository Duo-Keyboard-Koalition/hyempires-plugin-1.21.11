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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.managers.CampsiteManager;

/**
 * Handles events related to campsite blocks.
 * Players can place a campsite block (using a campfire with specific placement)
 * to establish a new campsite.
 */
public class CampsiteListener implements Listener {
    private final JavaPlugin plugin;
    private final CampsiteManager campsiteManager;

    public CampsiteListener(JavaPlugin plugin, CampsiteManager campsiteManager) {
        this.plugin = plugin;
        this.campsiteManager = campsiteManager;
    }

    /**
     * Handles placing of campsite blocks.
     * For now, we use a campfire placed on grass as the campsite marker.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Check if placing a campfire on grass/dirt (campsite creation)
        if (block.getType() == Material.CAMPFIRE) {
            Block below = block.getRelative(0, -1, 0);
            if (isNaturalGround(below.getType())) {
                // Create a campsite after a short delay to ensure block is fully placed
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    campsiteManager.createCampsite(block.getLocation(), player, null);
                }, 5L);
            }
        }
    }

    /**
     * Handles breaking of campsite blocks.
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() == Material.CAMPFIRE) {
            CampsiteManager.CampsiteData campsite = campsiteManager.getCampsiteAt(block.getLocation());
            if (campsite != null) {
                // Check if player owns this campsite
                if (campsite.owner != null && !campsite.owner.equals(player.getUniqueId()) && !player.isOp()) {
                    player.sendMessage("§cYou can only break your own campsites!");
                    event.setCancelled(true);
                    return;
                }

                // Remove the campsite
                campsiteManager.removeCampsite(block.getLocation());
                player.sendMessage("§6Campsite has been abandoned.");
            }
        }
    }

    /**
     * Handles right-clicking on campsite blocks.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CAMPFIRE) return;

        Player player = event.getPlayer();
        CampsiteManager.CampsiteData campsite = campsiteManager.getCampsiteAt(block.getLocation());

        if (campsite != null) {
            event.setCancelled(true);

            // Show campsite info
            player.sendMessage("§6=== Campsite Info ===");
            player.sendMessage("§eName: §f" + campsite.name);
            player.sendMessage("§eOwner: §f" + (campsite.owner != null ? campsite.owner.toString() : "Unowned"));
            player.sendMessage("§eLocation: §f" + campsite.x + ", " + campsite.y + ", " + campsite.z);

            // If owner, show additional options
            if (campsite.owner != null && campsite.owner.equals(player.getUniqueId())) {
                player.sendMessage("§a[Left-click to abandon campsite]");
            }
        }
    }

    /**
     * Handles left-clicking to abandon campsite.
     */
    @EventHandler
    public void onPlayerInteractLeft(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CAMPFIRE) return;

        Player player = event.getPlayer();
        CampsiteManager.CampsiteData campsite = campsiteManager.getCampsiteAt(block.getLocation());

        if (campsite != null && campsite.owner != null && campsite.owner.equals(player.getUniqueId())) {
            campsiteManager.removeCampsite(block.getLocation());
            player.sendMessage("§cCampsite has been abandoned.");
            event.setCancelled(true);
        }
    }

    /**
     * Checks if a block is natural ground suitable for camping.
     */
    private boolean isNaturalGround(Material material) {
        return material == Material.GRASS_BLOCK ||
                material == Material.DIRT ||
                material == Material.COARSE_DIRT ||
                material == Material.PODZOL ||
                material == Material.MYCELIUM ||
                material == Material.SAND ||
                material == Material.SNOW_BLOCK;
    }
}
