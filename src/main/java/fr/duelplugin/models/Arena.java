package fr.duelplugin.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Arena {

    private final String name;
    private DuelGameMode gameMode;
    private Location spawn1;
    private Location spawn2;
    private Location minCorner;
    private Location maxCorner;
    private String spawn1WorldName;
    private String spawn2WorldName;
    private String minWorldName;
    private String maxWorldName;
    private final List<BlockSnapshot> originalBlocks;
    private boolean snapshotActive;

    private static final long MAX_SNAPSHOT_VOLUME = 2_000_000L;

    public Arena(String name, DuelGameMode gameMode) {
        this.name = name;
        this.gameMode = gameMode;
        this.originalBlocks = new ArrayList<>();
        this.snapshotActive = false;
    }

    public String getName() { return name; }

    public DuelGameMode getGameMode() { return gameMode; }
    public void setGameMode(DuelGameMode gameMode) { this.gameMode = gameMode; }

    public Location getSpawn1() { return spawn1; }
    public void setSpawn1(Location spawn1) {
        this.spawn1 = spawn1;
        this.spawn1WorldName = spawn1 != null && spawn1.getWorld() != null ? spawn1.getWorld().getName() : null;
    }

    public Location getSpawn2() { return spawn2; }
    public void setSpawn2(Location spawn2) {
        this.spawn2 = spawn2;
        this.spawn2WorldName = spawn2 != null && spawn2.getWorld() != null ? spawn2.getWorld().getName() : null;
    }

    public Location getMinCorner() { return minCorner; }
    public void setMinCorner(Location minCorner) {
        this.minCorner = minCorner;
        this.minWorldName = minCorner != null && minCorner.getWorld() != null ? minCorner.getWorld().getName() : null;
    }

    public Location getMaxCorner() { return maxCorner; }
    public void setMaxCorner(Location maxCorner) {
        this.maxCorner = maxCorner;
        this.maxWorldName = maxCorner != null && maxCorner.getWorld() != null ? maxCorner.getWorld().getName() : null;
    }

    public Location resolveSpawn1() { return resolveLocation(spawn1, spawn1WorldName); }
    public Location resolveSpawn2() { return resolveLocation(spawn2, spawn2WorldName); }
    public Location resolveMinCorner() { return resolveLocation(minCorner, minWorldName); }
    public Location resolveMaxCorner() { return resolveLocation(maxCorner, maxWorldName); }

    private static Location resolveLocation(Location loc, String worldName) {
        if (loc == null) return null;
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null && (loc.getWorld() == null || !loc.getWorld().equals(world))) {
                Location resolved = loc.clone();
                resolved.setWorld(world);
                return resolved;
            }
        }
        return loc.clone();
    }

    public boolean isSetup() {
        return spawn1 != null && spawn2 != null;
    }

    public boolean isInArena(Location loc) {
        Location min = resolveMinCorner();
        Location max = resolveMaxCorner();
        if (min == null || max == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().equals(min.getWorld())) return false;
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        double minX = Math.min(min.getX(), max.getX());
        double minY = Math.min(min.getY(), max.getY());
        double minZ = Math.min(min.getZ(), max.getZ());
        double maxX = Math.max(min.getX(), max.getX());
        double maxY = Math.max(min.getY(), max.getY());
        double maxZ = Math.max(min.getZ(), max.getZ());
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean canInteractBlocks() {
        return gameMode == DuelGameMode.UHC || gameMode == DuelGameMode.DIASMP || gameMode == DuelGameMode.VANILLA;
    }

    public boolean isWallBlock(Location loc) {
        Location min = resolveMinCorner();
        Location max = resolveMaxCorner();
        if (min == null || max == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().equals(min.getWorld())) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        int minX = (int) Math.min(min.getX(), max.getX());
        int minY = (int) Math.min(min.getY(), max.getY());
        int minZ = (int) Math.min(min.getZ(), max.getZ());
        int maxX = (int) Math.max(min.getX(), max.getX());
        int maxY = (int) Math.max(min.getY(), max.getY());
        int maxZ = (int) Math.max(min.getZ(), max.getZ());
        return x < minX || x > maxX || z < minZ || z > maxZ || y < minY || y > maxY
                || x == minX || x == maxX || z == minZ || z == maxZ || y == minY || y == maxY;
    }

    public boolean isSnapshotSizeAllowed() {
        Location min = resolveMinCorner();
        Location max = resolveMaxCorner();
        if (min == null || max == null) return true;
        long x = Math.abs((long) max.getBlockX() - min.getBlockX()) + 1;
        long y = Math.abs((long) max.getBlockY() - min.getBlockY()) + 1;
        long z = Math.abs((long) max.getBlockZ() - min.getBlockZ()) + 1;
        try {
            return Math.multiplyExact(Math.multiplyExact(x, y), z) <= MAX_SNAPSHOT_VOLUME;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    public void takeSnapshot() {
        originalBlocks.clear();
        Location min = resolveMinCorner();
        Location max = resolveMaxCorner();
        if (min == null || max == null) return;
        if (!isSnapshotSizeAllowed()) {
            Bukkit.getLogger().warning("[DuelPlugin] Arena '" + name + "' exceeds the maximum snapshot volume; snapshot skipped.");
            return;
        }
        World world = min.getWorld();
        if (world == null) return;

        int minX = (int) Math.min(min.getX(), max.getX());
        int minY = (int) Math.min(min.getY(), max.getY());
        int minZ = (int) Math.min(min.getZ(), max.getZ());
        int maxX = (int) Math.max(min.getX(), max.getX());
        int maxY = (int) Math.max(min.getY(), max.getY());
        int maxZ = (int) Math.max(min.getZ(), max.getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR && block.getType() != Material.CAVE_AIR) {
                        originalBlocks.add(new BlockSnapshot(
                                world.getName(), x, y, z,
                                block.getType(), block.getBlockData().getAsString()
                        ));
                    }
                }
            }
        }
        snapshotActive = true;
    }

    public void restoreFromSnapshot() {
        if (!snapshotActive) return;
        Location min = resolveMinCorner();
        Location max = resolveMaxCorner();
        if (min == null || max == null) return;
        World world = min.getWorld();
        if (world == null) world = Bukkit.getWorlds().get(0);

        int minX = (int) Math.min(min.getX(), max.getX());
        int minY = (int) Math.min(min.getY(), max.getY());
        int minZ = (int) Math.min(min.getZ(), max.getZ());
        int maxX = (int) Math.max(min.getX(), max.getX());
        int maxY = (int) Math.max(min.getY(), max.getY());
        int maxZ = (int) Math.max(min.getZ(), max.getZ());

        Map<Long, BlockSnapshot> byCoord = new HashMap<>();
        for (BlockSnapshot snap : originalBlocks) {
            byCoord.put(snapshotKey(snap.x, snap.y, snap.z, minX, minY, minZ), snap);
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    BlockSnapshot snap = byCoord.get(snapshotKey(x, y, z, minX, minY, minZ));
                    if (snap != null) {
                        if (!block.getBlockData().getAsString().equals(snap.blockData)) {
                            try {
                                block.setBlockData(Bukkit.createBlockData(snap.blockData), false);
                            } catch (Exception e) {
                                block.setType(snap.material, false);
                            }
                        }
                    } else if (block.getType() != Material.AIR && block.getType() != Material.CAVE_AIR) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }

        snapshotActive = false;
        originalBlocks.clear();
    }

    private static long snapshotKey(int x, int y, int z, int minX, int minY, int minZ) {
        long rx = (long) x - minX;
        long ry = (long) y - minY;
        long rz = (long) z - minZ;
        return (rx & 0x1FFFFF) | ((ry & 0x1FFFFF) << 21) | ((rz & 0x1FFFFF) << 42);
    }

    public void saveToConfig(ConfigurationSection section) {
        section.set("gamemode", gameMode.getConfigName());
        if (spawn1 != null) saveLocation(section.createSection("spawn1"), spawn1);
        if (spawn2 != null) saveLocation(section.createSection("spawn2"), spawn2);
        if (minCorner != null) saveLocation(section.createSection("min"), minCorner);
        if (maxCorner != null) saveLocation(section.createSection("max"), maxCorner);
    }

    public static Arena loadFromConfig(String name, ConfigurationSection section) {
        DuelGameMode mode = DuelGameMode.fromConfig(section.getString("gamemode", "sword"));
        if (mode == null) mode = DuelGameMode.SWORD;
        Arena arena = new Arena(name, mode);
        if (section.contains("spawn1")) arena.setSpawn1(loadLocation(section.getConfigurationSection("spawn1")));
        if (section.contains("spawn2")) arena.setSpawn2(loadLocation(section.getConfigurationSection("spawn2")));
        if (section.contains("min")) arena.setMinCorner(loadLocation(section.getConfigurationSection("min")));
        if (section.contains("max")) arena.setMaxCorner(loadLocation(section.getConfigurationSection("max")));
        return arena;
    }

    private void saveLocation(ConfigurationSection section, Location loc) {
        section.set("world", loc.getWorld().getName());
        section.set("x", loc.getX());
        section.set("y", loc.getY());
        section.set("z", loc.getZ());
        section.set("yaw", (double) loc.getYaw());
        section.set("pitch", (double) loc.getPitch());
    }

    private static Location loadLocation(ConfigurationSection section) {
        String worldName = section.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    public static class BlockSnapshot {
        public final String worldName;
        public final int x, y, z;
        public final Material material;
        public final String blockData;

        public BlockSnapshot(String worldName, int x, int y, int z, Material material, String blockData) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
            this.blockData = blockData;
        }
    }
}
