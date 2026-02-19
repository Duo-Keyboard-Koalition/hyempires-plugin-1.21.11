package org.duoKeyboardKoalition.hyempires.Listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.managers.ChunkTerritoryManager;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;

import java.util.Arrays;
import java.util.List;

/**
 * Handles boundary drawing tool for village territory management.
 * Uses a STICK with custom name as the boundary tool.
 */
public class BoundaryToolListener implements Listener {
    private final HyEmpiresPlugin plugin;
    private final VillageManager villageManager;
    private final ChunkTerritoryManager chunkTerritoryManager;
    
    private static final String BOUNDARY_TOOL_NAME = "§6Village Boundary Tool";
    
    public BoundaryToolListener(HyEmpiresPlugin plugin) {
        this.plugin = plugin;
        this.villageManager = plugin.getVillageManager();
        this.chunkTerritoryManager = plugin.getChunkTerritoryManager();
    }
    
    /**
     * Check if item is the boundary tool.
     */
    public static boolean isBoundaryTool(ItemStack item) {
        if (item == null || item.getType() != Material.STICK) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Component displayName = meta.displayName();
        if (displayName == null) {
            return false;
        }
        String nameStr = LegacyComponentSerializer.legacySection().serialize(displayName);
        return nameStr.equals(BOUNDARY_TOOL_NAME);
    }
    
    /**
     * Create a boundary tool item.
     */
    public static ItemStack createBoundaryTool() {
        ItemStack tool = new ItemStack(Material.STICK);
        ItemMeta meta = tool.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(BOUNDARY_TOOL_NAME));
            List<Component> loreComponents = Arrays.asList(
                    LegacyComponentSerializer.legacySection().deserialize("§7Right-click chunk to claim"),
                    LegacyComponentSerializer.legacySection().deserialize("§7Left-click chunk to unclaim"),
                    LegacyComponentSerializer.legacySection().deserialize("§7Shift+Right-click to see info")
            );
            meta.lore(loreComponents);
            tool.setItemMeta(meta);
        }
        return tool;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (!isBoundaryTool(item)) {
            return;
        }
        
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        
        // Find village containing this location
        VillageManager.VillageData village = villageManager.getVillageContaining(block.getLocation());
        if (village == null) {
            player.sendMessage("§cYou must be within a village to manage boundaries!");
            return;
        }
        
        // Check if player can administer
        if (!villageManager.canAdminister(player, village)) {
            player.sendMessage("§cYou don't have enough influence to manage village boundaries!");
            return;
        }
        
        org.bukkit.Chunk chunk = block.getChunk();
        String chunkOwner = chunkTerritoryManager.getVillageForChunk(chunk);
        boolean isClaimedByThisVillage = village.name.equals(chunkOwner);
        
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                // Show chunk info
                showChunkInfo(player, village, chunk);
            } else {
                // Claim chunk
                if (isClaimedByThisVillage) {
                    player.sendMessage("§eThis chunk is already claimed by " + village.name);
                    return;
                }
                
                if (chunkOwner != null) {
                    player.sendMessage("§cThis chunk is claimed by another village: " + chunkOwner);
                    return;
                }
                
                // Calculate power
                double totalInfluence = 0.0;
                if (plugin.getInfluenceManager() != null) {
                    List<java.util.Map.Entry<java.util.UUID, org.duoKeyboardKoalition.hyempires.managers.InfluenceManager.InfluenceData>> ranking = 
                            plugin.getInfluenceManager().getInfluenceRanking(village.name);
                    totalInfluence = ranking.stream()
                            .mapToDouble(e -> e.getValue().influence)
                            .sum();
                }
                
                int villagePower = chunkTerritoryManager.calculateVillagePower(village.population, totalInfluence);
                
                if (chunkTerritoryManager.claimChunk(village.name, chunk, villagePower)) {
                    player.sendMessage("§aChunk claimed for " + village.name + "!");
                    int chunks = chunkTerritoryManager.getClaimedChunkCount(village.name);
                    int maxChunks = chunkTerritoryManager.getMaxChunks(villagePower);
                    player.sendMessage("§eChunks: " + chunks + "/" + maxChunks + " (Power: " + villagePower + ")");
                } else {
                    int currentChunks = chunkTerritoryManager.getClaimedChunkCount(village.name);
                    int maxChunks = chunkTerritoryManager.getMaxChunks(villagePower);
                    player.sendMessage("§cNot enough power to claim this chunk!");
                    player.sendMessage("§eCurrent: " + currentChunks + "/" + maxChunks + " chunks");
                    player.sendMessage("§7Increase population or influence to gain more power!");
                }
            }
            event.setCancelled(true);
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Unclaim chunk
            if (!isClaimedByThisVillage) {
                player.sendMessage("§cThis chunk is not claimed by " + village.name);
                return;
            }
            
            // Don't allow unclaiming the chunk containing the primary bell
            Location primaryLoc = village.getAdminLocation();
            if (primaryLoc != null && primaryLoc.getChunk().equals(chunk)) {
                player.sendMessage("§cCannot unclaim the chunk containing the village bell!");
                return;
            }
            
            if (chunkTerritoryManager.unclaimChunk(village.name, chunk)) {
                player.sendMessage("§6Chunk unclaimed from " + village.name);
                int chunks = chunkTerritoryManager.getClaimedChunkCount(village.name);
                player.sendMessage("§eRemaining chunks: " + chunks);
            }
            event.setCancelled(true);
        }
    }
    
    private void showChunkInfo(Player player, VillageManager.VillageData village, org.bukkit.Chunk chunk) {
        String chunkOwner = chunkTerritoryManager.getVillageForChunk(chunk);
        boolean isClaimed = chunkOwner != null;
        boolean isClaimedByThisVillage = village.name.equals(chunkOwner);
        
        player.sendMessage("§6=== Chunk Info ===");
        player.sendMessage("§eLocation: §f" + chunk.getX() + ", " + chunk.getZ());
        player.sendMessage("§eWorld: §f" + chunk.getWorld().getName());
        
        if (isClaimed) {
            if (isClaimedByThisVillage) {
                player.sendMessage("§eStatus: §aClaimed by " + village.name);
            } else {
                player.sendMessage("§eStatus: §cClaimed by " + chunkOwner);
            }
        } else {
            player.sendMessage("§eStatus: §7Unclaimed");
        }
        
        // Show village power info
        double totalInfluence = 0.0;
        if (plugin.getInfluenceManager() != null) {
            List<java.util.Map.Entry<java.util.UUID, org.duoKeyboardKoalition.hyempires.managers.InfluenceManager.InfluenceData>> ranking = 
                    plugin.getInfluenceManager().getInfluenceRanking(village.name);
            totalInfluence = ranking.stream()
                    .mapToDouble(e -> e.getValue().influence)
                    .sum();
        }
        
        int villagePower = chunkTerritoryManager.calculateVillagePower(village.population, totalInfluence);
        int currentChunks = chunkTerritoryManager.getClaimedChunkCount(village.name);
        int maxChunks = chunkTerritoryManager.getMaxChunks(villagePower);
        
        player.sendMessage("§eVillage Power: §f" + villagePower);
        player.sendMessage("§eClaimed Chunks: §f" + currentChunks + "/" + maxChunks);
    }
}
