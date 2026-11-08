package fr.duelplugin.listeners;

import fr.duelplugin.DuelPlugin;
import fr.duelplugin.managers.DuelManager;
import fr.duelplugin.models.DuelGameMode;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;
import java.util.Set;

public class GameListener implements Listener {

    private final DuelPlugin plugin;
    private static final double SPEC_RADIUS = 100.0;

    public GameListener(DuelPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDuelManager().isInDuel(player)) return;

        var duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null) return;

        DuelGameMode mode = duel.getMode();

        if (!mode.canBreakBlocks()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLanguageManager().msg(player, "gamemode_disabled"));
            return;
        }

        if (mode.isSandbox()) {
            if (duel.getArena() != null && duel.getArena().isWallBlock(event.getBlock().getLocation())) {
                event.setCancelled(true);
                player.sendMessage(plugin.getLanguageManager().msg(player, "gamemode_disabled"));
            }
            return;
        }

        if (!duel.isPlayerPlacedBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLanguageManager().msg(player, "gamemode_disabled"));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDuelManager().isInDuel(player)) return;

        var duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null) return;

        DuelGameMode mode = duel.getMode();

        if (!mode.canPlaceBlocks()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLanguageManager().msg(player, "gamemode_disabled"));
            return;
        }

        duel.addPlacedBlock(event.getBlock().getLocation());
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker == null) return;

        if (plugin.getDuelManager().isFrozen(victim) || plugin.getDuelManager().isFrozen(attacker)) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.getDuelManager().isInDuel(attacker) || !plugin.getDuelManager().isInDuel(victim)) {
            event.setCancelled(true);
            return;
        }

        var duel = plugin.getDuelManager().getDuel(attacker.getUniqueId());
        if (duel == null) return;

        boolean isFFA = duel.isFFA();

        if (isFFA) {
            var victimDuel = plugin.getDuelManager().getDuel(victim.getUniqueId());
            if (victimDuel == null || !victimDuel.isFFA()
                    || !duel.getFFAId().equals(victimDuel.getFFAId())) {
                event.setCancelled(true);
                return;
            }
        } else {
            UUID uuid1 = duel.getPlayer1();
            UUID uuid2 = duel.getPlayer2();
            boolean validFight = (attacker.getUniqueId().equals(uuid1) && victim.getUniqueId().equals(uuid2)) ||
                    (attacker.getUniqueId().equals(uuid2) && victim.getUniqueId().equals(uuid1));

            if (!validFight) {
                event.setCancelled(true);
                return;
            }
        }

        if (victim.getHealth() - event.getFinalDamage() <= 0) {
            ItemStack mainHand = victim.getInventory().getItemInMainHand();
            ItemStack offHand = victim.getInventory().getItemInOffHand();
            if (mainHand.getType() == Material.TOTEM_OF_UNDYING || offHand.getType() == Material.TOTEM_OF_UNDYING) {
                return;
            }
            event.setCancelled(true);
            victim.setHealth(20.0);

            if (duel.isFFA()) {
                Set<UUID> participants = duel.getFFAParticipants();
                participants.remove(victim.getUniqueId());

                victim.sendMessage(plugin.getLanguageManager().msg(victim, "duel_eliminated_ffa"));
                plugin.getDuelManager().restoreInventory(victim);
                PlayerListener.giveLobbyItems(victim);
                victim.setGameMode(GameMode.SURVIVAL);
                if (plugin.getLobbyManager().isLobbySet()) {
                    plugin.getLobbyManager().teleportToLobby(victim);
                }

                if (participants.size() <= 1) {
                    UUID winnerId = participants.iterator().next();
                    Player winner = Bukkit.getPlayer(winnerId);
                    if (winner != null) {
                        winner.sendMessage(plugin.getLanguageManager().msg(winner, "duel_winner_ffa"));
                    }
                    plugin.getDuelManager().endDuel(attacker.getUniqueId(), winnerId, victim.getUniqueId());
                }
            } else {
                plugin.getDuelManager().endDuel(attacker.getUniqueId(), attacker.getUniqueId(), victim.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getDuelManager().isInDuel(player)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            return;
        }

        var duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null) return;

        if (duel.getMode().isNaturalRegenDisabled() || duel.getMode().isPotionBased()) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (plugin.getDuelManager().isFrozen(player)) {
            if (event.getFrom().getX() != event.getTo().getX() ||
                event.getFrom().getY() != event.getTo().getY() ||
                event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom().clone());
            }
            return;
        }

        if (plugin.getTabManager().isSpectating(player.getUniqueId())) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SPECTATOR);
            }

            DuelManager.ActiveDuel specDuel = plugin.getDuelManager().getDuelOfSpectator(player.getUniqueId());
            if (specDuel != null) {
                Location center = getDuelCenter(specDuel);
                if (center != null && player.getWorld().equals(center.getWorld())) {
                    double dist = player.getLocation().distance(center);
                    if (dist > SPEC_RADIUS) {
                        Player target = Bukkit.getPlayer(specDuel.getPlayer1());
                        if (target != null) {
                            player.teleport(target.getLocation());
                        } else {
                            player.teleport(center);
                        }
                    }
                }
            }
            return;
        }

        if (!plugin.getDuelManager().isInDuel(player)) return;

        var duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null) return;

        if (duel.getArena() != null && duel.getArena().getMinCorner() != null && duel.getArena().getMaxCorner() != null) {
            if (!duel.getArena().isInArena(event.getTo())) {
                player.teleport(event.getFrom());
            }
        }
    }

    private Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        return null;
    }

    private Location getDuelCenter(DuelManager.ActiveDuel duel) {
        if (duel.getArena() != null) {
            Location min = duel.getArena().resolveMinCorner();
            Location max = duel.getArena().resolveMaxCorner();
            if (min != null && max != null && min.getWorld() != null) {
                return new Location(min.getWorld(),
                        (min.getX() + max.getX()) / 2,
                        (min.getY() + max.getY()) / 2,
                        (min.getZ() + max.getZ()) / 2);
            }
        }
        Player p1 = Bukkit.getPlayer(duel.getPlayer1());
        if (p1 != null) return p1.getLocation();
        Player p2 = Bukkit.getPlayer(duel.getPlayer2());
        if (p2 != null) return p2.getLocation();
        return null;
    }
}
