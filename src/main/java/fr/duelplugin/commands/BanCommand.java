package fr.duelplugin.commands;

import fr.duelplugin.DuelPlugin;
import fr.duelplugin.managers.BanManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class BanCommand implements CommandExecutor {

    private final DuelPlugin plugin;

    public BanCommand(DuelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("duelplugin.admin.ban")) {
            sender.sendMessage(plugin.getLanguageManager().msg(null, "no_permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§dUsage: /ban <player> [duration] [reason]");
            sender.sendMessage("§7Examples: §f/ban Player1 7d hack");
            sender.sendMessage("§7           §f/ban Player1 Permanent cheat");
            return true;
        }

        String targetName = args[0];
        String reason = "";
        long duration = -1;

        if (args.length >= 2) {
            duration = BanManager.parseDuration(args[1]);
            if (duration == -1 && !args[1].equalsIgnoreCase("permanent") && !args[1].equalsIgnoreCase("perm")) {
                sender.sendMessage("§cInvalid duration. Use: 30s, 10m, 2h, 7d, 2w, or perm");
                return true;
            }
            if (args[1].equalsIgnoreCase("permanent") || args[1].equalsIgnoreCase("perm")) {
                duration = -1;
            }
        }

        if (args.length >= 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) sb.append(" ");
                sb.append(args[i]);
            }
            reason = sb.toString();
        }

        Player target = Bukkit.getPlayerExact(targetName);
        UUID uuid;
        if (target != null) {
            uuid = target.getUniqueId();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(targetName);
            if (offline == null) {
                sender.sendMessage("§cPlayer not found. The player must have joined the server at least once.");
                return true;
            }
            uuid = offline.getUniqueId();
        }

        String bannerName = sender instanceof Player ? sender.getName() : "Console";
        plugin.getBanManager().ban(uuid, targetName, reason, bannerName, duration);

        String durationStr = duration == -1 ? "Permanent" : BanManager.formatDuration(duration);
        sender.sendMessage("§a§l" + targetName + " §7has been banned.");
        sender.sendMessage("§7Duration: §f" + durationStr);
        if (!reason.isEmpty()) sender.sendMessage("§7Reason: §f" + reason);

        if (target != null) {
            target.kick(plugin.getBanManager().buildBanScreen(uuid));
        }

        return true;
    }
}
