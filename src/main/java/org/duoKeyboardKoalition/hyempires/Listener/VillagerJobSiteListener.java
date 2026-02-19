package org.duoKeyboardKoalition.hyempires.Listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

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
    
    @EventHandler
    public void onWorkstationPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material blockType = block.getType();
        
        // Check if this is a workstation block
        if (!WORKSTATION_BLOCKS.contains(blockType)) {
            return;
        }
        
        // Check if the block is in a village's claimed territory
        String villageName = plugin.getChunkTerritoryManager().getVillageForLocation(block.getLocation());
        
        if (villageName == null) {
            // Not in any village territory - warn player
            Player player = event.getPlayer();
            player.sendMessage("§eWarning: This workstation is outside village territory!");
            player.sendMessage("§7Villagers can only claim job sites within village chunks.");
            player.sendMessage("§7Use §f/hyempires tool §7to get a boundary tool and claim chunks.");
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
