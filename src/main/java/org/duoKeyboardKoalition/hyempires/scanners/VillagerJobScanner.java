package org.duoKeyboardKoalition.hyempires.scanners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.utils.NBTFileManager;

import java.util.*;

public class VillagerJobScanner implements Listener {
    private final JavaPlugin plugin;
    private final NBTFileManager nbtManager;
    private final Map<UUID, VillagerData> villagerDataMap = new HashMap<>();
    
    // NBT file structure:
    // villager_jobs.nbt:
    //   villagers:
    //     - name: Villager
    //       uuid: uuid-string
    //       jobsite:
    //         world: world
    //         x: 100
    //         y: 64
    //         z: 200
    //       profession: farmer
    //       bed:
    //         world: world
    //         x: 101
    //         y: 64
    //         z: 201
    //       status: ALIVE

    public class VillagerData {
        public String name;
        public UUID uuid;
        public Location jobsite;
        public Villager.Profession profession;
        public Location bed;
        public String status = "ALIVE";

        public String toCsvString() {
            return String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    name,
                    uuid,
                    jobsite != null ? jobsite.getBlockX() : "null",
                    jobsite != null ? jobsite.getBlockY() : "null",
                    jobsite != null ? jobsite.getBlockZ() : "null",
                    getProfessionKey(profession),
                    bed != null ? bed.getBlockX() : "null",
                    bed != null ? bed.getBlockY() : "null",
                    bed != null ? bed.getBlockZ() : "null",
                    status
            );
        }
    }

    public VillagerJobScanner(JavaPlugin plugin) {
        this.plugin = plugin;
        this.nbtManager = new NBTFileManager(plugin, "villager_jobs.nbt");
        loadExistingData();
        startPeriodicScanning();
    }
    
    /**
     * Helper: Get villager name as string from Component.
     */
    private String getVillagerNameString(Villager villager) {
        Component customName = villager.customName();
        return customName != null ? LegacyComponentSerializer.legacySection().serialize(customName) : null;
    }

    @SuppressWarnings("unchecked")
    private void loadExistingData() {
        // Try loading from NBT first
        List<Map<String, Object>> nbtData = nbtManager.loadList("villagers");
        
        if (!nbtData.isEmpty()) {
            // Load from NBT
            for (Map<String, Object> nbtVillager : nbtData) {
                VillagerData data = fromNBT(nbtVillager);
                if (data != null && data.uuid != null) {
                    villagerDataMap.put(data.uuid, data);
                }
            }
            plugin.getLogger().info("Loaded " + villagerDataMap.size() + " villagers from NBT");
            return;
        }
        
        // Fallback: Try loading from old CSV format (migration)
        // Note: CSV migration would require parsing the old format
        // For now, we'll just start fresh with NBT
        // Load existing data into villagerDataMap
    }

    private void startPeriodicScanning() {
        // Run scanner every minute
        Bukkit.getScheduler().runTaskTimer(plugin, this::scanAllVillagers, 20L * 60, 20L * 60);
    }

    public void scanAllVillagers() {
        plugin.getServer().getWorlds().forEach(world -> {
            world.getEntitiesByClass(Villager.class).forEach(villager -> {
                updateVillagerData(villager);
                
                // Check if villager's job site is outside village territory
                if (plugin instanceof HyEmpiresPlugin) {
                    HyEmpiresPlugin hyEmpiresPlugin = (HyEmpiresPlugin) plugin;
                    
                    // Get job site from stored data
                    VillagerData data = villagerDataMap.get(villager.getUniqueId());
                    if (data != null && data.jobsite != null) {
                        org.bukkit.Location jobSiteLoc = data.jobsite;
                        String villageName = hyEmpiresPlugin.getChunkTerritoryManager().getVillageForLocation(jobSiteLoc);
                        if (villageName == null) {
                            // Job site is outside village territory - break it
                            org.bukkit.block.Block workstationBlock = jobSiteLoc.getBlock();
                            if (isWorkstationBlock(workstationBlock.getType())) {
                                workstationBlock.breakNaturally();
                                String nameStr = getVillagerNameString(villager);
                                plugin.getLogger().info("Removed workstation outside village territory for villager " + (nameStr != null ? nameStr : "Unknown"));
                            }
                        }
                    }
                }
            });
        });

        // Check for dead villagers and update bed/workstation locations
        Set<UUID> activeVillagers = new HashSet<>();
        plugin.getServer().getWorlds().forEach(world -> {
            world.getEntitiesByClass(Villager.class).forEach(v -> {
                activeVillagers.add(v.getUniqueId());
                // Update villager data to sync bed/workstation locations
                updateVillagerData(v);
            });
        });

        villagerDataMap.forEach((uuid, data) -> {
            if (!activeVillagers.contains(uuid) && "ALIVE".equals(data.status)) {
                data.status = "DEAD";
                updateCsv();
            }
        });
    }
    
    private boolean isWorkstationBlock(org.bukkit.Material material) {
        String name = material.name();
        return name.contains("COMPOSTER") || name.contains("TABLE") || name.contains("LECTERN") ||
               name.contains("STAND") || name.contains("FURNACE") || name.contains("CAULDRON") ||
               name.contains("CUTTER") || name.contains("LOOM") || name.contains("GRINDSTONE") ||
               name.contains("BARREL");
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
            NamespacedKey key = profession.getKey();
            if (key == null) {
                return "NONE";
            }
            return key.getKey();
        } catch (Exception e) {
            // Fallback: try to get enum name
            try {
                return profession.name().toLowerCase();
            } catch (Exception e2) {
                return "NONE";
            }
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
        Villager villager = event.getEntity();
        updateVillagerData(villager);
        
        // Check if villager is trying to claim a job site outside village territory
        if (plugin instanceof HyEmpiresPlugin) {
            HyEmpiresPlugin hyEmpiresPlugin = (HyEmpiresPlugin) plugin;
            
            // Get villager's job site location from stored data
            VillagerData data = villagerDataMap.get(villager.getUniqueId());
            if (data != null && data.jobsite != null) {
                org.bukkit.Location jobSiteLoc = data.jobsite;
                // Check if job site is in village territory
                String villageName = hyEmpiresPlugin.getChunkTerritoryManager().getVillageForLocation(jobSiteLoc);
                
                if (villageName == null) {
                    // Job site is outside village territory - break the link
                    // Schedule task to break the workstation block
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        org.bukkit.block.Block workstationBlock = jobSiteLoc.getBlock();
                        if (isWorkstationBlock(workstationBlock.getType())) {
                            // Break the block to unlink the villager
                            workstationBlock.breakNaturally();
                            plugin.getLogger().info("Broke workstation outside village territory at " + jobSiteLoc);
                        }
                    }, 20L); // 1 second delay
                }
            }
        }
    }

    @EventHandler
    public void onVillagerAcquireTrade(VillagerAcquireTradeEvent event) {
        updateVillagerData((Villager) event.getEntity());
    }

    public void updateCsv() {
        saveData();
    }
    
    /**
     * Public method to update villager data (called by assignment listener).
     */
    public void updateVillagerData(Villager villager) {
        UUID uuid = villager.getUniqueId();
        VillagerData data = villagerDataMap.computeIfAbsent(uuid, k -> new VillagerData());

        boolean changed = false;

        // Update name if changed
        String currentName = getVillagerNameString(villager);
        if (currentName == null) {
            currentName = "Villager-" + uuid.toString().substring(0, 8);
        }
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
        
        // Note: Workstation and bed locations are tracked through events and manual assignment
        // They are not directly accessible via villager API in newer versions

        if (changed) {
            updateCsv();
        }
    }
    
    private void saveData() {
        List<Map<String, Object>> nbtVillagers = new ArrayList<>();
        for (VillagerData data : villagerDataMap.values()) {
            nbtVillagers.add(toNBT(data));
        }
        nbtManager.saveList("villagers", nbtVillagers);
    }
    
    /**
     * Convert VillagerData to NBT map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toNBT(VillagerData data) {
        Map<String, Object> nbt = new HashMap<>();
        nbt.put("name", data.name);
        nbt.put("uuid", data.uuid != null ? data.uuid.toString() : null);
        
        if (data.jobsite != null) {
            nbt.put("jobsite", NBTFileManager.locationToNBT(data.jobsite));
        } else {
            nbt.put("jobsite", null);
        }
        
        nbt.put("profession", getProfessionKey(data.profession));
        
        if (data.bed != null) {
            nbt.put("bed", NBTFileManager.locationToNBT(data.bed));
        } else {
            nbt.put("bed", null);
        }
        
        nbt.put("status", data.status);
        return nbt;
    }
    
    /**
     * Convert NBT map to VillagerData.
     */
    @SuppressWarnings("unchecked")
    private VillagerData fromNBT(Map<String, Object> nbt) {
        VillagerData data = new VillagerData();
        data.name = (String) nbt.get("name");
        
        Object uuidObj = nbt.get("uuid");
        if (uuidObj != null) {
            try {
                data.uuid = UUID.fromString(uuidObj.toString());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        
        Object jobsiteObj = nbt.get("jobsite");
        if (jobsiteObj instanceof Map) {
            data.jobsite = NBTFileManager.nbtToLocation((Map<String, Object>) jobsiteObj);
        }
        
        String professionStr = (String) nbt.getOrDefault("profession", "NONE");
        if (!professionStr.equals("NONE")) {
            try {
                // Try to parse as NamespacedKey first (e.g., "minecraft:farmer")
                if (professionStr.contains(":")) {
                    NamespacedKey key = NamespacedKey.fromString(professionStr);
                    // Find profession by key using registry
                    try {
                        org.bukkit.Registry<Villager.Profession> registry = org.bukkit.Registry.VILLAGER_PROFESSION;
                        Villager.Profession prof = registry.get(key);
                        if (prof != null) {
                            data.profession = prof;
                        }
                    } catch (Exception e) {
                        // Fallback: iterate through registry
                        try {
                            org.bukkit.Registry<Villager.Profession> registry = org.bukkit.Registry.VILLAGER_PROFESSION;
                            for (Villager.Profession prof : registry) {
                                NamespacedKey profKey = prof.getKey();
                                if (profKey != null && profKey.equals(key)) {
                                    data.profession = prof;
                                    break;
                                }
                            }
                        } catch (Exception e2) {
                            data.profession = null;
                        }
                    }
                } else {
                    // Fallback: try direct enum value (deprecated but needed for backward compatibility)
                    try {
                        @SuppressWarnings("deprecation")
                        Villager.Profession prof = Villager.Profession.valueOf(professionStr.toUpperCase());
                        data.profession = prof;
                    } catch (IllegalArgumentException e) {
                        data.profession = null;
                    }
                }
            } catch (Exception e) {
                data.profession = null;
            }
        }
        
        Object bedObj = nbt.get("bed");
        if (bedObj instanceof Map) {
            data.bed = NBTFileManager.nbtToLocation((Map<String, Object>) bedObj);
        }
        
        data.status = (String) nbt.getOrDefault("status", "ALIVE");
        return data;
    }

    // Utility method to get current data
    public Map<UUID, VillagerData> getVillagerData() {
        return Collections.unmodifiableMap(villagerDataMap);
    }
    
    /**
     * Get villager data by UUID.
     */
    public VillagerData getVillagerData(UUID uuid) {
        return villagerDataMap.get(uuid);
    }
    
    /**
     * Get villager bed location.
     */
    public Location getVillagerBedLocation(Villager villager) {
        VillagerData data = villagerDataMap.get(villager.getUniqueId());
        return data != null ? data.bed : null;
    }
    
    /**
     * Get villager workstation location.
     */
    public Location getVillagerWorkstationLocation(Villager villager) {
        VillagerData data = villagerDataMap.get(villager.getUniqueId());
        return data != null ? data.jobsite : null;
    }
    
    /**
     * Assign bed to villager (public API).
     */
    public boolean assignBed(Villager villager, Location bedLocation) {
        UUID uuid = villager.getUniqueId();
        VillagerData data = villagerDataMap.computeIfAbsent(uuid, k -> {
            VillagerData newData = new VillagerData();
            newData.uuid = uuid;
            String name = getVillagerNameString(villager);
            newData.name = name != null ? name : "Villager-" + uuid.toString().substring(0, 8);
            return newData;
        });
        
        data.bed = bedLocation.clone();
        updateCsv();
        return true;
    }
    
    /**
     * Assign workstation to villager (public API).
     */
    public boolean assignWorkstation(Villager villager, Location workstationLocation) {
        UUID uuid = villager.getUniqueId();
        VillagerData data = villagerDataMap.computeIfAbsent(uuid, k -> {
            VillagerData newData = new VillagerData();
            newData.uuid = uuid;
            String name = getVillagerNameString(villager);
            newData.name = name != null ? name : "Villager-" + uuid.toString().substring(0, 8);
            return newData;
        });
        
        data.jobsite = workstationLocation.clone();
        updateCsv();
        return true;
    }
}