package org.duoKeyboardKoalition.hyempires.Listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.gui.VillageMenuGUI;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;
import org.duoKeyboardKoalition.hyempires.systems.RollcallSystem;

/**
 * Handles menu interactions for village administration.
 */
public class VillageMenuListener implements Listener {
    private final HyEmpiresPlugin plugin;
    private final VillageManager villageManager;
    private final VillageMenuGUI menuGUI;
    private final RollcallSystem rollcallSystem;
    
    public VillageMenuListener(HyEmpiresPlugin plugin, VillageManager villageManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
        this.menuGUI = new VillageMenuGUI(plugin, villageManager);
        this.rollcallSystem = new RollcallSystem(plugin);
    }
    
    /**
     * Handle right-clicking bell with administration token to open menu.
     */
    @EventHandler
    public void onBellInteractWithToken(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.BELL) {
            return;
        }
        
        Player player = event.getPlayer();
        org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
        
        // Check if holding administration token
        if (item == null || !org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.isToken(item)) {
            return;
        }
        
        VillageManager.VillageData village = villageManager.getVillageAt(event.getClickedBlock().getLocation());
        
        if (village != null) {
            event.setCancelled(true);
            
            // Verify token matches this village
            String tokenVillageName = org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.getVillageName(plugin, item);
            if (tokenVillageName == null || !tokenVillageName.equals(village.name)) {
                player.sendMessage("§cThis token is for a different village!");
                return;
            }
            
            // Check if player can administer
            if (!villageManager.canAdminister(player, village)) {
                player.sendMessage("§cYou need more influence in this village to access the administration menu!");
                return;
            }
            
            // Open main menu
            menuGUI.openMainMenu(player, village);
        }
    }
    
    /**
     * Handle menu clicks.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        
        // Check if it's a village menu
        if (!title.contains("Administration") && !title.contains("Population")) {
            return;
        }
        
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        String itemName = clicked.getItemMeta() != null && clicked.getItemMeta().getDisplayName() != null ?
                clicked.getItemMeta().getDisplayName() : "";
        
        // Main menu actions
        if (title.contains("Administration")) {
            // Find village from bell location (we need to store this somehow)
            // For now, find village by player's location
            VillageManager.VillageData village = villageManager.getVillageContaining(player.getLocation());
            
            if (village == null) {
                player.closeInventory();
                player.sendMessage("§cCould not find village!");
                return;
            }
            
            if (itemName.contains("Population")) {
                // Open population table
                menuGUI.openPopulationTable(player, village);
            } else if (itemName.contains("Rollcall")) {
                // Start rollcall
                player.closeInventory();
                Location bellLocation = village.getAdminLocation();
                if (bellLocation != null) {
                    rollcallSystem.startRollcall(village, bellLocation);
                    player.sendMessage("§aRollcall started! Villagers are gathering at the bell.");
                }
            } else if (itemName.contains("Information")) {
                // Show village info
                player.closeInventory();
                player.sendMessage(villageManager.getVillageInfo(village, player));
            } else if (itemName.contains("Back")) {
                // Return to main menu
                menuGUI.openMainMenu(player, village);
            }
        }
        // Population table actions
        else if (title.contains("Population")) {
            if (itemName.contains("Back")) {
                // Find village and return to main menu
                VillageManager.VillageData village = villageManager.getVillageContaining(player.getLocation());
                if (village != null) {
                    menuGUI.openMainMenu(player, village);
                } else {
                    player.closeInventory();
                }
            }
        }
    }
    
    public RollcallSystem getRollcallSystem() {
        return rollcallSystem;
    }
}
