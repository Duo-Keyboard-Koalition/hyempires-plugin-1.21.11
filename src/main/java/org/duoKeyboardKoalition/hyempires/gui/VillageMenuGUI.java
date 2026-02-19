package org.duoKeyboardKoalition.hyempires.gui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;
import org.duoKeyboardKoalition.hyempires.scanners.VillagerJobScanner;

import java.util.*;

/**
 * Advanced menu system for village administration.
 * Accessed by left-clicking the village bell.
 */
public class VillageMenuGUI {
    private final HyEmpiresPlugin plugin;
    private final VillageManager villageManager;
    
    public VillageMenuGUI(HyEmpiresPlugin plugin, VillageManager villageManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
    }
    
    /**
     * Open the main village menu.
     */
    public void openMainMenu(Player player, VillageManager.VillageData village) {
        Inventory menu = Bukkit.createInventory(null, 27, "§6§l" + village.name + " §7Administration");
        
        // Population button
        ItemStack populationBtn = createMenuItem(
            Material.PLAYER_HEAD,
            "§a§lPopulation",
            Arrays.asList(
                "§7View all villagers in this village",
                "§7Shows: Name, Bed Location, Workplace",
                "",
                "§eClick to view population table"
            )
        );
        menu.setItem(11, populationBtn);
        
        // Rollcall button
        ItemStack rollcallBtn = createMenuItem(
            Material.BELL,
            "§6§lRollcall",
            Arrays.asList(
                "§7Summon all villagers to the bell",
                "§7Each villager will arrive with",
                "§7unique behavior patterns",
                "",
                "§eClick to start rollcall"
            )
        );
        menu.setItem(15, rollcallBtn);
        
        // Village info button
        ItemStack infoBtn = createMenuItem(
            Material.BOOK,
            "§b§lVillage Information",
            Arrays.asList(
                "§7Current Population: §a" + village.population,
                "§7Effective Radius: §e" + village.effectiveRadius + " blocks",
                "",
                "§eClick to view detailed info"
            )
        );
        menu.setItem(13, infoBtn);
        
        player.openInventory(menu);
    }
    
    /**
     * Open the population table view.
     */
    public void openPopulationTable(Player player, VillageManager.VillageData village) {
        // Get all villagers in the village
        List<VillagerInfo> villagers = getVillagersInVillage(village);
        
        // Create inventory with enough slots (9 rows = 54 slots)
        int size = Math.max(9, ((villagers.size() + 8) / 9) * 9);
        size = Math.min(size, 54); // Max 6 rows
        
        Inventory inv = Bukkit.createInventory(null, size, "§a§lPopulation - " + village.name);
        
        for (int i = 0; i < Math.min(villagers.size(), size); i++) {
            VillagerInfo info = villagers.get(i);
            ItemStack item = createVillagerItem(info);
            inv.setItem(i, item);
        }
        
        // Add back button
        ItemStack backBtn = createMenuItem(
            Material.ARROW,
            "§7§lBack to Main Menu",
            Collections.singletonList("§7Return to village administration")
        );
        inv.setItem(size - 1, backBtn);
        
        player.openInventory(inv);
    }
    
    /**
     * Get all villagers in a village.
     */
    private List<VillagerInfo> getVillagersInVillage(VillageManager.VillageData village) {
        List<VillagerInfo> villagers = new ArrayList<>();
        Location adminLoc = village.getAdminLocation();
        if (adminLoc == null) return villagers;
        
        VillagerJobScanner scanner = plugin.getVillagerScanner();
        Map<UUID, VillagerJobScanner.VillagerData> villagerData = scanner != null ? scanner.getVillagerData() : new HashMap<>();
        
        // Find all villagers within village radius
        Collection<Villager> nearbyVillagers = adminLoc.getWorld()
            .getNearbyEntities(adminLoc, village.effectiveRadius, 256, village.effectiveRadius, Villager.class);
        
        for (Villager villager : nearbyVillagers) {
            UUID uuid = villager.getUniqueId();
            VillagerJobScanner.VillagerData data = villagerData.get(uuid);
            
            VillagerInfo info = new VillagerInfo();
            info.villager = villager;
            info.name = villager.getCustomName() != null ? villager.getCustomName() : 
                       (data != null && data.name != null ? data.name : "Villager");
            info.bedLocation = data != null ? data.bed : null;
            info.workplaceLocation = data != null ? data.jobsite : null;
            info.profession = villager.getProfession();
            
            villagers.add(info);
        }
        
        // Sort by name
        villagers.sort(Comparator.comparing(v -> v.name));
        
        return villagers;
    }
    
    /**
     * Create a menu item with custom display.
     */
    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * Create an item representing a villager in the population table.
     */
    private ItemStack createVillagerItem(VillagerInfo info) {
        Material icon = getProfessionIcon(info.profession);
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§a" + info.name);
            
            List<String> lore = new ArrayList<>();
            lore.add("§7§m                    ");
            lore.add("§eBed Location:");
            if (info.bedLocation != null) {
                lore.add("§7  X: §f" + info.bedLocation.getBlockX());
                lore.add("§7  Y: §f" + info.bedLocation.getBlockY());
                lore.add("§7  Z: §f" + info.bedLocation.getBlockZ());
            } else {
                lore.add("§c  No bed assigned");
            }
            lore.add("");
            lore.add("§eWorkplace Location:");
            if (info.workplaceLocation != null) {
                lore.add("§7  X: §f" + info.workplaceLocation.getBlockX());
                lore.add("§7  Y: §f" + info.workplaceLocation.getBlockY());
                lore.add("§7  Z: §f" + info.workplaceLocation.getBlockZ());
            } else {
                lore.add("§c  No workplace assigned");
            }
            lore.add("");
            lore.add("§7Profession: §b" + (info.profession != null ? info.profession.name() : "NONE"));
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Get icon material for profession.
     */
    private Material getProfessionIcon(Villager.Profession profession) {
        if (profession == null) return Material.VILLAGER_SPAWN_EGG;
        
        switch (profession) {
            case FARMER: return Material.WHEAT;
            case FISHERMAN: return Material.FISHING_ROD;
            case SHEPHERD: return Material.WHITE_WOOL;
            case FLETCHER: return Material.ARROW;
            case LIBRARIAN: return Material.BOOK;
            case CARTOGRAPHER: return Material.MAP;
            case CLERIC: return Material.BREWING_STAND;
            case ARMORER: return Material.IRON_CHESTPLATE;
            case WEAPONSMITH: return Material.IRON_SWORD;
            case TOOLSMITH: return Material.IRON_PICKAXE;
            case BUTCHER: return Material.COOKED_BEEF;
            case LEATHERWORKER: return Material.LEATHER;
            case MASON: return Material.STONE;
            default: return Material.VILLAGER_SPAWN_EGG;
        }
    }
    
    /**
     * Villager information holder.
     */
    public static class VillagerInfo {
        public Villager villager;
        public String name;
        public Location bedLocation;
        public Location workplaceLocation;
        public Villager.Profession profession;
    }
}
