package org.duoKeyboardKoalition.hyempires.managers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
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
    //       population: 5  (villagers using this bell as gossip/MEETING_POINT)
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
        /** Villagers that use this village's bell(s) as their gossip/MEETING_POINT. */
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
                // 11 parts: createdDate, population, active, effectiveRadius, bells. Legacy 10 parts (no population): createdDate, active, effectiveRadius, bells.
                int popIdx = 7;
                int activeIdx = parts.length >= 11 ? 8 : 7;
                int radiusIdx = parts.length >= 11 ? 9 : 8;
                int bellsIdx = parts.length >= 11 ? 10 : 9;
                if (parts.length >= 11) {
                    try { population = Integer.parseInt(parts[popIdx]); } catch (NumberFormatException e) { population = 0; }
                } else {
                    population = 0;
                }
                active = Boolean.parseBoolean(parts[activeIdx]);
                if (parts.length > radiusIdx) {
                    try {
                        effectiveRadius = Integer.parseInt(parts[radiusIdx]);
                    } catch (NumberFormatException e) {
                        effectiveRadius = 48;
                    }
                } else {
                    effectiveRadius = 48;
                }
                if (parts.length > bellsIdx && !parts[bellsIdx].equals("none") && !parts[bellsIdx].isEmpty()) {
                    String bellsData = parts[bellsIdx];
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
            saveData();
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
        data.population = 0;
        data.active = true;

        villages.add(data);
        saveData();
        
        // Initialize founder influence
        if (influenceManager != null) {
            influenceManager.initializeFounder(data.name, owner.getName(), owner.getUniqueId());
        }

        notifyNearbyPlayers(adminBlockLocation, "§aNew village established: " + data.name);

        return data;
    }
    
    /**
     * Expands an existing village by adding a new bell location. Boundaries are natural (radius from bells only).
     */
    private VillageData expandVillage(VillageData village, Location newBellLocation, Player player) {
        village.additionalBells.add(newBellLocation.clone());

        Location primaryLoc = village.getAdminLocation();
        if (primaryLoc != null) {
            double distanceToNewBell = primaryLoc.distance(newBellLocation);
            village.effectiveRadius = Math.max(village.effectiveRadius, (int)(distanceToNewBell + 48));
        }

        if (influenceManager != null) {
            influenceManager.addInfluence(village.name, player.getUniqueId(), 10.0, "Village Expansion");
            influenceManager.updateActivity(village.name, player.getUniqueId());
        }

        saveData();

        notifyNearbyPlayers(newBellLocation, "§6Village '" + village.name + "' has been expanded!");
        player.sendMessage("§6You've expanded " + village.name + " by placing a new bell!");
        player.sendMessage("§eNew effective radius: " + village.effectiveRadius + " blocks");

        return village;
    }

    /**
     * Add a blank bell to an existing village (administrative paper on blank bell).
     * The bell must not already be registered as any village.
     */
    public VillageData addBellToVillage(VillageData village, Location newBellLocation, Player player) {
        if (village == null || newBellLocation == null || player == null) return null;
        if (getVillageAt(newBellLocation) != null) {
            player.sendMessage("§cThis bell is already part of a village!");
            return null;
        }
        if (!village.world.equals(newBellLocation.getWorld().getName())) {
            player.sendMessage("§cBell must be in the same world as the village!");
            return null;
        }
        return expandVillage(village, newBellLocation, player);
    }

    /**
     * Merge two villages into one with a new name. Same-owner only (different-owner consent not implemented).
     *
     * @param primary  Village from the token the player is holding
     * @param other    Village at the bell the player clicked
     * @param newName  Name for the merged village
     * @param initiator Player initiating the merge
     * @return The merged village, or null if merge failed
     */
    public VillageData mergeVillages(VillageData primary, VillageData other, String newName, Player initiator) {
        if (primary == null || other == null || primary == other || newName == null || newName.trim().isEmpty()) {
            return null;
        }
        String trimmedName = newName.trim();
        if (trimmedName.length() > 32) {
            if (initiator != null) initiator.sendMessage("§cName too long! Max 32 characters.");
            return null;
        }
        for (VillageData v : villages) {
            if (v.active && v != primary && v != other && v.name.equalsIgnoreCase(trimmedName)) {
                if (initiator != null) initiator.sendMessage("§cA village with that name already exists!");
                return null;
            }
        }

        VillageData merged = new VillageData();
        merged.name = trimmedName;
        merged.world = primary.world;
        merged.adminX = primary.adminX;
        merged.adminY = primary.adminY;
        merged.adminZ = primary.adminZ;
        merged.owner = primary.owner;
        merged.createdDate = Math.min(primary.createdDate, other.createdDate);
        merged.population = primary.population + other.population;
        merged.active = true;
        merged.effectiveRadius = Math.max(primary.effectiveRadius, other.effectiveRadius);
        Location otherPrimary = other.getAdminLocation();
        if (otherPrimary != null) {
            merged.additionalBells.add(otherPrimary.clone());
            double dist = primary.getAdminLocation() != null ? primary.getAdminLocation().distance(otherPrimary) : 0;
            merged.effectiveRadius = Math.max(merged.effectiveRadius, (int)(dist + 48));
        }
        merged.additionalBells.addAll(primary.additionalBells);
        for (Location bell : other.additionalBells) {
            if (bell != null && !merged.additionalBells.contains(bell)) merged.additionalBells.add(bell.clone());
        }

        if (chunkTerritoryManager != null) {
            chunkTerritoryManager.mergeVillages(primary.name, other.name, trimmedName);
        }
        if (influenceManager != null) {
            influenceManager.mergeVillages(primary.name, other.name, trimmedName);
        }
        if (plugin instanceof org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin) {
            org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin ep = (org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin) plugin;
            if (ep.getRoadNetworkManager() != null) ep.getRoadNetworkManager().mergeVillages(primary.name, other.name, trimmedName);
            if (ep.getTrailInfluenceManager() != null) ep.getTrailInfluenceManager().mergeVillages(primary.name, other.name, trimmedName);
        }

        villages.remove(primary);
        villages.remove(other);
        villages.add(merged);
        saveData();

        if (initiator != null) {
            initiator.sendMessage("§aVillages merged into §6" + trimmedName + "§a!");
        }
        return merged;
    }

    /**
     * Renames a village (internal method - called by naming system after voting).
     * No permission checks - villagers have voted!
     */
    public boolean renameVillage(VillageData village, String newName) {
        if (village == null || newName == null || newName.trim().isEmpty()) {
            return false;
        }
        
        // Check if name is too long
        if (newName.length() > 32) {
            return false;
        }
        
        // Check if name already exists
        String trimmedName = newName.trim();
        for (VillageData v : villages) {
            if (v.active && !v.equals(village) && v.name.equalsIgnoreCase(trimmedName)) {
                return false;
            }
        }
        
        String oldName = village.name;
        village.name = trimmedName;
        saveData();
        
        // Update influence manager if village name changed
        if (influenceManager != null && !oldName.equals(trimmedName)) {
            influenceManager.renameVillage(oldName, trimmedName);
        }
        
        // Update chunk territory manager if village name changed
        if (chunkTerritoryManager != null && !oldName.equals(trimmedName)) {
            chunkTerritoryManager.renameVillage(oldName, trimmedName);
        }
        
        return true;
    }
    
    /**
     * Get influence manager (for naming system).
     */
    public InfluenceManager getInfluenceManager() {
        return influenceManager;
    }
    
    /**
     * Get chunk territory manager (for naming system).
     */
    public ChunkTerritoryManager getChunkTerritoryManager() {
        return chunkTerritoryManager;
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
     * Gets the village that contains a specific location.
     * Uses natural Minecraft-style boundaries: distance from bells only (effectiveRadius from primary bell, 48 from additional bells).
     * No artificial chunk claims, roads, or trail influence – a location is in a village if it is within range of that village's bell(s).
     */
    public VillageData getVillageContaining(Location location) {
        if (location == null || location.getWorld() == null) return null;
        String worldName = location.getWorld().getName();
        for (VillageData data : villages) {
            if (!data.active) continue;
            if (!data.world.equals(worldName)) continue;
            Location adminLoc = data.getAdminLocation();
            if (adminLoc != null && location.distance(adminLoc) <= data.effectiveRadius) return data;
            for (Location bell : data.additionalBells) {
                if (bell != null && bell.getWorld() != null && bell.getWorld().getName().equals(worldName) && location.distance(bell) <= 48) return data;
            }
        }
        return null;
    }

    /** Get village by exact name. */
    public VillageData getVillageByName(String name) {
        if (name == null) return null;
        for (VillageData data : villages) {
            if (data.active && name.equals(data.name)) return data;
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
     * Set village population (count of villagers using this village's bell as MEETING_POINT/gossip). Saves after update.
     */
    public void setPopulation(VillageData data, int count) {
        data.population = Math.max(0, count);
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
        
        info.append("§ePopulation: §f").append(data.population).append(" villagers (use this bell as gossip)\n");
        info.append("§ePrimary Bell: §f").append(data.adminX).append(", ").append(data.adminY).append(", ").append(data.adminZ).append("\n");
        info.append("§eEffective Radius: §f").append(data.effectiveRadius).append(" blocks\n");
        if (!data.additionalBells.isEmpty()) {
            info.append("§eAdditional Bells: §f").append(data.additionalBells.size()).append("\n");
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

    /** Persist villages to disk. Call on plugin disable so bells/villages survive server restart. */
    public void save() {
        saveData();
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
                            data.population = 0;
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
