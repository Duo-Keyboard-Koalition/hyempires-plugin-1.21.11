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

    private TrailInfluenceManager trailInfluenceManager;
    private RoadNetworkManager roadNetworkManager;
    /** Used for natural boundaries: getVillageFor* delegates to radius-from-bell. */
    private VillageManager villageManager;

    // Map: VillageName -> Set of claimed chunks (kept for boundary tool / optional display only; not used for containment)
    private final Map<String, Set<ChunkKey>> villageChunks = new ConcurrentHashMap<>();

    // Map: ChunkKey -> VillageName (for quick lookup)
    private final Map<ChunkKey, String> chunkToVillage = new ConcurrentHashMap<>();

    public void setTrailInfluenceManager(TrailInfluenceManager trailInfluenceManager) {
        this.trailInfluenceManager = trailInfluenceManager;
    }

    public void setRoadNetworkManager(RoadNetworkManager roadNetworkManager) {
        this.roadNetworkManager = roadNetworkManager;
    }

    public void setVillageManager(VillageManager villageManager) {
        this.villageManager = villageManager;
    }

    // Land influence: power = villagers living here. More villagers = more chunks.
    private static final int CHUNKS_PER_POWER = 2; // 2 chunks per villager (Vassal)
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
    
    /** Initial village claim: 5x5 chunks (radius 2) centered on the bell chunk. */
    private static final int BELL_CLAIM_RADIUS = 2;
    /** Max path length (chunks) when finding path to bell. */
    private static final int MAX_PATH_CHUNKS = 500;

    /**
     * Claim 5x5 chunks (radius 2) centered on a bell. Used for initial village and for each additional bell.
     * Does not overwrite chunks claimed by another village.
     */
    public int claimAreaAroundBell(String villageName, Location bellLocation) {
        if (bellLocation == null || bellLocation.getWorld() == null) return 0;
        Chunk center = bellLocation.getChunk();
        String worldName = center.getWorld().getName();
        int cx = center.getX();
        int cz = center.getZ();
        int claimed = 0;
        Set<ChunkKey> claimedSet = villageChunks.computeIfAbsent(villageName, k -> ConcurrentHashMap.newKeySet());
        for (int dx = -BELL_CLAIM_RADIUS; dx <= BELL_CLAIM_RADIUS; dx++) {
            for (int dz = -BELL_CLAIM_RADIUS; dz <= BELL_CLAIM_RADIUS; dz++) {
                ChunkKey key = new ChunkKey(worldName, cx + dx, cz + dz);
                String existing = chunkToVillage.get(key);
                if (existing != null && !existing.equals(villageName)) continue;
                if (existing != null && existing.equals(villageName)) continue;
                claimedSet.add(key);
                chunkToVillage.put(key, villageName);
                claimed++;
            }
        }
        if (claimed > 0) saveData();
        return claimed;
    }

    /**
     * Initialize village with a 5x5 chunk area centered on the bell.
     * Every bell claims radius 2 from its chunk (25 chunks total).
     */
    public void initializeVillage(String villageName, Location bellLocation) {
        claimAreaAroundBell(villageName, bellLocation);
    }
    
    /**
     * Village power (land influence) is directly from villagers living here.
     * More villagers = more land influence = more chunks the village can claim.
     * totalInfluence is kept for API compatibility but land power is population-only.
     */
    public int calculateVillagePower(int population, double totalInfluence) {
        return Math.max(0, population);
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
     * Find a path of chunks from a location to the bell, preferring already-claimed chunks and existing influence paths.
     * Hierarchy: beds and workstations are under the nearest bell; paths from structures to the bell leave a mark in the
     * influence path so other structures have an easier path by following existing paths.
     * Cost: 0 = claimed by us, 0.25 = has path/trail influence (existing path), 1.0 = unclaimed.
     * Chunks claimed by another village are not traversable.
     *
     * @return List of chunk keys from start (structure) to bell, or empty if no path.
     */
    public List<ChunkKey> findPathToBellPreferClaimed(Location from, Location bellLocation, String villageName) {
        if (from == null || bellLocation == null || from.getWorld() == null || !from.getWorld().equals(bellLocation.getWorld()))
            return Collections.emptyList();
        String worldName = from.getWorld().getName();
        int startCx = from.getBlockX() >> 4;
        int startCz = from.getBlockZ() >> 4;
        int endCx = bellLocation.getBlockX() >> 4;
        int endCz = bellLocation.getBlockZ() >> 4;

        Set<ChunkKey> claimedByUs = villageChunks.getOrDefault(villageName, Collections.emptySet());
        java.util.PriorityQueue<ChunkNode> open = new java.util.PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<String, ChunkNode> closed = new HashMap<>();
        double h0 = Math.sqrt((endCx - startCx) * (endCx - startCx) + (endCz - startCz) * (endCz - startCz));
        open.add(new ChunkNode(startCx, startCz, 0, h0, null));

        while (!open.isEmpty()) {
            ChunkNode cur = open.poll();
            String ck = cur.cx + "," + cur.cz;
            if (closed.containsKey(ck)) continue;
            closed.put(ck, cur);
            if (cur.g > MAX_PATH_CHUNKS) break;
            if (cur.cx == endCx && cur.cz == endCz) {
                List<ChunkKey> path = new ArrayList<>();
                ChunkNode n = cur;
                while (n != null) {
                    path.add(new ChunkKey(worldName, n.cx, n.cz));
                    n = n.parent;
                }
                Collections.reverse(path);
                return path;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int ncx = cur.cx + dx;
                    int ncz = cur.cz + dz;
                    ChunkKey nkey = new ChunkKey(worldName, ncx, ncz);
                    String existing = chunkToVillage.get(nkey);
                    if (existing != null && !existing.equals(villageName)) continue; // other village – not traversable
                    // Prefer: claimed by us (0), then existing path/trail influence (0.25), then unclaimed (1.0)
                    double stepCost;
                    if (claimedByUs.contains(nkey)) {
                        stepCost = 0.0;
                    } else if (trailInfluenceManager != null && trailInfluenceManager.getTrailInfluence(villageName, nkey) >= TrailInfluenceManager.TRAIL_THRESHOLD) {
                        stepCost = 0.25; // existing path makes it easier for others to find the bell
                    } else {
                        stepCost = 1.0;
                    }
                    double ng = cur.g + (dx != 0 && dz != 0 ? 1.414 : 1.0) * stepCost;
                    String nk = ncx + "," + ncz;
                    if (closed.containsKey(nk)) continue;
                    double nh = Math.sqrt((endCx - ncx) * (endCx - ncx) + (endCz - ncz) * (endCz - ncz));
                    ChunkNode next = new ChunkNode(ncx, ncz, ng, nh, cur);
                    next.f = next.g + next.h;
                    open.add(next);
                }
            }
        }
        return Collections.emptyList();
    }

    private static class ChunkNode {
        final int cx, cz;
        final double g, h;
        double f;
        final ChunkNode parent;
        ChunkNode(int cx, int cz, double g, double h, ChunkNode parent) {
            this.cx = cx; this.cz = cz; this.g = g; this.h = h; this.parent = parent;
            this.f = g + h;
        }
    }

    /**
     * Claim all chunks along a path for a village. Skips chunks already claimed by another village.
     * No power limit – path claiming extends territory to connect structures to the bell.
     */
    public int claimChunksAlongPath(String villageName, List<ChunkKey> path) {
        if (villageName == null || path == null) return 0;
        int claimed = 0;
        for (ChunkKey key : path) {
            String existing = chunkToVillage.get(key);
            if (existing != null) {
                if (existing.equals(villageName)) continue; // already ours
                continue; // other village – skip
            }
            villageChunks.computeIfAbsent(villageName, k -> ConcurrentHashMap.newKeySet()).add(key);
            chunkToVillage.put(key, villageName);
            claimed++;
        }
        if (claimed > 0) saveData();
        return claimed;
    }

    /**
     * Expand territory from beds/workstations (playdough effect).
     * Claims chunks that contain any of the given locations, up to max chunks for village power.
     */
    public int claimChunksFromStructures(String villageName, Collection<Location> structureLocations, int villagePower) {
        if (structureLocations == null || structureLocations.isEmpty()) return 0;
        Set<ChunkKey> preferred = new LinkedHashSet<>();
        for (Location loc : structureLocations) {
            if (loc != null && loc.getWorld() != null)
                preferred.add(new ChunkKey(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4));
        }
        int maxChunks = getMaxChunks(villagePower);
        int claimed = 0;
        for (ChunkKey key : preferred) {
            if (getClaimedChunkCount(villageName) >= maxChunks) break;
            if (chunkToVillage.containsKey(key)) {
                if (chunkToVillage.get(key).equals(villageName)) continue;
                else continue;
            }
            villageChunks.computeIfAbsent(villageName, k -> ConcurrentHashMap.newKeySet()).add(key);
            chunkToVillage.put(key, villageName);
            claimed++;
        }
        if (claimed > 0) saveData();
        return claimed;
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
     * Get the village for a chunk using natural boundaries only (distance from bells).
     * Delegates to VillageManager.getVillageContaining(chunk center).
     */
    public String getVillageForChunk(Chunk chunk) {
        if (villageManager != null && chunk != null && chunk.getWorld() != null) {
            Location center = new Location(chunk.getWorld(), chunk.getX() * 16 + 8, 64, chunk.getZ() * 16 + 8);
            VillageManager.VillageData v = villageManager.getVillageContaining(center);
            return v != null ? v.name : null;
        }
        return null;
    }

    /**
     * Get the village for a location using natural boundaries only (distance from bells).
     */
    public String getVillageForLocation(Location location) {
        if (villageManager != null && location != null) {
            VillageManager.VillageData v = villageManager.getVillageContaining(location);
            return v != null ? v.name : null;
        }
        return null;
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
     * Remove all chunks for a village. Also notifies trail and road managers if set.
     */
    public void removeVillage(String villageName) {
        Set<ChunkKey> chunks = villageChunks.remove(villageName);
        if (chunks != null) {
            chunks.forEach(chunkToVillage::remove);
            saveData();
        }
        if (trailInfluenceManager != null) trailInfluenceManager.removeVillage(villageName);
        if (roadNetworkManager != null) roadNetworkManager.removeVillage(villageName);
    }

    /**
     * Merge two villages into one: combine chunks under newName, remove old names.
     */
    public void mergeVillages(String nameA, String nameB, String newName) {
        if (nameA == null || nameB == null || newName == null) return;
        Set<ChunkKey> chunksA = villageChunks.remove(nameA);
        Set<ChunkKey> chunksB = villageChunks.remove(nameB);
        Set<ChunkKey> merged = ConcurrentHashMap.newKeySet();
        if (chunksA != null) {
            merged.addAll(chunksA);
            chunksA.forEach(key -> chunkToVillage.put(key, newName));
        }
        if (chunksB != null) {
            merged.addAll(chunksB);
            chunksB.forEach(key -> chunkToVillage.put(key, newName));
        }
        if (!merged.isEmpty()) {
            villageChunks.put(newName, merged);
            saveData();
        }
    }

    /**
     * Rename a village (update chunk territory data key).
     */
    public void renameVillage(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) {
            return;
        }
        Set<ChunkKey> chunks = villageChunks.remove(oldName);
        if (chunks != null) {
            villageChunks.put(newName, chunks);
            chunks.forEach(key -> chunkToVillage.put(key, newName));
            saveData();
        }
        if (trailInfluenceManager != null) trailInfluenceManager.renameVillage(oldName, newName);
        if (roadNetworkManager != null) roadNetworkManager.renameVillage(oldName, newName);
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
