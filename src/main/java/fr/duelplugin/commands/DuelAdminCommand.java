package fr.duelplugin.commands;

import fr.duelplugin.DuelPlugin;
import fr.duelplugin.managers.LanguageManager;
import fr.duelplugin.managers.ReportManager;
import fr.duelplugin.models.Arena;
import fr.duelplugin.models.DuelGameMode;
import fr.duelplugin.models.Rank;
import fr.duelplugin.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DuelAdminCommand implements CommandExecutor, TabCompleter, Listener {

    private final DuelPlugin plugin;

    public DuelAdminCommand(DuelPlugin plugin) {
        this.plugin = plugin;
    }

    private LanguageManager lang() {
        return plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang().msgRaw(null, "command_only_players"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!player.hasPermission("duelplugin.admin.reload")) {
                    player.sendMessage(lang().msg(player, "no_permission"));
                    return true;
                }
                plugin.getArenaManager().loadArenas();
                plugin.getLobbyManager().loadLobby();
                plugin.getChatFilterManager().loadFilter();
                player.sendMessage(lang().msg(player, "arena_config_reloaded"));
            }
            case "setlobby" -> {
                if (!player.hasPermission("duelplugin.admin.setlobby")) {
                    player.sendMessage(lang().msg(player, "no_permission"));
                    return true;
                }
                plugin.getLobbyManager().setLobby(player.getLocation());
                player.sendMessage(lang().msg(player, "arena_config_reloaded"));
            }
            case "arena" -> {
                if (!player.hasPermission("duelplugin.admin.arena")) {
                    player.sendMessage(lang().msg(player, "no_permission"));
                    return true;
                }
                handleArenaCommand(player, args);
            }
            case "lobby" -> {
                if (!player.hasPermission("duelplugin.admin.lobby")) {
                    player.sendMessage(lang().msg(player, "no_permission"));
                    return true;
                }
                handleLobbyCommand(player, args);
            }
            case "report" -> {
                if (!player.hasPermission("duelplugin.admin.report")) {
                    player.sendMessage(lang().msg(player, "no_permission"));
                    return true;
                }
                handleReportCommand(player, args);
            }
            case "rank" -> {
                if (!player.hasPermission("duelplugin.admin.rank")) {
                    player.sendMessage(lang().msg(player, "no_permission"));
                    return true;
                }
                handleRankCommand(player, args);
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleArenaCommand(Player player, String[] args) {
        if (args.length < 2) {
            sendArenaHelp(player);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "create" -> {
                if (args.length < 4) {
                    player.sendMessage(lang().msg(player, "arena_usage_create"));
                    return;
                }
                String name = args[2];
                DuelGameMode mode = DuelGameMode.fromName(args[3]);
                if (mode == null) {
                    player.sendMessage(lang().msg(player, "arena_unknown_mode") +
                            Arrays.stream(DuelGameMode.values()).map(DuelGameMode::getDisplayName).collect(Collectors.joining(", ")));
                    return;
                }
                if (plugin.getArenaManager().createArena(name, mode)) {
                    player.sendMessage(lang().msg(player, "arena_created", "%name%", name, "%mode%", mode.getColoredName()));
                } else {
                    player.sendMessage(lang().msg(player, "arena_already_exists"));
                }
            }
            case "delete" -> {
                if (args.length < 3) {
                    player.sendMessage(lang().msg(player, "arena_usage_delete"));
                    return;
                }
                if (plugin.getArenaManager().deleteArena(args[2])) {
                    player.sendMessage(lang().msg(player, "arena_deleted", "%name%", args[2]));
                } else {
                    player.sendMessage(lang().msg(player, "arena_not_found"));
                }
            }
            case "setspawn" -> {
                if (args.length < 4) {
                    player.sendMessage(lang().msg(player, "arena_usage_setspawn"));
                    return;
                }
                Arena arena = plugin.getArenaManager().getArena(args[2]);
                if (arena == null) {
                    player.sendMessage(lang().msg(player, "arena_not_found"));
                    return;
                }
                int slot;
                try {
                    slot = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    player.sendMessage(lang().msg(player, "arena_invalid_slot"));
                    return;
                }
                Location loc = player.getLocation();
                if (slot == 1) {
                    arena.setSpawn1(loc);
                } else if (slot == 2) {
                    arena.setSpawn2(loc);
                } else {
                    player.sendMessage(lang().msg(player, "arena_invalid_slot"));
                    return;
                }
                plugin.getArenaManager().saveArenas();
                player.sendMessage(lang().msg(player, "arena_spawn_set", "%slot%", String.valueOf(slot), "%name%", arena.getName()));
            }
            case "setmin" -> {
                if (args.length < 3) {
                    player.sendMessage(lang().msg(player, "arena_usage_setmin"));
                    return;
                }
                Arena arena = plugin.getArenaManager().getArena(args[2]);
                if (arena == null) {
                    player.sendMessage(lang().msg(player, "arena_not_found"));
                    return;
                }
                arena.setMinCorner(player.getLocation());
                plugin.getArenaManager().saveArenas();
                player.sendMessage(lang().msg(player, "arena_min_set", "%name%", arena.getName()));
            }
            case "setmax" -> {
                if (args.length < 3) {
                    player.sendMessage(lang().msg(player, "arena_usage_setmax"));
                    return;
                }
                Arena arena = plugin.getArenaManager().getArena(args[2]);
                if (arena == null) {
                    player.sendMessage(lang().msg(player, "arena_not_found"));
                    return;
                }
                arena.setMaxCorner(player.getLocation());
                plugin.getArenaManager().saveArenas();
                player.sendMessage(lang().msg(player, "arena_max_set", "%name%", arena.getName()));
            }
            case "info" -> {
                if (args.length < 3) {
                    player.sendMessage(lang().msg(player, "arena_usage_info"));
                    return;
                }
                Arena arena = plugin.getArenaManager().getArena(args[2]);
                if (arena == null) {
                    player.sendMessage(lang().msg(player, "arena_not_found"));
                    return;
                }
                player.sendMessage("§5═══════════════════════");
                player.sendMessage(lang().msgRaw(player, "arena_info_name", "%name%", arena.getName()));
                player.sendMessage("§dMode: " + arena.getGameMode().getColoredName());
                player.sendMessage("§dSpawn 1: " + formatLoc(arena.resolveSpawn1()));
                player.sendMessage("§dSpawn 2: " + formatLoc(arena.resolveSpawn2()));
                player.sendMessage("§dMin: " + formatLoc(arena.resolveMinCorner()));
                player.sendMessage("§dMax: " + formatLoc(arena.resolveMaxCorner()));
                player.sendMessage("§dConfigured: " + (arena.isSetup() ? lang().msgRaw(player, "arena_configured") : lang().msgRaw(player, "arena_not_configured")));
                player.sendMessage("§5═══════════════════════");
            }
            case "tp" -> {
                if (args.length < 3) {
                    player.sendMessage(lang().msg(player, "arena_usage_tp"));
                    return;
                }
                Arena arena = plugin.getArenaManager().getArena(args[2]);
                if (arena == null) {
                    player.sendMessage(lang().msg(player, "arena_not_found"));
                    return;
                }
                Location spawn = arena.resolveSpawn1();
                if (spawn == null) {
                    player.sendMessage(lang().msg(player, "arena_no_spawn", "%name%", arena.getName()));
                    return;
                }
                player.teleport(spawn);
                player.sendMessage(lang().msg(player, "arena_teleported", "%name%", arena.getName()));
            }
            case "list" -> {
                var arenas = plugin.getArenaManager().getAllArenas();
                if (arenas.isEmpty()) {
                    player.sendMessage(lang().msg(player, "arena_list_empty"));
                    return;
                }
                player.sendMessage("§5═══════════════════════");
                player.sendMessage(lang().msgRaw(player, "arena_list_header"));
                for (Arena arena : arenas) {
                    player.sendMessage(lang().msgRaw(player, "arena_list_entry", "%name%", arena.getName(), "%mode%", arena.getGameMode().getColoredName()));
                }
                player.sendMessage("§5═══════════════════════");
            }
            default -> sendArenaHelp(player);
        }
    }

    private void handleLobbyCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§5═══════════════════════");
            player.sendMessage("§d§lLobby commands:");
            player.sendMessage("");
            player.sendMessage("§d/da lobby build §7- Toggle build mode");
            player.sendMessage("§5═══════════════════════");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "build" -> {
                boolean wasBuild = plugin.isBuildMode(player.getUniqueId());
                if (wasBuild) {
                    plugin.setBuildMode(player.getUniqueId(), false);
                    fr.duelplugin.listeners.PlayerListener.giveLobbyItems(player);
                    player.sendMessage(lang().msg(player, "lobby_build_mode_disabled"));
                } else {
                    plugin.setBuildMode(player.getUniqueId(), true);
                    player.getInventory().clear();
                    player.getInventory().setArmorContents(null);
                    player.getInventory().setItemInOffHand(null);
                    player.sendMessage(lang().msg(player, "lobby_build_mode_enabled"));
                }
            }
            default -> {
                player.sendMessage("§5═══════════════════════");
                player.sendMessage("§d§lLobby commands:");
                player.sendMessage("");
                player.sendMessage("§d/da lobby build §7- Toggle build mode");
                player.sendMessage("§5═══════════════════════");
            }
        }
    }

    private void handleReportCommand(Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("close")) {
            if (args.length < 3) { player.sendMessage(lang().msg(player, "report_usage_admin")); return; }
            try {
                int id = Integer.parseInt(args[2]);
                ReportManager.Report report = plugin.getReportManager().getReport(id);
                if (report == null) { player.sendMessage(lang().msg(player, "report_not_found")); return; }
                plugin.getReportManager().closeReport(id);
                player.sendMessage(lang().msg(player, "report_closed", "%id%", String.valueOf(id)));
            } catch (NumberFormatException e) { player.sendMessage(lang().msg(player, "report_usage_admin")); }
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("delete")) {
            if (args.length < 3) { player.sendMessage(lang().msg(player, "report_usage_admin")); return; }
            try {
                int id = Integer.parseInt(args[2]);
                ReportManager.Report report = plugin.getReportManager().getReport(id);
                if (report == null) { player.sendMessage(lang().msg(player, "report_not_found")); return; }
                plugin.getReportManager().deleteReport(id);
                player.sendMessage(lang().msg(player, "report_deleted", "%id%", String.valueOf(id)));
            } catch (NumberFormatException e) { player.sendMessage(lang().msg(player, "report_usage_admin")); }
            return;
        }
        openReportGUI(player);
    }

    private void openReportGUI(Player player) {
        List<ReportManager.Report> allReports = plugin.getReportManager().getAllReports();
        int openCount = plugin.getReportManager().getOpenReportCount();
        Inventory inv = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text("\u00A75\u00A7lReports \u00A77(" + openCount + " open)", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }
        int slot = 10;
        for (ReportManager.Report report : allReports) {
            if (slot >= 45) break;
            Material icon = report.getStatus().equals("open") ? Material.PAPER : Material.BARRIER;
            String statusText = report.getStatus().equals("open") ? "\u00A7cOpen" : "\u00A77Closed";
            long diff = System.currentTimeMillis() - report.getTimestamp();
            String timeAgo = formatTimeAgo(diff);
            inv.setItem(slot, new ItemBuilder(icon)
                    .name("\u00A7d#" + report.getId() + " \u00A77- \u00A7c" + report.getReported())
                    .lore("", "\u00A77Reported by: \u00A7f" + report.getReporter(),
                            "\u00A77Reason: \u00A7f" + report.getReason(),
                            "\u00A77Date: \u00A7f" + timeAgo,
                            "\u00A77Status: " + statusText, "",
                            report.getStatus().equals("open") ? "\u00A7aClick to close" : "\u00A77Closed")
                    .build());
            slot++;
            if (slot == 17) slot = 19;
            else if (slot == 26) slot = 28;
            else if (slot == 35) slot = 37;
        }
        if (allReports.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("\u00A7aNo reports").lore("", "\u00A77No pending reports").build());
        }
        inv.setItem(49, new ItemBuilder(Material.ARROW).name("\u00A7dBack").lore("", "\u00A77Back to admin menu").build());
        player.openInventory(inv);
    }

    private String formatTimeAgo(long diffMs) {
        long s = diffMs / 1000;
        if (s < 60) return s + "s";
        long m = s / 60;
        if (m < 60) return m + "m";
        long h = m / 60;
        if (h < 24) return h + "h";
        return (h / 24) + "d";
    }

    @EventHandler
    public void onReportGUIClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("duelplugin.admin.report")) return;
        String title = event.getView().getTitle();
        if (!title.contains("Reports")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;
        if (slot == 49) { player.closeInventory(); return; }
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR || item.getType() == Material.BLACK_STAINED_GLASS_PANE || item.getType() == Material.LIME_STAINED_GLASS_PANE) return;
        String name = item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : "";
        if (name.contains("#")) {
            try {
                String idStr = name.replaceAll("\u00A7[0-9a-fk-or]", "").replace("#", "").trim().split(" ")[0];
                int id = Integer.parseInt(idStr);
                ReportManager.Report report = plugin.getReportManager().getReport(id);
                if (report != null && report.getStatus().equals("open")) {
                    plugin.getReportManager().closeReport(id);
                    player.sendMessage(lang().msg(player, "report_closed", "%id%", String.valueOf(id)));
                    openReportGUI(player);
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    private void handleRankCommand(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§5═══════════════════════");
            player.sendMessage("§d§lRank management:");
            player.sendMessage("");
            player.sendMessage("§d/da rank <admin|manager|moderateur|vip> add <player>");
            player.sendMessage("§d/da rank <admin|manager|moderateur|vip> remove <player>");
            player.sendMessage("§5═══════════════════════");
            return;
        }

        Rank rank = Rank.fromId(args[1]);
        if (rank == null) {
            player.sendMessage("§cInvalid rank. Ranks: ADMIN, MANAGER, MODERATOR, VIP");
            return;
        }

        String action = args[2].toLowerCase();
        if (!action.equals("add") && !action.equals("remove")) {
            player.sendMessage("§cUsage: /da rank <rank> add|remove <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[3]);
        if (target == null) {
            player.sendMessage(lang().msg(player, "player_not_found"));
            return;
        }

        if (action.equals("add")) {
            plugin.getRankManager().setRank(target.getUniqueId(), rank);
            player.sendMessage(rank.getColor() + rank.getDisplayName() + " §agiven to §d" + target.getName() + "§a!");
            target.sendMessage(plugin.colorize(rank.getColor() + "You received the rank " + rank.getDisplayName() + "§r!"));
        } else {
            plugin.getRankManager().setRank(target.getUniqueId(), null);
            player.sendMessage("§cRank removed from §d" + target.getName() + "§c.");
            target.sendMessage("§cYour rank has been removed.");
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§5═══════════════════════");
        player.sendMessage("§d§l" + plugin.getConfig().getString("server-info.name", "Duel Plugin") + " §7- Admin");
        player.sendMessage("");
        player.sendMessage("§d/da reload §7- Reload the config");
        player.sendMessage("§d/da setlobby §7- Set the lobby");
        player.sendMessage("§d/da arena <cmd> §7- Arena management");
        player.sendMessage("§d/da lobby <cmd> §7- Lobby management");
        player.sendMessage("§d/da report §7- Manage reports");
        player.sendMessage("§d/da rank <rank> add/remove <player> §7- Manage ranks");
        player.sendMessage("§5═══════════════════════");
    }

    private void sendArenaHelp(Player player) {
        player.sendMessage("§5═══════════════════════");
        player.sendMessage("§d§lArena management:");
        player.sendMessage("");
        player.sendMessage("§d/da arena create <name> <mode>");
        player.sendMessage("§d/da arena delete <name>");
        player.sendMessage("§d/da arena setspawn <name> <1|2>");
        player.sendMessage("§d/da arena setmin <name>");
        player.sendMessage("§d/da arena setmax <name>");
        player.sendMessage("§d/da arena info <name>");
        player.sendMessage("§d/da arena tp <name>");
        player.sendMessage("§d/da arena list");
        player.sendMessage("§5═══════════════════════");
    }

    private String formatLoc(Location loc) {
        if (loc == null) return "§cNot set";
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
        return String.format("§f%s §7(§f%.1f, %.1f, %.1f§7)", worldName, loc.getX(), loc.getY(), loc.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("duelplugin.admin.reload")) completions.add("reload");
            if (sender.hasPermission("duelplugin.admin.setlobby")) completions.add("setlobby");
            if (sender.hasPermission("duelplugin.admin.arena")) completions.add("arena");
            if (sender.hasPermission("duelplugin.admin.lobby")) completions.add("lobby");
            if (sender.hasPermission("duelplugin.admin.report")) completions.add("report");
            if (sender.hasPermission("duelplugin.admin.rank")) completions.add("rank");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("rank")) {
            completions.addAll(Arrays.asList("admin", "manager", "moderateur", "vip"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("rank")) {
            completions.addAll(Arrays.asList("add", "remove"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("rank")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("arena")) {
            completions.addAll(Arrays.asList("create", "delete", "setspawn", "setmin", "setmax", "tp", "info", "list"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("lobby")) {
            completions.add("build");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("report")) {
            completions.addAll(Arrays.asList("close", "delete"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("report")) {
            for (ReportManager.Report r : plugin.getReportManager().getAllReports()) {
                completions.add(String.valueOf(r.getId()));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("arena")) {
            String sub = args[1].toLowerCase();
            if (sub.equals("delete") || sub.equals("setspawn") || sub.equals("info") || sub.equals("setmin") || sub.equals("setmax") || sub.equals("tp")) {
                completions.addAll(plugin.getArenaManager().getAllArenas().stream()
                        .map(Arena::getName).collect(Collectors.toList()));
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("create")) {
            completions.addAll(Arrays.stream(DuelGameMode.values())
                    .map(DuelGameMode::getDisplayName).collect(Collectors.toList()));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("setspawn")) {
            completions.addAll(Arrays.asList("1", "2"));
        }
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
