package fr.duelplugin.commands;

import fr.duelplugin.DuelPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class UnmuteCommand implements CommandExecutor {

    private final DuelPlugin plugin;

    public UnmuteCommand(DuelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("duelplugin.admin.unmute")) {
            sender.sendMessage(plugin.getLanguageManager().msg(null, "no_permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§dUsage: /unmute <player>");
            return true;
        }

        String targetName = args[0];
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(targetName);
        if (offline == null) {
            sender.sendMessage("§cPlayer not found in the server cache.");
            return true;
        }
        UUID uuid = offline.getUniqueId();

        if (!plugin.getBanManager().isMuted(uuid)) {
            sender.sendMessage("§c" + targetName + " is not muted.");
            return true;
        }

        plugin.getBanManager().unmute(uuid);
        sender.sendMessage("§a§l" + targetName + " §7has been unmuted.");

        Player target = Bukkit.getPlayer(targetName);
        if (target != null) {
            target.sendMessage("§aYou have been unmuted!");
        }

        return true;
    }
}
