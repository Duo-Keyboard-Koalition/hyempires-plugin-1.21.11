package org.duoKeyboardKoalition.hyempires.scanners;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.duoKeyboardKoalition.hyempires.utils.CSVWriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MultiblockScanner implements Listener {
    private final JavaPlugin plugin;
    private final CSVWriter csvWriter;
    private final Set<Location> foundStructures = new HashSet<>();

    public MultiblockScanner(JavaPlugin plugin) {
        this.plugin = plugin;
        String[] headers = {"World", "X", "Y", "Z"};
        this.csvWriter = new CSVWriter(plugin, "multiblocks.csv", headers);
    }

    // Event Handlers
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> scanChunk(event.getChunk()), 1L);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location blockLocation = block.getLocation();

        if (block.getType() == Material.FARMLAND) {
            checkAndUnregisterMultiblockFromFarmland(block);
        }
        else if (block.getType() == Material.COMPOSTER) {
            Location chestLocation = blockLocation.clone().subtract(0, 1, 0);
            if (isStructureRegistered(chestLocation)) {
                unregisterMultiblock(chestLocation);
            }
        }
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.FARMLAND) {
            checkAndUnregisterMultiblockFromFarmland(block);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplodedBlocks(event.blockList());
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplodedBlocks(event.blockList());
    }

    // Multiblock Registration Methods
    private void registerNewMultiblock(Location location) {
        foundStructures.add(location);

        String csvLine = String.format("%s,%d,%d,%d",
                Objects.requireNonNull(location.getWorld()).getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());

        csvWriter.append(csvLine);
        notifyNearbyPlayers(location, "New multiblock structure registered at: ");
    }

    private void unregisterMultiblock(Location location) {
        foundStructures.remove(location);

        List<String> updatedEntries = new ArrayList<>();
        for (Location loc : foundStructures) {
            updatedEntries.add(String.format("%s,%d,%d,%d",
                    loc.getWorld().getName(),
                    loc.getBlockX(),
                    loc.getBlockY(),
                    loc.getBlockZ()
            ));
        }

        csvWriter.writeAll(updatedEntries);
        notifyNearbyPlayers(location, "Multiblock structure broken at: ");
    }

    // Scanning Methods
    private void scanChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int minY = Math.max(world.getMinHeight(), 0);
        int maxY = Math.min(world.getMaxHeight(), 320);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() == Material.COMPOSTER) {
                        checkMultiblockAtComposter(block);
                    }
                }
            }
        }
    }

    public void scanLoadedChunks() {
        plugin.getServer().getWorlds().forEach(world -> {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        });
    }

    // Validation Methods
    private boolean isStructureRegistered(Location location) {
        return foundStructures.stream().anyMatch(loc ->
                loc.getWorld().equals(location.getWorld()) &&
                        loc.getBlockX() == location.getBlockX() &&
                        loc.getBlockY() == location.getBlockY() &&
                        loc.getBlockZ() == location.getBlockZ()
        );
    }

    private boolean isWaterloggedChest(Block block) {
        if (block.getType() != Material.CHEST) return false;
        if (!(block.getBlockData() instanceof org.bukkit.block.data.Waterlogged)) return false;
        return ((org.bukkit.block.data.Waterlogged) block.getBlockData()).isWaterlogged();
    }

    private boolean isValidFarmlandPattern(Block center) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                if (center.getRelative(x, 0, z).getType() != Material.FARMLAND) {
                    return false;
                }
            }
        }
        return true;
    }

    // Helper Methods
    private void checkMultiblockAtComposter(Block composter) {
        Block potentialChest = composter.getRelative(0, -1, 0);
        if (!isWaterloggedChest(potentialChest)) return;
        if (foundStructures.contains(potentialChest.getLocation())) return;

        if (isNearChunkBorder(potentialChest) && !ensureAdjacentChunksLoaded(potentialChest.getChunk())) {
            return;
        }

        if (isValidFarmlandPattern(potentialChest)) {
            registerNewMultiblock(potentialChest.getLocation());
        }
    }

    private void checkAndUnregisterMultiblockFromFarmland(Block farmland) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location potentialChestLoc = farmland.getLocation().add(x, 1, z);
                if (isStructureRegistered(potentialChestLoc) && !isValidFarmlandPattern(potentialChestLoc.getBlock())) {
                    unregisterMultiblock(potentialChestLoc);
                }
            }
        }
    }

    private void handleExplodedBlocks(List<Block> blocks) {
        for (Block block : blocks) {
            if (block.getType() == Material.FARMLAND) {
                checkAndUnregisterMultiblockFromFarmland(block);
            } else if (block.getType() == Material.COMPOSTER) {
                Location chestLocation = block.getLocation().clone().subtract(0, 1, 0);
                if (isStructureRegistered(chestLocation)) {
                    unregisterMultiblock(chestLocation);
                }
            }
        }
    }

    private boolean isNearChunkBorder(Block center) {
        int x = center.getX() & 0xF;
        int z = center.getZ() & 0xF;
        return x <= 4 || x >= 11 || z <= 4 || z >= 11;
    }

    private boolean ensureAdjacentChunksLoaded(Chunk centerChunk) {
        World world = centerChunk.getWorld();
        int cx = centerChunk.getX();
        int cz = centerChunk.getZ();

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (!world.isChunkLoaded(cx + x, cz + z) && !world.loadChunk(cx + x, cz + z, true)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void notifyNearbyPlayers(Location location, String message) {
        location.getWorld().getPlayers().forEach(player -> {
            if (player.getLocation().distance(location) <= 50) {
                player.sendMessage(message +
                        location.getBlockX() + ", " +
                        location.getBlockY() + ", " +
                        location.getBlockZ());
            }
        });
    }
}