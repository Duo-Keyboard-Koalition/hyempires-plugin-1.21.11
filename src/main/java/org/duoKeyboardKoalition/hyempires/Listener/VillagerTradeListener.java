package org.duoKeyboardKoalition.hyempires.Listener;

import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.managers.InfluenceManager;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;

/**
 * Listens for villager trades to grant influence in nearby villages.
 */
public class VillagerTradeListener implements Listener {
    private final HyEmpiresPlugin plugin;
    private final VillageManager villageManager;
    private final InfluenceManager influenceManager;

    public VillagerTradeListener(HyEmpiresPlugin plugin) {
        this.plugin = plugin;
        this.villageManager = plugin.getVillageManager();
        this.influenceManager = plugin.getInfluenceManager();
    }

    @EventHandler
    public void onVillagerTrade(InventoryClickEvent event) {
        // Check if this is a merchant inventory (villager trade)
        if (!(event.getInventory() instanceof MerchantInventory)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        MerchantInventory merchantInv = (MerchantInventory) event.getInventory();
        if (!(merchantInv.getMerchant() instanceof Villager)) {
            return;
        }

        Villager villager = (Villager) merchantInv.getMerchant();
        Player player = (Player) event.getWhoClicked();

        // Only process when trade is completed (clicking result slot)
        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }

        // Find village containing this villager
        VillageManager.VillageData village = villageManager.getVillageContaining(villager.getLocation());
        if (village == null) {
            return; // Villager not in a managed village
        }

        // Grant influence for trading
        // Base influence: 2.0 points per trade
        // More valuable trades could grant more (future enhancement)
        double influenceGain = 2.0;
        influenceManager.addInfluence(village.name, player.getUniqueId(), influenceGain, "Villager Trade");
        
        // Update activity timestamp
        influenceManager.updateActivity(village.name, player.getUniqueId());
    }
}
