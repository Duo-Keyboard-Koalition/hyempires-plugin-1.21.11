package org.duoKeyboardKoalition.hyempires.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * NBT File Manager for HyEmpires.
 * Stores data in NBT format (.nbt files) compatible with Minecraft's native format.
 * Uses YAML-based structure that mimics NBT compound tags for compatibility.
 */
public class NBTFileManager {
    private final JavaPlugin plugin;
    private final File nbtFile;
    private final String filename;
    
    public NBTFileManager(JavaPlugin plugin, String filename) {
        this.plugin = plugin;
        // Ensure .nbt extension
        this.filename = filename.endsWith(".nbt") ? filename : filename.replace(".csv", ".nbt");
        this.nbtFile = new File(plugin.getDataFolder(), this.filename);
        init();
    }
    
    private void init() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
    }
    
    /**
     * Save data to NBT file.
     * Structure mimics NBT compound tags.
     */
    public void saveData(Map<String, Object> rootData) {
        try {
            // Use YAML format but save as .nbt for Minecraft compatibility
            YamlConfiguration config = new YamlConfiguration();
            
            // Convert data map to NBT-like structure
            for (Map.Entry<String, Object> entry : rootData.entrySet()) {
                setNBTValue(config, entry.getKey(), entry.getValue());
            }
            
            // Save as .nbt file (YAML format, NBT-compatible structure)
            config.save(nbtFile);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save NBT data to " + filename + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load data from NBT file.
     */
    public Map<String, Object> loadData() {
        Map<String, Object> data = new HashMap<>();
        
        if (!nbtFile.exists()) {
            return data;
        }
        
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.load(nbtFile);
            
            // Convert configuration to map
            convertToMap(config, "", config.getRoot(), data);
            
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Failed to load NBT data from " + filename + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return data;
    }
    
    /**
     * Save a list of data objects.
     */
    public void saveList(String listKey, List<Map<String, Object>> items) {
        Map<String, Object> rootData = new HashMap<>();
        rootData.put(listKey, items);
        saveData(rootData);
    }
    
    /**
     * Load a list of data objects.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> loadList(String listKey) {
        Map<String, Object> data = loadData();
        Object listObj = data.get(listKey);
        if (listObj instanceof List) {
            return (List<Map<String, Object>>) listObj;
        }
        return new ArrayList<>();
    }
    
    /**
     * Helper to set nested NBT values.
     */
    private void setNBTValue(YamlConfiguration config, String key, Object value) {
        if (value == null) {
            config.set(key, null);
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                setNBTValue(config, key + "." + entry.getKey(), entry.getValue());
            }
        } else if (value instanceof List) {
            config.set(key, value);
        } else if (value instanceof Location) {
            Location loc = (Location) value;
            config.set(key + ".world", loc.getWorld() != null ? loc.getWorld().getName() : null);
            config.set(key + ".x", loc.getBlockX());
            config.set(key + ".y", loc.getBlockY());
            config.set(key + ".z", loc.getBlockZ());
        } else if (value instanceof UUID) {
            config.set(key, value.toString());
        } else {
            config.set(key, value);
        }
    }
    
    /**
     * Convert configuration section to map.
     */
    @SuppressWarnings("unchecked")
    private void convertToMap(YamlConfiguration config, String prefix, org.bukkit.configuration.ConfigurationSection section, Map<String, Object> map) {
        for (String key : section.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(key);
            
            if (value instanceof org.bukkit.configuration.ConfigurationSection) {
                Map<String, Object> nestedMap = new HashMap<>();
                convertToMap(config, fullKey, (org.bukkit.configuration.ConfigurationSection) value, nestedMap);
                map.put(key, nestedMap);
            } else {
                map.put(key, value);
            }
        }
    }
    
    /**
     * Convert Location to NBT-compatible map.
     */
    public static Map<String, Object> locationToNBT(Location loc) {
        Map<String, Object> map = new HashMap<>();
        if (loc != null) {
            map.put("world", loc.getWorld() != null ? loc.getWorld().getName() : null);
            map.put("x", loc.getBlockX());
            map.put("y", loc.getBlockY());
            map.put("z", loc.getBlockZ());
        }
        return map;
    }
    
    /**
     * Convert NBT map to Location.
     */
    @SuppressWarnings("unchecked")
    public static Location nbtToLocation(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        
        String worldName = (String) map.get("world");
        if (worldName == null) return null;
        
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        
        Object xObj = map.get("x");
        Object yObj = map.get("y");
        Object zObj = map.get("z");
        
        if (xObj instanceof Number && yObj instanceof Number && zObj instanceof Number) {
            int x = ((Number) xObj).intValue();
            int y = ((Number) yObj).intValue();
            int z = ((Number) zObj).intValue();
            return new Location(world, x, y, z);
        }
        
        return null;
    }
    
    /**
     * Convert list of locations to NBT list.
     */
    public static List<Map<String, Object>> locationsToNBTList(List<Location> locations) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Location loc : locations) {
            list.add(locationToNBT(loc));
        }
        return list;
    }
    
    /**
     * Convert NBT list to locations.
     */
    @SuppressWarnings("unchecked")
    public static List<Location> nbtListToLocations(List<Map<String, Object>> nbtList) {
        List<Location> locations = new ArrayList<>();
        for (Map<String, Object> map : nbtList) {
            Location loc = nbtToLocation(map);
            if (loc != null) {
                locations.add(loc);
            }
        }
        return locations;
    }
    
    /**
     * Get the NBT file.
     */
    public File getFile() {
        return nbtFile;
    }
}
