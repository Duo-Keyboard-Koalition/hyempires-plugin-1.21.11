package org.duoKeyboardKoalition.hyempires.Listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.Plugin;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Names iron golems in a machine-like format: "VillageName - Golem Unit XXXX"
 * Only golems inside a village get named; unit numbers are per-village and 4-digit.
 */
public class IronGolemListener implements Listener {
    private static final Pattern GOLEM_UNIT_PATTERN = Pattern.compile(" - Golem Unit (\\d+)$");

    private final Plugin plugin;
    private final VillageManager villageManager;

    public IronGolemListener(Plugin plugin, VillageManager villageManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;

        // Schedule initial scan for unnamed golems after server is fully started
        plugin.getServer().getScheduler().runTaskLater(plugin, this::nameUnnamedIronGolems, 20L * 2); // 2 second delay
    }

    private void nameUnnamedIronGolems() {
        plugin.getLogger().info("Scanning for unnamed iron golems...");
        int namedCount = 0;

        for (World world : plugin.getServer().getWorlds()) {
            for (IronGolem golem : world.getEntitiesByClass(IronGolem.class)) {
                if (needsName(golem)) {
                    if (nameGolemIfInVillage(golem)) {
                        namedCount++;
                    }
                }
            }
        }

        if (namedCount > 0) {
            plugin.getLogger().info("Named " + namedCount + " iron golems");
        }
    }

    @EventHandler
    public void onIronGolemSpawn(EntitySpawnEvent event) {
        if (event.getEntityType() != EntityType.IRON_GOLEM) return;

        IronGolem golem = (IronGolem) event.getEntity();
        if (needsName(golem)) {
            // Delay by one tick so the entity is fully in the world and location is valid
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (golem.isValid() && needsName(golem)) {
                    nameGolemIfInVillage(golem);
                }
            }, 1L);
        }
    }

    private boolean needsName(IronGolem golem) {
        Component customName = golem.customName();
        if (customName == null) return true;
        String serialized = LegacyComponentSerializer.legacySection().serialize(customName);
        return serialized == null || serialized.trim().isEmpty();
    }

    /**
     * Names the golem if it is inside a village. Format: "VillageName - Golem Unit XXXX"
     * Returns true if the golem was named.
     */
    private boolean nameGolemIfInVillage(IronGolem golem) {
        VillageManager.VillageData village = villageManager.getVillageContaining(golem.getLocation());
        if (village == null) return false;

        int unitNumber = nextUnitNumberForVillage(village);
        String villageName = village.name != null ? village.name : "Village";
        String name = villageName + " - Golem Unit " + String.format("%04d", unitNumber);
        Component component = LegacyComponentSerializer.legacySection().deserialize("§7" + name); // Gray = machine-like

        golem.customName(component);
        golem.setCustomNameVisible(true);
        return true;
    }

    /**
     * Finds the next available unit number for this village by scanning existing golems
     * in the village that already have our name format.
     */
    private int nextUnitNumberForVillage(VillageManager.VillageData village) {
        int maxUnit = 0;
        World world = village.getAdminLocation() != null ? village.getAdminLocation().getWorld() : null;
        if (world == null) return 1;

        for (IronGolem golem : world.getEntitiesByClass(IronGolem.class)) {
            VillageManager.VillageData v = villageManager.getVillageContaining(golem.getLocation());
            if (v == null || !v.name.equals(village.name)) continue;

            Component customName = golem.customName();
            if (customName == null) continue;
            String serialized = LegacyComponentSerializer.legacySection().serialize(customName);
            if (serialized == null) continue;

            Matcher m = GOLEM_UNIT_PATTERN.matcher(serialized);
            if (m.find()) {
                try {
                    int num = Integer.parseInt(m.group(1));
                    if (num > maxUnit) maxUnit = num;
                } catch (NumberFormatException ignored) { }
            }
        }
        return maxUnit + 1;
    }
}
