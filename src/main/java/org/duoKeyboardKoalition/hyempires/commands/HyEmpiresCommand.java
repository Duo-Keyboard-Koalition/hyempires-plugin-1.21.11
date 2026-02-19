package org.duoKeyboardKoalition.hyempires.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.duoKeyboardKoalition.hyempires.HyEmpiresPlugin;
import org.duoKeyboardKoalition.hyempires.managers.CampsiteManager;
import org.duoKeyboardKoalition.hyempires.managers.VillageManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles /hyempires command and subcommands.
 */
public class HyEmpiresCommand implements CommandExecutor, TabCompleter {

    private final HyEmpiresPlugin plugin;
    private final VillageManager villageManager;
    private final CampsiteManager campsiteManager;

    public HyEmpiresCommand(HyEmpiresPlugin plugin) {
        this.plugin = plugin;
        this.villageManager = plugin.getVillageManager();
        this.campsiteManager = plugin.getCampsiteManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                sendHelp(sender);
                return true;
            case "village":
                return handleVillage(sender, args);
            case "campsite":
            case "camp":
                return handleCampsite(sender, args);
            case "tool":
            case "boundary":
                return handleTool(sender);
            case "villager":
                return handleVillager(sender, args);
            case "reload":
                return handleReload(sender);
            default:
                sender.sendMessage("§cUnknown subcommand. Use §f/hyempires help §cfor usage.");
                return true;
        }
    }

    private boolean handleVillage(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command must be run by a player.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage("§eUsage: §f/hyempires village <list|info|refresh|influence> [name]");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "list":
                listVillages(player);
                return true;
            case "info":
                return villageInfo(player, args);
            case "refresh":
                return villageRefresh(player, args);
            case "influence":
                return villageInfluence(player, args);
            default:
                player.sendMessage("§cUnknown village action. Use §flist§c, §finfo§c, §frefresh§c, or §finfluence§c.");
                return true;
        }
    }

    private void listVillages(Player player) {
        var villages = villageManager.getAllVillages().stream()
                .filter(v -> v.active)
                .collect(Collectors.toList());
        if (villages.isEmpty()) {
            player.sendMessage("§eNo villages found.");
            return;
        }
        player.sendMessage("§6=== Villages === §7(" + villages.size() + ")");
        for (VillageManager.VillageData v : villages) {
            String ownerStr = v.owner != null && v.owner.equals(player.getUniqueId()) ? "§a(you)" : "";
            player.sendMessage("§f" + v.name + " §7- Pop: " + v.population + " §7@ " + v.adminX + "," + v.adminY + "," + v.adminZ + " " + ownerStr);
        }
    }

    private boolean villageInfo(Player player, String[] args) {
        VillageManager.VillageData village;
        if (args.length >= 3) {
            String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            village = villageManager.getAllVillages().stream()
                    .filter(v -> v.active && v.name.equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
            if (village == null) {
                player.sendMessage("§cVillage not found: " + name);
                return true;
            }
        } else {
            village = villageManager.getVillageContaining(player.getLocation());
            if (village == null) {
                player.sendMessage("§cStand inside a village (within 48 blocks of a bell) or use §f/hyempires village info <name>");
                return true;
            }
        }
        player.sendMessage(villageManager.getVillageInfo(village, player));
        return true;
    }

    private boolean villageRefresh(Player player, String[] args) {
        VillageManager.VillageData village;
        if (args.length >= 3) {
            String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            village = villageManager.getAllVillages().stream()
                    .filter(v -> v.active && v.name.equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
            if (village == null) {
                player.sendMessage("§cVillage not found: " + name);
                return true;
            }
        } else {
            village = villageManager.getVillageContaining(player.getLocation());
            if (village == null) {
                player.sendMessage("§cStand inside a village or use §f/hyempires village refresh <name>");
                return true;
            }
        }
        if (!villageManager.canAdminister(player, village)) {
            player.sendMessage("§cYou cannot administer this village.");
            return true;
        }
        villageManager.updatePopulation(village);
        player.sendMessage("§aPopulation updated: " + village.population + " villagers");
        return true;
    }

    private boolean handleCampsite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command must be run by a player.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage("§eUsage: §f/hyempires campsite <list|info> [name]");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "list":
                listCampsites(player);
                return true;
            case "info":
                return campsiteInfo(player, args);
            default:
                player.sendMessage("§cUnknown campsite action. Use §flist§c or §finfo§c.");
                return true;
        }
    }

    private void listCampsites(Player player) {
        var campsites = campsiteManager.getAllCampsites().stream()
                .filter(c -> c.active)
                .collect(Collectors.toList());
        if (campsites.isEmpty()) {
            player.sendMessage("§eNo campsites found.");
            return;
        }
        player.sendMessage("§6=== Campsites === §7(" + campsites.size() + ")");
        for (CampsiteManager.CampsiteData c : campsites) {
            String ownerStr = c.owner != null && c.owner.equals(player.getUniqueId()) ? "§a(you)" : "";
            player.sendMessage("§f" + c.name + " §7@ " + c.x + "," + c.y + "," + c.z + " " + ownerStr);
        }
    }

    private boolean campsiteInfo(Player player, String[] args) {
        CampsiteManager.CampsiteData campsite;
        if (args.length >= 3) {
            String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            campsite = campsiteManager.getAllCampsites().stream()
                    .filter(c -> c.active && c.name.equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
            if (campsite == null) {
                player.sendMessage("§cCampsite not found: " + name);
                return true;
            }
        } else {
            Location loc = player.getLocation();
            campsite = campsiteManager.getCampsiteAt(loc);
            if (campsite == null) {
                player.sendMessage("§cStand on the campsite campfire block or use §f/hyempires campsite info <name>");
                return true;
            }
        }
        player.sendMessage("§6=== Campsite Info ===");
        player.sendMessage("§eName: §f" + campsite.name);
        player.sendMessage("§eOwner: §f" + (campsite.owner != null ? campsite.owner.toString() : "Unowned"));
        player.sendMessage("§eLocation: §f" + campsite.x + ", " + campsite.y + ", " + campsite.z);
        player.sendMessage("§eStatus: §f" + (campsite.active ? "§aActive" : "§cInactive"));
        return true;
    }

    private boolean handleTool(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command must be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        
        // Give player the boundary tool
        org.bukkit.inventory.ItemStack tool = org.duoKeyboardKoalition.hyempires.Listener.BoundaryToolListener.createBoundaryTool();
        player.getInventory().addItem(tool);
        player.sendMessage("§aYou received a Village Boundary Tool!");
        player.sendMessage("§7Right-click chunks to claim, Left-click to unclaim");
        player.sendMessage("§7Shift+Right-click to see chunk info");
        return true;
    }
    
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("hyempires.reload") && !sender.isOp()) {
            sender.sendMessage("§cYou do not have permission to reload HyEmpires.");
            return true;
        }
        villageManager.scanLoadedChunks();
        campsiteManager.scanLoadedChunks();
        plugin.getVillagerScanner().scanAllVillagers();
        sender.sendMessage("§aHyEmpires data rescanned.");
        return true;
    }

    private boolean villageInfluence(Player player, String[] args) {
        VillageManager.VillageData village;
        if (args.length >= 3) {
            String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            village = villageManager.getAllVillages().stream()
                    .filter(v -> v.active && v.name.equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
            if (village == null) {
                player.sendMessage("§cVillage not found: " + name);
                return true;
            }
        } else {
            village = villageManager.getVillageContaining(player.getLocation());
            if (village == null) {
                player.sendMessage("§cStand inside a village or use §f/hyempires village influence <name>");
                return true;
            }
        }
        
        if (plugin.getInfluenceManager() == null) {
            player.sendMessage("§cInfluence system not available.");
            return true;
        }
        
        List<Map.Entry<UUID, org.duoKeyboardKoalition.hyempires.managers.InfluenceManager.InfluenceData>> ranking = 
                plugin.getInfluenceManager().getInfluenceRanking(village.name);
        
        if (ranking.isEmpty()) {
            player.sendMessage("§eNo influence data for this village.");
            return true;
        }
        
        player.sendMessage("§6=== Influence Ranking: " + village.name + " ===");
        int rank = 1;
        for (Map.Entry<UUID, org.duoKeyboardKoalition.hyempires.managers.InfluenceManager.InfluenceData> entry : ranking) {
            String founderTag = entry.getValue().isFounder ? " §6[Founder]" : "";
            String youTag = entry.getKey().equals(player.getUniqueId()) ? " §a(You)" : "";
            long hoursSinceActivity = (System.currentTimeMillis() - entry.getValue().lastActivity) / (60 * 60 * 1000);
            String activityStr = hoursSinceActivity < 1 ? "Active now" : hoursSinceActivity + "h ago";
            
            player.sendMessage(String.format("§7%d. §f%s §7- §e%.1f §7influence §7(%s)%s%s",
                    rank, entry.getKey().toString().substring(0, 8), entry.getValue().influence, activityStr, founderTag, youTag));
            rank++;
            if (rank > 10) break; // Top 10
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== HyEmpires §eCommands ===");
        sender.sendMessage("§f/hyempires help §7- Show this help");
        sender.sendMessage("§f/hyempires village list §7- List all villages");
        sender.sendMessage("§f/hyempires village info [name] §7- Village info (stand in village or give name)");
        sender.sendMessage("§f/hyempires village refresh [name] §7- Refresh population");
        sender.sendMessage("§f/hyempires village influence [name] §7- Show influence ranking");
        sender.sendMessage("§f/hyempires campsite list §7- List all campsites");
        sender.sendMessage("§f/hyempires campsite info [name] §7- Campsite info");
        sender.sendMessage("§f/hyempires tool §7- Get boundary drawing tool");
        sender.sendMessage("§f/hyempires reload §7- Rescan data (OP)");
        sender.sendMessage("§7Block actions: Place §fBell §7for village, §fCampfire §7on grass for campsite.");
        sender.sendMessage("§7Note: Villages use chunk-based territory - claim chunks with power to expand!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : Arrays.asList("help", "village", "campsite", "camp", "villager", "tool", "boundary", "reload")) {
                if (sub.startsWith(partial)) completions.add(sub);
            }
            return completions;
        }
        if (args.length == 2 && "villager".equalsIgnoreCase(args[0])) {
            String partial = args[1].toLowerCase();
            for (String a : Arrays.asList("info", "assign")) {
                if (a.startsWith(partial)) completions.add(a);
            }
            return completions;
        }
        if (args.length == 2) {
            if ("village".equalsIgnoreCase(args[0])) {
                String partial = args[1].toLowerCase();
                for (String a : Arrays.asList("list", "info", "refresh", "influence")) {
                    if (a.startsWith(partial)) completions.add(a);
                }
                return completions;
            }
            if ("campsite".equalsIgnoreCase(args[0]) || "camp".equalsIgnoreCase(args[0])) {
                String partial = args[1].toLowerCase();
                for (String a : Arrays.asList("list", "info")) {
                    if (a.startsWith(partial)) completions.add(a);
                }
                return completions;
            }
        }
        if (args.length == 3 && ("info".equalsIgnoreCase(args[1]) || "refresh".equalsIgnoreCase(args[1]))) {
            if ("village".equalsIgnoreCase(args[0])) {
                String partial = args[2].toLowerCase();
                villageManager.getAllVillages().stream()
                        .filter(v -> v.active && v.name.toLowerCase().startsWith(partial))
                        .forEach(v -> completions.add(v.name));
                return completions;
            }
            if ("campsite".equalsIgnoreCase(args[0]) || "camp".equalsIgnoreCase(args[0])) {
                String partial = args[2].toLowerCase();
                campsiteManager.getAllCampsites().stream()
                        .filter(c -> c.active && c.name.toLowerCase().startsWith(partial))
                        .forEach(c -> completions.add(c.name));
                return completions;
            }
        }
        return completions;
    }
}
