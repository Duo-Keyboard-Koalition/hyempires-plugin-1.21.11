package org.duoKeyboardKoalition.hyempires.Listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.scanners.VillagerJobScanner;

import java.util.Set;
import java.util.UUID;

/**
 * Handles manual assignment of beds and workstations to villagers.
 * Players can assign beds/workstations by:
 * - Right-clicking a bed/workstation while holding a stick (assignment tool)
 * - Then right-clicking the target villager
 */
public class VillagerAssignmentListener implements Listener {
    private final HyEmpiresPlugin plugin;
    private static final Material ASSIGNMENT_TOOL = Material.STICK;
    
    // Set of workstation blocks
    private static final Set<Material> WORKSTATION_BLOCKS = Set.of(
        Material.COMPOSTER,
        Material.SMITHING_TABLE,
        Material.LECTERN,
        Material.FLETCHING_TABLE,
        Material.CARTOGRAPHY_TABLE,
        Material.BREWING_STAND,
        Material.BLAST_FURNACE,
        Material.SMOKER,
        Material.CAULDRON,
        Material.STONECUTTER,
        Material.LOOM,
        Material.GRINDSTONE,
        Material.BARREL
    );
    
    public VillagerAssignmentListener(HyEmpiresPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Handle right-clicking a bed or workstation with assignment tool.
     */
    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        // Check if holding assignment tool
        if (item.getType() != ASSIGNMENT_TOOL) return;
        
        Block block = event.getClickedBlock();
        if (block == null) return;
        
        Material blockType = block.getType();
        
        // Check if it's a bed
        if (blockType == Material.RED_BED || blockType == Material.WHITE_BED || 
            blockType == Material.BLACK_BED || blockType == Material.BLUE_BED ||
            blockType == Material.BROWN_BED || blockType == Material.CYAN_BED ||
            blockType == Material.GRAY_BED || blockType == Material.GREEN_BED ||
            blockType == Material.LIGHT_BLUE_BED || blockType == Material.LIGHT_GRAY_BED ||
            blockType == Material.LIME_BED || blockType == Material.MAGENTA_BED ||
            blockType == Material.ORANGE_BED || blockType == Material.PINK_BED ||
            blockType == Material.PURPLE_BED || blockType == Material.YELLOW_BED) {
            
            event.setCancelled(true);
            player.sendMessage("§a§lBed Selected!");
            player.sendMessage("§7Right-click a villager to assign this bed to them.");
            
            // Store assignment data temporarily (using metadata or a map)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Store in a simple way - we'll use player metadata
                player.setMetadata("hyempires_assigning_bed", 
                    new org.bukkit.metadata.FixedMetadataValue(plugin, block.getLocation()));
            });
            
            return;
        }
        
        // Check if it's a workstation
        if (WORKSTATION_BLOCKS.contains(blockType)) {
            event.setCancelled(true);
            player.sendMessage("§a§lWorkstation Selected!");
            player.sendMessage("§7Right-click a villager to assign this workstation to them.");
            
            // Store assignment data
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.setMetadata("hyempires_assigning_workstation", 
                    new org.bukkit.metadata.FixedMetadataValue(plugin, block.getLocation()));
            });
            
            return;
        }
    }
    
    /**
     * Handle right-clicking a villager to complete assignment or show info.
     */
    @EventHandler
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        
        Player player = event.getPlayer();
        Villager villager = (Villager) event.getRightClicked();
        
        // Check for bed assignment
        if (player.hasMetadata("hyempires_assigning_bed")) {
            event.setCancelled(true);
            
            org.bukkit.metadata.MetadataValue metadata = player.getMetadata("hyempires_assigning_bed").get(0);
            org.bukkit.Location bedLocation = (org.bukkit.Location) metadata.value();
            
            // Assign bed to villager
            assignBedToVillager(villager, bedLocation, player);
            
            // Clear metadata
            player.removeMetadata("hyempires_assigning_bed", plugin);
            
            return;
        }
        
        // Check for workstation assignment
        if (player.hasMetadata("hyempires_assigning_workstation")) {
            event.setCancelled(true);
            
            org.bukkit.metadata.MetadataValue metadata = player.getMetadata("hyempires_assigning_workstation").get(0);
            org.bukkit.Location workstationLocation = (org.bukkit.Location) metadata.value();
            
            // Assign workstation to villager
            assignWorkstationToVillager(villager, workstationLocation, player);
            
            // Clear metadata
            player.removeMetadata("hyempires_assigning_workstation", plugin);
            
            return;
        }
        
        // If holding stick but not assigning, show villager info
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == ASSIGNMENT_TOOL && !player.isSneaking()) {
            event.setCancelled(true);
            showVillagerInfo(villager, player);
        }
    }
    
    /**
     * Show villager bed and workstation information.
     */
    private void showVillagerInfo(Villager villager, Player player) {
        VillagerJobScanner scanner = plugin.getVillagerScanner();
        if (scanner == null) {
            player.sendMessage("§cError: Villager scanner not available!");
            return;
        }
        
        String villagerName = villager.getCustomName() != null ? villager.getCustomName() : "Villager";
        UUID uuid = villager.getUniqueId();
        
        // Get villager data
        VillagerJobScanner.VillagerData data = scanner.getVillagerData(uuid);
        
        player.sendMessage("§6=== Villager Information ===");
        player.sendMessage("§eName: §f" + villagerName);
        player.sendMessage("§eUUID: §7" + uuid.toString().substring(0, 8) + "...");
        player.sendMessage("§eProfession: §b" + (villager.getProfession() != null ? villager.getProfession().name() : "NONE"));
        player.sendMessage("");
        
        // Bed location
        org.bukkit.Location bedLoc = scanner.getVillagerBedLocation(villager);
        if (bedLoc != null) {
            player.sendMessage("§aBed Location:");
            player.sendMessage("§7  World: §f" + bedLoc.getWorld().getName());
            player.sendMessage("§7  X: §f" + bedLoc.getBlockX());
            player.sendMessage("§7  Y: §f" + bedLoc.getBlockY());
            player.sendMessage("§7  Z: §f" + bedLoc.getBlockZ());
        } else {
            player.sendMessage("§cBed Location: §7Not assigned");
        }
        
        player.sendMessage("");
        
        // Workstation location
        org.bukkit.Location workstationLoc = scanner.getVillagerWorkstationLocation(villager);
        if (workstationLoc != null) {
            player.sendMessage("§aWorkstation Location:");
            player.sendMessage("§7  World: §f" + workstationLoc.getWorld().getName());
            player.sendMessage("§7  X: §f" + workstationLoc.getBlockX());
            player.sendMessage("§7  Y: §f" + workstationLoc.getBlockY());
            player.sendMessage("§7  Z: §f" + workstationLoc.getBlockZ());
        } else {
            player.sendMessage("§cWorkstation Location: §7Not assigned");
        }
        
        player.sendMessage("");
        player.sendMessage("§7Tip: Hold a stick and right-click a bed/workstation, then right-click this villager to assign!");
    }
    
    /**
     * Assign a bed to a villager.
     */
    private void assignBedToVillager(Villager villager, org.bukkit.Location bedLocation, Player player) {
        VillagerJobScanner scanner = plugin.getVillagerScanner();
        if (scanner == null) {
            player.sendMessage("§cError: Villager scanner not available!");
            return;
        }
        
        // Use public API to assign bed
        boolean success = scanner.assignBed(villager, bedLocation);
        
        if (success) {
            // Try to make villager sleep in the bed (if night)
            if (villager.getWorld().getTime() > 12500) { // Night time
                villager.sleep(bedLocation);
            }
            
            String villagerName = villager.getCustomName() != null ? villager.getCustomName() : "Villager";
            player.sendMessage("§a§lBed Assigned!");
            player.sendMessage("§7Assigned bed at §f" + bedLocation.getBlockX() + ", " + 
                             bedLocation.getBlockY() + ", " + bedLocation.getBlockZ() + 
                             " §7to §e" + villagerName);
        } else {
            player.sendMessage("§cError: Could not assign bed to villager!");
        }
    }
    
    /**
     * Assign a workstation to a villager.
     */
    private void assignWorkstationToVillager(Villager villager, org.bukkit.Location workstationLocation, Player player) {
        VillagerJobScanner scanner = plugin.getVillagerScanner();
        if (scanner == null) {
            player.sendMessage("§cError: Villager scanner not available!");
            return;
        }
        
        // Check if workstation block still exists
        Block workstationBlock = workstationLocation.getBlock();
        if (!WORKSTATION_BLOCKS.contains(workstationBlock.getType())) {
            player.sendMessage("§cError: Workstation block no longer exists at that location!");
            return;
        }
        
        // Use public API to assign workstation
        boolean success = scanner.assignWorkstation(villager, workstationLocation);
        
        if (success) {
            // Note: setWorkstation() is not available in Bukkit API
            // Villager will claim the workstation naturally when they pathfind to it
            
            // Update profession based on workstation type
            Material workstationType = workstationBlock.getType();
            updateVillagerProfession(villager, workstationType);
            
            String villagerName = villager.getCustomName() != null ? villager.getCustomName() : "Villager";
            player.sendMessage("§a§lWorkstation Assigned!");
            player.sendMessage("§7Assigned workstation at §f" + workstationLocation.getBlockX() + ", " + 
                             workstationLocation.getBlockY() + ", " + workstationLocation.getBlockZ() + 
                             " §7to §e" + villagerName);
        } else {
            player.sendMessage("§cError: Could not assign workstation to villager!");
        }
    }
    
    /**
     * Update villager profession based on workstation type.
     */
    private void updateVillagerProfession(Villager villager, Material workstationType) {
        Villager.Profession profession = null;
        
        switch (workstationType) {
            case COMPOSTER:
                profession = Villager.Profession.FARMER;
                break;
            case SMITHING_TABLE:
                profession = Villager.Profession.TOOLSMITH;
                break;
            case LECTERN:
                profession = Villager.Profession.LIBRARIAN;
                break;
            case FLETCHING_TABLE:
                profession = Villager.Profession.FLETCHER;
                break;
            case CARTOGRAPHY_TABLE:
                profession = Villager.Profession.CARTOGRAPHER;
                break;
            case BREWING_STAND:
                profession = Villager.Profession.CLERIC;
                break;
            case BLAST_FURNACE:
                profession = Villager.Profession.ARMORER;
                break;
            case SMOKER:
                profession = Villager.Profession.BUTCHER;
                break;
            case CAULDRON:
                profession = Villager.Profession.LEATHERWORKER;
                break;
            case STONECUTTER:
                profession = Villager.Profession.MASON;
                break;
            case LOOM:
                profession = Villager.Profession.SHEPHERD;
                break;
            case GRINDSTONE:
                profession = Villager.Profession.WEAPONSMITH;
                break;
            case BARREL:
                profession = Villager.Profession.FISHERMAN;
                break;
        }
        
        if (profession != null && villager.getProfession() != profession) {
            villager.setProfession(profession);
        }
    }
}
