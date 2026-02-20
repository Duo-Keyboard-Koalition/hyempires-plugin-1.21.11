package org.duoKeyboardKoalition.hyempires.systems;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.managers.InfluenceManager;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;
import org.duoKeyboardKoalition.hyempires.scanners.VillagerJobScanner;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Village naming system - villagers can only vote when founder is inactive.
 * Founder sets initial name and can rename anytime. Villagers vote only when founder is offline for 7+ days.
 */
public class VillageNamingSystem {
    private final HyEmpiresPlugin plugin;
    private final VillageManager villageManager;
    private final Map<String, VillageNamingData> villageNamingData = new ConcurrentHashMap<>();
    private BukkitTask namingTask;
    
    // Configuration - use same threshold as influence system
    private static final long FOUNDER_INACTIVITY_THRESHOLD_MS = 7L * 24 * 60 * 60 * 1000; // 7 days
    private static final long VOTING_INTERVAL_MS = 300000; // 5 minutes between voting sessions
    private static final long NAME_PROPOSAL_INTERVAL_MS = 600000; // 10 minutes between name proposals
    private static final int MIN_VOTES_TO_CHANGE = 3; // Minimum votes needed to change name
    private static final double VOTE_THRESHOLD_RATIO = 0.4; // 40% of villagers must vote for a name
    
    /**
     * Data structure for tracking naming votes for a village.
     */
    public static class VillageNamingData {
        public String villageName;
        public Map<String, NameProposal> proposals = new ConcurrentHashMap<>(); // name -> proposal
        public long lastProposalTime = 0;
        public long lastVotingTime = 0;
        public Set<UUID> villagersWhoVoted = new HashSet<>();
        
        public VillageNamingData(String villageName) {
            this.villageName = villageName;
        }
    }
    
    /**
     * A name proposal made by a villager.
     */
    public static class NameProposal {
        public String proposedName;
        public UUID proposerUUID; // Villager who proposed it
        public long proposalTime;
        public Map<UUID, Boolean> votes = new ConcurrentHashMap<>(); // UUID -> true (vote) or false (no vote yet)
        public int voteCount = 0;
        
        public NameProposal(String name, UUID proposer) {
            this.proposedName = name;
            this.proposerUUID = proposer;
            this.proposalTime = System.currentTimeMillis();
        }
    }
    
    public VillageNamingSystem(HyEmpiresPlugin plugin, VillageManager villageManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
        startNamingCycle();
    }
    
    /**
     * Start the periodic naming cycle.
     */
    private void startNamingCycle() {
        namingTask = new BukkitRunnable() {
            @Override
            public void run() {
                processNamingCycle();
            }
        }.runTaskTimer(plugin, 200L, 200L); // Every 10 seconds
    }
    
    /**
     * Main cycle: villagers propose names and vote on them ONLY when founder is inactive.
     */
    private void processNamingCycle() {
        long currentTime = System.currentTimeMillis();
        InfluenceManager influenceManager = villageManager.getInfluenceManager();
        if (influenceManager == null) return;
        
        for (VillageManager.VillageData village : villageManager.getAllVillages()) {
            if (!village.active) continue;
            
            // Check if founder is inactive
            boolean founderInactive = isFounderInactive(village, influenceManager);
            
            // Only allow voting if founder has been inactive for 7+ days
            if (!founderInactive) {
                // Founder is active - clear any pending proposals
                VillageNamingData namingData = villageNamingData.get(village.name);
                if (namingData != null) {
                    namingData.proposals.clear();
                }
                continue;
            }
            
            VillageNamingData namingData = villageNamingData.computeIfAbsent(
                village.name, k -> new VillageNamingData(village.name));
            
            // Founder is inactive - allow villagers to propose and vote
            // Check if it's time for villagers to propose new names
            if (currentTime - namingData.lastProposalTime > NAME_PROPOSAL_INTERVAL_MS) {
                proposeNewNames(village, namingData);
                namingData.lastProposalTime = currentTime;
            }
            
            // Check if it's time for voting
            if (currentTime - namingData.lastVotingTime > VOTING_INTERVAL_MS) {
                conductVoting(village, namingData);
                namingData.lastVotingTime = currentTime;
                namingData.villagersWhoVoted.clear();
            }
            
            // Check if any proposal has enough votes to change the name
            checkAndApplyNameChange(village, namingData);
        }
    }
    
    /**
     * Check if founder has been inactive for 7+ days.
     */
    private boolean isFounderInactive(VillageManager.VillageData village, InfluenceManager influenceManager) {
        // Find founder
        List<Map.Entry<UUID, InfluenceManager.InfluenceData>> ranking = 
            influenceManager.getInfluenceRanking(village.name);
        
        for (Map.Entry<UUID, InfluenceManager.InfluenceData> entry : ranking) {
            if (entry.getValue().isFounder) {
                UUID founderUUID = entry.getKey();
                InfluenceManager.InfluenceData founderData = entry.getValue();
                
                // Check if founder has been inactive for threshold
                long inactiveTime = System.currentTimeMillis() - founderData.lastActivity;
                return inactiveTime > FOUNDER_INACTIVITY_THRESHOLD_MS;
            }
        }
        
        // No founder found - allow voting
        return true;
    }
    
    /**
     * Villagers propose new names based on village characteristics.
     */
    private void proposeNewNames(VillageManager.VillageData village, VillageNamingData namingData) {
        List<Villager> villagers = getVillagersInVillage(village);
        if (villagers.isEmpty()) return;
        
        // Generate name suggestions based on village characteristics
        List<String> suggestions = generateNameSuggestions(village, villagers);
        
        // Randomly select a villager to propose a name
        if (!suggestions.isEmpty() && !villagers.isEmpty()) {
            Random random = new Random();
            Villager proposer = villagers.get(random.nextInt(villagers.size()));
            String proposedName = suggestions.get(random.nextInt(suggestions.size()));
            
            // Don't propose the current name
            if (proposedName.equalsIgnoreCase(village.name)) {
                return;
            }
            
            // Create proposal
            NameProposal proposal = new NameProposal(proposedName, proposer.getUniqueId());
            namingData.proposals.put(proposedName, proposal);
            
            // Notify nearby players
            Location bellLoc = village.getAdminLocation();
            if (bellLoc != null) {
                String proposerName = getVillagerName(proposer);
                notifyNearbyPlayers(bellLoc, 
                    "§e" + proposerName + " §7suggests renaming the village to: §6" + proposedName);
            }
        }
    }
    
    /**
     * Generate name suggestions based on village characteristics.
     */
    private List<String> generateNameSuggestions(VillageManager.VillageData village, List<Villager> villagers) {
        List<String> suggestions = new ArrayList<>();
        Random random = new Random(village.name.hashCode() + System.currentTimeMillis() / 10000);
        
        // Analyze village characteristics
        Map<Villager.Profession, Integer> professionCounts = new HashMap<>();
        for (Villager v : villagers) {
            professionCounts.put(v.getProfession(), 
                professionCounts.getOrDefault(v.getProfession(), 0) + 1);
        }
        
        // Find dominant profession
        Villager.Profession dominantProfession = professionCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
        
        // Generate names based on location
        Location bellLoc = village.getAdminLocation();
        if (bellLoc != null) {
            try {
                org.bukkit.NamespacedKey biomeKey = bellLoc.getBlock().getBiome().getKey();
                if (biomeKey != null) {
                    String biomeName = biomeKey.getKey().toLowerCase();
                    String[] biomeWords = biomeName.split("_");
                    if (biomeWords.length > 0) {
                        String biomeWord = capitalize(biomeWords[biomeWords.length - 1]);
                        suggestions.add(biomeWord + "haven");
                        suggestions.add(biomeWord + "ville");
                        suggestions.add("New " + biomeWord);
                    }
                }
            } catch (Exception e) {
                // Fallback if biome key not available
                suggestions.add("Settlement");
                suggestions.add("Village");
            }
        }
        
        // Generate names based on profession
        if (dominantProfession != null) {
            String professionName = getProfessionDisplayName(dominantProfession);
            suggestions.add(professionName + "ton");
            suggestions.add(professionName + "ville");
            suggestions.add("The " + professionName + "s' Rest");
        }
        
        // Generate names based on population
        if (village.population >= 10) {
            suggestions.add("Grand Settlement");
            suggestions.add("Great Village");
        } else if (village.population >= 5) {
            suggestions.add("Prosperous Town");
        }
        
        // Generate names based on coordinates (for uniqueness)
        if (bellLoc != null) {
            int x = bellLoc.getBlockX();
            int z = bellLoc.getBlockZ();
            suggestions.add("Village " + Math.abs(x % 100) + "-" + Math.abs(z % 100));
        }
        
        // Add some generic village names
        String[] genericNames = {
            "Harmony", "Tranquil", "Prosper", "Unity", "Hope", 
            "Peace", "Serenity", "Bloom", "Dawn", "Twilight"
        };
        suggestions.addAll(Arrays.asList(genericNames));
        
        return suggestions;
    }
    
    /**
     * Conduct voting session - villagers vote on proposed names.
     * Villagers respect players with higher influence and are more likely to vote for their proposals.
     */
    private void conductVoting(VillageManager.VillageData village, VillageNamingData namingData) {
        if (namingData.proposals.isEmpty()) return;
        
        List<Villager> villagers = getVillagersInVillage(village);
        if (villagers.isEmpty()) return;
        
        // Calculate influence weights for each proposal
        Map<String, Double> proposalWeights = new HashMap<>();
        InfluenceManager influenceManager = villageManager.getInfluenceManager();
        
        for (NameProposal proposal : namingData.proposals.values()) {
            double weight = 1.0; // Base weight
            
            // If proposed by a player (not a villager), check their influence
            if (proposal.proposerUUID != null) {
                // Check if it's a player UUID (players have different UUID format)
                org.bukkit.entity.Player proposerPlayer = plugin.getServer().getPlayer(proposal.proposerUUID);
                if (proposerPlayer != null && influenceManager != null) {
                    // Player proposed it - weight based on influence
                    double influence = influenceManager.getInfluence(village.name, proposal.proposerUUID);
                    // Influence weight: 1.0 base + (influence / 50) bonus
                    // Example: 100 influence = 1.0 + 2.0 = 3.0x weight
                    weight = 1.0 + (influence / 50.0);
                    
                    // Founders get extra respect
                    InfluenceManager.InfluenceData data = influenceManager.getInfluenceRanking(village.name).stream()
                        .filter(e -> e.getKey().equals(proposal.proposerUUID))
                        .findFirst()
                        .map(Map.Entry::getValue)
                        .orElse(null);
                    if (data != null && data.isFounder) {
                        weight *= 1.5; // Founders get 50% more respect
                    }
                }
            }
            
            proposalWeights.put(proposal.proposedName, weight);
        }
        
        // Each villager votes on proposals (weighted by proposer influence)
        for (Villager villager : villagers) {
            UUID uuid = villager.getUniqueId();
            if (namingData.villagersWhoVoted.contains(uuid)) continue;
            
            List<NameProposal> proposals = new ArrayList<>(namingData.proposals.values());
            if (proposals.isEmpty()) continue;
            
            // Random chance to vote (villagers don't always vote)
            Random random = new Random(uuid.hashCode() + System.currentTimeMillis());
            if (random.nextDouble() < 0.6) { // 60% chance to vote
                // Weighted random selection based on proposer influence
                NameProposal selectedProposal = selectWeightedProposal(proposals, proposalWeights, random);
                if (selectedProposal != null) {
                    selectedProposal.votes.put(uuid, true);
                    selectedProposal.voteCount++;
                    namingData.villagersWhoVoted.add(uuid);
                }
            }
        }
    }
    
    /**
     * Select a proposal weighted by proposer influence.
     */
    private NameProposal selectWeightedProposal(List<NameProposal> proposals, 
                                                Map<String, Double> weights, Random random) {
        if (proposals.isEmpty()) return null;
        
        // Calculate total weight
        double totalWeight = 0.0;
        for (NameProposal proposal : proposals) {
            totalWeight += weights.getOrDefault(proposal.proposedName, 1.0);
        }
        
        if (totalWeight <= 0) {
            // Fallback to random if no weights
            return proposals.get(random.nextInt(proposals.size()));
        }
        
        // Select weighted random
        double randomValue = random.nextDouble() * totalWeight;
        double currentWeight = 0.0;
        
        for (NameProposal proposal : proposals) {
            currentWeight += weights.getOrDefault(proposal.proposedName, 1.0);
            if (randomValue <= currentWeight) {
                return proposal;
            }
        }
        
        // Fallback
        return proposals.get(proposals.size() - 1);
    }
    
    /**
     * Check if any proposal has enough votes and apply name change.
     */
    private void checkAndApplyNameChange(VillageManager.VillageData village, VillageNamingData namingData) {
        List<Villager> villagers = getVillagersInVillage(village);
        int totalVillagers = villagers.size();
        if (totalVillagers == 0) return;
        
        for (NameProposal proposal : new ArrayList<>(namingData.proposals.values())) {
            // Check if proposal has enough votes
            int requiredVotes = Math.max(MIN_VOTES_TO_CHANGE, 
                (int) Math.ceil(totalVillagers * VOTE_THRESHOLD_RATIO));
            
            if (proposal.voteCount >= requiredVotes) {
                // Change the village name!
                String oldName = village.name;
                String newName = proposal.proposedName;
                
                // Update village name (use internal rename method)
                villageManager.renameVillage(village, newName);
                
                // Update naming data key
                villageNamingData.remove(oldName);
                namingData.villageName = newName;
                villageNamingData.put(newName, namingData);
                
                // Update managers
                if (villageManager.getInfluenceManager() != null) {
                    villageManager.getInfluenceManager().renameVillage(oldName, newName);
                }
                if (villageManager.getChunkTerritoryManager() != null) {
                    villageManager.getChunkTerritoryManager().renameVillage(oldName, newName);
                }
                
                // Clear old proposals
                namingData.proposals.clear();
                
                // Notify everyone
                Location bellLoc = village.getAdminLocation();
                if (bellLoc != null) {
                    notifyNearbyPlayers(bellLoc, 
                        "§6§lThe villagers have voted! The village is now called: §f" + newName);
                }
                
                plugin.getLogger().info("Village '" + oldName + "' renamed to '" + newName + "' by villager vote!");
                break; // Only change to one name at a time
            }
        }
        
        // Remove old proposals (older than 30 minutes)
        long expireTime = System.currentTimeMillis() - 1800000; // 30 minutes
        namingData.proposals.entrySet().removeIf(entry -> 
            entry.getValue().proposalTime < expireTime);
    }
    
    /**
     * Get villagers that belong to this village (have both bed and workplace in the village).
     */
    private List<Villager> getVillagersInVillage(VillageManager.VillageData village) {
        return plugin.getResidentsInVillage(village);
    }
    
    /**
     * Get villager display name.
     */
    private String getVillagerName(Villager villager) {
        VillagerJobScanner scanner = plugin.getVillagerScanner();
        if (scanner != null) {
            VillagerJobScanner.VillagerData data = scanner.getVillagerData().get(villager.getUniqueId());
            if (data != null && data.name != null) {
                return data.name;
            }
        }
        return "A villager";
    }
    
    /**
     * Get profession display name.
     */
    private String getProfessionDisplayName(Villager.Profession profession) {
        if (profession == null) return "Villager";
        try {
            String key = profession.getKey().getKey();
            return capitalize(key.replace("minecraft:", ""));
        } catch (Exception e) {
            return "Villager";
        }
    }
    
    /**
     * Capitalize first letter of string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
    
    /**
     * Notify nearby players.
     */
    private void notifyNearbyPlayers(Location location, String message) {
        if (location.getWorld() == null) return;
        location.getWorld().getPlayers().forEach(player -> {
            if (player.getLocation().distance(location) <= 50) {
                player.sendMessage(message);
            }
        });
    }
    
    /**
     * Allow a player to propose a name (for player interaction).
     */
    public boolean proposeName(String villageName, String proposedName, Player player) {
        VillageManager.VillageData village = villageManager.getAllVillages().stream()
            .filter(v -> v.active && v.name.equals(villageName))
            .findFirst()
            .orElse(null);
        
        if (village == null) return false;
        
        VillageNamingData namingData = villageNamingData.computeIfAbsent(
            villageName, k -> new VillageNamingData(villageName));
        
        // Check if name already proposed
        if (namingData.proposals.containsKey(proposedName)) {
            player.sendMessage("§eThis name has already been proposed!");
            return false;
        }
        
        // Create proposal (using player UUID as proposer)
        NameProposal proposal = new NameProposal(proposedName, player.getUniqueId());
        namingData.proposals.put(proposedName, proposal);
        
        // Notify nearby players
        Location bellLoc = village.getAdminLocation();
        if (bellLoc != null) {
            notifyNearbyPlayers(bellLoc, 
                "§e" + player.getName() + " §7proposes renaming the village to: §6" + proposedName);
        }
        
        player.sendMessage("§aName proposal submitted! Villagers will vote on it.");
        return true;
    }
    
    /**
     * Get current proposals for a village.
     */
    public Map<String, NameProposal> getProposals(String villageName) {
        VillageNamingData namingData = villageNamingData.get(villageName);
        return namingData != null ? namingData.proposals : new HashMap<>();
    }
    
    /**
     * Shutdown the naming system.
     */
    public void shutdown() {
        if (namingTask != null && !namingTask.isCancelled()) {
            namingTask.cancel();
        }
    }
}
