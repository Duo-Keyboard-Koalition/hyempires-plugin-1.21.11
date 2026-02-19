package org.duoKeyboardKoalition.hyempires.managers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.utils.NBTFileManager;

import java.io.*;
import java.util.*;

/**
 * Manages villages and their administration blocks.
 * A Village Admin Block allows players to administrate a village.
 */
public class VillageManager {
    private final JavaPlugin plugin;
    private final NBTFileManager nbtManager;
    private final Set<VillageData> villages = new HashSet<>();
    private InfluenceManager influenceManager;
    private ChunkTerritoryManager chunkTerritoryManager;
    // NBT file structure:
    // villages.nbt:
    //   villages:
    //     - name: Village-X-Z
    //       world: world
    //       adminX: 100
    //       adminY: 64
    //       adminZ: 200
    //       owner: uuid-string
    //       createdDate: timestamp
    //       population: 5
    //       active: true
    //       effectiveRadius: 48
    //       additionalBells:
    //         - world: world
    //           x: 120
    //           y: 64
    //           z: 210

    public class VillageData {
        public String name;
        public String world;
        public int adminX, adminY, adminZ; // Primary bell location
        public List<Location> additionalBells = new ArrayList<>(); // Additional bells that expanded the village
        public int effectiveRadius = 48; // Can expand beyond base 48 blocks
        public String owner; // Founder username
        public long createdDate;
        public int population;
        public boolean active;

        public Location getAdminLocation() {
            World w = Bukkit.getWorld(world);
            return w != null ? new Location(w, adminX, adminY, adminZ) : null;
        }

        public String toCsvString() {
            // Serialize additional bells as "x1,y1,z1;x2,y2,z2;..."
            StringBuilder bellsStr = new StringBuilder();
            for (int i = 0; i < additionalBells.size(); i++) {
                Location bell = additionalBells.get(i);
                if (i > 0) bellsStr.append(";");
                bellsStr.append(bell.getBlockX()).append(",").append(bell.getBlockY()).append(",").append(bell.getBlockZ());
            }
            
            return String.format("%s,%s,%d,%d,%d,%s,%d,%d,%s,%d,%s",
                    name,
                    world,
                    adminX,
                    adminY,
                    adminZ,
                    owner != null ? owner : "none",
                    createdDate,
                    population,
                    active,
                    effectiveRadius,
                    bellsStr.toString().isEmpty() ? "none" : bellsStr.toString()
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
                // Try to parse as UUID first (for backward compatibility), then as username
                if (!parts[5].equals("none")) {
                    try {
                        // Try UUID first (old format)
                        UUID uuid = UUID.fromString(parts[5]);
                        // Convert UUID to username if possible
                        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                        owner = offlinePlayer.getName() != null ? offlinePlayer.getName() : parts[5];
                    } catch (IllegalArgumentException e) {
                        // Not a UUID, treat as username
                        owner = parts[5];
                    }
                } else {
                    owner = null;
                }
                createdDate = Long.parseLong(parts[6]);
                population = Integer.parseInt(parts[7]);
                active = Boolean.parseBoolean(parts[8]);
                
                // Parse effective radius (if present)
                if (parts.length >= 10) {
                    try {
                        effectiveRadius = Integer.parseInt(parts[9]);
                    } catch (NumberFormatException e) {
                        effectiveRadius = 48; // Default
                    }
                } else {
                    effectiveRadius = 48; // Default for old data
                }
                
                // Parse additional bells (if present)
                if (parts.length >= 11 && !parts[10].equals("none") && !parts[10].isEmpty()) {
                    String bellsData = parts[10];
                    String[] bellStrings = bellsData.split(";");
                    for (String bellStr : bellStrings) {
                        String[] coords = bellStr.split(",");
                        if (coords.length >= 3) {
                            try {
                                World w = Bukkit.getWorld(world);
                                if (w != null) {
                                    Location bellLoc = new Location(w, 
                                            Integer.parseInt(coords[0]),
                                            Integer.parseInt(coords[1]),
                                            Integer.parseInt(coords[2]));
                                    additionalBells.add(bellLoc);
                                }
                            } catch (NumberFormatException e) {
                                // Skip invalid bell location
                            }
                        }
                    }
                }
            }
        }
    }

    public VillageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.nbtManager = new NBTFileManager(plugin, "villages.nbt");
        loadExistingData();
    }
    
    /**
     * Set the influence manager (called after InfluenceManager is created).
     */
    public void setInfluenceManager(InfluenceManager influenceManager) {
        this.influenceManager = influenceManager;
    }
    
    /**
     * Set the chunk territory manager (called after ChunkTerritoryManager is created).
     */
    public void setChunkTerritoryManager(ChunkTerritoryManager chunkTerritoryManager) {
        this.chunkTerritoryManager = chunkTerritoryManager;
    }

    private void loadExistingData() {
        // Try loading from NBT first
        List<Map<String, Object>> nbtData = nbtManager.loadList("villages");
        
        if (!nbtData.isEmpty()) {
            // Load from NBT
            for (Map<String, Object> nbtVillage : nbtData) {
                VillageData data = fromNBT(nbtVillage);
                if (data != null) {
                    villages.add(data);
                }
            }
            plugin.getLogger().info("Loaded " + villages.size() + " villages from NBT");
            return;
        }
        
        // Fallback: Try loading from old CSV format (migration)
        File oldCsvFile = new File(plugin.getDataFolder(), "villages.csv");
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
                        VillageData data = new VillageData();
                        data.fromCsvLine(line);
                        villages.add(data);
                    }
                }
                plugin.getLogger().info("Migrated " + villages.size() + " villages from CSV to NBT");
                saveData(); // Save to NBT format
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to load villages from CSV: " + e.getMessage());
            }
        }
    }
    
    /**
     * Convert VillageData to NBT map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toNBT(VillageData data) {
        Map<String, Object> nbt = new HashMap<>();
        nbt.put("name", data.name);
        nbt.put("world", data.world);
        nbt.put("adminX", data.adminX);
        nbt.put("adminY", data.adminY);
        nbt.put("adminZ", data.adminZ);
        nbt.put("owner", data.owner);
        nbt.put("createdDate", data.createdDate);
        nbt.put("population", data.population);
        nbt.put("active", data.active);
        nbt.put("effectiveRadius", data.effectiveRadius);
        
        // Convert additional bells to NBT list
        List<Map<String, Object>> bellsList = new ArrayList<>();
        for (Location bell : data.additionalBells) {
            bellsList.add(NBTFileManager.locationToNBT(bell));
        }
        nbt.put("additionalBells", bellsList);
        
        return nbt;
    }
    
    /**
     * Convert NBT map to VillageData.
     */
    @SuppressWarnings("unchecked")
    private VillageData fromNBT(Map<String, Object> nbt) {
        VillageData data = new VillageData();
        data.name = (String) nbt.get("name");
        data.world = (String) nbt.get("world");
        data.adminX = ((Number) nbt.getOrDefault("adminX", 0)).intValue();
        data.adminY = ((Number) nbt.getOrDefault("adminY", 64)).intValue();
        data.adminZ = ((Number) nbt.getOrDefault("adminZ", 0)).intValue();
        
        Object ownerObj = nbt.get("owner");
        if (ownerObj != null) {
            String ownerStr = ownerObj.toString();
            // Try to parse as UUID first (for backward compatibility)
            try {
                UUID uuid = UUID.fromString(ownerStr);
                // Convert UUID to username if possible
                org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                data.owner = offlinePlayer.getName() != null ? offlinePlayer.getName() : ownerStr;
            } catch (IllegalArgumentException e) {
                // Not a UUID, treat as username
                data.owner = ownerStr;
            }
        }
        
        data.createdDate = ((Number) nbt.getOrDefault("createdDate", System.currentTimeMillis())).longValue();
        data.population = ((Number) nbt.getOrDefault("population", 0)).intValue();
        data.active = (Boolean) nbt.getOrDefault("active", true);
        data.effectiveRadius = ((Number) nbt.getOrDefault("effectiveRadius", 48)).intValue();
        
        // Load additional bells from NBT list
        Object bellsObj = nbt.get("additionalBells");
        if (bellsObj instanceof List) {
            List<Map<String, Object>> bellsList = (List<Map<String, Object>>) bellsObj;
            for (Map<String, Object> bellNBT : bellsList) {
                Location bell = NBTFileManager.nbtToLocation(bellNBT);
                if (bell != null) {
                    data.additionalBells.add(bell);
                }
            }
        }
        
        return data;
    }

    /**
     * Creates a new village with an administration block, or expands existing village if within territory.
     */
    public VillageData createVillage(Location adminBlockLocation, Player owner, String name) {
        // Check if a village already exists at this exact location
        if (getVillageAt(adminBlockLocation) != null) {
            return null; // Bell already exists here
        }

        // Check if this location is within an existing village's territory
        VillageData existingVillage = getVillageContaining(adminBlockLocation);
        if (existingVillage != null) {
            // Expand the existing village instead of creating a new one
            return expandVillage(existingVillage, adminBlockLocation, owner);
        }

        // Create new village
        VillageData data = new VillageData();
        data.name = name != null ? name : "Village-" + adminBlockLocation.getBlockX() + "-" + adminBlockLocation.getBlockZ();
        data.world = adminBlockLocation.getWorld().getName();
        data.adminX = adminBlockLocation.getBlockX();
        data.adminY = adminBlockLocation.getBlockY();
        data.adminZ = adminBlockLocation.getBlockZ();
        data.owner = owner.getName();
        data.createdDate = System.currentTimeMillis();
        data.population = countVillagersInRadius(adminBlockLocation, 48);
        data.active = true;

        villages.add(data);
        saveData();
        
        // Initialize founder influence
        if (influenceManager != null) {
            influenceManager.initializeFounder(data.name, owner.getName(), owner.getUniqueId());
        }
        
        // Initialize chunk territory (claim the chunk containing the bell)
        if (chunkTerritoryManager != null) {
            chunkTerritoryManager.initializeVillage(data.name, adminBlockLocation);
        }

        notifyNearbyPlayers(adminBlockLocation, "§aNew village established: " + data.name);

        return data;
    }
    
    /**
     * Expands an existing village by adding a new bell location.
     * Checks if village has enough power to claim the chunk.
     */
    private VillageData expandVillage(VillageData village, Location newBellLocation, Player player) {
        Chunk newChunk = newBellLocation.getChunk();
        
        // Check if village can claim this chunk (power requirement)
        if (chunkTerritoryManager != null) {
            // Calculate village power
            double totalInfluence = 0.0;
            if (influenceManager != null) {
                List<Map.Entry<UUID, InfluenceManager.InfluenceData>> ranking = 
                        influenceManager.getInfluenceRanking(village.name);
                totalInfluence = ranking.stream()
                        .mapToDouble(e -> e.getValue().influence)
                        .sum();
            }
            
            int villagePower = chunkTerritoryManager.calculateVillagePower(village.population, totalInfluence);
            
            // Check if chunk is already claimed by this village
            String chunkOwner = chunkTerritoryManager.getVillageForChunk(newChunk);
            if (chunkOwner != null && !chunkOwner.equals(village.name)) {
                player.sendMessage("§cThis chunk is already claimed by another village!");
                return null;
            }
            
            // Try to claim the chunk
            if (!chunkTerritoryManager.claimChunk(village.name, newChunk, villagePower)) {
                int currentChunks = chunkTerritoryManager.getClaimedChunkCount(village.name);
                int maxChunks = chunkTerritoryManager.getMaxChunks(villagePower);
                player.sendMessage("§cVillage doesn't have enough power to claim this chunk!");
                player.sendMessage("§eCurrent: " + currentChunks + " chunks, Power: " + villagePower + ", Max: " + maxChunks + " chunks");
                player.sendMessage("§7Increase population or influence to gain more power!");
                return null;
            }
        }
        
        // Add the new bell location
        village.additionalBells.add(newBellLocation.clone());
        
        // Calculate new effective radius (distance from primary bell to new bell + base radius)
        Location primaryLoc = village.getAdminLocation();
        if (primaryLoc != null) {
            double distanceToNewBell = primaryLoc.distance(newBellLocation);
            // Expand radius to include the new bell with some buffer
            village.effectiveRadius = Math.max(village.effectiveRadius, (int)(distanceToNewBell + 48));
        }
        
        // Update population to include area around new bell
        int newBellPopulation = countVillagersInRadius(newBellLocation, 48);
        village.population = Math.max(village.population, newBellPopulation);
        
        // Grant influence to player for expanding village
        if (influenceManager != null) {
            influenceManager.addInfluence(village.name, player.getUniqueId(), 10.0, "Village Expansion");
            influenceManager.updateActivity(village.name, player.getUniqueId());
        }
        
        saveData();
        
        notifyNearbyPlayers(newBellLocation, "§6Village '" + village.name + "' has been expanded!");
        player.sendMessage("§6You've expanded " + village.name + " by placing a new bell!");
        player.sendMessage("§eNew effective radius: " + village.effectiveRadius + " blocks");
        if (chunkTerritoryManager != null) {
            int chunks = chunkTerritoryManager.getClaimedChunkCount(village.name);
            player.sendMessage("§eClaimed chunks: " + chunks);
        }
        
        return village;
    }

    /**
     * Removes a village at the specified location.
     * Only for admin/OP use - villages cannot be abandoned by players.
     */
    public void removeVillage(Location location, Player admin) {
        if (admin == null || !admin.isOp()) {
            return; // Only OPs can remove villages
        }
        
        VillageData data = getVillageAt(location);
        if (data != null) {
            villages.remove(data);
            data.active = false;
            saveData();
            
            // Remove influence data
            if (influenceManager != null) {
                influenceManager.removeVillage(data.name);
            }
            
            // Remove chunk territory
            if (chunkTerritoryManager != null) {
                chunkTerritoryManager.removeVillage(data.name);
            }
            
            notifyNearbyPlayers(location, "§cVillage " + data.name + " has been removed by admin");
        }
    }

    /**
     * Gets the village at a specific admin block location.
     * Checks both primary bell and additional bells.
     */
    public VillageData getVillageAt(Location location) {
        for (VillageData data : villages) {
            if (!data.active) continue;
            if (!data.world.equals(location.getWorld().getName())) continue;
            
            // Check primary bell
            if (data.adminX == location.getBlockX() &&
                    data.adminY == location.getBlockY() &&
                    data.adminZ == location.getBlockZ()) {
                return data;
            }
            
            // Check additional bells
            for (Location bell : data.additionalBells) {
                if (bell.getBlockX() == location.getBlockX() &&
                        bell.getBlockY() == location.getBlockY() &&
                        bell.getBlockZ() == location.getBlockZ()) {
                    return data;
                }
            }
        }
        return null;
    }

    /**
     * Gets the village that contains a specific location (within village radius).
     * Checks both primary bell and additional bells, using effective radius.
     */
    public VillageData getVillageContaining(Location location) {
        for (VillageData data : villages) {
            if (!data.active) continue;
            if (!data.world.equals(location.getWorld().getName())) continue;

            Location adminLoc = data.getAdminLocation();
            if (adminLoc == null) continue;

            // Check distance to primary bell
            double distance = location.distance(adminLoc);
            if (distance <= data.effectiveRadius) {
                return data;
            }
            
            // Check distance to additional bells
            for (Location additionalBell : data.additionalBells) {
                double distToAdditional = location.distance(additionalBell);
                if (distToAdditional <= 48) { // Each additional bell has 48 block radius
                    return data;
                }
            }
        }
        return null;
    }

    /**
     * Gets all villages owned by a player (by username).
     */
    public List<VillageData> getVillagesByOwner(String ownerUsername) {
        List<VillageData> result = new ArrayList<>();
        for (VillageData data : villages) {
            if (data.active && data.owner != null && data.owner.equals(ownerUsername)) {
                result.add(data);
            }
        }
        return result;
    }
    
    /**
     * Gets all villages owned by a player (by UUID, for backward compatibility).
     */
    public List<VillageData> getVillagesByOwnerUUID(UUID ownerUUID) {
        List<VillageData> result = new ArrayList<>();
        if (ownerUUID == null) return result;
        
        // Get username from UUID
        org.bukkit.entity.Player player = plugin.getServer().getPlayer(ownerUUID);
        String username = player != null ? player.getName() : null;
        if (username == null) {
            org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(ownerUUID);
            username = offlinePlayer != null && offlinePlayer.hasPlayedBefore() ? offlinePlayer.getName() : null;
        }
        
        if (username != null) {
            return getVillagesByOwner(username);
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
     * Counts villagers around all bells (primary + additional).
     */
    public void updatePopulation(VillageData data) {
        Set<org.bukkit.entity.Villager> countedVillagers = new HashSet<>();
        
        // Count around primary bell
        Location primaryLoc = data.getAdminLocation();
        if (primaryLoc != null && primaryLoc.getWorld() != null) {
            primaryLoc.getWorld().getNearbyEntities(primaryLoc, data.effectiveRadius, data.effectiveRadius, data.effectiveRadius)
                    .stream()
                    .filter(e -> e instanceof org.bukkit.entity.Villager)
                    .map(e -> (org.bukkit.entity.Villager) e)
                    .forEach(countedVillagers::add);
        }
        
        // Count around additional bells (avoid double counting)
        for (Location bell : data.additionalBells) {
            if (bell.getWorld() != null) {
                bell.getWorld().getNearbyEntities(bell, 48, 48, 48)
                        .stream()
                        .filter(e -> e instanceof org.bukkit.entity.Villager)
                        .map(e -> (org.bukkit.entity.Villager) e)
                        .forEach(countedVillagers::add);
            }
        }
        
        data.population = countedVillagers.size();
        saveData();
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
    public String getVillageInfo(VillageData data, Player viewer) {
        StringBuilder info = new StringBuilder();
        info.append("§6=== Village Info ===\n");
        info.append("§eName: §f").append(data.name).append("\n");
        
        // Show current leader (from influence system)
        if (influenceManager != null) {
            UUID leader = influenceManager.getCurrentLeader(data.name);
            if (leader != null) {
                info.append("§eCurrent Leader: §f").append(leader.toString()).append("\n");
                
                // Show viewer's influence if they have any
                if (viewer != null) {
                    double viewerInfluence = influenceManager.getInfluence(data.name, viewer.getUniqueId());
                    if (viewerInfluence > 0) {
                        info.append("§eYour Influence: §f").append(String.format("%.1f", viewerInfluence)).append("\n");
                    }
                }
            }
        }
        
        // Founder field
        if (data.owner != null) {
            info.append("§eFounder: §f").append(data.owner).append("\n");
        }
        
        info.append("§ePopulation: §f").append(data.population).append(" villagers\n");
        info.append("§ePrimary Bell: §f").append(data.adminX).append(", ").append(data.adminY).append(", ").append(data.adminZ).append("\n");
        info.append("§eEffective Radius: §f").append(data.effectiveRadius).append(" blocks\n");
        if (!data.additionalBells.isEmpty()) {
            info.append("§eAdditional Bells: §f").append(data.additionalBells.size()).append("\n");
        }
        
        // Show power and chunk info
        if (chunkTerritoryManager != null) {
            double totalInfluence = 0.0;
            if (influenceManager != null) {
                List<Map.Entry<UUID, InfluenceManager.InfluenceData>> ranking = influenceManager.getInfluenceRanking(data.name);
                totalInfluence = ranking.stream().mapToDouble(e -> e.getValue().influence).sum();
            }
            int villagePower = chunkTerritoryManager.calculateVillagePower(data.population, totalInfluence);
            int claimedChunks = chunkTerritoryManager.getClaimedChunkCount(data.name);
            int maxChunks = chunkTerritoryManager.getMaxChunks(villagePower);
            
            info.append("§eVillage Power: §f").append(villagePower).append("\n");
            info.append("§eClaimed Chunks: §f").append(claimedChunks).append("/").append(maxChunks).append("\n");
        }
        
        info.append("§eStatus: §f").append(data.active ? "§aActive" : "§cInactive");
        
        // Show influence ranking
        if (influenceManager != null) {
            List<Map.Entry<UUID, InfluenceManager.InfluenceData>> ranking = influenceManager.getInfluenceRanking(data.name);
            if (!ranking.isEmpty()) {
                info.append("\n§7=== Top Influencers ===");
                int count = 0;
                for (Map.Entry<UUID, InfluenceManager.InfluenceData> entry : ranking) {
                    if (count >= 5) break; // Top 5
                    String founderTag = entry.getValue().isFounder ? " §6[Founder]" : "";
                    // Get username from UUID
                    String displayName = getUsernameFromUUID(entry.getKey());
                    info.append("\n§7").append(count + 1).append(". §f").append(displayName)
                            .append(" §7- ").append(String.format("%.1f", entry.getValue().influence)).append(" influence").append(founderTag);
                    count++;
                }
            }
        }
        
        return info.toString();
    }
    
    /**
     * Overload for backward compatibility.
     */
    public String getVillageInfo(VillageData data) {
        return getVillageInfo(data, null);
    }

    private void saveData() {
        List<Map<String, Object>> nbtVillages = new ArrayList<>();
        for (VillageData data : villages) {
            nbtVillages.add(toNBT(data));
        }
        nbtManager.saveList("villages", nbtVillages);
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
     * Checks if a player can administer a village based on influence system.
     */
    public boolean canAdminister(Player player, VillageData village) {
        if (player.isOp()) return true;
        if (influenceManager == null) {
            // Fallback to old system if influence manager not initialized
            return village.owner == null || village.owner.equals(player.getName());
        }
        return influenceManager.canAdminister(player, village.name);
    }
    
    /**
     * Get username from UUID, with fallback to UUID string.
     */
    private String getUsernameFromUUID(UUID uuid) {
        if (uuid == null) return "Unknown";
        // Try to find online player first
        org.bukkit.entity.Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }
        // Try offline player
        org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
        if (offlinePlayer != null && offlinePlayer.hasPlayedBefore() && offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }
        // Fallback to UUID string (first 8 chars)
        return uuid.toString().substring(0, 8);
    }
}
