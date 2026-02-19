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
     * Handle right-clicking with administration token to open menu (anywhere).
     */
    @EventHandler
    public void onTokenInteract(PlayerInteractEvent event) {
        // Check for right-click (both air and blocks)
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
        
        // Check if holding administration token
        if (item == null || !org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.isToken(item)) {
            return;
        }
        
        // Cancel the event to prevent other interactions
        event.setCancelled(true);
        
        // Get village name from token
        String tokenVillageName = org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.getVillageName(plugin, item);
        if (tokenVillageName == null) {
            player.sendMessage("§cInvalid administration token!");
            return;
        }
        
        // Find the village by name
        VillageManager.VillageData village = villageManager.getAllVillages().stream()
            .filter(v -> v.active && v.name.equals(tokenVillageName))
            .findFirst()
            .orElse(null);
        
        if (village == null) {
            player.sendMessage("§cVillage '" + tokenVillageName + "' no longer exists!");
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
            // Get village from token or player location
            VillageManager.VillageData village = null;
            
            // First, try to get village from token
            org.bukkit.inventory.ItemStack token = player.getInventory().getItemInMainHand();
            if (token != null && org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.isToken(token)) {
                String tokenVillageName = org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.getVillageName(plugin, token);
                if (tokenVillageName != null) {
                    village = villageManager.getAllVillages().stream()
                        .filter(v -> v.active && v.name.equals(tokenVillageName))
                        .findFirst()
                        .orElse(null);
                }
            }
            
            // Fallback: find village by player's location
            if (village == null) {
                village = villageManager.getVillageContaining(player.getLocation());
            }
            
            if (village == null) {
                player.closeInventory();
                player.sendMessage("§cCould not find village! Make sure you're holding a valid administration token.");
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
                // Get village from token or player location
                VillageManager.VillageData village = null;
                
                // First, try to get village from token
                org.bukkit.inventory.ItemStack token = player.getInventory().getItemInMainHand();
                if (token != null && org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.isToken(token)) {
                    String tokenVillageName = org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.getVillageName(plugin, token);
                    if (tokenVillageName != null) {
                        village = villageManager.getAllVillages().stream()
                            .filter(v -> v.active && v.name.equals(tokenVillageName))
                            .findFirst()
                            .orElse(null);
                    }
                }
                
                // Fallback: find village by player's location
                if (village == null) {
                    village = villageManager.getVillageContaining(player.getLocation());
                }
                
                if (village != null) {
                    menuGUI.openMainMenu(player, village);
                } else {
                    player.closeInventory();
                    player.sendMessage("§cCould not find village! Make sure you're holding a valid administration token.");
                }
            }
        }
    }
    
    public RollcallSystem getRollcallSystem() {
        return rollcallSystem;
    }
}
