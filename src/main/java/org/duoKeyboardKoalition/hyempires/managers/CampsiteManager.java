package org.duoKeyboardKoalition.hyempires.managers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.utils.NBTFileManager;

import java.io.*;
import java.util.*;

/**
 * Manages campsites across the server.
 * A campsite is created when a player places a Campsite Block.
 */
public class CampsiteManager {
    private final JavaPlugin plugin;
    private final NBTFileManager nbtManager;
    private final Set<CampsiteData> campsites = new HashSet<>();
    private static final String[] HEADERS = {
            "CampsiteName", "World", "X", "Y", "Z", "Owner", "CreatedDate", "Active"
    };

    public class CampsiteData {
        public String name;
        public String world;
        public int x, y, z;
        public UUID owner;
        public long createdDate;
        public boolean active;

        public Location getLocation() {
            World w = Bukkit.getWorld(world);
            return w != null ? new Location(w, x, y, z) : null;
        }

        public String toCsvString() {
            return String.format("%s,%s,%d,%d,%d,%s,%d,%s",
                    name,
                    world,
                    x,
                    y,
                    z,
                    owner != null ? owner.toString() : "none",
                    createdDate,
                    active
            );
        }

        public void fromCsvLine(String line) {
            String[] parts = line.split(",");
            if (parts.length >= 8) {
                name = parts[0];
                world = parts[1];
                x = Integer.parseInt(parts[2]);
                y = Integer.parseInt(parts[3]);
                z = Integer.parseInt(parts[4]);
                owner = !parts[5].equals("none") ? UUID.fromString(parts[5]) : null;
                createdDate = Long.parseLong(parts[6]);
                active = Boolean.parseBoolean(parts[7]);
            }
        }
    }

    public CampsiteManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.nbtManager = new NBTFileManager(plugin, "campsites.nbt");
        loadExistingData();
    }

    private void loadExistingData() {
        // Try loading from NBT first
        List<Map<String, Object>> nbtData = nbtManager.loadList("campsites");
        
        if (!nbtData.isEmpty()) {
            // Load from NBT
            for (Map<String, Object> nbtCampsite : nbtData) {
                CampsiteData data = fromNBT(nbtCampsite);
                if (data != null) {
                    campsites.add(data);
                }
            }
            plugin.getLogger().info("Loaded " + campsites.size() + " campsites from NBT");
            return;
        }
        
        // Fallback: Try loading from old CSV format (migration)
        File oldCsvFile = new File(plugin.getDataFolder(), "campsites.csv");
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
                        CampsiteData data = new CampsiteData();
                        data.fromCsvLine(line);
                        campsites.add(data);
                    }
                }
                plugin.getLogger().info("Migrated " + campsites.size() + " campsites from CSV to NBT");
                saveData(); // Save to NBT format
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to load campsites from CSV: " + e.getMessage());
            }
        }
    }
    
    /**
     * Convert CampsiteData to NBT map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toNBT(CampsiteData data) {
        Map<String, Object> nbt = new HashMap<>();
        nbt.put("name", data.name);
        nbt.put("world", data.world);
        nbt.put("x", data.x);
        nbt.put("y", data.y);
        nbt.put("z", data.z);
        nbt.put("owner", data.owner != null ? data.owner.toString() : null);
        nbt.put("createdDate", data.createdDate);
        nbt.put("active", data.active);
        return nbt;
    }
    
    /**
     * Convert NBT map to CampsiteData.
     */
    @SuppressWarnings("unchecked")
    private CampsiteData fromNBT(Map<String, Object> nbt) {
        CampsiteData data = new CampsiteData();
        data.name = (String) nbt.get("name");
        data.world = (String) nbt.get("world");
        data.x = ((Number) nbt.getOrDefault("x", 0)).intValue();
        data.y = ((Number) nbt.getOrDefault("y", 64)).intValue();
        data.z = ((Number) nbt.getOrDefault("z", 0)).intValue();
        
        Object ownerObj = nbt.get("owner");
        if (ownerObj != null) {
            try {
                data.owner = UUID.fromString(ownerObj.toString());
            } catch (IllegalArgumentException e) {
                data.owner = null;
            }
        }
        
        data.createdDate = ((Number) nbt.getOrDefault("createdDate", System.currentTimeMillis())).longValue();
        data.active = (Boolean) nbt.getOrDefault("active", true);
        return data;
    }

    /**
     * Creates a new campsite at the specified location.
     */
    public CampsiteData createCampsite(Location location, Player owner, String name) {
        // Check if a campsite already exists at this location
        if (getCampsiteAt(location) != null) {
            return null;
        }

        CampsiteData data = new CampsiteData();
        data.name = name != null ? name : "Campsite-" + location.getBlockX() + "-" + location.getBlockZ();
        data.world = location.getWorld().getName();
        data.x = location.getBlockX();
        data.y = location.getBlockY();
        data.z = location.getBlockZ();
        data.owner = owner.getUniqueId();
        data.createdDate = System.currentTimeMillis();
        data.active = true;

        campsites.add(data);
        saveData();

        // Spawn campsite structures (tents, campfire, etc.)
        spawnCampsiteStructures(location);

        notifyNearbyPlayers(location, "§6New campsite established: " + data.name);

        return data;
    }

    /**
     * Removes a campsite at the specified location.
     */
    public void removeCampsite(Location location) {
        CampsiteData data = getCampsiteAt(location);
        if (data != null) {
            campsites.remove(data);
            data.active = false;
            saveData();
            notifyNearbyPlayers(location, "§cCampsite " + data.name + " has been abandoned");
        }
    }

    /**
     * Gets the campsite at a specific location.
     */
    public CampsiteData getCampsiteAt(Location location) {
        for (CampsiteData data : campsites) {
            if (data.active &&
                    data.world.equals(location.getWorld().getName()) &&
                    data.x == location.getBlockX() &&
                    data.y == location.getBlockY() &&
                    data.z == location.getBlockZ()) {
                return data;
            }
        }
        return null;
    }

    /**
     * Gets all campsites owned by a player.
     */
    public List<CampsiteData> getCampsitesByOwner(UUID owner) {
        List<CampsiteData> result = new ArrayList<>();
        for (CampsiteData data : campsites) {
            if (data.active && data.owner != null && data.owner.equals(owner)) {
                result.add(data);
            }
        }
        return result;
    }

    /**
     * Gets all active campsites.
     */
    public Set<CampsiteData> getAllCampsites() {
        return Collections.unmodifiableSet(campsites);
    }

    /**
     * Spawns campsite structures around the campsite block.
     * Creates a small camp with tent (wool), campfire, and chest.
     */
    private void spawnCampsiteStructures(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Clear area slightly above the block
        Location groundLevel = center.clone().add(0.5, 1, 0.5);

        // Place tent structure (simple A-frame using wool)
        // Tent floor
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block floor = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + 1, center.getBlockZ() + z);
                if (floor.getType() == Material.AIR) {
                    floor.setType(Material.GRAY_CARPET);
                }
            }
        }

        // Tent walls and roof (simple pyramid)
        for (int y = 0; y < 3; y++) {
            int size = 2 - y;
            for (int x = -size; x <= size; x++) {
                for (int z = -size; z <= size; z++) {
                    if (Math.abs(x) == size || Math.abs(z) == size) {
                        Block wall = world.getBlockAt(
                                center.getBlockX() + x,
                                center.getBlockY() + 2 + y,
                                center.getBlockZ() + z
                        );
                        if (wall.getType() == Material.AIR) {
                            wall.setType(Material.WHITE_WOOL);
                        }
                    }
                }
            }
        }

        // Place campfire in front of tent
        Block campfire = world.getBlockAt(center.getBlockX() + 3, center.getBlockY() + 1, center.getBlockZ());
        if (campfire.getType() == Material.AIR || campfire.getType() == Material.GRASS_BLOCK) {
            campfire.setType(Material.CAMPFIRE);
        }

        // Place chest for storage
        Block chest = world.getBlockAt(center.getBlockX() - 3, center.getBlockY() + 1, center.getBlockZ());
        if (chest.getType() == Material.AIR) {
            chest.setType(Material.CHEST);
        }

        // Place crafting table
        Block craftingTable = world.getBlockAt(center.getBlockX(), center.getBlockY() + 1, center.getBlockZ() + 3);
        if (craftingTable.getType() == Material.AIR) {
            craftingTable.setType(Material.CRAFTING_TABLE);
        }
    }

    private void saveData() {
        List<Map<String, Object>> nbtCampsites = new ArrayList<>();
        for (CampsiteData data : campsites) {
            nbtCampsites.add(toNBT(data));
        }
        nbtManager.saveList("campsites", nbtCampsites);
    }

    private void notifyNearbyPlayers(Location location, String message) {
        if (location.getWorld() == null) return;
        location.getWorld().getPlayers().forEach(player -> {
            if (player.getLocation().distance(location) <= 50) {
                player.sendMessage(message);
            }
        });
    }

    /**
     * Scans all loaded chunks for campsite blocks and registers them.
     */
    public void scanLoadedChunks() {
        plugin.getServer().getWorlds().forEach(world -> {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        });
    }

    private void scanChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int minY = Math.max(world.getMinHeight(), 0);
        int maxY = Math.min(world.getMaxHeight(), 320);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() == Material.CAMPFIRE) {
                        // Check if this campfire is part of a campsite
                        Location loc = block.getLocation();
                        if (getCampsiteAt(loc) == null) {
                            // This could be a natural campfire, not a campsite block
                            // Only register if it has tent structures nearby
                            if (hasTentStructureNearby(block)) {
                                // Auto-register existing campsites
                                CampsiteData data = new CampsiteData();
                                data.name = "Campsite-" + block.getX() + "-" + block.getZ();
                                data.world = world.getName();
                                data.x = block.getX();
                                data.y = block.getY();
                                data.z = block.getZ();
                                data.owner = null;
                                data.createdDate = System.currentTimeMillis();
                                data.active = true;
                                campsites.add(data);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean hasTentStructureNearby(Block campfire) {
        World world = campfire.getWorld();
        // Check for wool blocks that would indicate a tent
        for (int x = -3; x <= 3; x++) {
            for (int y = -1; y <= 4; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block b = world.getBlockAt(campfire.getX() + x, campfire.getY() + y, campfire.getZ() + z);
                    if (b.getType().name().contains("WOOL")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
