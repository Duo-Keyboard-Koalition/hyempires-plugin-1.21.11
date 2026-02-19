package org.duoKeyboardKoalition.hyempires.utils;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.List;

public class CSVWriter {
    private final File csvFile;
    private final JavaPlugin plugin;
    private final String[] headers;

    public CSVWriter(JavaPlugin plugin, String filename, String[] headers) {
        this.plugin = plugin;
        this.csvFile = new File(plugin.getDataFolder(), filename);
        this.headers = headers;
        init();
    }

    public void init() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!csvFile.exists()) {
            try {
                csvFile.createNewFile();
                writeHeader();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create " + csvFile.getName() + ": " + e.getMessage());
            }
        }
    }

    private void writeHeader() {
        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write(String.join(",", headers) + "\n");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write CSV header: " + e.getMessage());
        }
    }

    public void writeAll(List<String> lines) {
        try (FileWriter writer = new FileWriter(csvFile)) {
            for (String line : lines) {
                writer.write(line + "\n");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write to CSV: " + e.getMessage());
        }
    }

    public void append(String line) {
        try (FileWriter writer = new FileWriter(csvFile, true)) {
            writer.write(line + "\n");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to append to CSV: " + e.getMessage());
        }
    }
}