package org.duoKeyboardKoalition.hyempires.Listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.entity.Villager;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.gui.VillageMenuGUI;
import org.duoKeyboardKoalition.hyempires.managers.InfluenceManager;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;
import org.duoKeyboardKoalition.hyempires.utils.TradingToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles menu interactions for village administration.
 */
public class VillageMenuListener implements Listener {
    private final HyEmpiresPlugin plugin;
    private final VillageManager villageManager;
    private final VillageMenuGUI menuGUI;
    private final Map<Player, VillageManager.VillageData> pendingRenames = new HashMap<>();
    private final Map<Player, org.bukkit.Location> pendingVillageCreations = new HashMap<>();
    /** Pending merge: player typed "merge" at bell; awaiting new village name in chat. */
    private final Map<Player, MergeInfo> pendingMerges = new HashMap<>();

    /** Holds the two villages to merge (token village + bell village). */
    public static class MergeInfo {
        public final VillageManager.VillageData primary;
        public final VillageManager.VillageData other;
        public MergeInfo(VillageManager.VillageData primary, VillageManager.VillageData other) {
            this.primary = primary;
            this.other = other;
        }
    }
    
    public VillageMenuListener(HyEmpiresPlugin plugin, VillageManager villageManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
        this.menuGUI = new VillageMenuGUI(plugin, villageManager);
    }

    /** Open the main administration menu for a village (e.g. when right-clicking same-village bell with token). */
    public void openMainMenu(Player player, VillageManager.VillageData village) {
        if (player != null && village != null) menuGUI.openMainMenu(player, village);
    }

    private static final Set<Material> WORKSTATION_BLOCKS = Set.of(
        Material.COMPOSTER, Material.SMITHING_TABLE, Material.LECTERN, Material.FLETCHING_TABLE,
        Material.CARTOGRAPHY_TABLE, Material.BREWING_STAND, Material.BLAST_FURNACE, Material.SMOKER,
        Material.CAULDRON, Material.STONECUTTER, Material.LOOM, Material.GRINDSTONE, Material.BARREL
    );

    /**
     * Handle right-clicking with administration token or trading token: open menu / master trading.
     */
    @EventHandler
    public void onTokenInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null) return;

        // Trading token: open master trading menu for that village
        if (TradingToken.isToken(item)) {
            String tokenVillageName = TradingToken.getVillageName(plugin, item);
            if (tokenVillageName == null) {
                player.sendMessage("§cInvalid trading token!");
                return;
            }
            VillageManager.VillageData village = villageManager.getAllVillages().stream()
                .filter(v -> v.active && v.name.equals(tokenVillageName))
                .findFirst()
                .orElse(null);
            if (village == null) {
                player.sendMessage("§cVillage '" + tokenVillageName + "' no longer exists!");
                return;
            }
            event.setCancelled(true);
            openMasterTradingMenu(player, village);
            return;
        }

        if (!org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.isToken(item)) {
            return;
        }

        event.setCancelled(true);

        String tokenVillageName = org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.getVillageName(plugin, item);
        if (tokenVillageName == null) {
            player.sendMessage("§cInvalid administration token!");
            return;
        }

        VillageManager.VillageData village = villageManager.getAllVillages().stream()
            .filter(v -> v.active && v.name.equals(tokenVillageName))
            .findFirst()
            .orElse(null);
        if (village == null) {
            player.sendMessage("§cVillage '" + tokenVillageName + "' no longer exists!");
            return;
        }

        // Right-click on workstation block: show status (natural boundaries = radius from bell only)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && WORKSTATION_BLOCKS.contains(clicked.getType())) {
                org.bukkit.Location workstationLoc = clicked.getLocation();
                VillageManager.VillageData alreadyIn = villageManager.getVillageContaining(workstationLoc);
                if (alreadyIn != null && alreadyIn.name.equals(village.name)) {
                    player.sendMessage("§7This workstation is already part of §6" + village.name + "§7 (within " + village.effectiveRadius + " blocks of bell).");
                    return;
                }
                if (alreadyIn != null) {
                    player.sendMessage("§7This workstation is part of §6" + alreadyIn.name + "§7 (within that village's radius).");
                    return;
                }
                player.sendMessage("§eWorkstation is outside any village. §7Place it within §f" + village.effectiveRadius + " §7blocks of §6" + village.name + "§7's bell to be part of that village.");
                return;
            }
        }

        // Open main menu (right-click air or non-workstation block)
        menuGUI.openMainMenu(player, village);
    }
    
    /**
     * Handle chat input for village renaming and initial naming.
     */
    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // Check for pending village creation (founder naming)
        if (hasPendingVillageCreation(player)) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                processVillageCreation(player, message);
            });
            return;
        }

        // Check for pending merge (same-owner merge; new name in chat)
        if (hasPendingMerge(player)) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                processMerge(player, message);
            });
            return;
        }

        // Check for pending rename
        if (hasPendingRename(player)) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                processRename(player, message);
            });
        }
    }
    
    /**
     * Handle menu clicks.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Component titleComponent = event.getView().title();
        String title = LegacyComponentSerializer.legacySection().serialize(titleComponent);
        
        // Check if it's a village menu (main, villagers by profession)
        if (!title.contains("Administration") && !title.contains("Villagers")) {
            return;
        }
        
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        Component displayName = clicked.getItemMeta() != null ? clicked.getItemMeta().displayName() : null;
        String itemName = displayName != null ? LegacyComponentSerializer.legacySection().serialize(displayName) : "";
        
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
            
            if (itemName.contains("Villagers by Profession") || itemName.contains("Profession")) {
                menuGUI.openVillagersByProfessionSelector(player, village);
            } else if (itemName.contains("Rename") || itemName.contains("Name")) {
                // Check if player is the current leader
                InfluenceManager influenceManager = plugin.getInfluenceManager();
                if (influenceManager == null) {
                    player.closeInventory();
                    player.sendMessage("§cInfluence system not available!");
                    return;
                }
                
                UUID currentLeader = influenceManager.getCurrentLeader(village.name);
                UUID playerUUID = player.getUniqueId();
                
                // Check if player is the leader
                boolean isLeader = false;
                if (currentLeader != null && currentLeader.equals(playerUUID)) {
                    isLeader = true;
                } else if (currentLeader == null) {
                    // No leader yet - founder can set name
                    if (village.owner != null && village.owner.equals(player.getName())) {
                        isLeader = true;
                    }
                }
                
                if (!isLeader) {
                    player.closeInventory();
                    String leaderName = "Unknown";
                    if (currentLeader != null) {
                        org.bukkit.entity.Player leaderPlayer = plugin.getServer().getPlayer(currentLeader);
                        if (leaderPlayer != null) {
                            leaderName = leaderPlayer.getName();
                        } else {
                            org.bukkit.OfflinePlayer offlineLeader = plugin.getServer().getOfflinePlayer(currentLeader);
                            if (offlineLeader != null && offlineLeader.hasPlayedBefore()) {
                                leaderName = offlineLeader.getName();
                            }
                        }
                    }
                    player.sendMessage("§cOnly the current village leader can rename the village!");
                    player.sendMessage("§7Current leader: §e" + leaderName);
                    double playerInfluence = influenceManager.getInfluence(village.name, playerUUID);
                    if (playerInfluence > 0) {
                        player.sendMessage("§7Your influence: §e" + String.format("%.1f", playerInfluence));
                    }
                    return;
                }
                
                // Leader can rename
                player.closeInventory();
                pendingRenames.put(player, village);
                player.sendMessage("§6=== Rename Village ===");
                player.sendMessage("§eCurrent name: §f" + village.name);
                player.sendMessage("§7As the village leader, you can set the name directly.");
                player.sendMessage("§7Please type the new name in chat (or type 'cancel' to cancel)");
            } else if (itemName.contains("Information")) {
                // Show village info
                player.closeInventory();
                player.sendMessage(villageManager.getVillageInfo(village, player));
            } else if (itemName.contains("Back")) {
                // Return to main menu
                menuGUI.openMainMenu(player, village);
            }
        }
        // Villagers by profession: selector (Villagers - VillageName) or table (Villagers: Profession - VillageName)
        else if (title.contains("Villagers")) {
            VillageManager.VillageData village = getVillageFromMenuTitle(title);
            if (village == null) village = getVillageFromTokenOrLocation(player);
            if (village != null) {
                if (itemName.contains("Back")) {
                    if (title.contains("Villagers:")) {
                        menuGUI.openVillagersByProfessionSelector(player, village);
                    } else {
                        menuGUI.openMainMenu(player, village);
                    }
                } else if (title.contains("Villagers - ") && !title.contains("Villagers:")) {
                    // Profession selector: slot 0 = All, slots 1–16 = professions, slot 26 = back
                    int slot = event.getRawSlot();
                    if (slot == 0) {
                        menuGUI.openVillagersByProfessionTable(player, village, null);
                    } else if (slot >= 1 && slot <= 16) {
                        org.bukkit.entity.Villager.Profession[] professions = org.bukkit.entity.Villager.Profession.values();
                        int idx = slot - 1;
                        if (idx < professions.length) {
                            menuGUI.openVillagersByProfessionTable(player, village, professions[idx]);
                        }
                    }
                }
            } else {
                player.closeInventory();
                player.sendMessage("§cCould not find village!");
            }
        }
    }

    /**
     * Open the master trading menu for a village: one merchant GUI with all villagers' trades
     * (all villagers that use this bell / are in village radius).
     */
    private void openMasterTradingMenu(Player player, VillageManager.VillageData village) {
        List<Villager> villagers = plugin.getVillagersInVillage(village);
        List<MerchantRecipe> allRecipes = new ArrayList<>();
        for (Villager v : villagers) {
            if (!v.isValid()) continue;
            try {
                for (MerchantRecipe r : v.getRecipes()) {
                    if (r != null) allRecipes.add(r);
                }
            } catch (Throwable t) {
                // Skip villager if getRecipes fails
            }
        }
        Merchant merchant = org.bukkit.Bukkit.getServer().createMerchant("§6" + village.name + " §7- Trades");
        if (!allRecipes.isEmpty()) {
            merchant.setRecipes(allRecipes);
        }
        player.openMerchant(merchant, true);
        if (allRecipes.isEmpty()) {
            player.sendMessage("§7No villagers with trades in this village yet.");
        }
    }

    /**
     * Check if player has a pending rename and process it.
     * Only leaders can rename - this is checked before setting pending rename.
     */
    public boolean processRename(Player player, String newName) {
        VillageManager.VillageData village = pendingRenames.remove(player);
        if (village != null) {
            if (newName.equalsIgnoreCase("cancel")) {
                player.sendMessage("§7Rename cancelled.");
                return true;
            }
            
            // Validate name
            String trimmedName = newName.trim();
            if (trimmedName.isEmpty()) {
                player.sendMessage("§cName cannot be empty!");
                pendingRenames.put(player, village); // Keep pending
                return false;
            }
            
            if (trimmedName.length() > 32) {
                player.sendMessage("§cName is too long! Maximum 32 characters.");
                pendingRenames.put(player, village); // Keep pending
                return false;
            }
            
            // Check if name already exists
            boolean nameExists = villageManager.getAllVillages().stream()
                .anyMatch(v -> v.active && !v.equals(village) && v.name.equalsIgnoreCase(trimmedName));
            
            if (nameExists) {
                player.sendMessage("§cA village with that name already exists!");
                pendingRenames.put(player, village); // Keep pending
                return false;
            }
            
            // Verify player is still the leader
            InfluenceManager influenceManager = plugin.getInfluenceManager();
            if (influenceManager != null) {
                UUID currentLeader = influenceManager.getCurrentLeader(village.name);
                UUID playerUUID = player.getUniqueId();
                
                boolean isLeader = false;
                if (currentLeader != null && currentLeader.equals(playerUUID)) {
                    isLeader = true;
                } else if (currentLeader == null && village.owner != null && village.owner.equals(player.getName())) {
                    isLeader = true;
                }
                
                if (!isLeader) {
                    player.sendMessage("§cYou are no longer the village leader! Name change cancelled.");
                    return false;
                }
            }
            
            // Rename the village directly
            String oldName = village.name;
            boolean success = villageManager.renameVillage(village, trimmedName);
            
            if (success) {
                player.sendMessage("§aVillage renamed successfully to: §f" + trimmedName);
                
                // Notify nearby players
                org.bukkit.Location bellLoc = village.getAdminLocation();
                if (bellLoc != null && bellLoc.getWorld() != null) {
                    bellLoc.getWorld().getPlayers().forEach(p -> {
                        if (p.getLocation().distance(bellLoc) <= 50) {
                            p.sendMessage("§6Village '" + oldName + "' has been renamed to '" + trimmedName + "' by " + player.getName());
                        }
                    });
                }
                return true;
            } else {
                player.sendMessage("§cFailed to rename village!");
                return false;
            }
        }
        return false;
    }
    
    /** Parse village name from menu title (e.g. "§6§lVillagers - MyVillage" or "§6§lVillagers: Farmer - MyVillage"). */
    private VillageManager.VillageData getVillageFromMenuTitle(String title) {
        int i = title.lastIndexOf(" - ");
        if (i < 0) return null;
        String name = title.substring(i + 3).replaceAll("§.", "").trim();
        if (name.isEmpty()) return null;
        return villageManager.getAllVillages().stream()
            .filter(v -> v.active && v.name.equals(name))
            .findFirst()
            .orElse(null);
    }

    private VillageManager.VillageData getVillageFromTokenOrLocation(Player player) {
        org.bukkit.inventory.ItemStack token = player.getInventory().getItemInMainHand();
        if (token != null && org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.isToken(token)) {
            String tokenVillageName = org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken.getVillageName(plugin, token);
            if (tokenVillageName != null) {
                return villageManager.getAllVillages().stream()
                    .filter(v -> v.active && v.name.equals(tokenVillageName))
                    .findFirst()
                    .orElse(null);
            }
        }
        return villageManager.getVillageContaining(player.getLocation());
    }

    /**
     * Check if player has a pending rename.
     */
    public boolean hasPendingRename(Player player) {
        return pendingRenames.containsKey(player);
    }
    
    /**
     * Cancel pending rename for a player.
     */
    public void cancelRename(Player player) {
        pendingRenames.remove(player);
    }
    
    /**
     * Set pending village creation (for founder to name it).
     */
    public void setPendingVillageCreation(Player player, org.bukkit.Location bellLocation) {
        pendingVillageCreations.put(player, bellLocation);
    }
    
    /**
     * Check if player has pending village creation.
     */
    public boolean hasPendingVillageCreation(Player player) {
        return pendingVillageCreations.containsKey(player);
    }

    /**
     * Set pending merge (same-owner merge; player will type new name in chat).
     */
    public void setPendingMerge(Player player, VillageManager.VillageData primary, VillageManager.VillageData other) {
        if (player != null && primary != null && other != null) {
            pendingMerges.put(player, new MergeInfo(primary, other));
        }
    }

    /**
     * Check if player has pending merge.
     */
    public boolean hasPendingMerge(Player player) {
        return pendingMerges.containsKey(player);
    }

    /**
     * Cancel pending merge.
     */
    public void cancelPendingMerge(Player player) {
        pendingMerges.remove(player);
    }
    
    /**
     * Process village creation with founder's name.
     */
    private void processVillageCreation(Player player, String name) {
        org.bukkit.Location bellLocation = pendingVillageCreations.remove(player);
        if (bellLocation == null) return;
        
        if (name.equalsIgnoreCase("skip") || name.trim().isEmpty()) {
            // Use default name
            VillageManager.VillageData village = villageManager.createVillage(bellLocation, player, null);
            if (village != null) {
                int pop = plugin.getMeetingPointCount(village);
                villageManager.setPopulation(village, pop);
                player.sendMessage("§aVillage '" + village.name + "' has been established!");
                player.sendMessage("§ePopulation: " + village.population + " villagers (use this bell as gossip)");
            }
            return;
        }
        
        // Validate name
        String trimmedName = name.trim();
        if (trimmedName.length() > 32) {
            player.sendMessage("§cName is too long! Maximum 32 characters. Try again:");
            pendingVillageCreations.put(player, bellLocation);
            return;
        }
        
        // Check if name already exists
        boolean nameExists = villageManager.getAllVillages().stream()
            .anyMatch(v -> v.active && v.name.equalsIgnoreCase(trimmedName));
        
        if (nameExists) {
            player.sendMessage("§cA village with that name already exists! Try again:");
            pendingVillageCreations.put(player, bellLocation);
            return;
        }
        
        // Create village with founder's chosen name
        VillageManager.VillageData village = villageManager.createVillage(bellLocation, player, trimmedName);
        if (village != null) {
            int pop = plugin.getMeetingPointCount(village);
            villageManager.setPopulation(village, pop);
            player.sendMessage("§aVillage '" + village.name + "' has been established!");
            player.sendMessage("§ePopulation: " + village.population + " villagers (use this bell as gossip)");
            player.sendMessage("§7As founder, you have set the initial name. Villagers may vote to change it later!");
        }
    }

    /**
     * Process merge: player typed new village name (or 'cancel'). Same-owner merge only.
     */
    private void processMerge(Player player, String message) {
        MergeInfo info = pendingMerges.remove(player);
        if (info == null) return;

        if (message == null || message.trim().isEmpty() || message.equalsIgnoreCase("cancel")) {
            player.sendMessage("§7Merge cancelled.");
            return;
        }

        String trimmedName = message.trim();
        if (trimmedName.length() > 32) {
            player.sendMessage("§cName too long! Max 32 characters. Try again (or 'cancel'):");
            pendingMerges.put(player, info);
            return;
        }

        VillageManager.VillageData merged = villageManager.mergeVillages(info.primary, info.other, trimmedName, player);
        if (merged == null) {
            player.sendMessage("§cMerge failed. Try again with a different name (or 'cancel'):");
            pendingMerges.put(player, info);
        } else {
            player.sendMessage("§ePopulation: " + merged.population + " villagers (use this bell as gossip)");
        }
    }
}
