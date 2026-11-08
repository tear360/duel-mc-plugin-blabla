package fr.duelplugin.managers;

import fr.duelplugin.DuelPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ReportManager {

    private final DuelPlugin plugin;
    private final File reportFile;
    private FileConfiguration reportConfig;
    private final Map<Integer, Report> reports = new ConcurrentHashMap<>();
    private int nextId = 1;

    public ReportManager(DuelPlugin plugin) {
        this.plugin = plugin;
        this.reportFile = new File(plugin.getDataFolder(), "reports.yml");
        if (!reportFile.exists()) {
            try { reportFile.createNewFile(); } catch (IOException ignored) {}
        }
        this.reportConfig = YamlConfiguration.loadConfiguration(reportFile);
        loadReports();
    }

    private void loadReports() {
        reports.clear();
        nextId = 1;
        if (!reportConfig.contains("reports")) return;

        ConfigurationSection section = reportConfig.getConfigurationSection("reports");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                String reporter = section.getString(key + ".reporter", "Unknown");
                String reported = section.getString(key + ".reported", "Unknown");
                String reason = section.getString(key + ".reason", "");
                long timestamp = section.getLong(key + ".timestamp", System.currentTimeMillis());
                String status = section.getString(key + ".status", "open");

                reports.put(id, new Report(id, reporter, reported, reason, timestamp, status));
                if (id >= nextId) nextId = id + 1;
            } catch (NumberFormatException ignored) {}
        }
    }

    public int createReport(String reporter, String reported, String reason) {
        int id = nextId++;
        Report report = new Report(id, reporter, reported, reason, System.currentTimeMillis(), "open");
        reports.put(id, report);
        saveReports();
        notifyAdmins(report);
        return id;
    }

    public void closeReport(int id) {
        Report report = reports.get(id);
        if (report != null) {
            report.setStatus("closed");
            saveReports();
        }
    }

    public void deleteReport(int id) {
        reports.remove(id);
        saveReports();
    }

    public Report getReport(int id) {
        return reports.get(id);
    }

    public List<Report> getOpenReports() {
        return reports.values().stream()
                .filter(r -> r.getStatus().equals("open"))
                .sorted(Comparator.comparingInt(Report::getId))
                .collect(Collectors.toList());
    }

    public List<Report> getAllReports() {
        return reports.values().stream()
                .sorted(Comparator.comparingInt(Report::getId))
                .collect(Collectors.toList());
    }

    public int getOpenReportCount() {
        return (int) reports.values().stream().filter(r -> r.getStatus().equals("open")).count();
    }

    private void notifyAdmins(Report report) {
        String reason = sanitizeForChat(report.getReason());
        String msg = "§5[Report §d#" + report.getId() + "§5] §d" + report.getReporter() + " §7reported §c" + report.getReported() + " §7: §f" + reason;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("duelplugin.admin.report")) {
                p.sendMessage(msg);
            }
        }
        plugin.getLogger().info("[Report #" + report.getId() + "] " + report.getReporter() + " reported " + report.getReported() + ": " + reason);
    }

    private String sanitizeForChat(String input) {
        if (input == null) return "";
        String cleaned = input.replace('\u00A7', '&')
                .replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll(" {2,}", " ")
                .trim();
        if (cleaned.length() > 200) {
            cleaned = cleaned.substring(0, 200) + "...";
        }
        return cleaned;
    }

    private void saveReports() {
        reportConfig.set("reports", null);
        for (Map.Entry<Integer, Report> entry : reports.entrySet()) {
            String path = "reports." + entry.getKey();
            Report r = entry.getValue();
            reportConfig.set(path + ".reporter", r.getReporter());
            reportConfig.set(path + ".reported", r.getReported());
            reportConfig.set(path + ".reason", r.getReason());
            reportConfig.set(path + ".timestamp", r.getTimestamp());
            reportConfig.set(path + ".status", r.getStatus());
        }
        try {
            reportConfig.save(reportFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save reports.yml");
        }
    }

    public void reload() {
        reportConfig = YamlConfiguration.loadConfiguration(reportFile);
        loadReports();
    }

    public static class Report {
        private final int id;
        private final String reporter;
        private final String reported;
        private final String reason;
        private final long timestamp;
        private String status;

        public Report(int id, String reporter, String reported, String reason, long timestamp, String status) {
            this.id = id;
            this.reporter = reporter;
            this.reported = reported;
            this.reason = reason;
            this.timestamp = timestamp;
            this.status = status;
        }

        public int getId() { return id; }
        public String getReporter() { return reporter; }
        public String getReported() { return reported; }
        public String getReason() { return reason; }
        public long getTimestamp() { return timestamp; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
