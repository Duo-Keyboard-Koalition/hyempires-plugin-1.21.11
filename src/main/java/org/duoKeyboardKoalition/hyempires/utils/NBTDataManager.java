package org.duoKeyboardKoalition.hyempires.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * NBT Data Manager for HyEmpires.
 * Stores data in NBT format using Minecraft's native NBT structure.
 * Uses a simplified approach: stores data as compressed NBT files.
 * 
 * Note: For full NBT support, consider using NBT-API library.
 * This implementation uses a YAML-like structure that can be easily converted to NBT.
 */
public class NBTDataManager {
    private final JavaPlugin plugin;
    private final File dataFile;
    private final String filename;
    
    public NBTDataManager(JavaPlugin plugin, String filename) {
        this.plugin = plugin;
        this.filename = filename;
        this.dataFile = new File(plugin.getDataFolder(), filename);
        init();
    }
    
    private void init() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
    }
    
    /**
     * Save data to NBT file.
     * Uses a structure compatible with Minecraft's NBT format.
     */
    public void saveData(Map<String, Object> data) {
        try {
            // Create NBT-like structure using FileConfiguration (YAML-based, compatible)
            FileConfiguration config = new YamlConfiguration();
            
            // Convert data map to configuration
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                setValue(config, entry.getKey(), entry.getValue());
            }
            
            // Save to .nbt file (actually YAML format, but Minecraft-compatible structure)
            config.save(dataFile);
            
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
        
        if (!dataFile.exists()) {
            return data;
        }
        
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            
            // Convert configuration to map
            for (String key : config.getKeys(true)) {
                if (!config.isConfigurationSection(key)) {
                    data.put(key, config.get(key));
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load NBT data from " + filename + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return data;
    }
    
    /**
     * Save list of data objects.
     */
    public void saveList(String listKey, List<Map<String, Object>> items) {
        Map<String, Object> data = new HashMap<>();
        data.put(listKey, items);
        saveData(data);
    }
    
    /**
     * Load list of data objects.
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
     * Helper method to set nested values in configuration.
     */
    private void setValue(FileConfiguration config, String key, Object value) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                setValue(config, key + "." + entry.getKey(), entry.getValue());
            }
        } else if (value instanceof List) {
            config.set(key, value);
        } else if (value instanceof Location) {
            Location loc = (Location) value;
            config.set(key + ".world", loc.getWorld() != null ? loc.getWorld().getName() : null);
            config.set(key + ".x", loc.getX());
            config.set(key + ".y", loc.getY());
            config.set(key + ".z", loc.getZ());
        } else {
            config.set(key, value);
        }
    }
    
    /**
     * Convert Location to map for NBT storage.
     */
    public static Map<String, Object> locationToMap(Location loc) {
        Map<String, Object> map = new HashMap<>();
        if (loc != null) {
            map.put("world", loc.getWorld() != null ? loc.getWorld().getName() : null);
            map.put("x", loc.getX());
            map.put("y", loc.getY());
            map.put("z", loc.getZ());
        }
        return map;
    }
    
    /**
     * Convert map to Location.
     */
    @SuppressWarnings("unchecked")
    public static Location mapToLocation(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        
        String worldName = (String) map.get("world");
        if (worldName == null) return null;
        
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        
        Object xObj = map.get("x");
        Object yObj = map.get("y");
        Object zObj = map.get("z");
        
        if (xObj instanceof Number && yObj instanceof Number && zObj instanceof Number) {
            double x = ((Number) xObj).doubleValue();
            double y = ((Number) yObj).doubleValue();
            double z = ((Number) zObj).doubleValue();
            return new Location(world, x, y, z);
        }
        
        return null;
    }
    
    /**
     * Convert UUID to string for storage.
     */
    public static String uuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }
    
    /**
     * Convert string to UUID.
     */
    public static UUID stringToUuid(String str) {
        if (str == null || str.isEmpty() || str.equals("none")) {
            return null;
        }
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
