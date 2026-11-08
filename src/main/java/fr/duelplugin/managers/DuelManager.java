package fr.duelplugin.managers;

import fr.duelplugin.DuelPlugin;
import fr.duelplugin.models.Arena;
import fr.duelplugin.models.DuelGameMode;
import fr.duelplugin.models.DuelPlayer;
import fr.duelplugin.models.DuelRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import fr.duelplugin.listeners.PlayerListener;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;

public class DuelManager {

    private final DuelPlugin plugin;
    private final ArenaManager arenaManager;
    private final PlayerManager playerManager;
    private final Map<UUID, DuelRequest> pendingRequests;
    private final Map<UUID, ActiveDuel> activeDuels;
    private final Map<UUID, ItemStack[]> savedInventories;
    private final Map<UUID, ItemStack[]> savedArmor;
    private final Map<UUID, Collection<PotionEffect>> savedEffects;
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> countdownActive = new HashSet<>();

    public DuelManager(DuelPlugin plugin) {
        this.plugin = plugin;
        this.arenaManager = plugin.getArenaManager();
        this.playerManager = plugin.getPlayerManager();
        this.pendingRequests = new HashMap<>();
        this.activeDuels = new HashMap<>();
        this.savedInventories = new HashMap<>();
        this.savedArmor = new HashMap<>();
        this.savedEffects = new HashMap<>();
    }

    public boolean sendRequest(Player sender, Player receiver, DuelGameMode mode) {
        if (sender.getUniqueId().equals(receiver.getUniqueId())) return false;
        if (isInDuel(sender) || isInDuel(receiver)) return false;
        if (mode.isArenaRestricted() && arenaManager.getAvailableArena(mode) == null) return false;
        if (!plugin.getSettingsManager().acceptsDuelRequests(receiver.getUniqueId())) return false;

        pendingRequests.put(receiver.getUniqueId(), new DuelRequest(sender.getUniqueId(), receiver.getUniqueId(), mode));

        receiver.sendMessage("");
        receiver.sendMessage("§5§l═══════════════════════════");
        receiver.sendMessage(plugin.getLanguageManager().msgRaw(receiver, "duel_received_title"));
        receiver.sendMessage(plugin.getLanguageManager().msgRaw(receiver, "duel_received_player", "%player%", sender.getName()));
        receiver.sendMessage(plugin.getLanguageManager().msgRaw(receiver, "duel_received_mode", "%mode%", mode.getDisplayName()));
        receiver.sendMessage("");

        Component acceptButton = Component.text(plugin.getLanguageManager().msgRaw(receiver, "duel_accept_button"), NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/acceptduel " + sender.getName()))
                .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text(plugin.getLanguageManager().msgRaw(receiver, "duel_accept_hover"), NamedTextColor.GREEN)));

        Component denyButton = Component.text(plugin.getLanguageManager().msgRaw(receiver, "duel_deny_button"), NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/denyduel " + sender.getName()))
                .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text(plugin.getLanguageManager().msgRaw(receiver, "duel_deny_hover"), NamedTextColor.RED)));

        receiver.sendMessage(Component.text(plugin.getLanguageManager().msgRaw(receiver, "duel_action"), NamedTextColor.LIGHT_PURPLE).append(acceptButton).append(denyButton));
        receiver.sendMessage("");
        receiver.sendMessage("§5§l═══════════════════════════");

        return true;
    }

    public DuelRequest getPendingRequest(Player player) {
        DuelRequest req = pendingRequests.get(player.getUniqueId());
        if (req != null && req.isExpired()) {
            pendingRequests.remove(player.getUniqueId());
            return null;
        }
        return req;
    }

    public DuelRequest getRequestFromSender(UUID receiver, UUID sender) {
        DuelRequest req = pendingRequests.get(receiver);
        if (req != null && req.getSender().equals(sender)) {
            if (req.isExpired()) {
                pendingRequests.remove(receiver);
                return null;
            }
            return req;
        }
        return null;
    }

    public void removeRequest(UUID uuid) {
        pendingRequests.remove(uuid);
    }

    public boolean acceptRequest(Player receiver) {
        DuelRequest request = getPendingRequest(receiver);
        if (request == null) return false;

        Player sender = request.getSenderPlayer();
        if (sender == null || !sender.isOnline()) {
            pendingRequests.remove(receiver.getUniqueId());
            return false;
        }

        DuelGameMode mode = request.getMode();
        Arena arena = null;
        if (mode.isArenaRestricted()) {
            arena = arenaManager.getAvailableArena(mode);
        }

        startDuel(sender, receiver, mode, arena);
        pendingRequests.remove(receiver.getUniqueId());
        return true;
    }

    public void startDuel(Player player1, Player player2, DuelGameMode mode, Arena arena) {
        if (arena != null && arena.canInteractBlocks() && !arena.isSnapshotSizeAllowed()) {
            String msg = "§cThis arena is too large to snapshot. Ask an admin to reduce its bounds.";
            player1.sendMessage(msg);
            player2.sendMessage(msg);
            return;
        }

        saveInventory(player1);
        saveInventory(player2);

        if (arena != null && arena.canInteractBlocks()) {
            arena.takeSnapshot();
        }

        Location loc1, loc2;
        if (arena != null && arena.isSetup()) {
            loc1 = arena.resolveSpawn1();
            loc2 = arena.resolveSpawn2();
        } else {
            Location lobby = plugin.getLobbyManager().resolveLobby();
            if (lobby == null) lobby = new Location(Bukkit.getWorlds().get(0), 0, 64, 0);
            loc1 = lobby.clone().add(2, 0, 0);
            loc2 = lobby.clone().add(-2, 0, 0);
        }

        final Location finalLoc1 = loc1;
        final Location finalLoc2 = loc2;

        ActiveDuel duel = new ActiveDuel(player1.getUniqueId(), player2.getUniqueId(), mode, arena);
        if (mode.supportsRounds()) {
            int roundsP1 = plugin.getSettingsManager().getRoundCount(player1.getUniqueId());
            int roundsP2 = plugin.getSettingsManager().getRoundCount(player2.getUniqueId());
            duel.setMaxRounds(Math.max(roundsP1, roundsP2));
        }
        activeDuels.put(player1.getUniqueId(), duel);
        activeDuels.put(player2.getUniqueId(), duel);

        frozenPlayers.add(player1.getUniqueId());
        frozenPlayers.add(player2.getUniqueId());

        player1.teleportAsync(finalLoc1, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
            if (!success || !player1.isOnline()) return;
            applyKit(player1, mode);
            player1.setHealth(20.0);
            player1.setFoodLevel(20);
            player1.setSaturation(20f);
            player1.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(plugin.getLanguageManager().msgRaw(player1, "title_duel_found"), NamedTextColor.DARK_PURPLE, TextDecoration.BOLD),
                    Component.text(player2.getName() + " - " + mode.getDisplayName(), NamedTextColor.LIGHT_PURPLE),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(1))
            ));
        });

        player2.teleportAsync(finalLoc2, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
            if (!success || !player2.isOnline()) return;
            applyKit(player2, mode);
            player2.setHealth(20.0);
            player2.setFoodLevel(20);
            player2.setSaturation(20f);
            player2.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(plugin.getLanguageManager().msgRaw(player2, "title_duel_found"), NamedTextColor.DARK_PURPLE, TextDecoration.BOLD),
                    Component.text(player1.getName() + " - " + mode.getDisplayName(), NamedTextColor.LIGHT_PURPLE),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(1))
            ));
        });

        plugin.getScoreboardManager().createDuelScoreboard(player1, player2, mode);
        plugin.getScoreboardManager().createDuelScoreboard(player2, player1, mode);

        startCountdown(player1, player2, mode);
    }

    public void startBotDuel(Player player1, UUID botUuid, Arena arena) {
        if (arena != null && arena.canInteractBlocks() && !arena.isSnapshotSizeAllowed()) {
            player1.sendMessage("§cThis arena is too large to snapshot. Ask an admin to reduce its bounds.");
            return;
        }

        saveInventory(player1);

        if (arena != null && arena.canInteractBlocks()) {
            arena.takeSnapshot();
        }

        Location loc1 = null;
        if (arena != null && arena.isSetup()) {
            loc1 = arena.resolveSpawn1();
        } else {
            Location lobby = plugin.getLobbyManager().resolveLobby();
            if (lobby == null) lobby = new Location(Bukkit.getWorlds().get(0), 0, 64, 0);
            loc1 = lobby.clone();
        }

        final Location finalLoc1 = loc1;

        DuelGameMode mode = DuelGameMode.SWORD;
        ActiveDuel duel = new ActiveDuel(player1.getUniqueId(), botUuid, mode, arena);
        duel.setBotDuel(true);
        activeDuels.put(player1.getUniqueId(), duel);
        activeDuels.put(botUuid, duel);

        frozenPlayers.add(player1.getUniqueId());

        String botName = plugin.getLanguageManager().msgRaw(player1, "bot_name");

        player1.teleportAsync(finalLoc1, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
            if (!success || !player1.isOnline()) return;
            applyKit(player1, mode);
            player1.setHealth(20.0);
            player1.setFoodLevel(20);
            player1.setSaturation(20f);
            player1.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(plugin.getLanguageManager().msgRaw(player1, "bot_duel_title"), NamedTextColor.DARK_PURPLE, TextDecoration.BOLD),
                    Component.text(botName + " - " + mode.getDisplayName(), NamedTextColor.LIGHT_PURPLE),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(1))
            ));
        });

        plugin.getScoreboardManager().createBotScoreboard(player1, botName, mode);

        startBotCountdown(player1, mode);
    }

    private void startBotCountdown(Player player1, DuelGameMode mode) {
        countdownActive.add(player1.getUniqueId());

        new org.bukkit.scheduler.BukkitRunnable() {
            int count = 3;

            @Override
            public void run() {
                if (count > 0) {
                    String msg = plugin.getLanguageManager().msgRaw(player1, "duel_countdown", "%count%", String.valueOf(count));
                    player1.sendActionBar(Component.text(msg));
                    count--;
                } else {
                    frozenPlayers.remove(player1.getUniqueId());
                    countdownActive.remove(player1.getUniqueId());

                    player1.sendActionBar(Component.text(plugin.getLanguageManager().msgRaw(player1, "duel_go")));
                    player1.sendMessage(plugin.getLanguageManager().msg(player1, "bot_duel_started", "%bot%", plugin.getLanguageManager().msgRaw(player1, "bot_name"), "%mode%", mode.getDisplayName()));

                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startCountdown(Player player1, Player player2, DuelGameMode mode) {
        countdownActive.add(player1.getUniqueId());
        countdownActive.add(player2.getUniqueId());

        new org.bukkit.scheduler.BukkitRunnable() {
            int count = 3;

            @Override
            public void run() {
                if (count > 0) {
                    String msg = plugin.getLanguageManager().msgRaw(player1, "duel_countdown", "%count%", String.valueOf(count));
                    player1.sendActionBar(Component.text(msg));
                    player2.sendActionBar(Component.text(msg));
                    count--;
                } else {
                    frozenPlayers.remove(player1.getUniqueId());
                    frozenPlayers.remove(player2.getUniqueId());
                    countdownActive.remove(player1.getUniqueId());
                    countdownActive.remove(player2.getUniqueId());

                    player1.sendActionBar(Component.text(plugin.getLanguageManager().msgRaw(player1, "duel_go")));
                    player2.sendActionBar(Component.text(plugin.getLanguageManager().msgRaw(player2, "duel_go")));

                    player1.sendMessage(plugin.getLanguageManager().msg(player1, "duel_started", "%player%", player2.getName(), "%mode%", mode.getDisplayName()));
                    player2.sendMessage(plugin.getLanguageManager().msg(player2, "duel_started", "%player%", player1.getName(), "%mode%", mode.getDisplayName()));

                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void endDuel(UUID uuid, UUID winner, UUID loser) {
        ActiveDuel duel = activeDuels.get(uuid);
        if (duel == null) return;

        if (duel.isBotDuel()) {
            endDuelFinal(duel, winner, loser);
            return;
        }

        if (duel.isFFA() && duel.getFFAParticipants() != null) {
            activeDuels.remove(uuid);
            for (UUID ffaUuid : duel.getFFAParticipants()) {
                activeDuels.remove(ffaUuid);
                frozenPlayers.remove(ffaUuid);
                countdownActive.remove(ffaUuid);
                Player ffaPlayer = Bukkit.getPlayer(ffaUuid);
                if (ffaPlayer != null) {
                    restoreInventory(ffaPlayer);
                    ffaPlayer.setGameMode(GameMode.SURVIVAL);
                    plugin.getScoreboardManager().removeScoreboard(ffaPlayer);
                    boolean isWinner = ffaUuid.equals(winner);
                    ffaPlayer.showTitle(net.kyori.adventure.title.Title.title(
                            Component.text(plugin.getLanguageManager().msgRaw(ffaPlayer, isWinner ? "title_victory" : "title_defeat"), isWinner ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD),
                            Component.empty(),
                            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(3), java.time.Duration.ofSeconds(1))
                    ));
                    final boolean fw = isWinner;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (ffaPlayer.isOnline() && plugin.getLobbyManager().isLobbySet()) {
                            plugin.getLobbyManager().teleportToLobby(ffaPlayer);
                            PlayerListener.giveLobbyItems(ffaPlayer);
                            plugin.getScoreboardManager().createLobbyScoreboard(ffaPlayer, null, null);
                        }
                    }, 100L);
                }
            }
            if (duel.getArena() != null) {
                duel.getArena().restoreFromSnapshot();
            }
            return;
        }

        if (duel.getMode().supportsRounds()) {
            int wins = duel.getRoundWins().getOrDefault(winner, 0) + 1;
            duel.getRoundWins().put(winner, wins);
            int maxR = duel.getMaxRounds();

            Player w = Bukkit.getPlayer(winner);
            Player l = Bukkit.getPlayer(loser);

            if (w != null) {
                w.showTitle(net.kyori.adventure.title.Title.title(
                        Component.text("Round won!", NamedTextColor.GREEN, TextDecoration.BOLD),
                        Component.text(wins + " - " + duel.getRoundWins().getOrDefault(loser, 0) + " (Best of " + maxR + ")", NamedTextColor.LIGHT_PURPLE),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(1))
                ));
            }
            if (l != null) {
                l.showTitle(net.kyori.adventure.title.Title.title(
                        Component.text("Round perdu!", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.text(duel.getRoundWins().getOrDefault(winner, 0) + " - " + wins + " (Best of " + maxR + ")", NamedTextColor.LIGHT_PURPLE),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(1))
                ));
            }

            if (wins >= maxR) {
                endDuelFinal(duel, winner, loser);
                return;
            }

            final UUID wUuid = winner;
            final UUID lUuid = loser;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Player pw = Bukkit.getPlayer(wUuid);
                Player pl = Bukkit.getPlayer(lUuid);
                if (pw == null || pl == null) {
                    endDuelFinal(duel, wUuid, lUuid);
                    return;
                }

                frozenPlayers.add(wUuid);
                frozenPlayers.add(lUuid);

                Location loc1, loc2;
                if (duel.getArena() != null && duel.getArena().isSetup()) {
                    loc1 = duel.getArena().resolveSpawn1();
                    loc2 = duel.getArena().resolveSpawn2();
                } else {
                    Location lobby = plugin.getLobbyManager().resolveLobby();
                    if (lobby == null) lobby = new Location(Bukkit.getWorlds().get(0), 0, 64, 0);
                    loc1 = lobby.clone().add(2, 0, 0);
                    loc2 = lobby.clone().add(-2, 0, 0);
                }

                final Location fLoc1 = loc1;
                final Location fLoc2 = loc2;

                pw.teleportAsync(fLoc1, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
                    if (!success || !pw.isOnline()) return;
                    applyKit(pw, duel.getMode());
                    pw.setHealth(20.0);
                    pw.setFoodLevel(20);
                    pw.setSaturation(20f);
                });
                pl.teleportAsync(fLoc2, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
                    if (!success || !pl.isOnline()) return;
                    applyKit(pl, duel.getMode());
                    pl.setHealth(20.0);
                    pl.setFoodLevel(20);
                    pl.setSaturation(20f);
                });

                plugin.getScoreboardManager().createDuelScoreboard(pw, pl, duel.getMode());
                plugin.getScoreboardManager().createDuelScoreboard(pl, pw, duel.getMode());

                startCountdown(pw, pl, duel.getMode());
            }, 60L);
            return;
        }

        activeDuels.remove(duel.getPlayer1());
        activeDuels.remove(duel.getPlayer2());
        frozenPlayers.remove(duel.getPlayer1());
        frozenPlayers.remove(duel.getPlayer2());
        countdownActive.remove(duel.getPlayer1());
        countdownActive.remove(duel.getPlayer2());

        endDuelFinal(duel, winner, loser);
    }

    private void endDuelFinal(ActiveDuel duel, UUID winner, UUID loser) {
        activeDuels.remove(duel.getPlayer1());
        activeDuels.remove(duel.getPlayer2());
        frozenPlayers.remove(duel.getPlayer1());
        frozenPlayers.remove(duel.getPlayer2());
        countdownActive.remove(duel.getPlayer1());
        countdownActive.remove(duel.getPlayer2());

        Player w = Bukkit.getPlayer(winner);
        Player l = Bukkit.getPlayer(loser);

        boolean isBot = duel.isBotDuel();

        if (!isBot) {
            DuelPlayer dw = playerManager.getDuelPlayer(winner);
            DuelPlayer dl = playerManager.getDuelPlayer(loser);

            dw.addKill(duel.getMode().getConfigName());
            dw.addWin();
            dl.addDeath(duel.getMode().getConfigName());
            dl.resetWinStreak();
        }

        if (!duel.isFFA() && !isBot) {
            boolean winnerDiscord = plugin.getSettingsManager().discordNotificationsEnabled(winner);
            boolean loserDiscord = plugin.getSettingsManager().discordNotificationsEnabled(loser);
            if (winnerDiscord && loserDiscord) {
                long duration = System.currentTimeMillis() - duel.getStartTime();
                String wName = w != null ? w.getName() : Bukkit.getOfflinePlayer(winner).getName();
                String lName = l != null ? l.getName() : Bukkit.getOfflinePlayer(loser).getName();
                if (wName != null && lName != null) {
                    plugin.getDiscordBotManager().sendDuelResult(wName, lName, duel.getMode().getDisplayName(), duration);
                }
            }
        }

        if (w != null) {
            restoreInventory(w);
            w.setGameMode(GameMode.SURVIVAL);
            w.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(plugin.getLanguageManager().msgRaw(w, "title_victory"), NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text(plugin.getLanguageManager().msgRaw(w, isBot ? "bot_duel_winner" : "duel_winner_against", "%player%", (l != null ? l.getName() : "Bot")), NamedTextColor.GRAY),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(3), java.time.Duration.ofSeconds(1))
            ));
            w.sendMessage("");
            w.sendMessage("§5§l═══════════════════════════");
            w.sendMessage(plugin.getLanguageManager().msg(w, "duel_winner"));
            if (duel.isFFA()) {
                w.sendMessage(plugin.getLanguageManager().msg(w, "duel_winner_ffa"));
            } else if (isBot) {
                w.sendMessage(plugin.getLanguageManager().msg(w, "bot_duel_winner"));
            } else {
                w.sendMessage(plugin.getLanguageManager().msg(w, "duel_winner_against", "%player%", (l != null ? l.getName() : "Unknown")));
            }
            w.sendMessage("§5§l═══════════════════════════");
            w.sendMessage("");
            plugin.getScoreboardManager().removeScoreboard(w);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (w.isOnline() && plugin.getLobbyManager().isLobbySet()) {
                    plugin.getLobbyManager().teleportToLobby(w);
                    PlayerListener.giveLobbyItems(w);
                    plugin.getScoreboardManager().createLobbyScoreboard(w, null, null);
                }
            }, 100L);
        }
        if (l != null) {
            restoreInventory(l);
            l.setGameMode(GameMode.SURVIVAL);
            l.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(plugin.getLanguageManager().msgRaw(l, "title_defeat"), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(plugin.getLanguageManager().msgRaw(l, isBot ? "bot_duel_defeat" : "duel_eliminated_against", "%player%", (w != null ? w.getName() : "Unknown")), NamedTextColor.GRAY),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(3), java.time.Duration.ofSeconds(1))
            ));
            l.sendMessage("");
            l.sendMessage("§5§l═══════════════════════════");
            l.sendMessage(plugin.getLanguageManager().msg(l, "duel_eliminated"));
            if (duel.isFFA()) {
                l.sendMessage(plugin.getLanguageManager().msg(l, "duel_eliminated_ffa"));
            } else if (isBot) {
                l.sendMessage(plugin.getLanguageManager().msg(l, "bot_duel_defeat"));
            } else {
                l.sendMessage(plugin.getLanguageManager().msg(l, "duel_eliminated_against", "%player%", (w != null ? w.getName() : "Unknown")));
            }
            l.sendMessage("§5§l═══════════════════════════");
            l.sendMessage("");
            plugin.getScoreboardManager().removeScoreboard(l);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (l.isOnline() && plugin.getLobbyManager().isLobbySet()) {
                    plugin.getLobbyManager().teleportToLobby(l);
                    PlayerListener.giveLobbyItems(l);
                    plugin.getScoreboardManager().createLobbyScoreboard(l, null, null);
                }
            }, 100L);
        }

        if (isBot) {
            plugin.getDuelBotManager().cleanup(duel.getPlayer1());
        }

        for (UUID specUuid : new HashSet<>(plugin.getTabManager().getSpectators(duel.getPlayer1()))) {
            Player spec = Bukkit.getPlayer(specUuid);
            if (spec != null) {
                spec.setGameMode(GameMode.SURVIVAL);
                plugin.getScoreboardManager().removeScoreboard(spec);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (spec.isOnline() && plugin.getLobbyManager().isLobbySet()) {
                        plugin.getLobbyManager().teleportToLobby(spec);
                        PlayerListener.giveLobbyItems(spec);
                        plugin.getScoreboardManager().createLobbyScoreboard(spec, null, null);
                    }
                }, 100L);
                spec.sendMessage(plugin.getLanguageManager().msg(spec, "duel_ended"));
            }
        }
        plugin.getTabManager().clearSpectators(duel.getPlayer1());

        if (duel.getArena() != null) {
            duel.getArena().restoreFromSnapshot();
        }
    }

    public void handleDisconnect(Player player) {
        UUID uuid = player.getUniqueId();
        if (isInDuel(uuid)) {
            ActiveDuel duel = activeDuels.get(uuid);
            UUID opponent = duel.getOpponent(uuid);
            endDuel(uuid, opponent, uuid);
        }
        restoreInventory(player);
    }

    public boolean isInDuel(Player player) {
        return activeDuels.containsKey(player.getUniqueId());
    }

    public boolean isInDuel(UUID uuid) {
        return activeDuels.containsKey(uuid);
    }

    public boolean isFrozen(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }

    private void applyKit(Player player, DuelGameMode mode) {
        java.util.Map<String, ItemStack[]> customKit = plugin.getKitManager().loadKit(player.getUniqueId(), mode);
        if (customKit != null) {
            ItemStack[] contents = customKit.get("contents");
            if (contents != null) player.getInventory().setContents(contents);
            ItemStack[] armor = customKit.get("armor");
            if (armor != null) {
                player.getInventory().setArmorContents(armor);

                java.util.Map<Integer, org.bukkit.inventory.meta.trim.ArmorTrim> trims =
                        plugin.getKitManager().loadKitTrims(player.getUniqueId(), mode);
                if (trims != null) {
                    applyTrimsToArmor(player, trims);
                }
            }
            ItemStack[] offhand = customKit.get("offhand");
            if (offhand != null && offhand.length > 0) player.getInventory().setItemInOffHand(offhand[0]);
        } else {
            fr.duelplugin.models.Kit.giveKit(player, mode);
        }
    }

    private void applyTrimsToArmor(Player player, java.util.Map<Integer, org.bukkit.inventory.meta.trim.ArmorTrim> trims) {
        int[] armorSlots = {0, 1, 2, 3}; // boots, leggings, chest, helmet indices
        ItemStack[] armor = player.getInventory().getArmorContents();

        for (int i = 0; i < armorSlots.length && i < armor.length; i++) {
            int trimSlot = armorSlots[i];
            org.bukkit.inventory.meta.trim.ArmorTrim trim = trims.get(trimSlot);
            if (trim != null && armor[i] != null && armor[i].hasItemMeta() && armor[i].getItemMeta() instanceof org.bukkit.inventory.meta.ArmorMeta) {
                org.bukkit.inventory.meta.ArmorMeta meta = (org.bukkit.inventory.meta.ArmorMeta) armor[i].getItemMeta();
                meta.setTrim(trim);
                armor[i].setItemMeta(meta);
            }
        }
        player.getInventory().setArmorContents(armor);
    }

    public ActiveDuel getDuel(UUID uuid) {
        return activeDuels.get(uuid);
    }

    public ActiveDuel getDuelOfSpectator(UUID spectatorUuid) {
        for (ActiveDuel duel : activeDuels.values()) {
            if (plugin.getTabManager().getSpectators(duel.getPlayer1()).contains(spectatorUuid)) {
                return duel;
            }
        }
        return null;
    }

    public void saveInventory(Player player) {
        savedInventories.put(player.getUniqueId(), player.getInventory().getContents().clone());
        savedArmor.put(player.getUniqueId(), player.getInventory().getArmorContents().clone());
        savedEffects.put(player.getUniqueId(), new ArrayList<>(player.getActivePotionEffects()));
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.getActivePotionEffects().forEach(pe -> player.removePotionEffect(pe.getType()));
    }

    public void restoreInventory(Player player) {
        UUID uuid = player.getUniqueId();
        if (savedInventories.containsKey(uuid)) {
            player.getInventory().setContents(savedInventories.remove(uuid));
            player.getInventory().setArmorContents(savedArmor.remove(uuid));
            player.getActivePotionEffects().forEach(pe -> player.removePotionEffect(pe.getType()));
            Collection<PotionEffect> effects = savedEffects.remove(uuid);
            if (effects != null) {
                effects.forEach(pe -> player.addPotionEffect(pe));
            }
        }
    }

    public void cleanup() {
        for (UUID uuid : new ArrayList<>(activeDuels.keySet())) {
            ActiveDuel duel = activeDuels.get(uuid);
            if (duel != null) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) restoreInventory(p);
            }
        }
        activeDuels.clear();
        pendingRequests.clear();
        frozenPlayers.clear();
        countdownActive.clear();
    }

    public void startPartyFFA(Player leader, DuelGameMode mode) {
        PartyManager.Party party = plugin.getPartyManager().getParty(leader.getUniqueId());
        if (party == null) return;

        List<UUID> allPlayers = new ArrayList<>();
        allPlayers.add(leader.getUniqueId());
        allPlayers.addAll(party.getMembers());

        List<Player> online = new ArrayList<>();
        for (UUID uuid : allPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) online.add(p);
        }
        if (online.size() < 3) {
            leader.sendMessage(plugin.getLanguageManager().msg(leader, "party_ffa_not_enough"));
            return;
        }

        Arena arena = null;
        if (mode.isArenaRestricted()) {
            arena = plugin.getArenaManager().getAvailableArena(mode);
        }

        if (arena != null && arena.canInteractBlocks() && !arena.isSnapshotSizeAllowed()) {
            leader.sendMessage("§cThis arena is too large to snapshot. Ask an admin to reduce its bounds.");
            return;
        }

        if (arena != null && arena.canInteractBlocks()) {
            arena.takeSnapshot();
        }

        final Location spawnBase;
        if (arena != null && arena.isSetup()) {
            spawnBase = arena.resolveSpawn1();
        } else {
            Location lobby = plugin.getLobbyManager().resolveLobby();
            if (lobby == null) lobby = new Location(Bukkit.getWorlds().get(0), 0, 64, 0);
            spawnBase = lobby.clone();
        }

        UUID ffaId = UUID.randomUUID();
        for (Player p : online) {
            saveInventory(p);
            frozenPlayers.add(p.getUniqueId());

            int idx = online.indexOf(p);
            double angle = 2 * Math.PI * idx / online.size();
            Location spawn = spawnBase.clone().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);
            if (arena == null || !arena.isSetup()) {
                spawn = spawnBase.clone().add(idx * 2 - online.size(), 0, 0);
            }

            final Location finalSpawn = spawn;
            p.teleportAsync(finalSpawn, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
                if (!success || !p.isOnline()) return;
                applyKit(p, mode);
                p.setHealth(20.0);
                p.setFoodLevel(20);
                p.setSaturation(20f);
                p.sendActionBar(Component.text(plugin.getLanguageManager().msgRaw(p, "duel_countdown", "%count%", "3")));
            });
        }

        for (UUID uuid : allPlayers) {
            ActiveDuel ffaDuel = new ActiveDuel(uuid, uuid, mode, arena);
            ffaDuel.setFFA(ffaId, new HashSet<>(allPlayers));
            activeDuels.put(uuid, ffaDuel);
        }

        for (Player p : online) {
            for (Player other : online) {
                if (!p.getUniqueId().equals(other.getUniqueId())) {
                    plugin.getScoreboardManager().createDuelScoreboard(p, other, mode);
                }
            }
        }

        new org.bukkit.scheduler.BukkitRunnable() {
            int count = 3;

            @Override
            public void run() {
                if (count > 0) {
                    for (Player p : online) {
                        if (p.isOnline()) p.sendActionBar(Component.text(plugin.getLanguageManager().msgRaw(p, "duel_countdown", "%count%", String.valueOf(count))));
                    }
                    count--;
                } else {
                    for (UUID uuid : allPlayers) {
                        frozenPlayers.remove(uuid);
                        countdownActive.remove(uuid);
                    }
                    for (Player p : online) {
                        if (p.isOnline()) p.sendActionBar(Component.text(plugin.getLanguageManager().msgRaw(p, "duel_go")));
                        if (p.isOnline()) p.sendMessage(plugin.getLanguageManager().msg(p, "party_ffa_started", "%mode%", mode.getDisplayName()));
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public boolean isInFFA(UUID uuid) {
        ActiveDuel duel = activeDuels.get(uuid);
        return duel != null && duel.isFFA();
    }

    public boolean isFFASameTeam(UUID a, UUID b) {
        ActiveDuel duelA = activeDuels.get(a);
        if (duelA == null || !duelA.isFFA()) return false;
        return duelA.getFFAParticipants().contains(a) && duelA.getFFAParticipants().contains(b);
    }

    public static class ActiveDuel {
        private final UUID player1;
        private final UUID player2;
        private final DuelGameMode mode;
        private final Arena arena;
        private final long startTime;
        private UUID ffaId;
        private Set<UUID> ffaParticipants;
        private boolean botDuel = false;
        private final Set<String> placedBlocks = new HashSet<>();
        private final Map<UUID, Integer> roundWins = new HashMap<>();
        private int maxRounds = 2;

        public ActiveDuel(UUID player1, UUID player2, DuelGameMode mode, Arena arena) {
            this.player1 = player1;
            this.player2 = player2;
            this.mode = mode;
            this.arena = arena;
            this.startTime = System.currentTimeMillis();
            this.roundWins.put(player1, 0);
            this.roundWins.put(player2, 0);
        }

        public UUID getPlayer1() { return player1; }
        public UUID getPlayer2() { return player2; }
        public DuelGameMode getMode() { return mode; }
        public Arena getArena() { return arena; }
        public long getStartTime() { return startTime; }
        public Map<UUID, Integer> getRoundWins() { return roundWins; }
        public int getMaxRounds() { return maxRounds; }
        public void setMaxRounds(int maxRounds) { this.maxRounds = maxRounds; }

        public boolean isBotDuel() { return botDuel; }
        public void setBotDuel(boolean botDuel) { this.botDuel = botDuel; }

        public UUID getOpponent(UUID uuid) {
            return uuid.equals(player1) ? player2 : player1;
        }

        public void setFFA(UUID ffaId, Set<UUID> participants) {
            this.ffaId = ffaId;
            this.ffaParticipants = participants;
        }

        public boolean isFFA() {
            return ffaId != null;
        }

        public UUID getFFAId() {
            return ffaId;
        }

        public Set<UUID> getFFAParticipants() {
            return ffaParticipants;
        }

        public void addPlacedBlock(Location loc) {
            placedBlocks.add(loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ());
        }

        public boolean isPlayerPlacedBlock(Location loc) {
            return placedBlocks.contains(loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ());
        }
    }
}
