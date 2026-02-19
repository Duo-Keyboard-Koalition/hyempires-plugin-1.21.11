package org.duoKeyboardKoalition.hyempires.Listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.Random;

public class VillagerListener implements Listener {
    private final Plugin plugin;
    private final Random random = new Random();

    // First names for villagers
    private final String[] firstNames = {
            "Aldrich", "Bruno", "Cecil", "Dexter", "Edmund",
            "Felix", "Gustav", "Harold", "Igor", "Julius",
            "Klaus", "Leopold", "Magnus", "Norbert", "Otto",
            "Percy", "Quincy", "Roland", "Siegfried", "Theodore",
            "Ada", "Beatrice", "Clara", "Dora", "Elsa",
            "Flora", "Greta", "Helga", "Ida", "Julia",
            "Klara", "Lydia", "Martha", "Nora", "Olga"
    };

    public VillagerListener(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Schedule the initial villager naming task
        plugin.getServer().getScheduler().runTaskLater(plugin, this::nameUnnamedVillagers, 20L); // Run 1 second after server start
    }

    private void nameUnnamedVillagers() {
        plugin.getLogger().info("Scanning for unnamed villagers...");
        int namedCount = 0;

        // Scan all loaded worlds
        for (World world : plugin.getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (needsName(villager)) {
                    nameVillager(villager);
                    namedCount++;
                }
            }
        }

        plugin.getLogger().info("Named " + namedCount + " unnamed villagers");
    }

    @EventHandler
    public void onVillagerSpawn(EntitySpawnEvent event) {
        if (event.getEntityType() != EntityType.VILLAGER) return;

        Villager villager = (Villager) event.getEntity();
        if (needsName(villager)) {
            nameVillager(villager);
        }
    }

    private boolean needsName(Villager villager) {
        Component customName = villager.customName();
        return customName == null || LegacyComponentSerializer.legacySection().serialize(customName).isEmpty();
    }

    private void nameVillager(Villager villager) {
        String firstName = firstNames[random.nextInt(firstNames.length)];

        // Format: FirstName the ProfessionName
        Component fullName = LegacyComponentSerializer.legacySection().deserialize("§6" + firstName);

        villager.customName(fullName);
        villager.setCustomNameVisible(true);
    }
}