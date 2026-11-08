package fr.duelplugin.managers;

import fr.duelplugin.DuelPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateManager {

    private final DuelPlugin plugin;

    private static final String API_URL = "https://api.github.com/repos/%s/releases/latest";
    private static final Pattern REPO_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ASSET_PATTERN = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SAFE_VERSION = Pattern.compile("^[0-9A-Za-z._-]{1,32}$");
    private static final Pattern VERSION_PART = Pattern.compile("^\\d+");

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final long MAX_JSON_BYTES = 2L * 1024 * 1024;
    private static final long MAX_DOWNLOAD_BYTES = 50L * 1024 * 1024;

    private boolean updateAvailable = false;
    private String latestVersion = "";
    private String downloadUrl = "";

    public UpdateManager(DuelPlugin plugin) {
        this.plugin = plugin;
    }

    public void checkForUpdates() {
        new BukkitRunnable() {
            @Override
            public void run() {
                String repo = plugin.getConfig().getString("github-repo", "");
                if (repo == null || repo.isBlank()) return;
                repo = repo.trim();

                if (!REPO_PATTERN.matcher(repo).matches()) {
                    plugin.getLogger().warning("[Update] Invalid github-repo value. Expected \"owner/repo\".");
                    return;
                }

                try {
                    String currentVersion = plugin.getDescription().getVersion();
                    String jsonResponse = fetchJson(String.format(API_URL, repo));

                    if (jsonResponse == null) {
                        plugin.getLogger().warning("[Update] Could not contact GitHub.");
                        return;
                    }

                    latestVersion = extractTag(jsonResponse);
                    downloadUrl = extractJarDownloadUrl(jsonResponse);

                    if (latestVersion == null || latestVersion.isEmpty()) {
                        plugin.getLogger().warning("[Update] Could not read the latest version.");
                        return;
                    }

                    if (!SAFE_VERSION.matcher(latestVersion).matches()) {
                        plugin.getLogger().warning("[Update] Ignoring release with an unsafe version tag: " + latestVersion);
                        latestVersion = "";
                        downloadUrl = "";
                        return;
                    }

                    if (isNewerVersion(latestVersion, currentVersion)) {
                        updateAvailable = true;
                        plugin.getLogger().info("[Update] New version available: " + latestVersion + " (current: " + currentVersion + ")");

                        if (!plugin.getConfig().getBoolean("github-auto-download", false)) {
                            plugin.getLogger().info("[Update] Automatic download is disabled. Download it manually from: "
                                    + (downloadUrl.isBlank() ? "the GitHub releases page" : downloadUrl));
                            return;
                        }

                        if (downloadUpdate(downloadUrl)) {
                            plugin.getLogger().info("[Update] Release downloaded to plugins/DuelPlugin/updates/. "
                                    + "Verify it, then install it manually and restart the server.");
                        }
                    } else {
                        plugin.getLogger().info("[Update] You are up to date (" + currentVersion + ").");
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Update] Error during update check:", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private String fetchJson(String urlString) throws IOException {
        String token = plugin.getConfig().getString("github-token", "");
        plugin.getLogger().info("[Update] GitHub token: " + (token != null && !token.isBlank() ? "configured" : "not configured"));

        URL url = URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("User-Agent", "DuelPlugin");
            if (token != null && !token.isBlank()) {
                conn.setRequestProperty("Authorization", "token " + token.trim());
            }
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            int code = conn.getResponseCode();
            plugin.getLogger().info("[Update] GitHub response: HTTP " + code);
            if (code != 200) {
                plugin.getLogger().warning("[Update] GitHub responded with code: " + code);
                if (code == 404) {
                    plugin.getLogger().warning("[Update] Repository or release not found.");
                } else if (code == 401 || code == 403) {
                    if (token == null || token.isBlank()) {
                        plugin.getLogger().warning("[Update] Private repository. Add a token in config.yml (github-token).");
                    } else {
                        plugin.getLogger().warning("[Update] Invalid token or insufficient permissions.");
                    }
                }
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[8192];
                long total = 0;
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_JSON_BYTES) {
                        throw new IOException("GitHub response is too large");
                    }
                    sb.append(buffer, 0, read);
                }
                return sb.toString();
            }
        } finally {
            conn.disconnect();
        }
    }

    private String extractTag(String json) {
        Matcher matcher = TAG_PATTERN.matcher(json);
        if (!matcher.find()) return null;
        String tag = matcher.group(1);
        if (tag.startsWith("v") || tag.startsWith("V")) {
            tag = tag.substring(1);
        }
        return tag;
    }

    private String extractJarDownloadUrl(String json) {
        Matcher matcher = ASSET_PATTERN.matcher(json);
        while (matcher.find()) {
            String url = matcher.group(1).replace("\\/", "/");
            if (url.toLowerCase().endsWith(".jar")) {
                return url;
            }
        }
        return null;
    }

    private boolean isNewerVersion(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");

        int maxLength = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < maxLength; i++) {
            int l = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
            int c = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            if (l > c) return true;
            if (l < c) return false;
        }
        return false;
    }

    private int parseVersionPart(String part) {
        Matcher matcher = VERSION_PART.matcher(part);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean downloadUpdate(String urlString) {
        if (urlString == null || !isTrustedDownloadUrl(urlString)) {
            plugin.getLogger().warning("[Update] Refusing to download from an untrusted URL.");
            return false;
        }

        Path updateDir = plugin.getDataFolder().toPath().resolve("updates");
        Path target = updateDir.resolve("DuelPlugin-" + latestVersion + ".jar");
        Path temp = updateDir.resolve(target.getFileName() + ".part");

        HttpURLConnection conn = null;
        try {
            Files.createDirectories(updateDir);

            if (Files.exists(target)) {
                plugin.getLogger().info("[Update] The file already exists: " + target.getFileName());
                return true;
            }

            String token = plugin.getConfig().getString("github-token", "");
            URL url = URI.create(urlString).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "DuelPlugin");
            conn.setRequestProperty("Accept", "application/octet-stream");
            if (token != null && !token.isBlank()) {
                conn.setRequestProperty("Authorization", "token " + token.trim());
            }
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            int code = conn.getResponseCode();
            if (code != 200) {
                plugin.getLogger().warning("[Update] Download error: HTTP " + code);
                return false;
            }

            long totalBytes = 0;
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(temp,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    totalBytes += bytesRead;
                    if (totalBytes > MAX_DOWNLOAD_BYTES) {
                        throw new IOException("Release JAR exceeds the maximum allowed size");
                    }
                    out.write(buffer, 0, bytesRead);
                }
            }

            if (!verifyChecksum(temp)) {
                Files.deleteIfExists(temp);
                return false;
            }

            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }

            plugin.getLogger().info("[Update] Downloaded " + target.getFileName() + " (" + (totalBytes / 1024) + " KB) "
                    + "to plugins/DuelPlugin/updates/.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Update] Error during download:", e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private boolean verifyChecksum(Path file) throws IOException {
        String expected = plugin.getConfig().getString("github-update-sha256", "");
        if (expected == null || expected.isBlank()) {
            plugin.getLogger().warning("[Update] No github-update-sha256 configured; the download was not integrity-checked.");
            return true;
        }

        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(expected.trim())) {
                plugin.getLogger().warning("[Update] SHA-256 mismatch, refusing to keep the downloaded file.");
                return false;
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Update] Could not verify the SHA-256 checksum: " + e.getMessage());
            return false;
        }
    }

    private boolean isTrustedDownloadUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;

            String host = uri.getHost();
            if (host == null) return false;
            if (!host.equalsIgnoreCase("github.com") && !host.equalsIgnoreCase("objects.githubusercontent.com")) {
                return false;
            }

            String path = uri.getPath() == null ? "" : uri.getPath();
            if (!path.toLowerCase().endsWith(".jar")) return false;

            return host.equalsIgnoreCase("objects.githubusercontent.com") || path.contains("/releases/download/");
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}
