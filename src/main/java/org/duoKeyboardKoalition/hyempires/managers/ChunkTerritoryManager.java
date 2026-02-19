package org.duoKeyboardKoalition.hyempires.managers;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.utils.NBTFileManager;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages chunk-based territory for villages.
 * Villages claim chunks, and villagers can only use job sites in claimed chunks.
 */
public class ChunkTerritoryManager {
    private final JavaPlugin plugin;
    private final NBTFileManager nbtManager;
    
    // Map: VillageName -> Set of claimed chunks
    private final Map<String, Set<ChunkKey>> villageChunks = new ConcurrentHashMap<>();
    
    // Map: ChunkKey -> VillageName (for quick lookup)
    private final Map<ChunkKey, String> chunkToVillage = new ConcurrentHashMap<>();
    
    // Power requirements: chunks per power point
    private static final int CHUNKS_PER_POWER = 1; // 1 chunk per power point
    private static final int BASE_CHUNKS = 1; // Starting village gets 1 chunk free
    
    // NBT file structure:
    // village_chunks.nbt:
    //   chunks:
    //     - villageName: Village-X-Z
    //       world: world
    //       chunkX: 6
    //       chunkZ: 12
    
    /**
     * Key for identifying chunks across worlds.
     */
    public static class ChunkKey {
        public final String world;
        public final int x;
        public final int z;
        
        public ChunkKey(String world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }
        
        public ChunkKey(Chunk chunk) {
            this.world = chunk.getWorld().getName();
            this.x = chunk.getX();
            this.z = chunk.getZ();
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkKey chunkKey = (ChunkKey) o;
            return x == chunkKey.x && z == chunkKey.z && world.equals(chunkKey.world);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(world, x, z);
        }
        
        public String toString() {
            return world + ":" + x + "," + z;
        }
    }
    
    public ChunkTerritoryManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.nbtManager = new NBTFileManager(plugin, "village_chunks.nbt");
        loadExistingData();
    }
    
    private void loadExistingData() {
        // Try loading from NBT first
        List<Map<String, Object>> nbtData = nbtManager.loadList("chunks");
        
        if (!nbtData.isEmpty()) {
            // Load from NBT
            for (Map<String, Object> nbtChunk : nbtData) {
                String villageName = (String) nbtChunk.get("villageName");
                String world = (String) nbtChunk.get("world");
                Object xObj = nbtChunk.get("chunkX");
                Object zObj = nbtChunk.get("chunkZ");
                
                if (villageName != null && world != null && xObj instanceof Number && zObj instanceof Number) {
                    int chunkX = ((Number) xObj).intValue();
                    int chunkZ = ((Number) zObj).intValue();
                    ChunkKey key = new ChunkKey(world, chunkX, chunkZ);
                    villageChunks.computeIfAbsent(villageName, k -> ConcurrentHashMap.newKeySet()).add(key);
                    chunkToVillage.put(key, villageName);
                }
            }
            plugin.getLogger().info("Loaded chunk territory data from NBT for " + villageChunks.size() + " villages");
            return;
        }
        
        // Fallback: Try loading from old CSV format (migration)
        File oldCsvFile = new File(plugin.getDataFolder(), "village_chunks.csv");
        if (oldCsvFile.exists()) {
            File dataFile = oldCsvFile;
            if (!dataFile.exists()) {
                return;
            }
            
            try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                if (!line.trim().isEmpty()) {
                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        String villageName = parts[0];
                        String world = parts[1];
                        int chunkX = Integer.parseInt(parts[2]);
                        int chunkZ = Integer.parseInt(parts[3]);
                        
                        ChunkKey key = new ChunkKey(world, chunkX, chunkZ);
                        villageChunks.computeIfAbsent(villageName, k -> ConcurrentHashMap.newKeySet()).add(key);
                        chunkToVillage.put(key, villageName);
                    }
                }
            }
                plugin.getLogger().info("Migrated chunk territory from CSV to NBT for " + villageChunks.size() + " villages");
                saveData(); // Save to NBT format
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to load chunk territory from CSV: " + e.getMessage());
            }
        }
    }
    
    /**
     * Initialize village with base chunk (the chunk containing the bell).
     */
    public void initializeVillage(String villageName, Location bellLocation) {
        Chunk chunk = bellLocation.getChunk();
        ChunkKey key = new ChunkKey(chunk);
        
        villageChunks.computeIfAbsent(villageName, k -> ConcurrentHashMap.newKeySet()).add(key);
        chunkToVillage.put(key, villageName);
        saveData();
    }
    
    /**
     * Calculate village power based on population and total influence.
     */
    public int calculateVillagePower(int population, double totalInfluence) {
        // Power = population + (total influence / 10)
        // Example: 10 villagers + 100 influence = 10 + 10 = 20 power
        return population + (int)(totalInfluence / 10.0);
    }
    
    /**
     * Get maximum chunks a village can claim based on power.
     */
    public int getMaxChunks(int power) {
        return BASE_CHUNKS + (power * CHUNKS_PER_POWER);
    }
    
    /**
     * Check if village can claim a new chunk.
     */
    public boolean canClaimChunk(String villageName, int currentPower) {
        int currentChunks = getClaimedChunkCount(villageName);
        int maxChunks = getMaxChunks(currentPower);
        return currentChunks < maxChunks;
    }
    
    /**
     * Claim a chunk for a village (if it has enough power).
     */
    public boolean claimChunk(String villageName, Chunk chunk, int villagePower) {
        ChunkKey key = new ChunkKey(chunk);
        
        // Check if chunk is already claimed
        if (chunkToVillage.containsKey(key)) {
            String existingVillage = chunkToVillage.get(key);
            if (existingVillage.equals(villageName)) {
                return true; // Already claimed by this village
            }
            return false; // Claimed by another village
        }
        
        // Check if village has enough power
        if (!canClaimChunk(villageName, villagePower)) {
            return false;
        }
        
        // Claim the chunk
        villageChunks.computeIfAbsent(villageName, k -> ConcurrentHashMap.newKeySet()).add(key);
        chunkToVillage.put(key, villageName);
        saveData();
        
        return true;
    }
    
    /**
     * Unclaim a chunk (for boundary redrawing).
     */
    public boolean unclaimChunk(String villageName, Chunk chunk) {
        ChunkKey key = new ChunkKey(chunk);
        Set<ChunkKey> chunks = villageChunks.get(villageName);
        
        if (chunks != null && chunks.remove(key)) {
            chunkToVillage.remove(key);
            saveData();
            return true;
        }
        
        return false;
    }
    
    /**
     * Get the village that owns a chunk.
     */
    public String getVillageForChunk(Chunk chunk) {
        ChunkKey key = new ChunkKey(chunk);
        return chunkToVillage.get(key);
    }
    
    /**
     * Get the village that owns a chunk at a location.
     */
    public String getVillageForLocation(Location location) {
        Chunk chunk = location.getChunk();
        return getVillageForChunk(chunk);
    }
    
    /**
     * Check if a location is within a village's claimed territory.
     */
    public boolean isLocationInVillageTerritory(String villageName, Location location) {
        Chunk chunk = location.getChunk();
        ChunkKey key = new ChunkKey(chunk);
        Set<ChunkKey> chunks = villageChunks.get(villageName);
        return chunks != null && chunks.contains(key);
    }
    
    /**
     * Get all chunks claimed by a village.
     */
    public Set<ChunkKey> getClaimedChunks(String villageName) {
        return Collections.unmodifiableSet(villageChunks.getOrDefault(villageName, Collections.emptySet()));
    }
    
    /**
     * Get count of chunks claimed by a village.
     */
    public int getClaimedChunkCount(String villageName) {
        return villageChunks.getOrDefault(villageName, Collections.emptySet()).size();
    }
    
    /**
     * Remove all chunks for a village.
     */
    public void removeVillage(String villageName) {
        Set<ChunkKey> chunks = villageChunks.remove(villageName);
        if (chunks != null) {
            chunks.forEach(chunkToVillage::remove);
            saveData();
        }
    }
    
    private void saveData() {
        List<Map<String, Object>> nbtChunks = new ArrayList<>();
        
        for (Map.Entry<String, Set<ChunkKey>> entry : villageChunks.entrySet()) {
            String villageName = entry.getKey();
            for (ChunkKey key : entry.getValue()) {
                Map<String, Object> nbtChunk = new HashMap<>();
                nbtChunk.put("villageName", villageName);
                nbtChunk.put("world", key.world);
                nbtChunk.put("chunkX", key.x);
                nbtChunk.put("chunkZ", key.z);
                nbtChunks.add(nbtChunk);
            }
        }
        
        nbtManager.saveList("chunks", nbtChunks);
    }
}
