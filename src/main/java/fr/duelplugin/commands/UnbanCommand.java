package fr.duelplugin.commands;

import fr.duelplugin.DuelPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class UnbanCommand implements CommandExecutor {

    private final DuelPlugin plugin;

    public UnbanCommand(DuelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("duelplugin.admin.unban")) {
            sender.sendMessage(plugin.getLanguageManager().msg(null, "no_permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§dUsage: /unban <player>");
            return true;
        }

        String targetName = args[0];
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(targetName);
        if (offline == null) {
            sender.sendMessage("§cPlayer not found in the server cache.");
            return true;
        }
        UUID uuid = offline.getUniqueId();

        if (!plugin.getBanManager().isBanned(uuid)) {
            sender.sendMessage("§c" + targetName + " is not banned.");
            return true;
        }

        plugin.getBanManager().unban(uuid);
        sender.sendMessage("§a§l" + targetName + " §7has been unbanned.");

        return true;
    }
}
