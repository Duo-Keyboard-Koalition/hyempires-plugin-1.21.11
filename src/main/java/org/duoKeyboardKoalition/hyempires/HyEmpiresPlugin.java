package org.duoKeyboardKoalition.hyempires;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.scanners.MultiblockScanner;
import org.duoKeyboardKoalition.hyempires.Listener.VillagerListener;
import org.duoKeyboardKoalition.hyempires.Listener.VillagerTradeListener;
import org.duoKeyboardKoalition.hyempires.scanners.VillagerJobScanner;
import org.duoKeyboardKoalition.hyempires.managers.CampsiteManager;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;
import org.duoKeyboardKoalition.hyempires.managers.InfluenceManager;
import org.duoKeyboardKoalition.hyempires.managers.ChunkTerritoryManager;
import org.duoKeyboardKoalition.hyempires.Listener.CampsiteListener;
import org.duoKeyboardKoalition.hyempires.Listener.VillageAdminListener;
import org.duoKeyboardKoalition.hyempires.Listener.BoundaryToolListener;
import org.duoKeyboardKoalition.hyempires.Listener.VillagerJobSiteListener;
import org.duoKeyboardKoalition.hyempires.Listener.VillageMenuListener;
import org.duoKeyboardKoalition.hyempires.Listener.VillagerAssignmentListener;
import org.duoKeyboardKoalition.hyempires.commands.HyEmpiresCommand;

public final class HyEmpiresPlugin extends JavaPlugin {
    private MultiblockScanner multiblockScanner;
    private VillagerJobScanner villagerScanner;
    private CampsiteManager campsiteManager;
    private VillageManager villageManager;
    private InfluenceManager influenceManager;
    private ChunkTerritoryManager chunkTerritoryManager;

    @Override
    public void onEnable() {
        // Initialize managers
        campsiteManager = new CampsiteManager(this);
        villageManager = new VillageManager(this);
        influenceManager = new InfluenceManager(this);
        chunkTerritoryManager = new ChunkTerritoryManager(this);
        
        // Link managers
        villageManager.setInfluenceManager(influenceManager);
        villageManager.setChunkTerritoryManager(chunkTerritoryManager);

        // Initialize scanners
        multiblockScanner = new MultiblockScanner(this);
        villagerScanner = new VillagerJobScanner(this);

        // Register event listeners
        getServer().getPluginManager().registerEvents(multiblockScanner, this);
        getServer().getPluginManager().registerEvents(villagerScanner, this);
        getServer().getPluginManager().registerEvents(new VillagerListener(this), this);
        getServer().getPluginManager().registerEvents(new VillagerTradeListener(this), this);
        getServer().getPluginManager().registerEvents(new CampsiteListener(this, campsiteManager), this);
        getServer().getPluginManager().registerEvents(new VillageAdminListener(this, villageManager), this);
        getServer().getPluginManager().registerEvents(new BoundaryToolListener(this), this);
        getServer().getPluginManager().registerEvents(new VillagerJobSiteListener(this), this);
        getServer().getPluginManager().registerEvents(new VillageMenuListener(this, villageManager), this);
        getServer().getPluginManager().registerEvents(new VillagerAssignmentListener(this), this);

        // Register /hyempires command
        HyEmpiresCommand hyEmpiresCommand = new HyEmpiresCommand(this);
        getCommand("hyempires").setExecutor(hyEmpiresCommand);
        getCommand("hyempires").setTabCompleter(hyEmpiresCommand);

        // Schedule initial scans after server is fully started
        Bukkit.getScheduler().runTaskLater(this, () -> {
            multiblockScanner.scanLoadedChunks();
            villagerScanner.scanAllVillagers();
            campsiteManager.scanLoadedChunks();
            villageManager.scanLoadedChunks();
        }, 20L * 5); // 5 second delay

        getLogger().info("HyEmpires has been enabled!");
        getLogger().info("Campsite and Village management systems active!");
        getLogger().info("Build your empire, one settlement at a time!");
    }

    @Override
    public void onDisable() {
        // Perform final scans and save data
        if (multiblockScanner != null) {
            multiblockScanner.scanLoadedChunks();
        }
        if (villagerScanner != null) {
            villagerScanner.scanAllVillagers();
        }

        getLogger().info("HyEmpires has been disabled.");
    }

    // Getters for the scanners and managers if needed
    public MultiblockScanner getMultiblockScanner() {
        return multiblockScanner;
    }

    public VillagerJobScanner getVillagerScanner() {
        return villagerScanner;
    }

    public CampsiteManager getCampsiteManager() {
        return campsiteManager;
    }

    public VillageManager getVillageManager() {
        return villageManager;
    }
    
    public InfluenceManager getInfluenceManager() {
        return influenceManager;
    }
    
    public ChunkTerritoryManager getChunkTerritoryManager() {
        return chunkTerritoryManager;
    }
}
