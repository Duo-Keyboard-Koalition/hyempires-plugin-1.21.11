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
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;
import org.duoKeyboardKoalition.hyempires.utils.TradingToken;
import org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken;

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
     * Placing a bell does NOT create a village.
     * Villages are only registered when a player right-clicks an existing bell with paper.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // No village creation on bell place
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
     * Bells start blank. Paper = new village. Administrative paper (token) = add bell to token's village, or merge if bell is another village.
     */
    @EventHandler
    public void onPlayerInteractRight(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BELL) return;

        Player player = event.getPlayer();
        org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
        VillageManager.VillageData villageAtBell = villageManager.getVillageAt(block.getLocation());

        // ---- Blank bell ----
        if (villageAtBell == null) {
            if (item != null && item.getType() == Material.PAPER) {
                event.setCancelled(true);
                // Administrative paper (token) = add this bell to token's village
                if (VillageAdminToken.isToken(item)) {
                    String tokenVillageName = VillageAdminToken.getVillageName((HyEmpiresPlugin) plugin, item);
                    if (tokenVillageName == null) {
                        player.sendMessage("§cInvalid administration token!");
                        return;
                    }
                    VillageManager.VillageData tokenVillage = villageManager.getVillageByName(tokenVillageName);
                    if (tokenVillage == null) {
                        player.sendMessage("§cVillage '" + tokenVillageName + "' no longer exists!");
                        return;
                    }
                    VillageManager.VillageData updated = villageManager.addBellToVillage(tokenVillage, block.getLocation(), player);
                    if (updated != null) {
                        player.sendMessage("§aBell added to §6" + tokenVillage.name + "§a!");
                    }
                    return;
                }
                // Plain paper = register new village
                player.sendMessage("§6=== Register Village ===");
                player.sendMessage("§eType the village name in chat (or 'skip' for default)");
                VillageMenuListener menuListener = plugin.getVillageMenuListener();
                if (menuListener != null) {
                    menuListener.setPendingVillageCreation(player, block.getLocation());
                }
            }
            return;
        }

        // ---- Bell already has a village ----
        // Emerald on village bell = give trading token (master trading menu for this bell)
        if (villageAtBell != null && item != null && item.getType() == Material.EMERALD) {
            event.setCancelled(true);
            org.bukkit.inventory.ItemStack token = TradingToken.createToken((HyEmpiresPlugin) plugin, villageAtBell.name);
            java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> overflow = player.getInventory().addItem(token);
            if (overflow.isEmpty()) {
                if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
                else player.getInventory().setItemInMainHand(null);
                player.sendMessage("§a§lTrading Token received! §7Right-click to open the village trading menu.");
            } else {
                player.sendMessage("§cYour inventory is full!");
            }
            return;
        }
        // Only handle paper/token; empty hand or other item = let bell ring (don't cancel)
        if (item == null || item.getType() != Material.PAPER) {
            return;
        }
        event.setCancelled(true);

        if (VillageAdminToken.isToken(item)) {
                String tokenVillageName = VillageAdminToken.getVillageName((HyEmpiresPlugin) plugin, item);
                if (tokenVillageName == null) {
                    player.sendMessage("§cInvalid administration token!");
                    return;
                }
                VillageManager.VillageData tokenVillage = villageManager.getVillageByName(tokenVillageName);
                if (tokenVillage == null) {
                    player.sendMessage("§cVillage '" + tokenVillageName + "' no longer exists!");
                    return;
                }
                if (tokenVillage.name.equals(villageAtBell.name)) {
                    // Same village: open administration menu
                    VillageMenuListener menuListenerSame = plugin.getVillageMenuListener();
                    if (menuListenerSame != null) menuListenerSame.openMainMenu(player, villageAtBell);
                    return;
                }
                // Different village: merge prompt
                boolean sameOwner = tokenVillage.owner != null && tokenVillage.owner.equals(villageAtBell.owner);
                if (sameOwner) {
                    player.sendMessage("§6=== Merge Villages ===");
                    player.sendMessage("§eMerge §6" + tokenVillage.name + " §eand §6" + villageAtBell.name + "§e?");
                    player.sendMessage("§7Type the new village name in chat (or 'cancel' to abort).");
                    VillageMenuListener menuListener = plugin.getVillageMenuListener();
                    if (menuListener != null) {
                        menuListener.setPendingMerge(player, tokenVillage, villageAtBell);
                    }
                } else {
                    player.sendMessage("§cMerge requires consent of the other village's owner.");
                    player.sendMessage("§7(Consent flow not yet implemented.)");
                }
                return;
            }
            // Plain paper on village bell: give admin token
            boolean hasToken = false;
            for (org.bukkit.inventory.ItemStack invItem : player.getInventory().getContents()) {
                if (invItem != null && VillageAdminToken.isToken(invItem)) {
                    String tokenVillage = VillageAdminToken.getVillageName((HyEmpiresPlugin) plugin, invItem);
                    if (villageAtBell.name.equals(tokenVillage)) {
                        hasToken = true;
                        break;
                    }
                }
            }
            if (!hasToken) {
                org.bukkit.inventory.ItemStack token = VillageAdminToken.createToken((HyEmpiresPlugin) plugin, villageAtBell.name);
                java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> overflow = player.getInventory().addItem(token);
                if (overflow.isEmpty()) {
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

        if (village != null) {
            int count = plugin.getResidentCount(village);
            villageManager.setPopulationFromResidentCount(village, count);
            player.sendMessage("§aPopulation updated: " + village.population + " villagers (bed + workplace in village)");
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
