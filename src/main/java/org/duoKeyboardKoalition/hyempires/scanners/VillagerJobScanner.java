package org.duoKeyboardKoalition.hyempires.scanners;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.utils.CSVWriter;

import java.util.*;

public class VillagerJobScanner implements Listener {
    private final JavaPlugin plugin;
    private final CSVWriter csvWriter;
    private final Map<UUID, VillagerData> villagerDataMap = new HashMap<>();
    private static final String[] HEADERS = {
            "VillagerName", "UUID", "JobsiteX", "JobsiteY", "JobsiteZ",
            "Profession", "BedX", "BedY", "BedZ", "Status"
    };

    private class VillagerData {
        String name;
        UUID uuid;
        Location jobsite;
        Villager.Profession profession;
        Location bed;
        String status = "ALIVE";

        public String toCsvString() {
            return String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    name,
                    uuid,
                    jobsite != null ? jobsite.getBlockX() : "null",
                    jobsite != null ? jobsite.getBlockY() : "null",
                    jobsite != null ? jobsite.getBlockZ() : "null",
                    profession != null ? profession.getKeyOrThrow().getKey() : "NONE",
                    bed != null ? bed.getBlockX() : "null",
                    bed != null ? bed.getBlockY() : "null",
                    bed != null ? bed.getBlockZ() : "null",
                    status
            );
        }
    }

    public VillagerJobScanner(JavaPlugin plugin) {
        this.plugin = plugin;
        this.csvWriter = new CSVWriter(plugin, "villager_jobs.csv", HEADERS);
        loadExistingData();
        startPeriodicScanning();
    }

    private void loadExistingData() {
        // Implementation will be provided by the CSVWriter
        // Load existing data into villagerDataMap
    }

    private void startPeriodicScanning() {
        // Run scanner every minute
        Bukkit.getScheduler().runTaskTimer(plugin, this::scanAllVillagers, 20L * 60, 20L * 60);
    }

    public void scanAllVillagers() {
        plugin.getServer().getWorlds().forEach(world -> {
            world.getEntitiesByClass(Villager.class).forEach(this::updateVillagerData);
        });

        // Check for dead villagers
        Set<UUID> activeVillagers = new HashSet<>();
        plugin.getServer().getWorlds().forEach(world ->
                world.getEntitiesByClass(Villager.class)
                        .forEach(v -> activeVillagers.add(v.getUniqueId())));

        villagerDataMap.forEach((uuid, data) -> {
            if (!activeVillagers.contains(uuid) && "ALIVE".equals(data.status)) {
                data.status = "DEAD";
                updateCsv();
            }
        });
    }
    private void updateVillagerData(Villager villager) {
        UUID uuid = villager.getUniqueId();
        VillagerData data = villagerDataMap.computeIfAbsent(uuid, k -> new VillagerData());

        boolean changed = false;

        // Update name if changed
        String currentName = villager.getCustomName() != null ?
                villager.getCustomName() : "Villager-" + uuid.toString().substring(0, 8);
        if (!currentName.equals(data.name)) {
            data.name = currentName;
            changed = true;
        }

        // Update UUID (shouldn't change, but for completeness)
        data.uuid = uuid;

        // Update profession
        if (villager.getProfession() != data.profession) {
            data.profession = villager.getProfession();
            changed = true;
        }

        // For job site and bed location, we'll need to track these through events instead
        // or implement a different detection method since direct memory access isn't available

        if (changed) {
            updateCsv();
        }
    }

    @EventHandler
    public void onVillagerDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Villager) {
            UUID uuid = event.getEntity().getUniqueId();
            VillagerData data = villagerDataMap.get(uuid);
            if (data != null) {
                data.status = "DEAD";
                updateCsv();
            }
        }
    }

    @EventHandler
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        updateVillagerData(event.getEntity());
    }

    @EventHandler
    public void onVillagerAcquireTrade(VillagerAcquireTradeEvent event) {
        updateVillagerData((Villager) event.getEntity());
    }

    private void updateCsv() {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", HEADERS));

        villagerDataMap.values().forEach(data ->
                lines.add(data.toCsvString())
        );

        // Use CSVWriter to write all lines
        csvWriter.writeAll(lines);
    }

    // Utility method to get current data
    public Map<UUID, VillagerData> getVillagerData() {
        return Collections.unmodifiableMap(villagerDataMap);
    }
}