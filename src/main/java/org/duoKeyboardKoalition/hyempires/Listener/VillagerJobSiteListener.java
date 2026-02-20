package org.duoKeyboardKoalition.hyempires.Listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.bukkit.Material;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;

import java.util.Set;

/**
 * Prevents villagers from claiming job sites outside village territory.
 */
public class VillagerJobSiteListener implements Listener {
    private final HyEmpiresPlugin plugin;
    
    // Workstation blocks that villagers can claim
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
    
    public VillagerJobSiteListener(HyEmpiresPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * On workstation place: no search. If within a village's 5x5/claimed territory it is part of that bell's influence.
     * If outside, player can right-click the block with an admin token to add it to a village (that starts the search).
     */
    @EventHandler
    public void onWorkstationPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!WORKSTATION_BLOCKS.contains(block.getType())) return;

        VillageManager.VillageData village = plugin.getVillageManager().getVillageContaining(block.getLocation());
        if (village != null) {
            return; // Within 5x5 or claimed chunks – already part of bell's influence
        }

        Player player = event.getPlayer();
        if (player != null) {
            player.sendMessage("§eWorkstation is outside village territory.");
            player.sendMessage("§7Place it within a village's radius of its bell to be part of that village.");
        }
    }
    
    /**
     * Check if a villager can claim a job site at the given location.
     * Called by other systems to validate job site claims.
     */
    public boolean canVillagerClaimJobSite(Villager villager, org.bukkit.Location location) {
        String villageName = plugin.getChunkTerritoryManager().getVillageForLocation(location);
        
        if (villageName == null) {
            // No village claims this chunk - villagers cannot claim job sites here
            return false;
        }
        
        // Check if villager is within the village's territory
        org.duoKeyboardKoalition.hyempires.managers.VillageManager.VillageData village = 
                plugin.getVillageManager().getVillageContaining(villager.getLocation());
        
        if (village == null || !village.name.equals(villageName)) {
            // Villager is not in the same village that claims this chunk
            return false;
        }
        
        return true;
    }
}
