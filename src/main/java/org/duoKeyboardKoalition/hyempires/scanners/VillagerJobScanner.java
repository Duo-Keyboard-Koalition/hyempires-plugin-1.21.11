package org.duoKeyboardKoalition.hyempires.scanners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
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
        // Use getKey() instead of deprecated name()
        try {
            org.bukkit.NamespacedKey key = material.getKey();
            if (key != null) {
                String keyStr = key.getKey().toUpperCase();
                return keyStr.contains("COMPOSTER") || keyStr.contains("TABLE") || keyStr.contains("LECTERN") ||
                       keyStr.contains("STAND") || keyStr.contains("FURNACE") || keyStr.contains("CAULDRON") ||
                       keyStr.contains("CUTTER") || keyStr.contains("LOOM") || keyStr.contains("GRINDSTONE") ||
                       keyStr.contains("BARREL");
            }
        } catch (Exception e) {
            // Fallback to enum name if key not available
        }
        // Fallback: use toString() which should work
        String name = material.toString().toUpperCase();
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
            // Fallback: use toString() instead of deprecated name()
            try {
                return profession.toString().toLowerCase().replace("minecraft:", "");
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
        
        // Sync bed and workstation from entity brain when we don't have them stored
        Location bedFromEntity = getBedLocationFromEntity(villager);
        if (bedFromEntity != null && data.bed == null) {
            data.bed = bedFromEntity.clone();
            changed = true;
        }
        Location jobFromEntity = getWorkstationLocationFromEntity(villager);
        if (jobFromEntity != null && data.jobsite == null) {
            data.jobsite = jobFromEntity.clone();
            changed = true;
        }

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
                    // Fallback: try to get profession from registry using NamespacedKey
                    try {
                        // Try to create NamespacedKey with minecraft: prefix
                        NamespacedKey key;
                        if (professionStr.contains(":")) {
                            key = NamespacedKey.fromString(professionStr);
                        } else {
                            key = NamespacedKey.minecraft(professionStr.toLowerCase());
                        }
                        
                        if (key != null) {
                            // Use Registry.VILLAGER_PROFESSION
                            org.bukkit.Registry<Villager.Profession> registry = org.bukkit.Registry.VILLAGER_PROFESSION;
                            Villager.Profession prof = registry.get(key);
                            if (prof != null) {
                                data.profession = prof;
                            } else {
                                data.profession = null;
                            }
                        } else {
                            data.profession = null;
                        }
                    } catch (Exception e) {
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
     * Get villager bed location: from stored data, or from entity (brain memory) if not stored.
     */
    public Location getVillagerBedLocation(Villager villager) {
        VillagerData data = villagerDataMap.get(villager.getUniqueId());
        if (data != null && data.bed != null) return data.bed;
        Location fromEntity = getBedLocationFromEntity(villager);
        if (fromEntity != null && data != null) {
            data.bed = fromEntity.clone();
            updateCsv();
        }
        return fromEntity;
    }
    
    /**
     * Get villager workstation location: from stored data, or from entity (brain memory) if not stored.
     */
    public Location getVillagerWorkstationLocation(Villager villager) {
        VillagerData data = villagerDataMap.get(villager.getUniqueId());
        if (data != null && data.jobsite != null) return data.jobsite;
        Location fromEntity = getWorkstationLocationFromEntity(villager);
        if (fromEntity != null && data != null) {
            data.jobsite = fromEntity.clone();
            updateCsv();
        }
        return fromEntity;
    }
    
    /**
     * Read bed location from villager's brain (HOME memory). Uses Bukkit MemoryKey API.
     */
    private Location getBedLocationFromEntity(Villager villager) {
        try {
            return villager.getMemory(MemoryKey.HOME);
        } catch (Throwable t) {
            return null;
        }
    }
    
    /**
     * Read workstation location from villager's brain (JOB_SITE memory). Uses Bukkit MemoryKey API.
     */
    private Location getWorkstationLocationFromEntity(Villager villager) {
        try {
            return villager.getMemory(MemoryKey.JOB_SITE);
        } catch (Throwable t) {
            return null;
        }
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