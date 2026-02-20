package org.duoKeyboardKoalition.hyempires.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Utility for Village Trading Tokens.
 * Obtained by right-clicking a village bell with an emerald.
 * Right-click with token to open the master trading menu (all villagers' trades for that bell).
 */
public class TradingToken {
    private static final String TOKEN_NAME = "§a§lVillage Trading Token";
    private static final String TOKEN_LORE_1 = "§7Right-click to open";
    private static final String TOKEN_LORE_2 = "§7master trading menu";

    public static ItemStack createToken(HyEmpiresPlugin plugin, String villageName) {
        ItemStack token = new ItemStack(Material.PAPER);
        ItemMeta meta = token.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(TOKEN_NAME));
            List<Component> lore = Arrays.asList(
                LegacyComponentSerializer.legacySection().deserialize(TOKEN_LORE_1),
                LegacyComponentSerializer.legacySection().deserialize(TOKEN_LORE_2),
                LegacyComponentSerializer.legacySection().deserialize(""),
                LegacyComponentSerializer.legacySection().deserialize("§7Village: §e" + villageName)
            );
            meta.lore(lore);
            PersistentDataContainer container = meta.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(plugin, "trading_village_name");
            container.set(key, PersistentDataType.STRING, villageName);
            token.setItemMeta(meta);
        }
        return token;
    }

    public static boolean isToken(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Component displayName = meta.displayName();
        if (displayName == null) return false;
        String nameStr = LegacyComponentSerializer.legacySection().serialize(displayName);
        return nameStr.equals(TOKEN_NAME);
    }

    public static String getVillageName(HyEmpiresPlugin plugin, ItemStack token) {
        if (!isToken(token)) return null;
        ItemMeta meta = token.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "trading_village_name");
        if (container.has(key, PersistentDataType.STRING)) {
            return container.get(key, PersistentDataType.STRING);
        }
        List<Component> lore = meta.lore();
        if (lore != null) {
            for (Component lineComponent : lore) {
                String line = LegacyComponentSerializer.legacySection().serialize(lineComponent);
                if (line != null && line.contains("Village:")) {
                    int i = line.indexOf("§e");
                    if (i >= 0) return line.substring(i + 2).trim();
                }
            }
        }
        return null;
    }
}
