package fr.duelplugin.managers;

import fr.duelplugin.DuelPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class BanManager {

    private final DuelPlugin plugin;
    private File bansFile, mutesFile;
    private FileConfiguration bansConfig, mutesConfig;

    public BanManager(DuelPlugin plugin) {
        this.plugin = plugin;
        loadFiles();
    }

    private void loadFiles() {
        bansFile = new File(plugin.getDataFolder(), "bans.yml");
        mutesFile = new File(plugin.getDataFolder(), "mutes.yml");
        if (!bansFile.exists()) {
            try { bansFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        if (!mutesFile.exists()) {
            try { mutesFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        bansConfig = YamlConfiguration.loadConfiguration(bansFile);
        mutesConfig = YamlConfiguration.loadConfiguration(mutesFile);
    }

    // ─────── BANS ───────

    public synchronized void ban(UUID uuid, String name, String reason, String banner, long durationMs) {
        String path = uuid.toString();
        bansConfig.set(path + ".name", name);
        bansConfig.set(path + ".reason", reason != null ? reason : "");
        bansConfig.set(path + ".banner", banner);
        bansConfig.set(path + ".timestamp", System.currentTimeMillis());
        bansConfig.set(path + ".duration", durationMs);
        save(bansConfig, bansFile);
    }

    public synchronized void unban(UUID uuid) {
        bansConfig.set(uuid.toString(), null);
        save(bansConfig, bansFile);
    }

    public synchronized boolean isBanned(UUID uuid) {
        String path = uuid.toString();
        if (!bansConfig.contains(path)) return false;
        long duration = bansConfig.getLong(path + ".duration", -1);
        if (duration == -1) return true;
        long timestamp = bansConfig.getLong(path + ".timestamp", 0);
        if (System.currentTimeMillis() >= timestamp + duration) {
            bansConfig.set(path, null);
            save(bansConfig, bansFile);
            return false;
        }
        return true;
    }

    public synchronized String getBanReason(UUID uuid) {
        return bansConfig.getString(uuid.toString() + ".reason", "");
    }

    public synchronized String getBanner(UUID uuid) {
        return bansConfig.getString(uuid.toString() + ".banner", "Unknown");
    }

    public synchronized long getBanTimestamp(UUID uuid) {
        return bansConfig.getLong(uuid.toString() + ".timestamp", 0);
    }

    public synchronized long getBanDuration(UUID uuid) {
        return bansConfig.getLong(uuid.toString() + ".duration", -1);
    }

    public synchronized long getBanRemaining(UUID uuid) {
        if (!isBanned(uuid)) return 0;
        long duration = getBanDuration(uuid);
        if (duration == -1) return -1;
        long remaining = (getBanTimestamp(uuid) + duration) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    // ─────── MUTES ───────

    public synchronized void mute(UUID uuid, String name, String reason, String muter, long durationMs) {
        String path = uuid.toString();
        mutesConfig.set(path + ".name", name);
        mutesConfig.set(path + ".reason", reason != null ? reason : "");
        mutesConfig.set(path + ".muter", muter);
        mutesConfig.set(path + ".timestamp", System.currentTimeMillis());
        mutesConfig.set(path + ".duration", durationMs);
        save(mutesConfig, mutesFile);
    }

    public synchronized void unmute(UUID uuid) {
        mutesConfig.set(uuid.toString(), null);
        save(mutesConfig, mutesFile);
    }

    public synchronized boolean isMuted(UUID uuid) {
        String path = uuid.toString();
        if (!mutesConfig.contains(path)) return false;
        long duration = mutesConfig.getLong(path + ".duration", -1);
        if (duration == -1) return true;
        long timestamp = mutesConfig.getLong(path + ".timestamp", 0);
        if (System.currentTimeMillis() >= timestamp + duration) {
            mutesConfig.set(path, null);
            save(mutesConfig, mutesFile);
            return false;
        }
        return true;
    }

    public synchronized String getMuteReason(UUID uuid) {
        return mutesConfig.getString(uuid.toString() + ".reason", "");
    }

    public synchronized long getMuteRemaining(UUID uuid) {
        if (!isMuted(uuid)) return 0;
        long duration = mutesConfig.getLong(uuid.toString() + ".duration", -1);
        if (duration == -1) return -1;
        long remaining = (mutesConfig.getLong(uuid.toString() + ".timestamp", 0) + duration) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    // ─────── HELPERS ───────

    public static long parseDuration(String input) {
        if (input == null || input.isEmpty()) return -1;
        input = input.trim().toLowerCase();
        long total = 0;
        StringBuilder num = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
            } else if (num.length() > 0) {
                long val = Long.parseLong(num.toString());
                switch (c) {
                    case 's' -> total += val * 1000L;
                    case 'm' -> total += val * 60_000L;
                    case 'h' -> total += val * 3_600_000L;
                    case 'j', 'd' -> total += val * 86_400_000L;
                    case 'w' -> total += val * 604_800_000L;
                    default -> total += val * 60_000L;
                }
                num = new StringBuilder();
            }
        }
        if (num.length() > 0) total += Long.parseLong(num.toString()) * 60_000L;
        return total > 0 ? total : -1;
    }

    public static String formatDuration(long ms) {
        if (ms == -1) return "Permanent";
        if (ms <= 0) return "Expired";
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;

        if (weeks > 0) return weeks + "w " + (days % 7) + "d " + (hours % 24) + "h";
        if (days > 0) return days + "d " + (hours % 24) + "h " + (minutes % 60) + "min";
        if (hours > 0) return hours + "h " + (minutes % 60) + "min " + (seconds % 60) + "s";
        if (minutes > 0) return minutes + "min " + (seconds % 60) + "s";
        return seconds + "s";
    }

    public synchronized Component buildBanScreen(UUID uuid) {
        String reason = getBanReason(uuid);
        String banner = getBanner(uuid);
        long duration = getBanDuration(uuid);
        long remaining = getBanRemaining(uuid);

        Component line = Component.text("═══════════════════════════════", NamedTextColor.DARK_PURPLE);
        Component title = Component.text("You are banned!", NamedTextColor.RED, TextDecoration.BOLD);
        Component spacer = Component.empty();

        Component reasonLine = Component.text("Reason: ", NamedTextColor.GRAY)
                .append(Component.text(reason.isEmpty() ? "None" : reason, NamedTextColor.WHITE));
        Component byLine = Component.text("Banned by: ", NamedTextColor.GRAY)
                .append(Component.text(banner, NamedTextColor.RED));
        Component durationLine;
        if (duration == -1) {
            durationLine = Component.text("Duration: ", NamedTextColor.GRAY)
                    .append(Component.text("Permanent", NamedTextColor.RED, TextDecoration.BOLD));
        } else {
            durationLine = Component.text("Duration: ", NamedTextColor.GRAY)
                    .append(Component.text(formatDuration(duration), NamedTextColor.YELLOW));
        }
        Component remainingLine;
        if (duration == -1) {
            remainingLine = Component.text("This ban is ", NamedTextColor.GRAY)
                    .append(Component.text("permanent", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                    .append(Component.text(".", NamedTextColor.GRAY));
        } else {
            remainingLine = Component.text("Remaining time: ", NamedTextColor.GRAY)
                    .append(Component.text(formatDuration(remaining), NamedTextColor.YELLOW, TextDecoration.BOLD));
        }
        Component unbanNote = Component.text("Appeal on the server Discord.", NamedTextColor.DARK_PURPLE);

        return Component.empty()
                .append(line)
                .appendNewline()
                .append(title)
                .appendNewline()
                .append(spacer)
                .appendNewline()
                .append(reasonLine)
                .appendNewline()
                .append(byLine)
                .appendNewline()
                .append(durationLine)
                .appendNewline()
                .append(remainingLine)
                .appendNewline()
                .append(spacer)
                .appendNewline()
                .append(unbanNote)
                .appendNewline()
                .append(line);
    }

    private synchronized void save(FileConfiguration config, File file) {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
