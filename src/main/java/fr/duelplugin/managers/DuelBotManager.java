package fr.duelplugin.managers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHurtAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import fr.duelplugin.DuelPlugin;
import fr.duelplugin.DuelPlugin;
import fr.duelplugin.models.Arena;
import fr.duelplugin.models.DuelGameMode;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.util.Vector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DuelBotManager extends SimplePacketListenerAbstract implements Listener {

    private final DuelPlugin plugin;
    private final Map<UUID, BotState> botDuels = new HashMap<>();
    private final Map<UUID, UUID> entityToPlayer = new HashMap<>();
    private final Map<Integer, UUID> npcToPlayer = new java.util.concurrent.ConcurrentHashMap<>();

    private static final double BOT_SPEED = 0.26;
    private static final double STRAFE_SPEED = 0.20;
    private static final double ATTACK_RANGE = 2.9;
    private static final double BOT_DAMAGE = 8.0;
    private static final int ATTACK_COOLDOWN = 12;
    private static final int STRAFE_INTERVAL = 40;
    private static final int NPC_ID_MIN = 100_000_000;

    private static final String SKIN_UUID = "853c80ef-3c37-49fd-aa49-938b674adae6";
    private static final Pattern JSON_EXTRACT = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"signature\"\\s*:\\s*\"([^\"]+)\"");

    public DuelBotManager(DuelPlugin plugin) {
        this.plugin = plugin;
        startAITask();
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        if (event.isCancelled()) return;
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        try {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

            int entityId = interact.getEntityId();
            UUID playerUuid = npcToPlayer.get(entityId);
            if (playerUuid == null) return;

            event.setCancelled(true);
            UUID attackerUuid = event.getUser().getUUID();
            if (attackerUuid == null || !playerUuid.equals(attackerUuid)) return;

            Bukkit.getScheduler().runTask(plugin, () -> handlePlayerAttack(playerUuid, entityId));
        } catch (Exception ignored) {
        }
    }

    private void handlePlayerAttack(UUID playerUuid, int npcId) {
        Player p = Bukkit.getPlayer(playerUuid);
        BotState state = botDuels.get(playerUuid);
        if (p == null || !p.isOnline() || state == null) return;
        if (state.npcId != npcId) return;

        Entity entity = Bukkit.getEntity(state.botEntityUuid);
        if (!(entity instanceof Zombie bot) || bot.isDead()) return;

        if (p.getLocation().distance(bot.getLocation()) > 4.0) return;
        if (plugin.getDuelManager().isFrozen(p)) return;

        double cooldown = p.getCooledAttackStrength(0.0f);
        double damage = meleeDamage(p) * Math.max(0.2, cooldown);

        bot.damage(damage, p);
        Vector dir = bot.getLocation().toVector().subtract(p.getLocation().toVector());
        dir.setY(0).normalize();
        bot.setVelocity(dir.multiply(0.4).add(new Vector(0, 0.2, 0)));

        sendHurtAnimation(state, p);
    }

    private double meleeDamage(Player player) {
        double attack = 1.0;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item != null && item.getType() != Material.AIR && item.getItemMeta() != null) {
            for (Map.Entry<Attribute, AttributeModifier> entry : item.getItemMeta().getAttributeModifiers(EquipmentSlot.HAND).entries()) {
                if (entry.getKey() == Attribute.ATTACK_DAMAGE) {
                    attack += entry.getValue().getAmount();
                }
            }
        }
        return attack;
    }

    private void sendHurtAnimation(BotState state, Player viewer) {
        try {
            sendPacket(viewer, new WrapperPlayServerHurtAnimation(state.npcId, 0.0f));
        } catch (Exception ignored) {
        }
    }

    public boolean startBotDuel(Player player) {
        if (plugin.getDuelManager().isInDuel(player)) return false;
        if (plugin.getQueueManager().isInAnyQueue(player)) return false;

        Arena arena = plugin.getArenaManager().getAvailableArena(DuelGameMode.SWORD);
        if (arena == null) {
            player.sendMessage(plugin.getLanguageManager().msg(player, "queue_no_arena", "%mode%", DuelGameMode.SWORD.getDisplayName()));
            return false;
        }

        Location botSpawn = arena.resolveSpawn2();
        if (botSpawn == null || botSpawn.getWorld() == null) {
            player.sendMessage("§cThis arena has no valid spawn 2 for the bot.");
            return false;
        }

        UUID botUuid = UUID.randomUUID();
        plugin.getDuelManager().startBotDuel(player, botUuid, arena);
        spawnBot(player, arena, botUuid);
        return true;
    }

    private void spawnBot(Player player, Arena arena, UUID botUuid) {
        Location loc = arena.resolveSpawn2();
        if (loc == null || loc.getWorld() == null) return;
        World world = loc.getWorld();

        String botName = strip(plugin.getLanguageManager().msgRaw(player, "bot_name"));

        Zombie bot = world.spawn(loc, Zombie.class, z -> {
            z.setAI(false);
            z.setBaby(false);
            z.setSilent(true);
            z.setCanPickupItems(false);
            z.setRemoveWhenFarAway(false);
            z.setPersistent(true);
            z.setCollidable(false);
            z.setShouldBurnInDay(false);
            z.setInvisible(true);
            z.setCustomNameVisible(false);
            z.setCustomName(null);
        });

        bot.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0);
        bot.setHealth(40.0);

        int npcId = allocateNpcId();
        UUID profileUuid = UUID.randomUUID();
        BotState state = new BotState(bot.getUniqueId(), arena, world, npcId, profileUuid, botName, loc.clone(),
                loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        botDuels.put(player.getUniqueId(), state);
        npcToPlayer.put(npcId, player.getUniqueId());
        entityToPlayer.put(bot.getUniqueId(), player.getUniqueId());

        fetchSkinAsync(state, player);
    }

    private void spawnForViewer(BotState state, Player viewer) {
        if (state.spawned) return;
        try {
            Location spawnLoc = new Location(state.world, state.spawnX, state.spawnY, state.spawnZ,
                    state.spawnYaw, state.spawnPitch);
            com.github.retrooper.packetevents.protocol.world.Location loc = SpigotConversionUtil.fromBukkitLocation(spawnLoc);
            List<TextureProperty> textures = state.textures;

            UserProfile profile = new UserProfile(state.profileUuid, state.name, textures);
            PlayerInfo info = new PlayerInfo(profile, true, 0, GameMode.SURVIVAL,
                    Component.text(state.name, NamedTextColor.LIGHT_PURPLE), null, 0, false);
            WrapperPlayServerPlayerInfoUpdate add = new WrapperPlayServerPlayerInfoUpdate(
                    WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                    Collections.singletonList(info));
            sendPacket(viewer, add);

            WrapperPlayServerSpawnPlayer spawn = new WrapperPlayServerSpawnPlayer(
                    state.npcId,
                    state.profileUuid,
                    loc,
                    Collections.emptyList());
            sendPacket(viewer, spawn);

            sendEquipment(state, viewer);
            state.spawned = true;
        } catch (Exception ignored) {
        }
    }

    private void sendEquipment(BotState state, Player viewer) {
        try {
            List<Equipment> equipment = new ArrayList<>();
            equipment.add(new Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.MAIN_HAND,
                    SpigotConversionUtil.fromBukkitItemStack(ench(Material.DIAMOND_SWORD, Enchantment.SWEEPING_EDGE, 3))));
            equipment.add(new Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.HELMET,
                    SpigotConversionUtil.fromBukkitItemStack(ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 3))));
            equipment.add(new Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.CHEST_PLATE,
                    SpigotConversionUtil.fromBukkitItemStack(ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 3))));
            equipment.add(new Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.LEGGINGS,
                    SpigotConversionUtil.fromBukkitItemStack(ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 3))));
            equipment.add(new Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.BOOTS,
                    SpigotConversionUtil.fromBukkitItemStack(ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 3))));
            sendPacket(viewer, new WrapperPlayServerEntityEquipment(state.npcId, equipment));
        } catch (Exception ignored) {
        }
    }

    @EventHandler
    public void onPlayerChunkLoad(io.papermc.paper.event.packet.PlayerChunkLoadEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        BotState state = botDuels.get(playerUuid);
        if (state == null || state.spawned) return;
        if (event.getChunk().getX() == state.chunkX && event.getChunk().getZ() == state.chunkZ) {
            spawnForViewer(state, event.getPlayer());
        }
    }

    private int allocateNpcId() {
        Set<Integer> used = new HashSet<>();
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) used.add(e.getEntityId());
        }
        for (BotState s : botDuels.values()) {
            used.add(s.npcId);
        }
        Random random = new Random();
        int id;
        int attempts = 0;
        do {
            id = NPC_ID_MIN + random.nextInt(NPC_ID_MIN);
            attempts++;
        } while (used.contains(id) && attempts < 100);
        return id;
    }

    private void fetchSkinAsync(BotState state, Player viewer) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<TextureProperty> textures = fetchSkinTextures();
            Bukkit.getScheduler().runTask(plugin, () -> {
                BotState current = botDuels.get(viewer.getUniqueId());
                if (current == null || current.npcId != state.npcId) return;
                current.textures = textures != null ? textures : Collections.emptyList();
                if (current.spawned) {
                    despawn(current, viewer);
                    current.spawned = false;
                    spawnForViewer(current, viewer);
                }
            });
        });
    }

    private List<TextureProperty> fetchSkinTextures() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + SKIN_UUID + "?unsigned=false");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "DuelPlugin/1.12");
            int code = conn.getResponseCode();
            if (code != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            Matcher m = JSON_EXTRACT.matcher(sb.toString());
            if (m.find()) {
                List<TextureProperty> list = new ArrayList<>();
                list.add(new TextureProperty("textures", m.group(1), m.group(2)));
                return list;
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String strip(String s) {
        if (s == null) return "Bot";
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
    }

    public boolean isBotDuelEntity(Entity entity) {
        return entityToPlayer.containsKey(entity.getUniqueId());
    }

    private void startAITask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID playerUuid : new ArrayList<>(botDuels.keySet())) {
                    tickBot(playerUuid);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void tickBot(UUID playerUuid) {
        Player p = Bukkit.getPlayer(playerUuid);
        BotState state = botDuels.get(playerUuid);
        if (p == null || !p.isOnline() || state == null) {
            cleanup(playerUuid);
            return;
        }

        if (!plugin.getDuelManager().isInDuel(playerUuid)) {
            cleanup(playerUuid);
            return;
        }

        Entity entity = Bukkit.getEntity(state.botEntityUuid);
        if (!(entity instanceof Zombie bot) || bot.isDead()) {
            cleanup(playerUuid);
            return;
        }

        if (!state.spawned) {
            state.pendingTicks++;
            if (state.pendingTicks >= 40 && state.world.equals(p.getWorld())) {
                spawnForViewer(state, p);
            }
            return;
        }

        if (plugin.getDuelManager().isFrozen(p)) return;

        Location pLoc = p.getLocation();
        Location bLoc = bot.getLocation();
        Vector to = pLoc.toVector().subtract(bLoc.toVector());
        double horiz = Math.sqrt(to.getX() * to.getX() + to.getZ() * to.getZ());
        if (horiz < 0.01) return;

        Vector dir = new Vector(to.getX(), 0, to.getZ()).normalize();
        double desiredYaw = Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));

        Location look = bLoc.clone();
        look.setYaw((float) desiredYaw);
        look.setPitch(0f);
        bot.teleport(look);

        boolean inRange = horiz <= ATTACK_RANGE && Math.abs(to.getY()) <= 2.0
                && bot.hasLineOfSight(p);

        if (!inRange) {
            state.strafeTicks++;
            if (state.strafeTicks >= STRAFE_INTERVAL) {
                state.strafeTicks = 0;
                state.strafeDir = -state.strafeDir;
            }
            Vector move = dir.clone().multiply(BOT_SPEED);
            move.add(new Vector(-dir.getZ() * state.strafeDir * STRAFE_SPEED, 0,
                    dir.getX() * state.strafeDir * STRAFE_SPEED));
            Location next = bLoc.clone().add(move);
            next.setY(next.getY() + to.getY() * 0.1);
            bot.teleport(next);
        }

        if (inRange) {
            state.attackTicks++;
            if (state.attackTicks >= ATTACK_COOLDOWN) {
                state.attackTicks = 0;
                if (p.isOnline() && !p.isDead()) {
                    p.damage(BOT_DAMAGE, bot);
                    Vector knock = dir.clone().multiply(0.4).add(new Vector(0, 0.3, 0));
                    p.setVelocity(knock);
                    playSwing(state, p);
                }
            }
        } else {
            state.attackTicks = 0;
        }

        syncNpc(state, bot, p);
    }

    private void syncNpc(BotState state, Zombie bot, Player viewer) {
        try {
            Location loc = bot.getLocation();
            WrapperPlayServerEntityPositionSync sync = new WrapperPlayServerEntityPositionSync(
                    state.npcId,
                    new EntityPositionData(
                            new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                            new Vector3d(0, 0, 0),
                            loc.getYaw(),
                            loc.getPitch()),
                    true);
            sendPacket(viewer, sync);
        } catch (Exception ignored) {
        }
    }

    private void playSwing(BotState state, Player viewer) {
        try {
            sendPacket(viewer, new WrapperPlayServerEntityAnimation(
                    state.npcId, WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM));
        } catch (Exception ignored) {
        }
    }

    private void despawn(BotState state, Player viewer) {
        try {
            sendPacket(viewer, new WrapperPlayServerDestroyEntities(state.npcId));
            sendPacket(viewer, new WrapperPlayServerPlayerInfoRemove(Collections.singletonList(state.profileUuid)));
        } catch (Exception ignored) {
        }
    }

    private void sendPacket(Player viewer, com.github.retrooper.packetevents.wrapper.PacketWrapper<?> wrapper) {
        Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
        if (channel != null) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(channel, wrapper);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        UUID playerUuid = entityToPlayer.remove(event.getEntity().getUniqueId());
        if (playerUuid == null) return;

        event.setDroppedExp(0);
        event.getDrops().clear();

        UUID botUuid = event.getEntity().getUniqueId();
        plugin.getDuelManager().endDuel(playerUuid, playerUuid, botUuid);
    }

    public void cleanup(UUID playerUuid) {
        BotState state = botDuels.remove(playerUuid);
        if (state != null) {
            entityToPlayer.remove(state.botEntityUuid);
            npcToPlayer.remove(state.npcId);
            Player viewer = Bukkit.getPlayer(playerUuid);
            if (viewer != null && viewer.isOnline() && state.spawned) {
                despawn(state, viewer);
            }
            Entity entity = Bukkit.getEntity(state.botEntityUuid);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
    }

    public void cleanup() {
        for (UUID playerUuid : new ArrayList<>(botDuels.keySet())) {
            cleanup(playerUuid);
        }
    }

    private ItemStack ench(Material mat, Enchantment ench, int level) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(ench, level, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static class BotState {
        final UUID botEntityUuid;
        final Arena arena;
        final World world;
        final int npcId;
        final UUID profileUuid;
        final String name;
        final int chunkX;
        final int chunkZ;
        final double spawnX;
        final double spawnY;
        final double spawnZ;
        final float spawnYaw;
        final float spawnPitch;
        List<TextureProperty> textures = Collections.emptyList();
        boolean spawned;
        int pendingTicks;
        int strafeDir = 1;
        int strafeTicks;
        int attackTicks;

        BotState(UUID botEntityUuid, Arena arena, World world, int npcId, UUID profileUuid, String name,
                 Location spawn, int chunkX, int chunkZ) {
            this.botEntityUuid = botEntityUuid;
            this.arena = arena;
            this.world = world;
            this.npcId = npcId;
            this.profileUuid = profileUuid;
            this.name = name;
            this.spawnX = spawn.getX();
            this.spawnY = spawn.getY();
            this.spawnZ = spawn.getZ();
            this.spawnYaw = spawn.getYaw();
            this.spawnPitch = spawn.getPitch();
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }
}
