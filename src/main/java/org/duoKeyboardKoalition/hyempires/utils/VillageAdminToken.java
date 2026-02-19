package org.duoKeyboardKoalition.hyempires.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class for creating and managing Village Administration Tokens.
 * These tokens allow players to interact with village administration menus.
 */
public class VillageAdminToken {
    private static final String TOKEN_NAME = "§6§lVillage Administration Token";
    private static final String TOKEN_LORE_1 = "§7Right-click anywhere";
    private static final String TOKEN_LORE_2 = "§7to open administration menu";
    
    /**
     * Create a village administration token.
     */
    public static ItemStack createToken(HyEmpiresPlugin plugin, String villageName) {
        ItemStack token = new ItemStack(Material.PAPER);
        ItemMeta meta = token.getItemMeta();
        
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(TOKEN_NAME));
            List<Component> loreComponents = Arrays.asList(
                LegacyComponentSerializer.legacySection().deserialize(TOKEN_LORE_1),
                LegacyComponentSerializer.legacySection().deserialize(TOKEN_LORE_2),
                LegacyComponentSerializer.legacySection().deserialize(""),
                LegacyComponentSerializer.legacySection().deserialize("§7Village: §e" + villageName)
            );
            meta.lore(loreComponents);
            
            // Store village name in persistent data
            PersistentDataContainer container = meta.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(plugin, "village_name");
            container.set(key, PersistentDataType.STRING, villageName);
            
            token.setItemMeta(meta);
        }
        
        return token;
    }
    
    /**
     * Check if an item is a village administration token.
     */
    public static boolean isToken(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) {
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
        return nameStr.equals(TOKEN_NAME);
    }
    
    /**
     * Get village name from token.
     */
    public static String getVillageName(HyEmpiresPlugin plugin, ItemStack token) {
        if (!isToken(token)) {
            return null;
        }
        
        ItemMeta meta = token.getItemMeta();
        if (meta == null) {
            return null;
        }
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "village_name");
        
        if (container.has(key, PersistentDataType.STRING)) {
            return container.get(key, PersistentDataType.STRING);
        }
        
        // Fallback: try to get from lore
        List<Component> lore = meta.lore();
        if (lore != null) {
            for (Component lineComponent : lore) {
                String line = LegacyComponentSerializer.legacySection().serialize(lineComponent);
                if (line.contains("Village:")) {
                    return line.substring(line.indexOf("§e") + 2);
                }
            }
        }
        
        return null;
    }
}
