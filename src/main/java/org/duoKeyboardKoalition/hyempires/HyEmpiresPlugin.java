package org.duoKeyboardKoalition.hyempires;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.duoKeyboardKoalition.hyempires.scanners.MultiblockScanner;
import org.duoKeyboardKoalition.hyempires.Listener.VillagerListener;
import org.duoKeyboardKoalition.hyempires.Listener.VillagerTradeListener;
import org.duoKeyboardKoalition.hyempires.scanners.VillagerJobScanner;
import org.duoKeyboardKoalition.hyempires.managers.CampsiteManager;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;
import org.duoKeyboardKoalition.hyempires.managers.InfluenceManager;
import org.duoKeyboardKoalition.hyempires.managers.ChunkTerritoryManager;
import org.duoKeyboardKoalition.hyempires.managers.TrailInfluenceManager;
import org.duoKeyboardKoalition.hyempires.managers.RoadNetworkManager;
import org.duoKeyboardKoalition.hyempires.Listener.CampsiteListener;
import org.duoKeyboardKoalition.hyempires.Listener.VillageAdminListener;
import org.duoKeyboardKoalition.hyempires.Listener.BoundaryToolListener;
import org.duoKeyboardKoalition.hyempires.Listener.VillagerJobSiteListener;
import org.duoKeyboardKoalition.hyempires.Listener.VillageMenuListener;
import org.duoKeyboardKoalition.hyempires.Listener.VillagerAssignmentListener;
import org.duoKeyboardKoalition.hyempires.Listener.VillageBedListener;
import org.duoKeyboardKoalition.hyempires.Listener.IronGolemListener;
import org.duoKeyboardKoalition.hyempires.commands.HyEmpiresCommand;
import org.duoKeyboardKoalition.hyempires.systems.VillageNamingSystem;
import org.duoKeyboardKoalition.hyempires.systems.VillagerHealingSystem;
import org.duoKeyboardKoalition.hyempires.utils.VillageAdminToken;
import org.duoKeyboardKoalition.hyempires.managers.ChunkTerritoryManager.ChunkKey;

public final class HyEmpiresPlugin extends JavaPlugin {

    /** A workspace (workstation) location and its block type, for listing in the admin menu. */
    public static final class WorkspaceEntry {
        public final Location location;
        public final Material type;
        public WorkspaceEntry(Location location, Material type) {
            this.location = location;
            this.type = type;
        }
    }

    private MultiblockScanner multiblockScanner;
    private VillagerJobScanner villagerScanner;
    private CampsiteManager campsiteManager;
    private VillageManager villageManager;
    private InfluenceManager influenceManager;
    private ChunkTerritoryManager chunkTerritoryManager;
    private TrailInfluenceManager trailInfluenceManager;
    private RoadNetworkManager roadNetworkManager;
    private VillageNamingSystem villageNamingSystem;
    private VillagerHealingSystem villagerHealingSystem;
    private VillageMenuListener villageMenuListener;

    @Override
    public void onEnable() {
        // Initialize managers
        campsiteManager = new CampsiteManager(this);
        villageManager = new VillageManager(this);
        influenceManager = new InfluenceManager(this);
        chunkTerritoryManager = new ChunkTerritoryManager(this);
        trailInfluenceManager = new TrailInfluenceManager(this);
        roadNetworkManager = new RoadNetworkManager(this);

        chunkTerritoryManager.setTrailInfluenceManager(trailInfluenceManager);
        chunkTerritoryManager.setRoadNetworkManager(roadNetworkManager);

        villageManager.setInfluenceManager(influenceManager);
        villageManager.setChunkTerritoryManager(chunkTerritoryManager);
        chunkTerritoryManager.setVillageManager(villageManager);

        // Initialize naming system (villager voting for village names)
        villageNamingSystem = new VillageNamingSystem(this, villageManager);
        // Smiths heal iron golems; clerics throw healing/regen potions at villagers and golems
        villagerHealingSystem = new VillagerHealingSystem(this);

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
        villageMenuListener = new VillageMenuListener(this, villageManager);
        getServer().getPluginManager().registerEvents(villageMenuListener, this);
        getServer().getPluginManager().registerEvents(new VillagerAssignmentListener(this), this);
        getServer().getPluginManager().registerEvents(new VillageBedListener(this, villageManager), this);
        getServer().getPluginManager().registerEvents(new IronGolemListener(this, villageManager), this);

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
            // Refresh village population from entity RAM (MEETING_POINT at each village's bell)
            for (VillageManager.VillageData v : villageManager.getAllVillages()) {
                if (v.active) villageManager.setPopulation(v, getMeetingPointCount(v));
            }
        }, 20L * 5); // 5 second delay

        // Show influence trail particles to players holding Village Administration Token (paper)
        Bukkit.getScheduler().runTaskTimer(this, this::showTrailParticlesForTokenHolders, 40L, 40L); // every 2 seconds

        getLogger().info("HyEmpires has been enabled!");
        getLogger().info("Campsite and Village management systems active!");
        getLogger().info("Build your empire, one settlement at a time!");
    }

    @Override
    public void onDisable() {
        // Shutdown naming system
        if (villageNamingSystem != null) {
            villageNamingSystem.shutdown();
        }

        // Save village data (bells, radius, etc.) so it persists across restarts
        if (villageManager != null) {
            villageManager.save();
        }

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

    public TrailInfluenceManager getTrailInfluenceManager() {
        return trailInfluenceManager;
    }

    public RoadNetworkManager getRoadNetworkManager() {
        return roadNetworkManager;
    }
    
    public VillageNamingSystem getVillageNamingSystem() {
        return villageNamingSystem;
    }
    
    public VillageMenuListener getVillageMenuListener() {
        return villageMenuListener;
    }

    /**
     * Classify a villager by the Feudal hierarchy (Scout / Laborer / Peasant / Vassal).
     */
    public FeudalVillagerType getVillagerType(Villager villager) {
        if (villager == null) return FeudalVillagerType.SCOUT;
        VillagerJobScanner scanner = getVillagerScanner();
        if (scanner == null) return FeudalVillagerType.SCOUT;
        Location bed = scanner.getVillagerBedLocation(villager);
        Location workstation = scanner.getVillagerWorkstationLocation(villager);
        return FeudalVillagerType.from(bed != null, workstation != null);
    }

    /** Read villager bed from live entity brain in RAM (MemoryKey.HOME). Same data the game AI uses. */
    private static Location getVillagerBedFromGame(Villager villager) {
        try {
            return villager.getMemory(MemoryKey.HOME);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Read villager workstation from live entity brain in RAM (MemoryKey.JOB_SITE). Same data the game AI uses. */
    private static Location getVillagerWorkstationFromGame(Villager villager) {
        try {
            return villager.getMemory(MemoryKey.JOB_SITE);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Read villager meeting point (gossip bell) from live entity brain in RAM (MemoryKey.MEETING_POINT). Same data the game AI uses. */
    private static Location getVillagerMeetingPointFromGame(Villager villager) {
        try {
            return villager.getMemory(MemoryKey.MEETING_POINT);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * A villager is a resident of a village (Vassal) if and only if they have BOTH
     * an assigned bed AND an assigned workplace (from base game), and BOTH are in that village.
     */
    public boolean isVillagerResidentOfVillage(Villager villager, VillageManager.VillageData village) {
        if (villager == null || village == null) return false;
        Location bed = getVillagerBedFromGame(villager);
        Location workplace = getVillagerWorkstationFromGame(villager);
        if (bed == null || workplace == null) return false;
        VillageManager.VillageData bedVillage = villageManager.getVillageContaining(bed);
        VillageManager.VillageData workVillage = villageManager.getVillageContaining(workplace);
        return bedVillage != null && workVillage != null
            && village.name.equals(bedVillage.name) && village.name.equals(workVillage.name);
    }

    /**
     * Count of villagers who have both an assigned bed and an assigned workplace in this village.
     * Membership determined by base game only (MemoryKey.HOME, MemoryKey.JOB_SITE).
     */
    public int getResidentCount(VillageManager.VillageData village) {
        if (village == null) return 0;
        int count = 0;
        World world = village.getAdminLocation() != null ? village.getAdminLocation().getWorld() : null;
        if (world == null) return 0;
        for (Villager entity : world.getEntitiesByClass(Villager.class)) {
            if (!entity.isValid()) continue;
            Location bed = getVillagerBedFromGame(entity);
            Location job = getVillagerWorkstationFromGame(entity);
            if (bed == null || job == null) continue;
            VillageManager.VillageData bedV = villageManager.getVillageContaining(bed);
            VillageManager.VillageData workV = villageManager.getVillageContaining(job);
            if (bedV != null && workV != null && village.name.equals(bedV.name) && village.name.equals(workV.name))
                count++;
        }
        return count;
    }

    /**
     * All villagers that belong to this village (have bed and workplace in the village). Base game only.
     */
    public List<Villager> getResidentsInVillage(VillageManager.VillageData village) {
        return getVillagersInVillageByType(village, FeudalVillagerType.VASSAL);
    }

    /**
     * Villagers linked to this village by feudal type. Membership from base game only (MemoryKey.HOME, MemoryKey.JOB_SITE).
     * Vassal: bed + workstation in village. Peasant: bed in village only. Laborer: workstation in village only. Scout: neither in village.
     */
    public List<Villager> getVillagersInVillageByType(VillageManager.VillageData village, FeudalVillagerType type) {
        List<Villager> out = new ArrayList<>();
        if (village == null) return out;
        World world = village.getAdminLocation() != null ? village.getAdminLocation().getWorld() : null;
        if (world == null) return out;

        for (Villager entity : world.getEntitiesByClass(Villager.class)) {
            if (!entity.isValid()) continue;
            Location bed = getVillagerBedFromGame(entity);
            Location job = getVillagerWorkstationFromGame(entity);
            boolean hasBed = bed != null && bed.getWorld() != null;
            boolean hasWork = job != null && job.getWorld() != null;
            VillageManager.VillageData bedV = hasBed ? villageManager.getVillageContaining(bed) : null;
            VillageManager.VillageData workV = hasWork ? villageManager.getVillageContaining(job) : null;
            boolean bedInVillage = bedV != null && village.name.equals(bedV.name);
            boolean workInVillage = workV != null && village.name.equals(workV.name);

            switch (type) {
                case VASSAL:
                    if (hasBed && hasWork && bedInVillage && workInVillage) out.add(entity);
                    break;
                case PEASANT:
                    if (bedInVillage && !workInVillage) out.add(entity);
                    break;
                case LABORER:
                    if (workInVillage && !bedInVillage) out.add(entity);
                    break;
                case SCOUT:
                    if (!hasBed && !hasWork) out.add(entity);
                    break;
                default:
                    break;
            }
        }
        return out;
    }

    /**
     * Bed locations in this village from base game only: unique HOME (MemoryKey.HOME) of villagers whose bed is in village.
     */
    public List<Location> getBedLocationsInVillageFromVillagers(VillageManager.VillageData village) {
        List<Location> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (village == null) return out;
        World world = village.getAdminLocation() != null ? village.getAdminLocation().getWorld() : null;
        if (world == null) return out;
        for (Villager entity : world.getEntitiesByClass(Villager.class)) {
            if (!entity.isValid()) continue;
            Location bed = getVillagerBedFromGame(entity);
            if (bed == null) continue;
            VillageManager.VillageData v = villageManager.getVillageContaining(bed);
            if (v == null || !v.name.equals(village.name)) continue;
            String key = bed.getWorld().getName() + "|" + bed.getBlockX() + "|" + bed.getBlockY() + "|" + bed.getBlockZ();
            if (seen.add(key)) out.add(bed);
        }
        Location bell = village.getAdminLocation();
        if (bell != null) out.sort(java.util.Comparator.comparingDouble(loc -> loc.distanceSquared(bell)));
        return out;
    }

    /** All bed locations in this village: from villagers (HOME) plus block scan in village radius, so token menu shows every bed. */
    public List<Location> getBedLocationsInVillage(VillageManager.VillageData village) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<Location> out = new ArrayList<>();
        if (village == null) return out;
        for (Location loc : getBedLocationsInVillageFromVillagers(village)) {
            String key = loc.getWorld().getName() + "|" + loc.getBlockX() + "|" + loc.getBlockY() + "|" + loc.getBlockZ();
            if (seen.add(key)) out.add(loc);
        }
        Location bellLoc = village.getAdminLocation();
        if (bellLoc == null || bellLoc.getWorld() == null) return out;
        org.bukkit.World world = bellLoc.getWorld();
        int radius = village.effectiveRadius;
        int chunkRadius = (radius / 16) + 1;
        org.bukkit.Chunk centerChunk = bellLoc.getChunk();
        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                org.bukkit.Chunk chunk = world.getChunkAt(centerChunk.getX() + cx, centerChunk.getZ() + cz);
                if (!chunk.isLoaded()) continue;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int worldX = chunk.getX() * 16 + x;
                        int worldZ = chunk.getZ() * 16 + z;
                        if (Math.pow(worldX - bellLoc.getX(), 2) + Math.pow(worldZ - bellLoc.getZ(), 2) > radius * radius) continue;
                        int minY = Math.max(world.getMinHeight(), bellLoc.getBlockY() - 10);
                        int maxY = Math.min(world.getMaxHeight(), bellLoc.getBlockY() + 10);
                        for (int y = minY; y <= maxY; y++) {
                            org.bukkit.block.Block block = chunk.getBlock(x, y, z);
                            if (isBedHead(block)) {
                                Location loc = block.getLocation();
                                VillageManager.VillageData v = villageManager.getVillageContaining(loc);
                                if (v != null && v.name.equals(village.name)) {
                                    String key = loc.getWorld().getName() + "|" + loc.getBlockX() + "|" + loc.getBlockY() + "|" + loc.getBlockZ();
                                    if (seen.add(key)) out.add(loc);
                                }
                            }
                        }
                    }
                }
            }
        }
        for (Location additionalBell : village.additionalBells) {
            if (additionalBell.getWorld() == null || !additionalBell.getWorld().equals(world)) continue;
            int addRadius = 48;
            int addChunkRadius = (addRadius / 16) + 1;
            org.bukkit.Chunk addCenter = additionalBell.getChunk();
            for (int cx = -addChunkRadius; cx <= addChunkRadius; cx++) {
                for (int cz = -addChunkRadius; cz <= addChunkRadius; cz++) {
                    org.bukkit.Chunk chunk = world.getChunkAt(addCenter.getX() + cx, addCenter.getZ() + cz);
                    if (!chunk.isLoaded()) continue;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int worldX = chunk.getX() * 16 + x;
                            int worldZ = chunk.getZ() * 16 + z;
                            if (Math.pow(worldX - additionalBell.getX(), 2) + Math.pow(worldZ - additionalBell.getZ(), 2) > addRadius * addRadius) continue;
                            int minY = Math.max(world.getMinHeight(), additionalBell.getBlockY() - 10);
                            int maxY = Math.min(world.getMaxHeight(), additionalBell.getBlockY() + 10);
                            for (int y = minY; y <= maxY; y++) {
                                org.bukkit.block.Block block = chunk.getBlock(x, y, z);
                                if (isBedHead(block)) {
                                    Location loc = block.getLocation();
                                    VillageManager.VillageData v = villageManager.getVillageContaining(loc);
                                    if (v != null && v.name.equals(village.name)) {
                                        String key = loc.getWorld().getName() + "|" + loc.getBlockX() + "|" + loc.getBlockY() + "|" + loc.getBlockZ();
                                        if (seen.add(key)) out.add(loc);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (bellLoc != null) out.sort(java.util.Comparator.comparingDouble(loc -> loc.distanceSquared(bellLoc)));
        return out;
    }

    private static boolean isBedBlock(Material m) {
        return m != null && m.name().endsWith("_BED");
    }

    private static boolean isBedHead(org.bukkit.block.Block block) {
        if (!isBedBlock(block.getType())) return false;
        org.bukkit.block.data.BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Bed) {
            return ((org.bukkit.block.data.type.Bed) data).getPart() == org.bukkit.block.data.type.Bed.Part.HEAD;
        }
        return true;
    }

    /**
     * Workstation locations in this village from base game only: unique JOB_SITE (MemoryKey.JOB_SITE) of villagers whose workstation is in village.
     */
    public List<WorkspaceEntry> getWorkspaceLocationsInVillageFromVillagers(VillageManager.VillageData village) {
        List<WorkspaceEntry> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (village == null) return out;
        World world = village.getAdminLocation() != null ? village.getAdminLocation().getWorld() : null;
        if (world == null) return out;
        for (Villager entity : world.getEntitiesByClass(Villager.class)) {
            if (!entity.isValid()) continue;
            Location job = getVillagerWorkstationFromGame(entity);
            if (job == null || job.getWorld() == null) continue;
            VillageManager.VillageData v = villageManager.getVillageContaining(job);
            if (v == null || !v.name.equals(village.name)) continue;
            String key = job.getWorld().getName() + "|" + job.getBlockX() + "|" + job.getBlockY() + "|" + job.getBlockZ();
            if (seen.add(key)) {
                Material type = job.getWorld().isChunkLoaded(job.getBlockX() >> 4, job.getBlockZ() >> 4)
                    ? job.getBlock().getType() : Material.BARREL;
                out.add(new WorkspaceEntry(job, type));
            }
        }
        Location bell = village.getAdminLocation();
        if (bell != null) out.sort(java.util.Comparator.comparingDouble(w -> w.location.distanceSquared(bell)));
        return out;
    }

    /** Count of villagers linked to this village for the given type. */
    public int getVillagerCountByType(VillageManager.VillageData village, FeudalVillagerType type) {
        return getVillagersInVillageByType(village, type).size();
    }

    /**
     * Count of villagers that use this village's bell(s) as their gossip/MEETING_POINT (entity brain, RAM).
     * This is the village population.
     */
    public int getMeetingPointCount(VillageManager.VillageData village) {
        if (village == null) return 0;
        World world = village.getAdminLocation() != null ? village.getAdminLocation().getWorld() : null;
        if (world == null) return 0;
        int count = 0;
        for (Villager entity : world.getEntitiesByClass(Villager.class)) {
            if (!entity.isValid()) continue;
            Location meeting = getVillagerMeetingPointFromGame(entity);
            if (meeting == null || meeting.getWorld() == null) continue;
            VillageManager.VillageData atBell = villageManager.getVillageAt(meeting);
            if (atBell != null && village.name.equals(atBell.name)) count++;
        }
        return count;
    }

    /** All villagers linked to this village (HOME or JOB_SITE in village). Base game only. For GUI profession filter. */
    public List<Villager> getVillagersInVillage(VillageManager.VillageData village) {
        return new ArrayList<>(getVillagersLinkedToVillage(village));
    }

    /** Villagers linked to this village. Reads live entity brain (RAM): HOME, JOB_SITE, MEETING_POINT. */
    private java.util.Collection<Villager> getVillagersLinkedToVillage(VillageManager.VillageData village) {
        List<Villager> list = new ArrayList<>();
        if (village == null) return list;
        World world = village.getAdminLocation() != null ? village.getAdminLocation().getWorld() : null;
        if (world == null) return list;
        for (Villager entity : world.getEntitiesByClass(Villager.class)) {
            if (!entity.isValid()) continue;
            Location bed = getVillagerBedFromGame(entity);
            Location job = getVillagerWorkstationFromGame(entity);
            Location meeting = getVillagerMeetingPointFromGame(entity);
            VillageManager.VillageData bedV = bed != null && bed.getWorld() != null ? villageManager.getVillageContaining(bed) : null;
            VillageManager.VillageData workV = job != null && job.getWorld() != null ? villageManager.getVillageContaining(job) : null;
            // Meeting point = bell; getVillageAt(meeting) returns village that has a bell at that block
            VillageManager.VillageData meetingV = meeting != null && meeting.getWorld() != null ? villageManager.getVillageAt(meeting) : null;
            boolean inVillage = (bedV != null && village.name.equals(bedV.name))
                || (workV != null && village.name.equals(workV.name))
                || (meetingV != null && village.name.equals(meetingV.name));
            if (inVillage) list.add(entity);
        }
        return list;
    }

    /**
     * Sync bed/workstation data from entity brain for villagers linked to this village (by HOME/JOB_SITE).
     * Call when a player opens the village villager list so any scanner cache is up to date.
     */
    public void refreshVillageVillagerData(VillageManager.VillageData village) {
        VillagerJobScanner scanner = getVillagerScanner();
        if (scanner == null) return;
        for (Villager v : getVillagersLinkedToVillage(village)) {
            if (v.isValid()) scanner.updateVillagerData(v);
        }
    }

    /**
     * No-op: village boundaries are natural (radius from bells only). Kept for API compatibility.
     */
    public void refreshVillageTerritory(VillageManager.VillageData village) {
        // Boundaries = distance from bells only; no artificial chunk claiming
    }

    /** Villagers walking leave a trail of influence; chunks touched most fall under the village. */
    private void sampleVillagerTrails(VillageManager.VillageData village) {
        if (trailInfluenceManager == null) return;
        for (Villager v : getVillagersLinkedToVillage(village)) {
            if (!v.isValid()) continue;
            Location loc = v.getLocation();
            if (loc.getWorld() == null) continue;
            VillageManager.VillageData inV = villageManager.getVillageContaining(loc);
            if (inV != null && village.name.equals(inV.name))
                trailInfluenceManager.addTrail(village.name, loc);
        }
    }

    /**
     * Build roads from bell to beds and workstations. Influence = cost to build; elevation heavily penalized.
     */
    private void buildRoadsForVillage(VillageManager.VillageData village) {
        if (roadNetworkManager == null || influenceManager == null) return;
        Location bell = village.getAdminLocation();
        if (bell == null || bell.getWorld() == null) return;
        List<Location> targets = getVillageStructureLocations(village);
        double budget = influenceManager.getTotalInfluence(village.name);
        java.util.UUID leader = influenceManager.getCurrentLeader(village.name);
        if (leader == null) return;
        for (Location target : targets) {
            if (target == null || target.getWorld() == null || target.equals(bell)) continue;
            if (budget <= 0) break;
            org.duoKeyboardKoalition.hyempires.managers.ChunkTerritoryManager.ChunkKey targetChunkKey = new org.duoKeyboardKoalition.hyempires.managers.ChunkTerritoryManager.ChunkKey(target.getWorld().getName(), target.getBlockX() >> 4, target.getBlockZ() >> 4);
            if (roadNetworkManager.isChunkOnRoad(village.name, targetChunkKey)) continue; // already connected
            List<Location> blockPositions = roadNetworkManager.findPathBlockPositions(bell, target);
            if (blockPositions.isEmpty()) continue;
            double cost = blockPositions.size() * 1.5; // approximate cost (horizontal + elevation penalty)
            if (cost > budget) continue;
            roadNetworkManager.placeRoadBlocks(blockPositions); // prefer path blocks, cobblestone, deepslate, etc.
            roadNetworkManager.addRoadChunks(village.name, blockPositions);
            influenceManager.addInfluence(village.name, leader, -cost, "road_build");
            budget -= cost;
        }
    }

    /** Show influence trail particles to players holding Village Administration Token (paper). */
    private void showTrailParticlesForTokenHolders() {
        if (trailInfluenceManager == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;
            if (!VillageAdminToken.isToken(player.getInventory().getItemInMainHand())) continue;
            String villageName = VillageAdminToken.getVillageName(this, player.getInventory().getItemInMainHand());
            if (villageName == null) continue;
            World world = player.getWorld();
            int pcx = player.getLocation().getChunk().getX();
            int pcz = player.getLocation().getChunk().getZ();
            int range = 6;
            int shown = 0;
            int maxPerTick = 40;
            for (Map.Entry<ChunkKey, Double> e : trailInfluenceManager.getTrailChunksForVillage(villageName).entrySet()) {
                if (shown >= maxPerTick) break;
                ChunkKey ck = e.getKey();
                if (!ck.world.equals(world.getName())) continue;
                if (Math.abs(ck.x - pcx) > range || Math.abs(ck.z - pcz) > range) continue;
                int cx = ck.x;
                int cz = ck.z;
                int gx = cx * 16 + 8;
                int gz = cz * 16 + 8;
                int y = world.getHighestBlockYAt(gx, gz) + 1;
                Location at = new Location(world, gx + 0.5, y, gz + 0.5);
                world.spawnParticle(Particle.HAPPY_VILLAGER, at, 2, 0.3, 0.2, 0.3, 0);
                shown++;
            }
        }
    }

    /** All workspace (workstation) locations in this village, with block type. Deduplicated by location. */
    public List<WorkspaceEntry> getWorkspaceLocationsInVillage(VillageManager.VillageData village) {
        List<WorkspaceEntry> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        VillagerJobScanner scanner = getVillagerScanner();
        if (scanner == null) return out;
        for (Map.Entry<UUID, VillagerJobScanner.VillagerData> e : scanner.getVillagerData().entrySet()) {
            VillagerJobScanner.VillagerData vd = e.getValue();
            if (vd == null || vd.jobsite == null || vd.jobsite.getWorld() == null) continue;
            VillageManager.VillageData workV = villageManager.getVillageContaining(vd.jobsite);
            if (workV == null || !workV.name.equals(village.name)) continue;
            String key = vd.jobsite.getWorld().getName() + "|" + vd.jobsite.getBlockX() + "|" + vd.jobsite.getBlockY() + "|" + vd.jobsite.getBlockZ();
            if (seen.contains(key)) continue;
            seen.add(key);
            Material type = vd.jobsite.getBlock().getType();
            out.add(new WorkspaceEntry(vd.jobsite.clone(), type));
        }
        Location bell = village.getAdminLocation();
        if (bell != null) out.sort(java.util.Comparator.comparingDouble(w -> w.location.distanceSquared(bell)));
        return out;
    }

    /** All locations that should push village territory: bell(s), beds/workstations from base game (villagers' HOME/JOB_SITE). */
    private List<Location> getVillageStructureLocations(VillageManager.VillageData village) {
        List<Location> out = new ArrayList<>();
        Location bell = village.getAdminLocation();
        if (bell != null) out.add(bell);
        for (Location loc : village.additionalBells) if (loc != null) out.add(loc);
        out.addAll(getBedLocationsInVillageFromVillagers(village));
        for (WorkspaceEntry w : getWorkspaceLocationsInVillageFromVillagers(village)) out.add(w.location);
        return out;
    }

    private Villager findVillagerByUuid(UUID uuid) {
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : w.getEntities()) {
                if (entity instanceof Villager && entity.getUniqueId().equals(uuid))
                    return (Villager) entity;
            }
        }
        return null;
    }
}
