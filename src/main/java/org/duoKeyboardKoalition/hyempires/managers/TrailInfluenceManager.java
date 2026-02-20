package org.duoKeyboardKoalition.hyempires.managers;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.utils.NBTFileManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.duoKeyboardKoalition.hyempires.managers.ChunkTerritoryManager.ChunkKey;

/**
 * Villager movement leaves a trail of influence per chunk.
 * Chunks touched most by a village's villagers get under that village's influence.
 * Influence decays over time.
 */
public class TrailInfluenceManager {
    private final JavaPlugin plugin;
    private final NBTFileManager nbtManager;

    // (villageName, ChunkKey) -> influence amount (decays over time)
    private final Map<String, Map<ChunkKey, Double>> villageTrailInfluence = new ConcurrentHashMap<>();

    private static final double DECAY_FACTOR_PER_MINUTE = 0.95; // 5% decay per minute
    /** Chunk is "under influence" (path/trail) if above this; pathfinding prefers these chunks. */
    public static final double TRAIL_THRESHOLD = 2.0;
    private static final double TRAIL_ADD_PER_VISIT = 1.0;
    /** Influence added per chunk when a path from bed/workstation to bell is claimed; marks the path for easier future pathfinding. */
    private static final double PATH_INFLUENCE_PER_CHUNK = 10.0;

    public TrailInfluenceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.nbtManager = new NBTFileManager(plugin, "trail_influence.nbt");
        loadData();
        startDecayTask();
    }

    /** Add trail influence when a villager (of this village) is in this chunk. */
    public void addTrail(String villageName, ChunkKey chunkKey) {
        if (villageName == null || chunkKey == null) return;
        villageTrailInfluence
            .computeIfAbsent(villageName, k -> new ConcurrentHashMap<>())
            .merge(chunkKey, TRAIL_ADD_PER_VISIT, Double::sum);
    }

    public void addTrail(String villageName, Location location) {
        if (location == null || location.getWorld() == null) return;
        addTrail(villageName, new ChunkKey(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4));
    }

    public void addTrail(String villageName, Chunk chunk) {
        if (chunk == null) return;
        addTrail(villageName, new ChunkKey(chunk));
    }

    /**
     * Mark a path (bed/workstation → bell) in the influence map so future pathfinding prefers following existing paths.
     * Each chunk on the path gets PATH_INFLUENCE_PER_CHUNK added; other structures will find the bell easier by following this path.
     */
    public void addPathInfluence(String villageName, java.util.List<ChunkKey> path) {
        if (villageName == null || path == null) return;
        for (ChunkKey key : path) {
            if (key == null) continue;
            villageTrailInfluence
                .computeIfAbsent(villageName, k -> new ConcurrentHashMap<>())
                .merge(key, PATH_INFLUENCE_PER_CHUNK, Double::sum);
        }
        saveData();
    }

    public double getTrailInfluence(String villageName, ChunkKey chunkKey) {
        Map<ChunkKey, Double> map = villageTrailInfluence.get(villageName);
        return map != null ? map.getOrDefault(chunkKey, 0.0) : 0.0;
    }

    /**
     * Which village has the most trail influence in this chunk (if above threshold).
     */
    public String getVillageForChunk(ChunkKey chunkKey) {
        String best = null;
        double bestVal = TRAIL_THRESHOLD;
        for (Map.Entry<String, Map<ChunkKey, Double>> e : villageTrailInfluence.entrySet()) {
            double v = e.getValue().getOrDefault(chunkKey, 0.0);
            if (v > bestVal) {
                bestVal = v;
                best = e.getKey();
            }
        }
        return best;
    }

    /** All trail chunks for a village (for showing influence particles). Returns chunk -> influence. */
    public Map<ChunkKey, Double> getTrailChunksForVillage(String villageName) {
        Map<ChunkKey, Double> map = villageTrailInfluence.get(villageName);
        return map == null ? Collections.emptyMap() : new HashMap<>(map);
    }

    private void decayAll() {
        for (Map<ChunkKey, Double> map : villageTrailInfluence.values()) {
            Iterator<Map.Entry<ChunkKey, Double>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ChunkKey, Double> e = it.next();
                double next = e.getValue() * DECAY_FACTOR_PER_MINUTE;
                if (next < 0.01) it.remove();
                else e.setValue(next);
            }
        }
        saveData();
    }

    private void startDecayTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::decayAll, 20L * 60, 20L * 60); // every 1 min
    }

    private void loadData() {
        List<Map<String, Object>> list = nbtManager.loadList("trails");
        for (Map<String, Object> entry : list) {
            String villageName = (String) entry.get("villageName");
            String world = (String) entry.get("world");
            Object xObj = entry.get("chunkX");
            Object zObj = entry.get("chunkZ");
            Object vObj = entry.get("influence");
            if (villageName == null || world == null || !(xObj instanceof Number) || !(zObj instanceof Number) || !(vObj instanceof Number)) continue;
            ChunkKey key = new ChunkKey(world, ((Number) xObj).intValue(), ((Number) zObj).intValue());
            double val = ((Number) vObj).doubleValue();
            if (val < 0.01) continue;
            villageTrailInfluence.computeIfAbsent(villageName, k -> new ConcurrentHashMap<>()).put(key, val);
        }
    }

    public void saveData() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Map<ChunkKey, Double>> e1 : villageTrailInfluence.entrySet()) {
            for (Map.Entry<ChunkKey, Double> e2 : e1.getValue().entrySet()) {
                if (e2.getValue() < 0.01) continue;
                Map<String, Object> m = new HashMap<>();
                m.put("villageName", e1.getKey());
                m.put("world", e2.getKey().world);
                m.put("chunkX", e2.getKey().x);
                m.put("chunkZ", e2.getKey().z);
                m.put("influence", e2.getValue());
                list.add(m);
            }
        }
        nbtManager.saveList("trails", list);
    }

    public void removeVillage(String villageName) {
        villageTrailInfluence.remove(villageName);
        saveData();
    }

    /**
     * Merge two villages' trail influence under newName; remove old names.
     */
    public void mergeVillages(String nameA, String nameB, String newName) {
        if (nameA == null || nameB == null || newName == null) return;
        Map<ChunkKey, Double> mapA = villageTrailInfluence.remove(nameA);
        Map<ChunkKey, Double> mapB = villageTrailInfluence.remove(nameB);
        Map<ChunkKey, Double> merged = new HashMap<>();
        if (mapA != null) merged.putAll(mapA);
        if (mapB != null) {
            for (Map.Entry<ChunkKey, Double> e : mapB.entrySet()) {
                merged.merge(e.getKey(), e.getValue(), Double::sum);
            }
        }
        if (!merged.isEmpty()) {
            villageTrailInfluence.put(newName, merged);
            saveData();
        }
    }

    public void renameVillage(String oldName, String newName) {
        Map<ChunkKey, Double> map = villageTrailInfluence.remove(oldName);
        if (map != null) {
            villageTrailInfluence.put(newName, map);
            saveData();
        }
    }
}
