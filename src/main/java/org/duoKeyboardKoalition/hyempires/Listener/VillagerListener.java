package org.duoKeyboardKoalition.hyempires.Listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import io.papermc.paper.event.entity.EntityMoveEvent;
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

    /** Chance per block exit that ground decays (villager foot traffic). */
    private static final double BLOCK_DECAY_CHANCE = 0.004;

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

    /**
     * When a villager transitions from one block to another, the block they exited (ground under feet)
     * has a small chance to decay from frequent use. Only non-farmland. Chains: grass->dirt->coarse->path;
     * stone->cobble->gravel; stone bricks->cracked; cobbled deepslate->deepslate tiles.
     */
    @EventHandler
    public void onVillagerMove(EntityMoveEvent event) {
        if (event.getEntityType() != EntityType.VILLAGER) return;
        if (!event.hasChangedBlock()) return; // only when transitioning to another block (exiting a block)
        Location from = event.getFrom();
        if (random.nextDouble() >= BLOCK_DECAY_CHANCE) return;

        // Block they were standing on (ground they exited)
        Block ground = from.getWorld().getBlockAt(from.getBlockX(), from.getBlockY() - 1, from.getBlockZ());
        if (ground.getType() == Material.FARMLAND) return; // never decay farmland
        Material next = decayNext(ground.getType());
        if (next != null) ground.setType(next);
    }

    /** Next material in the foot-traffic decay chain, or null if not decayable. */
    private static Material decayNext(Material current) {
        switch (current) {
            case GRASS_BLOCK: return Material.DIRT;
            case DIRT: return Material.COARSE_DIRT;
            case COARSE_DIRT: return Material.DIRT_PATH;
            case STONE: return Material.COBBLESTONE;
            case COBBLESTONE: return Material.GRAVEL;
            case STONE_BRICKS: return Material.CRACKED_STONE_BRICKS;
            case COBBLED_DEEPSLATE: return Material.DEEPSLATE_TILES;
            default: return null;
        }
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