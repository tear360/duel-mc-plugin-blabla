package fr.duelplugin.commands;

import fr.duelplugin.DuelPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class BugReportCommand implements CommandExecutor, TabCompleter {

    private final DuelPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public BugReportCommand(DuelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(plugin.getLanguageManager().msg(player, "bugreport_usage"));
            return true;
        }

        if (!plugin.getDiscordBotManager().isEnabled()) {
            player.sendMessage("§cThe Discord bot is not connected.");
            return true;
        }

        String bug = sanitizeChat(String.join(" ", args));
        int maxBugLength = Math.max(1, plugin.getConfig().getInt("reports.max-bug-length", 1000));
        if (bug.length() > maxBugLength) {
            bug = bug.substring(0, maxBugLength);
        }
        if (bug.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().msg(player, "bugreport_usage"));
            return true;
        }

        long cooldownMs = Math.max(0, plugin.getConfig().getLong("reports.bug-cooldown-seconds", 3600)) * 1000L;
        long now = System.currentTimeMillis();
        if (cooldownMs > 0 && !player.hasPermission("duelplugin.bugreport.bypass")) {
            cooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownMs);
            Long lastUse = cooldowns.get(player.getUniqueId());
            if (lastUse != null && (now - lastUse) < cooldownMs) {
                long remaining = (cooldownMs - (now - lastUse) + 999) / 1000;
                long minutes = remaining / 60;
                long seconds = remaining % 60;
                player.sendMessage(plugin.getLanguageManager().msg(player, "bugreport_cooldown", "%time%", minutes + "m " + seconds + "s"));
                return true;
            }
        }

        cooldowns.put(player.getUniqueId(), now);

        plugin.getDiscordBotManager().createBugReportThread(player.getName(), player.getUniqueId().toString(), bug);
        player.sendMessage(plugin.getLanguageManager().msg(player, "bugreport_sent"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return Collections.emptyList();
    }

    private String sanitizeChat(String input) {
        return input.replaceAll("[\\p{Cntrl}]", " ")
                .replace('\u00A7', '&')
                .replaceAll(" {2,}", " ")
                .trim();
    }
}
