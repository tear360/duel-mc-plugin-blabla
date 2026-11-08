package fr.duelplugin.commands;

import fr.duelplugin.DuelPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ReportCommand implements CommandExecutor, TabCompleter {

    private final DuelPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ReportCommand(DuelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().msgRaw(null, "command_only_players"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getLanguageManager().msg(player, "report_usage"));
            return true;
        }

        String reportedName = args[0];
        Player reported = plugin.getServer().getPlayer(reportedName);
        if (reported == null) {
            player.sendMessage(plugin.getLanguageManager().msg(player, "player_not_found"));
            return true;
        }

        if (reported.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getLanguageManager().msg(player, "report_cannot_self"));
            return true;
        }

        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (reasonBuilder.length() > 0) reasonBuilder.append(" ");
            reasonBuilder.append(args[i]);
        }
        String reason = sanitizeChat(reasonBuilder.toString());
        int maxReasonLength = Math.max(1, plugin.getConfig().getInt("reports.max-reason-length", 250));
        if (reason.length() > maxReasonLength) {
            reason = reason.substring(0, maxReasonLength);
        }
        if (reason.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().msg(player, "report_usage"));
            return true;
        }

        long cooldownMs = Math.max(0, plugin.getConfig().getLong("reports.cooldown-seconds", 60)) * 1000L;
        if (cooldownMs > 0 && !player.hasPermission("duelplugin.report.bypass")) {
            long now = System.currentTimeMillis();
            Long lastUse = cooldowns.get(player.getUniqueId());
            if (lastUse != null && now - lastUse < cooldownMs) {
                long remaining = (cooldownMs - (now - lastUse) + 999) / 1000;
                player.sendMessage(plugin.getLanguageManager().msg(player, "report_cooldown", "%time%", formatRemaining(remaining)));
                return true;
            }
            cooldowns.put(player.getUniqueId(), now);
        }

        int id = plugin.getReportManager().createReport(player.getName(), reported.getName(), reason);
        plugin.getDiscordBotManager().createReportForumPost(player.getName(), reported.getName(), reason, id);
        player.sendMessage(plugin.getLanguageManager().msg(player, "report_success", "%id%", String.valueOf(id), "%player%", reported.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }

    private String sanitizeChat(String input) {
        return input.replaceAll("[\\p{Cntrl}]", " ")
                .replace('\u00A7', '&')
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private String formatRemaining(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return minutes > 0 ? minutes + "m " + secs + "s" : secs + "s";
    }
}
