package org.duoKeyboardKoalition.hyempires.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.duoKeyboardKoalition.hyempires.FeudalVillagerType;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;
import org.duoKeyboardKoalition.hyempires.scanners.VillagerJobScanner;

import java.util.*;
import java.util.UUID;

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
        Component title = LegacyComponentSerializer.legacySection().deserialize("§6§l" + village.name + " §7Administration");
        Inventory menu = Bukkit.createInventory(null, 27, title);
        
        // Population button
        ItemStack populationBtn = createMenuItem(
            Material.PLAYER_HEAD,
            "§a§lPopulation",
            Arrays.asList(
                "§7View villagers by type:",
                "§7Scout, Laborer, Peasant, Vassal",
                "",
                "§eClick to choose type"
            )
        );
        menu.setItem(10, populationBtn);

        // Villagers by profession (Minecraft profession filter)
        ItemStack professionBtn = createMenuItem(
            Material.EMERALD,
            "§6§lVillagers by Profession",
            Arrays.asList(
                "§7View villagers in this village",
                "§7Filter by Minecraft profession",
                "§7(Farmer, Librarian, etc.)",
                "",
                "§eClick to filter by profession"
            )
        );
        menu.setItem(11, professionBtn);

        // Rename Village button (only for leaders)
        ItemStack renameBtn = createMenuItem(
            Material.NAME_TAG,
            "§6§lRename Village",
            Arrays.asList(
                "§7Change the name of this village",
                "§7Only the current village leader",
                "§7can rename the village",
                "",
                "§eClick to rename village"
            )
        );
        menu.setItem(14, renameBtn);
        
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
        menu.setItem(16, infoBtn);
        
        player.openInventory(menu);
    }
    
    /** Title prefix for profession selector (choose Minecraft profession). */
    public static final String PROFESSION_SELECTOR_TITLE_PREFIX = "§6§lVillagers - ";
    /** Title prefix for profession table (list of villagers for one profession). */
    public static final String PROFESSION_TABLE_TITLE_PREFIX = "§6§lVillagers: ";

    /**
     * Open profession selector: All + Minecraft professions (Farmer, Librarian, etc.).
     */
    public void openVillagersByProfessionSelector(Player player, VillageManager.VillageData village) {
        plugin.refreshVillageVillagerData(village);
        List<Villager> all = plugin.getVillagersInVillage(village);
        Component title = LegacyComponentSerializer.legacySection().deserialize(PROFESSION_SELECTOR_TITLE_PREFIX + village.name);
        Inventory inv = Bukkit.createInventory(null, 27, title);

        inv.setItem(0, createMenuItem(Material.EMERALD, "§a§lAll Professions",
            Arrays.asList("§7Count: §f" + all.size(), "§7Show all villagers in this village", "", "§eClick to view")));

        Villager.Profession[] professions = Villager.Profession.values();
        for (int i = 0; i < Math.min(professions.length, 16); i++) {
            Villager.Profession p = professions[i];
            long count = all.stream().filter(v -> v.getProfession() == p).count();
            Material icon = getProfessionIcon(p);
            inv.setItem(1 + i, createMenuItem(icon, "§f§l" + formatProfessionName(p),
                Arrays.asList("§7Count: §f" + count, "", "§eClick to filter")));
        }

        inv.setItem(26, createMenuItem(Material.ARROW, "§7§lBack to Main Menu",
            Collections.singletonList("§7Return to village administration")));
        player.openInventory(inv);
    }

    /**
     * Open list of villagers in village filtered by Minecraft profession. profession null = all.
     */
    public void openVillagersByProfessionTable(Player player, VillageManager.VillageData village, Villager.Profession profession) {
        List<Villager> all = plugin.getVillagersInVillage(village);
        List<Villager> list = profession == null ? all : all.stream().filter(v -> v.getProfession() == profession).toList();
        String profLabel = profession == null ? "All" : formatProfessionName(profession);
        Component title = LegacyComponentSerializer.legacySection().deserialize(PROFESSION_TABLE_TITLE_PREFIX + profLabel + " - " + village.name);
        Inventory inv = Bukkit.createInventory(null, 54, title);

        int slot = 0;
        if (list.isEmpty()) {
            inv.setItem(0, createMenuItem(Material.BARRIER, "§c§lNo villagers",
                Arrays.asList("§7No " + profLabel + " villagers in this village.", "§7Villagers are linked by bed/workstation in village radius.")));
            slot = 1;
        } else {
            for (Villager v : list) {
                if (slot >= 53) break;
                inv.setItem(slot++, createVillagerProfessionItem(v));
            }
        }
        inv.setItem(53, createMenuItem(Material.ARROW, "§7§lBack to Profession Selection",
            Collections.singletonList("§7Choose profession filter")));
        player.openInventory(inv);
    }

    private ItemStack createVillagerProfessionItem(Villager villager) {
        Villager.Profession p = villager.getProfession();
        Material icon = getProfessionIcon(p);
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component customName = villager.customName();
            String nameStr = customName != null ? LegacyComponentSerializer.legacySection().serialize(customName) : null;
            String display = nameStr != null && !nameStr.isEmpty() ? nameStr : ("Villager - " + formatProfessionName(p));
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§a" + display));
            List<String> lore = Arrays.asList(
                "§7§m                    ",
                "§7Profession: §b" + formatProfessionName(p),
                "§7Type: §f" + (plugin.getVillagerType(villager) != null ? plugin.getVillagerType(villager).getDisplayName() : "—")
            );
            meta.lore(lore.stream().map(LegacyComponentSerializer.legacySection()::deserialize).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String formatProfessionName(Villager.Profession p) {
        if (p == null) return "None";
        try {
            String key = p.getKey() != null ? p.getKey().getKey() : p.name();
            return key == null ? "None" : key.substring(0, 1).toUpperCase() + key.substring(1).toLowerCase().replace('_', ' ');
        } catch (Exception e) {
            return p.name() != null ? p.name() : "None";
        }
    }

    /** Title prefix for population type selector (choose Scout/Laborer/Peasant/Vassal). */
    public static final String POPULATION_SELECTOR_TITLE_PREFIX = "§a§lPopulation - ";
    /** Title prefix for population table (list of villagers for one type). */
    public static final String POPULATION_TABLE_TITLE_PREFIX = "§a§lPopulation: ";

    /**
     * Open the population type selector (All / Scout / Laborer / Peasant / Vassal).
     * Refreshes villager data for this village when opened so the list is current.
     */
    public void openPopulationTypeSelector(Player player, VillageManager.VillageData village) {
        plugin.refreshVillageVillagerData(village);

        Component title = LegacyComponentSerializer.legacySection().deserialize(POPULATION_SELECTOR_TITLE_PREFIX + village.name);
        Inventory inv = Bukkit.createInventory(null, 9, title);

        int allCount = 0;
        for (FeudalVillagerType t : FeudalVillagerType.values()) allCount += plugin.getVillagerCountByType(village, t);
        inv.setItem(0, createMenuItem(Material.EMERALD, "§a§lAll Types",
            Arrays.asList("§7Count: §f" + allCount, "§7Show all villagers linked to this village", "", "§eClick to view")));

        inv.setItem(1, createTypeButton(village, FeudalVillagerType.SCOUT, Material.LEATHER_BOOTS));
        inv.setItem(2, createTypeButton(village, FeudalVillagerType.LABORER, Material.IRON_PICKAXE));
        inv.setItem(3, createTypeButton(village, FeudalVillagerType.PEASANT, Material.RED_BED));
        inv.setItem(4, createTypeButton(village, FeudalVillagerType.VASSAL, Material.GOLD_INGOT));

        ItemStack backBtn = createMenuItem(Material.ARROW, "§7§lBack to Main Menu",
            Collections.singletonList("§7Return to village administration"));
        inv.setItem(8, backBtn);

        player.openInventory(inv);
    }

    private ItemStack createTypeButton(VillageManager.VillageData village, FeudalVillagerType type, Material icon) {
        int count = plugin.getVillagerCountByType(village, type);
        return createMenuItem(icon, "§f§l" + type.getDisplayName(),
            Arrays.asList(type.getDescription(), "§7Count: §f" + count, "", "§eClick to view"));
    }

    /**
     * Open the population table for a chosen type (or All). Shows placeholder if empty.
     */
    public void openPopulationTable(Player player, VillageManager.VillageData village, FeudalVillagerType filterType) {
        List<VillagerInfo> villagers = getVillagersInVillage(village, filterType);
        int size = 54;
        String typeLabel = filterType == null ? "All" : filterType.getDisplayName();
        Component popTitle = LegacyComponentSerializer.legacySection().deserialize(POPULATION_TABLE_TITLE_PREFIX + typeLabel + " - " + village.name);
        Inventory inv = Bukkit.createInventory(null, size, popTitle);

        int slot = 0;
        if (villagers.isEmpty()) {
            inv.setItem(0, createMenuItem(Material.BARRIER, "§c§lNo villagers",
                Arrays.asList("§7No " + typeLabel + " villagers in this village.", "§7Assign beds/workstations in village to see them here.")));
            slot = 1;
        } else {
            for (VillagerInfo info : villagers) {
                if (slot >= size - 1) break;
                inv.setItem(slot++, createVillagerItem(info));
            }
        }

        ItemStack backBtn = createMenuItem(Material.ARROW, "§7§lBack to Type Selection",
            Collections.singletonList("§7Choose Scout / Laborer / Peasant / Vassal"));
        inv.setItem(size - 1, backBtn);

        player.openInventory(inv);
    }

    /**
     * Get villagers linked to this village, optionally filtered by type. Null = all types.
     */
    private List<VillagerInfo> getVillagersInVillage(VillageManager.VillageData village, FeudalVillagerType filterType) {
        List<Villager> villagers;
        if (filterType == null) {
            villagers = new ArrayList<>();
            for (FeudalVillagerType t : FeudalVillagerType.values()) {
                villagers.addAll(plugin.getVillagersInVillageByType(village, t));
            }
        } else {
            villagers = plugin.getVillagersInVillageByType(village, filterType);
        }
        return toVillagerInfoList(villagers);
    }

    private List<VillagerInfo> toVillagerInfoList(List<Villager> villagers) {
        List<VillagerInfo> out = new ArrayList<>();
        VillagerJobScanner scanner = plugin.getVillagerScanner();
        Map<UUID, VillagerJobScanner.VillagerData> villagerData = scanner != null ? scanner.getVillagerData() : new HashMap<>();
        for (Villager villager : villagers) {
            UUID uuid = villager.getUniqueId();
            VillagerJobScanner.VillagerData data = villagerData.get(uuid);
            VillagerInfo info = new VillagerInfo();
            info.villager = villager;
            Component customName = villager.customName();
            String nameStr = customName != null ? LegacyComponentSerializer.legacySection().serialize(customName) : null;
            info.name = nameStr != null ? nameStr : (data != null && data.name != null ? data.name : "Villager");
            info.bedLocation = scanner != null ? scanner.getVillagerBedLocation(villager) : (data != null ? data.bed : null);
            info.workplaceLocation = scanner != null ? scanner.getVillagerWorkstationLocation(villager) : (data != null ? data.jobsite : null);
            info.profession = villager.getProfession();
            info.type = plugin.getVillagerType(villager);
            out.add(info);
        }
        out.sort(Comparator.comparing(v -> v.name));
        return out;
    }
    
    /**
     * Create a menu item with custom display.
     */
    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(LegacyComponentSerializer.legacySection().deserialize(line));
            }
            meta.lore(loreComponents);
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
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§a" + info.name));

            List<String> loreStrings = new ArrayList<>();
            loreStrings.add("§7§m                    ");
            loreStrings.add("§6Type: §f" + (info.type != null ? info.type.getDisplayName() : "Vassal"));
            String professionName = getProfessionKey(info.profession);
            loreStrings.add("§7Profession: §b" + professionName);
            
            List<Component> loreComponents = new ArrayList<>();
            for (String line : loreStrings) {
                loreComponents.add(LegacyComponentSerializer.legacySection().deserialize(line));
            }
            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Get icon material for profession.
     */
    private Material getProfessionIcon(Villager.Profession profession) {
        if (profession == null) return Material.VILLAGER_SPAWN_EGG;
        
        String professionName = getProfessionKey(profession).toUpperCase();
        switch (professionName) {
            case "FARMER": return Material.WHEAT;
            case "FISHERMAN": return Material.FISHING_ROD;
            case "SHEPHERD": return Material.WHITE_WOOL;
            case "FLETCHER": return Material.ARROW;
            case "LIBRARIAN": return Material.BOOK;
            case "CARTOGRAPHER": return Material.MAP;
            case "CLERIC": return Material.BREWING_STAND;
            case "ARMORER": return Material.IRON_CHESTPLATE;
            case "WEAPONSMITH": return Material.IRON_SWORD;
            case "TOOLSMITH": return Material.IRON_PICKAXE;
            case "BUTCHER": return Material.COOKED_BEEF;
            case "LEATHERWORKER": return Material.LEATHER;
            case "MASON": return Material.STONE;
            default: return Material.VILLAGER_SPAWN_EGG;
        }
    }
    
    /**
     * Safely get profession key string.
     * Handles null profession and null keys.
     */
    private String getProfessionKey(Villager.Profession profession) {
        if (profession == null) {
            return "NONE";
        }
        try {
            org.bukkit.NamespacedKey key = profession.getKey();
            if (key == null) {
                return "NONE";
            }
            return key.getKey();
        } catch (Exception e) {
            // Fallback: try to get key
            try {
                org.bukkit.NamespacedKey key = profession.getKey();
                if (key != null) {
                    return key.getKey();
                }
            } catch (Exception e2) {
                // Ignore
            }
            return "NONE";
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
        /** Feudal hierarchy: Scout / Laborer / Peasant / Vassal (population table shows Vassals only). */
        public FeudalVillagerType type;
    }
}
