package org.duoKeyboardKoalition.hyempires.managers;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.utils.NBTFileManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.duoKeyboardKoalition.hyempires.managers.ChunkTerritoryManager.ChunkKey;

/**
 * Road networks per village. Roads connect bell to beds and workstations.
 * Cost to build = path cost (horizontal + heavy elevation penalty). Influence pays for roads.
 */
public class RoadNetworkManager {
    private final JavaPlugin plugin;
    private final NBTFileManager nbtManager;

    // villageName -> chunks that have a road segment
    private final Map<String, Set<ChunkKey>> villageRoadChunks = new ConcurrentHashMap<>();

    /** Cost per block horizontal; elevation change is multiplied by this (high penalty for ups/downs). */
    private static final double ELEVATION_PENALTY = 5.0;
    private static final double HORIZONTAL_COST = 1.0;
    private static final int MAX_PATH_LENGTH = 500;

    /** Preferred road blocks (path, cobblestone, deepslate, etc.) – give path drawing a bonus / mark roads. */
    private static final Material[] ROAD_PREFERRED_MATERIALS = {
        Material.DIRT_PATH,
        Material.MOSSY_COBBLESTONE,
        Material.COBBLESTONE,
        Material.COBBLED_DEEPSLATE,
        Material.DEEPSLATE_BRICKS,
        Material.DEEPSLATE_TILES,
        Material.STONE_BRICKS,
        Material.MOSSY_STONE_BRICKS,
        Material.GRAVEL
    };

    public RoadNetworkManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.nbtManager = new NBTFileManager(plugin, "village_roads.nbt");
        loadData();
    }

    public boolean isChunkOnRoad(String villageName, ChunkKey key) {
        Set<ChunkKey> set = villageRoadChunks.get(villageName);
        return set != null && set.contains(key);
    }

    /** Which village has a road in this chunk (first match). */
    public String getVillageForChunk(ChunkKey key) {
        for (Map.Entry<String, Set<ChunkKey>> e : villageRoadChunks.entrySet()) {
            if (e.getValue().contains(key)) return e.getKey();
        }
        return null;
    }

    /**
     * Find path from start to end with elevation penalty, return list of chunk keys along path.
     * Uses A* with cost = horizontal + ELEVATION_PENALTY * |dy|.
     */
    public List<ChunkKey> findPath(Location start, Location end) {
        if (start == null || end == null || start.getWorld() == null || !start.getWorld().equals(end.getWorld()))
            return Collections.emptyList();
        World world = start.getWorld();
        int sx = start.getBlockX(), sy = start.getBlockY(), sz = start.getBlockZ();
        int ex = end.getBlockX(), ey = end.getBlockY(), ez = end.getBlockZ();

        // A* on 3D grid; we only expand to walkable neighbors (same y or ±1)
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<String, Node> closed = new HashMap<>();
        open.add(new Node(sx, sy, sz, 0, heuristic(sx, sy, sz, ex, ey, ez), null));

        while (!open.isEmpty()) {
            Node cur = open.poll();
            String ck = key(cur.x, cur.y, cur.z);
            if (closed.containsKey(ck)) continue;
            closed.put(ck, cur);
            if (cur.g > MAX_PATH_LENGTH) break;
            if (cur.x == ex && cur.z == ez && Math.abs(cur.y - ey) <= 2) {
                List<ChunkKey> path = new ArrayList<>();
                Node n = cur;
                String worldName = world.getName();
                while (n != null) {
                    path.add(new ChunkKey(worldName, n.x >> 4, n.z >> 4));
                    n = n.parent;
                }
                return path;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = cur.x + dx, ny = cur.y + dy, nz = cur.z + dz;
                        Block b = world.getBlockAt(nx, ny, nz);
                        Block above = world.getBlockAt(nx, ny + 1, nz);
                        if (!b.getType().isSolid() || above.getType().isSolid()) continue; // walkable: solid under, air above
                        double stepCost = (dx != 0 && dz != 0 ? 1.414 : 1.0) * HORIZONTAL_COST + ELEVATION_PENALTY * Math.abs(dy);
                        double ng = cur.g + stepCost;
                        String nk = key(nx, ny, nz);
                        if (closed.containsKey(nk)) continue;
                        Node next = new Node(nx, ny, nz, ng, heuristic(nx, ny, nz, ex, ey, ez), cur);
                        next.f = next.g + next.h;
                        open.add(next);
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Find path and return block positions (ground blocks along the path) for placing road blocks.
     */
    public List<Location> findPathBlockPositions(Location start, Location end) {
        if (start == null || end == null || start.getWorld() == null || !start.getWorld().equals(end.getWorld()))
            return Collections.emptyList();
        World world = start.getWorld();
        int sx = start.getBlockX(), sy = start.getBlockY(), sz = start.getBlockZ();
        int ex = end.getBlockX(), ey = end.getBlockY(), ez = end.getBlockZ();

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<String, Node> closed = new HashMap<>();
        open.add(new Node(sx, sy, sz, 0, heuristic(sx, sy, sz, ex, ey, ez), null));

        while (!open.isEmpty()) {
            Node cur = open.poll();
            String ck = key(cur.x, cur.y, cur.z);
            if (closed.containsKey(ck)) continue;
            closed.put(ck, cur);
            if (cur.g > MAX_PATH_LENGTH) break;
            if (cur.x == ex && cur.z == ez && Math.abs(cur.y - ey) <= 2) {
                List<Location> path = new ArrayList<>();
                Node n = cur;
                while (n != null) {
                    path.add(new Location(world, n.x, n.y, n.z));
                    n = n.parent;
                }
                return path;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = cur.x + dx, ny = cur.y + dy, nz = cur.z + dz;
                        Block b = world.getBlockAt(nx, ny, nz);
                        Block above = world.getBlockAt(nx, ny + 1, nz);
                        if (!b.getType().isSolid() || above.getType().isSolid()) continue;
                        double stepCost = (dx != 0 && dz != 0 ? 1.414 : 1.0) * HORIZONTAL_COST + ELEVATION_PENALTY * Math.abs(dy);
                        double ng = cur.g + stepCost;
                        String nk = key(nx, ny, nz);
                        if (closed.containsKey(nk)) continue;
                        Node next = new Node(nx, ny, nz, ng, heuristic(nx, ny, nz, ex, ey, ez), cur);
                        next.f = next.g + next.h;
                        open.add(next);
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Place preferred road blocks (dirt path, cobblestone, deepslate, etc.) along the path.
     * Gives the path drawing a visible bonus and marks the road.
     */
    public void placeRoadBlocks(List<Location> path) {
        if (path == null) return;
        Random r = new Random();
        for (Location loc : path) {
            if (loc == null || loc.getWorld() == null) continue;
            Block block = loc.getBlock();
            Material current = block.getType();
            Material choice = preferredRoadMaterial(current, r);
            if (choice != null && choice != current)
                block.setType(choice);
        }
    }

    private static Material preferredRoadMaterial(Material current, Random r) {
        for (Material m : ROAD_PREFERRED_MATERIALS) {
            if (m == current) return current; // already a road block – bonus, leave it
        }
        if (current == Material.GRASS_BLOCK || current == Material.DIRT || current == Material.PODZOL)
            return Material.DIRT_PATH;
        if (current == Material.STONE || current.name().contains("DEEPSLATE"))
            return ROAD_PREFERRED_MATERIALS[3 + r.nextInt(4)]; // deepslate/cobble variants
        return ROAD_PREFERRED_MATERIALS[r.nextInt(ROAD_PREFERRED_MATERIALS.length)];
    }

    private static double heuristic(int x, int y, int z, int ex, int ey, int ez) {
        double h = Math.sqrt((x - ex) * (x - ex) + (z - ez) * (z - ez)) + ELEVATION_PENALTY * Math.abs(y - ey);
        return h * HORIZONTAL_COST;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static class Node {
        final int x, y, z;
        final double g, h;
        double f;
        final Node parent;
        Node(int x, int y, int z, double g, double h, Node parent) {
            this.x = x; this.y = y; this.z = z;
            this.g = g; this.h = h; this.parent = parent;
            this.f = g + h;
        }
    }

    /**
     * Build road from start to end; add path chunks to village. Cost = path cost. Returns true if built (influence spent).
     */
    public boolean buildRoad(String villageName, Location start, Location end, double availableInfluence) {
        List<ChunkKey> path = findPath(start, end);
        if (path.isEmpty()) return false;
        double cost = path.size() * (HORIZONTAL_COST + 0.5); // approximate cost
        if (cost > availableInfluence) return false;
        villageRoadChunks.computeIfAbsent(villageName, k -> ConcurrentHashMap.newKeySet()).addAll(path);
        saveData();
        return true;
    }

    public void addRoadChunks(String villageName, Collection<ChunkKey> chunks) {
        if (villageName == null || chunks == null) return;
        villageRoadChunks.computeIfAbsent(villageName, k -> ConcurrentHashMap.newKeySet()).addAll(chunks);
        saveData();
    }

    /** Add road chunks from block positions (e.g. after placing road blocks). */
    public void addRoadChunks(String villageName, List<Location> path) {
        if (villageName == null || path == null) return;
        Set<ChunkKey> chunks = new HashSet<>();
        for (Location loc : path) {
            if (loc != null && loc.getWorld() != null)
                chunks.add(new ChunkKey(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4));
        }
        addRoadChunks(villageName, chunks);
    }

    public Set<ChunkKey> getRoadChunks(String villageName) {
        return Collections.unmodifiableSet(villageRoadChunks.getOrDefault(villageName, Collections.emptySet()));
    }

    public void removeVillage(String villageName) {
        villageRoadChunks.remove(villageName);
        saveData();
    }

    /**
     * Merge two villages' road chunks under newName; remove old names.
     */
    public void mergeVillages(String nameA, String nameB, String newName) {
        if (nameA == null || nameB == null || newName == null) return;
        Set<ChunkKey> setA = villageRoadChunks.remove(nameA);
        Set<ChunkKey> setB = villageRoadChunks.remove(nameB);
        Set<ChunkKey> merged = ConcurrentHashMap.newKeySet();
        if (setA != null) merged.addAll(setA);
        if (setB != null) merged.addAll(setB);
        if (!merged.isEmpty()) {
            villageRoadChunks.put(newName, merged);
            saveData();
        }
    }

    public void renameVillage(String oldName, String newName) {
        Set<ChunkKey> set = villageRoadChunks.remove(oldName);
        if (set != null) {
            villageRoadChunks.put(newName, set);
            saveData();
        }
    }

    private void loadData() {
        List<Map<String, Object>> list = nbtManager.loadList("roads");
        for (Map<String, Object> entry : list) {
            String villageName = (String) entry.get("villageName");
            String world = (String) entry.get("world");
            Object xObj = entry.get("chunkX");
            Object zObj = entry.get("chunkZ");
            if (villageName == null || world == null || !(xObj instanceof Number) || !(zObj instanceof Number)) continue;
            ChunkKey key = new ChunkKey(world, ((Number) xObj).intValue(), ((Number) zObj).intValue());
            villageRoadChunks.computeIfAbsent(villageName, k -> ConcurrentHashMap.newKeySet()).add(key);
        }
    }

    private void saveData() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Set<ChunkKey>> e1 : villageRoadChunks.entrySet()) {
            for (ChunkKey k : e1.getValue()) {
                Map<String, Object> m = new HashMap<>();
                m.put("villageName", e1.getKey());
                m.put("world", k.world);
                m.put("chunkX", k.x);
                m.put("chunkZ", k.z);
                list.add(m);
            }
        }
        nbtManager.saveList("roads", list);
    }
}
