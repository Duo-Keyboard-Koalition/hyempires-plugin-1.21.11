package org.duoKeyboardKoalition.hyempires.managers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.utils.CSVWriter;

import java.io.*;
import java.util.*;

/**
 * Manages villages and their administration blocks.
 * A Village Admin Block allows players to administrate a village.
 */
public class VillageManager {
    private final JavaPlugin plugin;
    private final CSVWriter csvWriter;
    private final Set<VillageData> villages = new HashSet<>();
    private static final String[] HEADERS = {
            "VillageName", "World", "AdminX", "AdminY", "AdminZ",
            "Owner", "CreatedDate", "Population", "Active"
    };

    public class VillageData {
        public String name;
        public String world;
        public int adminX, adminY, adminZ;
        public UUID owner;
        public long createdDate;
        public int population;
        public boolean active;

        public Location getAdminLocation() {
            World w = Bukkit.getWorld(world);
            return w != null ? new Location(w, adminX, adminY, adminZ) : null;
        }

        public String toCsvString() {
            return String.format("%s,%s,%d,%d,%d,%s,%d,%d,%s",
                    name,
                    world,
                    adminX,
                    adminY,
                    adminZ,
                    owner != null ? owner.toString() : "none",
                    createdDate,
                    population,
                    active
            );
        }

        public void fromCsvLine(String line) {
            String[] parts = line.split(",");
            if (parts.length >= 9) {
                name = parts[0];
                world = parts[1];
                adminX = Integer.parseInt(parts[2]);
                adminY = Integer.parseInt(parts[3]);
                adminZ = Integer.parseInt(parts[4]);
                owner = !parts[5].equals("none") ? UUID.fromString(parts[5]) : null;
                createdDate = Long.parseLong(parts[6]);
                population = Integer.parseInt(parts[7]);
                active = Boolean.parseBoolean(parts[8]);
            }
        }
    }

    public VillageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.csvWriter = new CSVWriter(plugin, "villages.csv", HEADERS);
        loadExistingData();
    }

    private void loadExistingData() {
        File dataFile = new File(plugin.getDataFolder(), "villages.csv");
        if (!dataFile.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header
                }
                if (!line.trim().isEmpty()) {
                    VillageData data = new VillageData();
                    data.fromCsvLine(line);
                    villages.add(data);
                }
            }
            plugin.getLogger().info("Loaded " + villages.size() + " villages from disk");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load villages: " + e.getMessage());
        }
    }

    /**
     * Creates a new village with an administration block.
     */
    public VillageData createVillage(Location adminBlockLocation, Player owner, String name) {
        // Check if a village already exists at this location
        if (getVillageAt(adminBlockLocation) != null) {
            return null;
        }

        VillageData data = new VillageData();
        data.name = name != null ? name : "Village-" + adminBlockLocation.getBlockX() + "-" + adminBlockLocation.getBlockZ();
        data.world = adminBlockLocation.getWorld().getName();
        data.adminX = adminBlockLocation.getBlockX();
        data.adminY = adminBlockLocation.getBlockY();
        data.adminZ = adminBlockLocation.getBlockZ();
        data.owner = owner.getUniqueId();
        data.createdDate = System.currentTimeMillis();
        data.population = countVillagersInRadius(adminBlockLocation, 48);
        data.active = true;

        villages.add(data);
        saveData();

        notifyNearbyPlayers(adminBlockLocation, "§aNew village established: " + data.name);

        return data;
    }

    /**
     * Removes a village at the specified location.
     */
    public void removeVillage(Location location) {
        VillageData data = getVillageAt(location);
        if (data != null) {
            villages.remove(data);
            data.active = false;
            saveData();
            notifyNearbyPlayers(location, "§cVillage " + data.name + " has been abandoned");
        }
    }

    /**
     * Gets the village at a specific admin block location.
     */
    public VillageData getVillageAt(Location location) {
        for (VillageData data : villages) {
            if (data.active &&
                    data.world.equals(location.getWorld().getName()) &&
                    data.adminX == location.getBlockX() &&
                    data.adminY == location.getBlockY() &&
                    data.adminZ == location.getBlockZ()) {
                return data;
            }
        }
        return null;
    }

    /**
     * Gets the village that contains a specific location (within village radius).
     */
    public VillageData getVillageContaining(Location location) {
        for (VillageData data : villages) {
            if (!data.active) continue;
            if (!data.world.equals(location.getWorld().getName())) continue;

            Location adminLoc = data.getAdminLocation();
            if (adminLoc == null) continue;

            double distance = location.distance(adminLoc);
            if (distance <= 48) { // 48 block radius
                return data;
            }
        }
        return null;
    }

    /**
     * Gets all villages owned by a player.
     */
    public List<VillageData> getVillagesByOwner(UUID owner) {
        List<VillageData> result = new ArrayList<>();
        for (VillageData data : villages) {
            if (data.active && data.owner != null && data.owner.equals(owner)) {
                result.add(data);
            }
        }
        return result;
    }

    /**
     * Gets all active villages.
     */
    public Set<VillageData> getAllVillages() {
        return Collections.unmodifiableSet(villages);
    }

    /**
     * Updates the population count for a village.
     */
    public void updatePopulation(VillageData data) {
        Location loc = data.getAdminLocation();
        if (loc != null) {
            data.population = countVillagersInRadius(loc, 48);
            saveData();
        }
    }

    /**
     * Counts villagers within a radius of a location.
     */
    private int countVillagersInRadius(Location center, int radius) {
        if (center.getWorld() == null) return 0;

        return center.getWorld().getNearbyEntities(center, radius, radius, radius)
                .stream()
                .filter(entity -> entity instanceof org.bukkit.entity.Villager)
                .mapToInt(e -> 1)
                .sum();
    }

    /**
     * Gets village statistics for display.
     */
    public String getVillageInfo(VillageData data) {
        StringBuilder info = new StringBuilder();
        info.append("§6=== Village Info ===\n");
        info.append("§eName: §f").append(data.name).append("\n");
        info.append("§eOwner: §f").append(data.owner != null ? data.owner.toString() : "Unowned").append("\n");
        info.append("§ePopulation: §f").append(data.population).append(" villagers\n");
        info.append("§eLocation: §f").append(data.adminX).append(", ").append(data.adminY).append(", ").append(data.adminZ).append("\n");
        info.append("§eStatus: §f").append(data.active ? "§aActive" : "§cAbandoned");
        return info.toString();
    }

    private void saveData() {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", HEADERS));
        for (VillageData data : villages) {
            lines.add(data.toCsvString());
        }
        csvWriter.writeAll(lines);
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
     * Scans all loaded chunks for village admin blocks and registers them.
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
                    // Look for bell blocks as potential village centers
                    if (block.getType() == Material.BELL) {
                        Location loc = block.getLocation();
                        if (getVillageAt(loc) == null) {
                            // Auto-register existing village bells
                            VillageData data = new VillageData();
                            data.name = "Village-" + block.getX() + "-" + block.getZ();
                            data.world = world.getName();
                            data.adminX = block.getX();
                            data.adminY = block.getY();
                            data.adminZ = block.getZ();
                            data.owner = null;
                            data.createdDate = System.currentTimeMillis();
                            data.population = countVillagersInRadius(loc, 48);
                            data.active = true;
                            villages.add(data);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if a player owns or can administer a village.
     */
    public boolean canAdminister(Player player, VillageData village) {
        return village.owner == null || village.owner.equals(player.getUniqueId()) || player.isOp();
    }
}
