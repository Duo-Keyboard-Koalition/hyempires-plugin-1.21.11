package org.duoKeyboardKoalition.hyempires.managers;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.utils.NBTFileManager;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player influence within villages.
 * Influence decays over time and determines village leadership.
 */
public class InfluenceManager {
    private final JavaPlugin plugin;
    private final NBTFileManager nbtManager;
    
    // Map: VillageName -> Map: PlayerUUID -> InfluenceData
    private final Map<String, Map<UUID, InfluenceData>> villageInfluences = new ConcurrentHashMap<>();
    
    // Configuration
    private static final long INACTIVITY_THRESHOLD_MS = 7L * 24 * 60 * 60 * 1000; // 7 days
    private static final double INFLUENCE_DECAY_PER_HOUR = 0.5; // 0.5 points per hour
    private static final double MIN_INFLUENCE = 0.0;
    private static final double MAX_INFLUENCE = 1000.0;
    
    // NBT file structure:
    // village_influence.nbt:
    //   influences:
    //     - villageName: Village-X-Z
    //       playerUUID: uuid-string
    //       influence: 50.5
    //       lastActivity: timestamp
    //       isFounder: true
    
    public static class InfluenceData {
        public double influence;
        public long lastActivity;
        public boolean isFounder;
        
        public InfluenceData(double influence, long lastActivity, boolean isFounder) {
            this.influence = Math.max(MIN_INFLUENCE, Math.min(MAX_INFLUENCE, influence));
            this.lastActivity = lastActivity;
            this.isFounder = isFounder;
        }
        
        public String toCsvString(String villageName, UUID playerUUID) {
            return String.format("%s,%s,%.2f,%d,%s",
                    villageName,
                    playerUUID.toString(),
                    influence,
                    lastActivity,
                    isFounder
            );
        }
        
        public static InfluenceData fromCsvLine(String[] parts) {
            if (parts.length >= 5) {
                double inf = Double.parseDouble(parts[2]);
                long activity = Long.parseLong(parts[3]);
                boolean founder = Boolean.parseBoolean(parts[4]);
                return new InfluenceData(inf, activity, founder);
            }
            return null;
        }
    }
    
    public InfluenceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.nbtManager = new NBTFileManager(plugin, "village_influence.nbt");
        loadExistingData();
        startDecayTask();
    }
    
    private void loadExistingData() {
        // Try loading from NBT first
        List<Map<String, Object>> nbtData = nbtManager.loadList("influences");
        
        if (!nbtData.isEmpty()) {
            // Load from NBT
            for (Map<String, Object> nbtInfluence : nbtData) {
                String villageName = (String) nbtInfluence.get("villageName");
                String playerUUIDStr = (String) nbtInfluence.get("playerUUID");
                if (villageName != null && playerUUIDStr != null) {
                    try {
                        UUID playerUUID = UUID.fromString(playerUUIDStr);
                        InfluenceData data = fromNBT(nbtInfluence);
                        if (data != null) {
                            villageInfluences.computeIfAbsent(villageName, k -> new ConcurrentHashMap<>())
                                    .put(playerUUID, data);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in influence data: " + playerUUIDStr);
                    }
                }
            }
            plugin.getLogger().info("Loaded influence data from NBT for " + villageInfluences.size() + " villages");
            return;
        }
        
        // Fallback: Try loading from old CSV format (migration)
        File oldCsvFile = new File(plugin.getDataFolder(), "village_influence.csv");
        if (oldCsvFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(oldCsvFile))) {
                String line;
                boolean firstLine = true;
                while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header
                }
                if (!line.trim().isEmpty()) {
                    String[] parts = line.split(",");
                    if (parts.length >= 5) {
                        String villageName = parts[0];
                        UUID playerUUID = UUID.fromString(parts[1]);
                        InfluenceData data = InfluenceData.fromCsvLine(parts);
                        if (data != null) {
                            villageInfluences.computeIfAbsent(villageName, k -> new ConcurrentHashMap<>())
                                    .put(playerUUID, data);
                        }
                    }
                }
            }
            plugin.getLogger().info("Migrated influence data from CSV to NBT for " + villageInfluences.size() + " villages");
            saveData(); // Save to NBT format
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load influence data from CSV: " + e.getMessage());
        }
    }
    
    /**
     * Convert InfluenceData to NBT map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toNBT(String villageName, UUID playerUUID, InfluenceData data) {
        Map<String, Object> nbt = new HashMap<>();
        nbt.put("villageName", villageName);
        nbt.put("playerUUID", playerUUID.toString());
        nbt.put("influence", data.influence);
        nbt.put("lastActivity", data.lastActivity);
        nbt.put("isFounder", data.isFounder);
        return nbt;
    }
    
    /**
     * Convert NBT map to InfluenceData.
     */
    @SuppressWarnings("unchecked")
    private InfluenceData fromNBT(Map<String, Object> nbt) {
        double influence = ((Number) nbt.getOrDefault("influence", 0.0)).doubleValue();
        long lastActivity = ((Number) nbt.getOrDefault("lastActivity", System.currentTimeMillis())).longValue();
        boolean isFounder = (Boolean) nbt.getOrDefault("isFounder", false);
        return new InfluenceData(influence, lastActivity, isFounder);
    }
    
    /**
     * Initialize founder influence when a village is created.
     */
    public void initializeFounder(String villageName, UUID founderUUID) {
        Map<UUID, InfluenceData> influences = villageInfluences.computeIfAbsent(villageName, k -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        influences.put(founderUUID, new InfluenceData(100.0, now, true));
        saveData();
    }
    
    /**
     * Add influence to a player in a village.
     * Called when player trades, completes quests, builds, etc.
     */
    public void addInfluence(String villageName, UUID playerUUID, double amount, String reason) {
        Map<UUID, InfluenceData> influences = villageInfluences.computeIfAbsent(villageName, k -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        
        InfluenceData data = influences.get(playerUUID);
        if (data == null) {
            // New player in village
            data = new InfluenceData(amount, now, false);
        } else {
            // Existing player - add influence and update activity
            double newInfluence = Math.min(MAX_INFLUENCE, data.influence + amount);
            data = new InfluenceData(newInfluence, now, data.isFounder);
        }
        
        influences.put(playerUUID, data);
        saveData();
        
        // Check for usurpation
        checkAndProcessUsurpation(villageName);
    }
    
    /**
     * Get a player's influence in a village.
     */
    public double getInfluence(String villageName, UUID playerUUID) {
        Map<UUID, InfluenceData> influences = villageInfluences.get(villageName);
        if (influences == null) return 0.0;
        InfluenceData data = influences.get(playerUUID);
        return data != null ? data.influence : 0.0;
    }
    
    /**
     * Get the current leader (highest influence) of a village.
     */
    public UUID getCurrentLeader(String villageName) {
        Map<UUID, InfluenceData> influences = villageInfluences.get(villageName);
        if (influences == null || influences.isEmpty()) return null;
        
        return influences.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().influence))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    /**
     * Check if a player can administer a village (has sufficient influence or is OP).
     */
    public boolean canAdminister(Player player, String villageName) {
        if (player.isOp()) return true;
        
        UUID leader = getCurrentLeader(villageName);
        if (leader == null) return true; // No leader, anyone can try
        
        double playerInfluence = getInfluence(villageName, player.getUniqueId());
        double leaderInfluence = getInfluence(villageName, leader);
        
        // Can administer if within 20% of leader's influence
        return playerInfluence >= leaderInfluence * 0.8;
    }
    
    /**
     * Get all players with influence in a village, sorted by influence.
     */
    public List<Map.Entry<UUID, InfluenceData>> getInfluenceRanking(String villageName) {
        Map<UUID, InfluenceData> influences = villageInfluences.get(villageName);
        if (influences == null) return new ArrayList<>();
        
        List<Map.Entry<UUID, InfluenceData>> ranking = new ArrayList<>(influences.entrySet());
        ranking.sort((a, b) -> Double.compare(b.getValue().influence, a.getValue().influence));
        return ranking;
    }
    
    /**
     * Check if founder has been inactive too long and process usurpation.
     */
    private void checkAndProcessUsurpation(String villageName) {
        Map<UUID, InfluenceData> influences = villageInfluences.get(villageName);
        if (influences == null) return;
        
        long now = System.currentTimeMillis();
        UUID founderUUID = null;
        InfluenceData founderData = null;
        
        // Find founder
        for (Map.Entry<UUID, InfluenceData> entry : influences.entrySet()) {
            if (entry.getValue().isFounder) {
                founderUUID = entry.getKey();
                founderData = entry.getValue();
                break;
            }
        }
        
        if (founderUUID == null || founderData == null) return;
        
        // Check if founder has been inactive too long
        long inactiveTime = now - founderData.lastActivity;
        if (inactiveTime > INACTIVITY_THRESHOLD_MS) {
            // Founder disappeared - find new leader
            UUID newLeader = getCurrentLeader(villageName);
            if (newLeader != null && !newLeader.equals(founderUUID)) {
                // Usurpation!
                InfluenceData newLeaderData = influences.get(newLeader);
                if (newLeaderData != null) {
                    // Mark new leader as founder
                    newLeaderData = new InfluenceData(newLeaderData.influence, newLeaderData.lastActivity, true);
                    influences.put(newLeader, newLeaderData);
                    
                    // Remove founder status from old founder
                    founderData = new InfluenceData(founderData.influence, founderData.lastActivity, false);
                    influences.put(founderUUID, founderData);
                    
                    saveData();
                    
                    plugin.getLogger().info("Village " + villageName + ": Leadership usurped by " + newLeader);
                    // Could notify players here
                }
            }
        }
    }
    
    /**
     * Apply influence decay over time.
     */
    private void applyDecay() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        
        for (Map<UUID, InfluenceData> influences : villageInfluences.values()) {
            for (Map.Entry<UUID, InfluenceData> entry : influences.entrySet()) {
                InfluenceData data = entry.getValue();
                long hoursSinceActivity = (now - data.lastActivity) / (60 * 60 * 1000);
                
                if (hoursSinceActivity > 0) {
                    double decay = hoursSinceActivity * INFLUENCE_DECAY_PER_HOUR;
                    double newInfluence = Math.max(MIN_INFLUENCE, data.influence - decay);
                    
                    if (newInfluence != data.influence) {
                        influences.put(entry.getKey(), new InfluenceData(newInfluence, data.lastActivity, data.isFounder));
                        changed = true;
                    }
                }
            }
        }
        
        if (changed) {
            saveData();
        }
    }
    
    /**
     * Start periodic decay task (runs every hour).
     */
    private void startDecayTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::applyDecay, 20L * 60 * 60, 20L * 60 * 60);
    }
    
    /**
     * Update player activity timestamp (called when player interacts with village).
     */
    public void updateActivity(String villageName, UUID playerUUID) {
        Map<UUID, InfluenceData> influences = villageInfluences.computeIfAbsent(villageName, k -> new ConcurrentHashMap<>());
        InfluenceData data = influences.get(playerUUID);
        if (data != null) {
            long now = System.currentTimeMillis();
            influences.put(playerUUID, new InfluenceData(data.influence, now, data.isFounder));
            saveData();
        }
    }
    
    private void saveData() {
        List<String> lines = new ArrayList<>();
        List<Map<String, Object>> nbtInfluences = new ArrayList<>();
        for (Map.Entry<String, Map<UUID, InfluenceData>> villageEntry : villageInfluences.entrySet()) {
            String villageName = villageEntry.getKey();
            for (Map.Entry<UUID, InfluenceData> playerEntry : villageEntry.getValue().entrySet()) {
                UUID playerUUID = playerEntry.getKey();
                InfluenceData data = playerEntry.getValue();
                nbtInfluences.add(toNBT(villageName, playerUUID, data));
            }
        }
        nbtManager.saveList("influences", nbtInfluences);
    }
    
    /**
     * Remove village influence data (when village is removed).
     */
    public void removeVillage(String villageName) {
        villageInfluences.remove(villageName);
        saveData();
    }
}
