package fr.duelplugin.listeners;

import fr.duelplugin.DuelPlugin;
import fr.duelplugin.models.DuelGameMode;
import fr.duelplugin.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LobbyItemListener implements Listener {

    private final DuelPlugin plugin;
    private final Map<UUID, Inventory> queueGUIs = new HashMap<>();
    private final java.util.Set<UUID> navigating = new java.util.HashSet<>();

    private String guiTitle(Player player, String key) {
        return plugin.getLanguageManager().msgRaw(player, key).replaceAll("§[0-9a-fk-or]", "");
    }

    private boolean isDuelGUI(Player player, String title) {
        if (title.equals(guiTitle(player, "gui_mode_select"))) return true;
        String prefix = guiTitle(player, "gui_duel_title").replace("%target%", "").trim();
        return !prefix.isEmpty() && title.startsWith(prefix);
    }

    private static final Map<String, net.kyori.adventure.text.format.TextColor> COLOR_MAP = Map.of(
            "§c", net.kyori.adventure.text.format.NamedTextColor.RED,
            "§6", net.kyori.adventure.text.format.NamedTextColor.GOLD,
            "§e", net.kyori.adventure.text.format.NamedTextColor.YELLOW,
            "§a", net.kyori.adventure.text.format.NamedTextColor.GREEN,
            "§b", net.kyori.adventure.text.format.NamedTextColor.AQUA,
            "§d", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE,
            "§5", net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE,
            "§f", net.kyori.adventure.text.format.NamedTextColor.WHITE,
            "§7", net.kyori.adventure.text.format.NamedTextColor.GRAY,
            "§0", net.kyori.adventure.text.format.NamedTextColor.BLACK
    );

    public LobbyItemListener(DuelPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDuelManager().isInDuel(player)) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item != null && isLobbyItem(item)) {
            event.setCancelled(true);
            if (item.getType() == Material.NETHERITE_SWORD) {
                openQueueGUI(player);
            } else if (item.getType() == Material.CRAFTING_TABLE) {
                plugin.getKitEditorGUI().openModeSelector(player);
            } else if (item.getType() == Material.NETHER_STAR) {
                plugin.getPartyGUI().openPartyMenu(player);
            } else if (item.getType() == Material.GRINDSTONE) {
                plugin.getServer().dispatchCommand(player, "settings");
            }
            return;
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType() == Material.WIND_CHARGE && plugin.getRankManager().isVip(player.getUniqueId())) {
            return;
        }
    }

    private void openQueueGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45,
                net.kyori.adventure.text.Component.text(plugin.getLanguageManager().msgRaw(player, "queue_title"),
                        net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE, net.kyori.adventure.text.format.TextDecoration.BOLD));

        for (int i = 0; i < 45; i++) {
            inv.setItem(i, new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build());
        }

        int[] border = {0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,37,38,39,40,41,42,43,44};
        for (int slot : border) {
            inv.setItem(slot, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }

        DuelGameMode[] modes = DuelGameMode.values();
        int[] slots = {10, 11, 12, 13, 14, 19, 20, 21};

        for (int i = 0; i < modes.length && i < slots.length; i++) {
            DuelGameMode mode = modes[i];
            int queueSize = plugin.getQueueManager().getQueueSize(mode);
            boolean hasArena = !mode.isArenaRestricted() || plugin.getArenaManager().getAvailableArena(mode) != null;

            Material icon = getModeIcon(mode);

            inv.setItem(slots[i], new ItemBuilder(icon)
                    .name(mode.getColoredName())
                    .lore(
                            "",
                            mode.isArenaRestricted() ?
                                    (hasArena ? plugin.getLanguageManager().msgRaw(player, "gui_arenas_available") : plugin.getLanguageManager().msgRaw(player, "gui_no_arena")) :
                                    plugin.getLanguageManager().msgRaw(player, "gui_free_mode"),
                            plugin.getLanguageManager().msgRaw(player, "gui_blocks") + (mode.canBreakBlocks() ? plugin.getLanguageManager().msgRaw(player, "gui_blocks_breakable") : plugin.getLanguageManager().msgRaw(player, "gui_blocks_unbreakable")),
                            "&dQueue: &f" + queueSize + " player" + (queueSize != 1 ? "s" : ""),
                            "",
                            plugin.getLanguageManager().msgRaw(player, "gui_click_to_play")
                    ).build());
        }

        inv.setItem(23, new ItemBuilder(Material.ZOMBIE_HEAD)
                .name(plugin.getLanguageManager().msgRaw(player, "bot_queue_name"))
                .lore(
                        "",
                        plugin.getLanguageManager().msgRaw(player, "bot_queue_lore"),
                        "",
                        plugin.getLanguageManager().msgRaw(player, "gui_click_to_play")
                ).build());

        queueGUIs.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.getDuelManager().isInDuel(player)) return;

        String cleanTitle = event.getView().getTitle().replaceAll("§[0-9a-fk-or]", "");
        Material clickedType = event.getCurrentItem() != null ? event.getCurrentItem().getType() : Material.AIR;

        if (cleanTitle.startsWith("Kit ") && !cleanTitle.equals(guiTitle(player, "gui_kit_editor_title"))) {
            handleKitEditorClick(event, player);
            return;
        }

        if (isPluginGUI(cleanTitle, event)) {
            event.setCancelled(true);
            if (clickedType == Material.AIR) return;
        } else {
            return;
        }

        if (queueGUIs.containsKey(player.getUniqueId()) && event.getView().getTopInventory() == queueGUIs.get(player.getUniqueId())) {
            handleQueueClick(event, player);
            return;
        }

        if (cleanTitle.equals("Party") || cleanTitle.contains("Party (Leader)") || cleanTitle.equals("Party")) {
            handlePartyClick(event, player, cleanTitle);
            return;
        }

        if (cleanTitle.equals("Kick un membre") || cleanTitle.equals(guiTitle(player, "gui_party_kick"))) {
            handleKickClick(event, player);
            return;
        }

        if (cleanTitle.equals("Transférer le leadership") || cleanTitle.equals(guiTitle(player, "gui_party_transfer"))) {
            handleTransferClick(event, player);
            return;
        }

        if (cleanTitle.equals("Choisir un mode FFA") || cleanTitle.equals(guiTitle(player, "gui_party_ffa"))) {
            handleFFAClick(event, player);
            return;
        }

        if (cleanTitle.contains("Badge VIP") || cleanTitle.contains("VIP Badge") || cleanTitle.equals(guiTitle(player, "vip_badges_title"))) {
            handleBadgeClick(event, player);
            return;
        }

        if (cleanTitle.contains("Kit Editor") || cleanTitle.contains("Éditeur de kits") || cleanTitle.equals(guiTitle(player, "gui_kit_editor_title"))) {
            handleKitEditorModeSelect(event, player);
            return;
        }

        if (cleanTitle.equals("Duel Settings")) {
            handleDuelSettingsClick(event, player);
            return;
        }

        if (cleanTitle.contains("Choisir une pièce") || cleanTitle.contains("Select Armor Piece") || cleanTitle.equals(guiTitle(player, "gui_kit_armor_select"))) {
            handleArmorPieceClick(event, player);
            return;
        }

        if (cleanTitle.contains("Choisir un pattern") || cleanTitle.contains("Select Pattern") || cleanTitle.equals(guiTitle(player, "gui_kit_pattern_select"))) {
            handlePatternClick(event, player);
            return;
        }

        if (cleanTitle.contains("Choisir un matériau") || cleanTitle.contains("Select Material") || cleanTitle.equals(guiTitle(player, "gui_kit_material_select"))) {
            handleMaterialClick(event, player);
            return;
        }

        if (cleanTitle.contains("Sélection de mode") || cleanTitle.contains("Mode Selection")
                || cleanTitle.contains("Défi →") || cleanTitle.contains("Challenge →") || isDuelGUI(player, cleanTitle)) {
            handleDuelGUIClick(event, player, cleanTitle);
            return;
        }
    }

    private boolean isPluginGUI(String cleanTitle, InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        if (cleanTitle.equals("Party") || cleanTitle.contains("Party (Leader)")) return true;
        if (cleanTitle.equals("Kick un membre") || cleanTitle.equals(guiTitle(player, "gui_party_kick"))) return true;
        if (cleanTitle.equals("Transférer le leadership") || cleanTitle.equals(guiTitle(player, "gui_party_transfer"))) return true;
        if (cleanTitle.equals("Choisir un mode FFA") || cleanTitle.equals(guiTitle(player, "gui_party_ffa"))) return true;
        if (cleanTitle.contains("Badge VIP") || cleanTitle.contains("VIP Badge") || cleanTitle.equals(guiTitle(player, "vip_badges_title"))) return true;
        if (cleanTitle.contains("Kit Editor") || cleanTitle.contains("Éditeur de kits") || cleanTitle.equals(guiTitle(player, "gui_kit_editor_title"))) return true;
        if (cleanTitle.equals("Duel Settings")) return true;
        if (cleanTitle.startsWith("Kit ")) return true;
        if (cleanTitle.contains("Choisir une pièce") || cleanTitle.contains("Select Armor Piece") || cleanTitle.equals(guiTitle(player, "gui_kit_armor_select"))) return true;
        if (cleanTitle.contains("Choisir un pattern") || cleanTitle.contains("Select Pattern") || cleanTitle.equals(guiTitle(player, "gui_kit_pattern_select"))) return true;
        if (cleanTitle.contains("Choisir un matériau") || cleanTitle.contains("Select Material") || cleanTitle.equals(guiTitle(player, "gui_kit_material_select"))) return true;
        if (cleanTitle.contains("Sélection de mode") || cleanTitle.contains("Mode Selection")
                || cleanTitle.contains("Défi →") || cleanTitle.contains("Challenge →") || isDuelGUI(player, cleanTitle)) return true;
        if (queueGUIs.containsKey(event.getWhoClicked().getUniqueId())) return true;
        if (event.getView().getTopInventory() != null && event.getView().getTopInventory().getItem(10) != null
                && isModeIcon(event.getView().getTopInventory().getItem(10).getType())) return true;
        return false;
    }

    private void handleDuelGUIClick(InventoryClickEvent event, Player player, String cleanTitle) {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (item.getType() == Material.BLACK_STAINED_GLASS_PANE || item.getType() == Material.PURPLE_STAINED_GLASS_PANE) return;

        if (item.getType() == Material.REDSTONE && event.getSlot() == 31) {
            plugin.getDuelSettingsGUI().open(player);
            return;
        }

        String name = item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : "";
        for (DuelGameMode mode : DuelGameMode.values()) {
            if (name.contains(mode.getColoredName())) {
                if (cleanTitle.contains("Défi →") || cleanTitle.contains("Challenge →") || isDuelGUI(player, cleanTitle)) {
                    UUID targetUuid = plugin.getDuelGUI().getPendingTarget(player.getUniqueId());
                    if (targetUuid != null) {
                        Player target = org.bukkit.Bukkit.getPlayer(targetUuid);
                        if (target != null) {
                            if (mode.isArenaRestricted() && plugin.getArenaManager().getAvailableArena(mode) == null) {
                                player.sendMessage(plugin.getLanguageManager().msg(player, "queue_no_arena", "%mode%", mode.getDisplayName()));
                                player.closeInventory();
                                return;
                            }
                            if (plugin.getDuelManager().isInDuel(player) || plugin.getDuelManager().isInDuel(target)) {
                                player.sendMessage(plugin.getLanguageManager().msg(player, "duel_both_in_duel"));
                                player.closeInventory();
                                return;
                            }
                            if (plugin.getDuelManager().sendRequest(player, target, mode)) {
                                player.sendMessage(plugin.getLanguageManager().msg(player, "duel_sent", "%player%", target.getName()));
                            } else {
                                player.sendMessage(plugin.getLanguageManager().msg(player, "duel_accept_fail"));
                            }
                        } else {
                            player.sendMessage(plugin.getLanguageManager().msg(player, "duel_target_online"));
                        }
                    }
                    player.closeInventory();
                }
                break;
            }
        }
    }

    private boolean hasDuelGUITitle(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return false;
        String cleanTitle = event.getView().getTitle().replaceAll("§[0-9a-fk-or]", "");
        return cleanTitle.contains("Sélection de mode") || cleanTitle.contains("Mode Selection")
                || cleanTitle.contains("Défi →") || cleanTitle.contains("Challenge →") || isDuelGUI(player, cleanTitle);
    }

    private void handleQueueClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (item.getType() == Material.BLACK_STAINED_GLASS_PANE || item.getType() == Material.PURPLE_STAINED_GLASS_PANE) return;

        String name = item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : "";

        if (item.getType() == Material.ZOMBIE_HEAD) {
            if (plugin.getDuelManager().isInDuel(player) || plugin.getQueueManager().isInAnyQueue(player)) {
                player.sendMessage(plugin.getLanguageManager().msg(player, "bot_in_duel"));
                return;
            }
            if (DuelGameMode.SWORD.isArenaRestricted() && plugin.getArenaManager().getAvailableArena(DuelGameMode.SWORD) == null) {
                player.sendMessage(plugin.getLanguageManager().msg(player, "bot_start_fail_arena"));
                return;
            }
            plugin.getDuelBotManager().startBotDuel(player);
            player.closeInventory();
            return;
        }

        for (DuelGameMode mode : DuelGameMode.values()) {
            if (name.contains(mode.getColoredName())) {
                if (plugin.getQueueManager().isInQueue(player, mode)) {
                    plugin.getQueueManager().leaveQueue(player, mode);
                    player.sendMessage(plugin.getLanguageManager().msg(player, "queue_left", "%mode%", mode.getDisplayName()));
                    openQueueGUI(player);
                    return;
                }
                if (mode.isArenaRestricted() && plugin.getArenaManager().getAvailableArena(mode) == null) {
                    player.sendMessage(plugin.getLanguageManager().msg(player, "queue_no_arena", "%mode%", mode.getDisplayName()));
                    return;
                }
                if (plugin.getQueueManager().isInAnyQueue(player)) {
                    plugin.getQueueManager().leaveQueue(player);
                }
                plugin.getQueueManager().joinQueue(player, mode);
                player.sendMessage(plugin.getLanguageManager().msg(player, "queue_joined", "%mode%", mode.getDisplayName()));
                player.closeInventory();
                return;
            }
        }
    }

    private void handlePartyClick(InventoryClickEvent event, Player player, String cleanTitle) {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (item.getType() == Material.BLACK_STAINED_GLASS_PANE || item.getType() == Material.PURPLE_STAINED_GLASS_PANE) return;

        Material type = item.getType();

        if (cleanTitle.equals("Party")) {
            if (type == Material.NETHER_STAR) {
                if (plugin.getPartyManager().createParty(player)) {
                    player.closeInventory();
                    player.sendMessage(plugin.getLanguageManager().msg(player, "party_created"));
                } else {
                    player.sendMessage(plugin.getLanguageManager().msg(player, "party_invite_expired"));
                }
                return;
            }
            if (type == Material.GREEN_WOOL) {
                UUID leaderUuid = plugin.getPartyManager().getPendingInvite(player.getUniqueId());
                if (leaderUuid != null) {
                    plugin.getPartyManager().acceptInvite(player);
                    player.closeInventory();
                    player.sendMessage(plugin.getLanguageManager().msg(player, "party_joined_broadcast", "%player%", player.getName()));
                }
                return;
            }
            if (type == Material.RED_WOOL) {
                plugin.getPartyManager().declineInvite(player);
                player.closeInventory();
                player.sendMessage(plugin.getLanguageManager().msg(player, "friend_deny_refused"));
                return;
            }
            if (type == Material.BARRIER) {
                plugin.getPartyManager().leaveParty(player);
                player.closeInventory();
                player.sendMessage(plugin.getLanguageManager().msg(player, "party_left"));
                return;
            }
            if (type == Material.ARROW) {
                player.closeInventory();
                return;
            }
            return;
        }

        if (cleanTitle.contains("Party (Leader)")) {
            if (type == Material.PLAYER_HEAD && event.getSlot() == 10) {
                player.closeInventory();
                player.sendMessage(plugin.getLanguageManager().msg(player, "party_help_invite"));
                return;
            }
            if (type == Material.NETHERITE_SWORD) {
                plugin.getPartyGUI().openFFASelector(player);
                return;
            }
            if (type == Material.TRIDENT) {
                plugin.getPartyGUI().openTransferSelector(player);
                return;
            }
            if (type == Material.RED_WOOL && event.getSlot() == 14) {
                fr.duelplugin.managers.PartyManager.Party party = plugin.getPartyManager().getParty(player.getUniqueId());
                if (party != null) {
                    java.util.Set<UUID> allMembers = new java.util.HashSet<>(party.getMembers());
                    allMembers.add(party.getLeader());
                    for (UUID m : allMembers) {
                        Player member = org.bukkit.Bukkit.getPlayer(m);
                        if (member != null && member.isOnline()) {
                            member.showTitle(net.kyori.adventure.title.Title.title(
                                    net.kyori.adventure.text.Component.text(plugin.getLanguageManager().msgRaw(member, "title_party_disbanded"), net.kyori.adventure.text.format.NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD),
                                    net.kyori.adventure.text.Component.empty(),
                                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(1))
                            ));
                        }
                    }
                }
                plugin.getPartyManager().disbandParty(player);
                player.closeInventory();
                player.sendMessage(plugin.getLanguageManager().msg(player, "party_disbanded"));
                return;
            }
            if (type == Material.BARRIER) {
                plugin.getPartyManager().leaveParty(player);
                player.closeInventory();
                player.sendMessage(plugin.getLanguageManager().msg(player, "party_left"));
                return;
            }
            if (type == Material.ARROW) {
                player.closeInventory();
                return;
            }
            return;
        }
    }

    private void handleKickClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        if (item.getType() == Material.ARROW) {
            plugin.getPartyGUI().openPartyMenu(player);
            return;
        }
        if (item.getType() != Material.PLAYER_HEAD) return;

        fr.duelplugin.managers.PartyManager.Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            player.closeInventory();
            return;
        }

        UUID targetUuid = getHeadOwner(item);
        if (targetUuid == null || targetUuid.equals(player.getUniqueId()) || !party.getMembers().contains(targetUuid)) {
            return;
        }

        Player member = Bukkit.getPlayer(targetUuid);
        if (member == null) return;

        plugin.getPartyManager().kickPlayer(player, member);
        player.sendMessage(plugin.getLanguageManager().msg(player, "party_kicked_broadcast", "%player%", member.getName()));
        plugin.getPartyGUI().openPartyMenu(player);
    }

    private void handleTransferClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        if (item.getType() == Material.ARROW) {
            plugin.getPartyGUI().openPartyMenu(player);
            return;
        }
        if (item.getType() != Material.PLAYER_HEAD) return;

        fr.duelplugin.managers.PartyManager.Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            player.closeInventory();
            return;
        }

        UUID targetUuid = getHeadOwner(item);
        if (targetUuid == null || targetUuid.equals(player.getUniqueId()) || !party.getMembers().contains(targetUuid)) {
            return;
        }

        Player member = Bukkit.getPlayer(targetUuid);
        if (member == null) return;

        plugin.getPartyManager().transferLeadership(player, member);
        player.closeInventory();
        player.sendMessage(plugin.getLanguageManager().msg(player, "party_transfer", "%player%", member.getName()));
    }

    private UUID getHeadOwner(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta && skullMeta.getOwningPlayer() != null) {
            return skullMeta.getOwningPlayer().getUniqueId();
        }
        return null;
    }

    private void handleFFAClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        if (item.getType() == Material.ARROW) {
            plugin.getPartyGUI().openPartyMenu(player);
            return;
        }
        for (DuelGameMode mode : DuelGameMode.values()) {
            if (item.getType() == getModeIcon(mode)) {
                if (mode.isArenaRestricted() && plugin.getArenaManager().getAvailableArena(mode) == null) {
                    player.sendMessage(plugin.getLanguageManager().msg(player, "queue_no_arena", "%mode%", mode.getDisplayName()));
                    return;
                }
                plugin.getDuelManager().startPartyFFA(player, mode);
                player.closeInventory();
                return;
            }
        }
    }

    private void handleBadgeClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (item.getType() == Material.BLACK_STAINED_GLASS_PANE || item.getType() == Material.LIME_STAINED_GLASS_PANE) return;
        if (item.getType() != Material.PAPER) return;

        String rawName = item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : "";
        if (rawName.isEmpty()) return;
        String badge = rawName.replaceAll("§[0-9a-fk-or]", "");
        if (badge.isEmpty()) return;

        String currentBadge = plugin.getVipManager().getBadge(player.getUniqueId());
        if (badge.equals(currentBadge)) {
            plugin.getVipManager().setBadge(player.getUniqueId(), plugin.getVipManager().getDefaultBadge());
        } else {
            plugin.getVipManager().setBadge(player.getUniqueId(), badge);
        }
        openBadgeGUI(player);
    }

    private void openBadgeGUI(Player player) {
        UUID uuid = player.getUniqueId();
        String currentBadge = plugin.getVipManager().getBadge(uuid);
        java.util.List<String> badges = plugin.getVipManager().getAvailableBadges();

        String colorCode = plugin.getVipManager().getNameColor(uuid);
        if (colorCode == null) colorCode = "§d";
        net.kyori.adventure.text.format.TextColor badgeColor = COLOR_MAP.getOrDefault(colorCode, net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE);

        Inventory gui = Bukkit.createInventory(null, 27, net.kyori.adventure.text.Component.text(
                plugin.getLanguageManager().msgRaw(player, "vip_badges_title"),
                net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE,
                net.kyori.adventure.text.format.TextDecoration.BOLD));

        for (int i = 0; i < 27; i++) {
            gui.setItem(i, new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
        }

        int slot = 10;
        for (String badge : badges) {
            gui.setItem(slot - 1, new ItemStack(Material.LIME_STAINED_GLASS_PANE));
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            if (meta != null) {
                boolean isSelected = badge.equals(currentBadge);
                meta.displayName(net.kyori.adventure.text.Component.text(badge, badgeColor, net.kyori.adventure.text.format.TextDecoration.BOLD));
                if (isSelected) {
                    meta.lore(java.util.List.of(
                        net.kyori.adventure.text.Component.text(plugin.getLanguageManager().msgRaw(player, "vip_badge_selected"), net.kyori.adventure.text.format.NamedTextColor.GREEN)
                    ));
                } else {
                    meta.lore(java.util.List.of(
                        net.kyori.adventure.text.Component.text(plugin.getLanguageManager().msgRaw(player, "vip_badge_click"), net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    ));
                }
                paper.setItemMeta(meta);
            }
            gui.setItem(slot, paper);
            slot += 2;
        }

        player.openInventory(gui);
    }

    private void handleKitEditorModeSelect(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (item.getType() == Material.BLACK_STAINED_GLASS_PANE || item.getType() == Material.PURPLE_STAINED_GLASS_PANE) return;

        String name = item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : "";
        for (DuelGameMode mode : DuelGameMode.values()) {
            if (name.contains(mode.getColoredName())) {
                navigating.add(player.getUniqueId());
                plugin.getKitEditorGUI().openKitEditor(player, mode);
                break;
            }
        }
    }

    private void handleDuelSettingsClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 11) {
            boolean current = plugin.getSettingsManager().discordNotificationsEnabled(player.getUniqueId());
            plugin.getSettingsManager().setDiscordNotifications(player.getUniqueId(), !current);
            plugin.getDuelSettingsGUI().open(player);
        } else if (slot == 15) {
            int current = plugin.getSettingsManager().getRoundCount(player.getUniqueId());
            if (event.isRightClick()) {
                current = Math.max(1, current - 1);
            } else {
                current = Math.min(5, current + 1);
            }
            plugin.getSettingsManager().setRoundCount(player.getUniqueId(), current);
            plugin.getDuelSettingsGUI().open(player);
        } else if (slot == 13) {
            plugin.getDuelGUI().openModeSelector(player, null);
        }
    }

    private void handleKitEditorClick(InventoryClickEvent event, Player player) {
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            event.setCancelled(true);
            return;
        }

        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                || event.getClick() == org.bukkit.event.inventory.ClickType.DROP
                || event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getSlot();
        if (slot < 0) return;

        if (slot >= 45) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) return;
            Material type = item.getType();

            if (type == Material.LIME_STAINED_GLASS_PANE) {
                DuelGameMode mode = plugin.getKitEditorGUI().getEditingMode(player.getUniqueId());
                if (mode != null) {
                    plugin.getKitEditorGUI().saveKit(player, mode, event.getInventory());
                }
                plugin.getKitEditorGUI().removeEditingMode(player.getUniqueId());
                plugin.getKitEditorGUI().openModeSelector(player);
                return;
            }
            if (type == Material.RED_STAINED_GLASS_PANE) {
                DuelGameMode mode = plugin.getKitEditorGUI().getEditingMode(player.getUniqueId());
                if (mode != null) {
                    plugin.getKitEditorGUI().resetKit(player, mode);
                }
                plugin.getKitEditorGUI().removeEditingMode(player.getUniqueId());
                plugin.getKitEditorGUI().openModeSelector(player);
                return;
            }
            if (type == Material.ARMOR_STAND) {
                if (!plugin.getRankManager().isVip(player.getUniqueId())) {
                    player.sendMessage(plugin.getLanguageManager().msg(player, "party_vip_only"));
                    return;
                }
                navigating.add(player.getUniqueId());
                plugin.getKitEditorGUI().openArmorPieceSelector(player);
                return;
            }
            if (type == Material.ARROW) {
                plugin.getKitEditorGUI().removeEditingMode(player.getUniqueId());
                plugin.getKitEditorGUI().openModeSelector(player);
                return;
            }
            return;
        }
    }

    private void handleArmorPieceClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (item.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        if (item.getType() == Material.ARROW) {
            DuelGameMode mode = plugin.getKitEditorGUI().getEditingMode(player.getUniqueId());
            if (mode != null) {
                navigating.add(player.getUniqueId());
                plugin.getKitEditorGUI().openKitEditor(player, mode);
            }
            return;
        }

        int slot = event.getSlot();
        int armorSlot = -1;
        if (slot == 10) armorSlot = 3;
        else if (slot == 12) armorSlot = 2;
        else if (slot == 14) armorSlot = 1;
        else if (slot == 16) armorSlot = 0;

        if (armorSlot >= 0) {
            navigating.add(player.getUniqueId());
            plugin.getKitEditorGUI().openPatternSelector(player, armorSlot);
        }
    }

    private void handlePatternClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (item.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        if (item.getType() == Material.ARROW) {
            navigating.add(player.getUniqueId());
            plugin.getKitEditorGUI().openArmorPieceSelector(player);
            return;
        }

        if (item.getType() == Material.SMITHING_TABLE || item.getType() == Material.ANVIL) return;

        TrimPattern[] patterns = {
                TrimPattern.SENTRY, TrimPattern.DUNE, TrimPattern.COAST, TrimPattern.WILD,
                TrimPattern.WARD, TrimPattern.EYE, TrimPattern.VEX, TrimPattern.TIDE,
                TrimPattern.SNOUT, TrimPattern.RIB, TrimPattern.SPIRE, TrimPattern.WAYFINDER,
                TrimPattern.SHAPER, TrimPattern.SILENCE, TrimPattern.RAISER, TrimPattern.HOST,
                TrimPattern.FLOW, TrimPattern.BOLT
        };
        Material[] patternIcons = {
                Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE
        };

        for (int i = 0; i < patterns.length && i < patternIcons.length; i++) {
            if (item.getType() == patternIcons[i]) {
                navigating.add(player.getUniqueId());
                plugin.getKitEditorGUI().openMaterialSelector(player, patterns[i]);
                return;
            }
        }
    }

    private void handleMaterialClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (item.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        if (item.getType() == Material.ARROW) {
            navigating.add(player.getUniqueId());
            int armorSlot = plugin.getKitEditorGUI().getEditingArmorSlot(player.getUniqueId());
            plugin.getKitEditorGUI().openPatternSelector(player, armorSlot);
            return;
        }

        TrimMaterial[] materials = {
                TrimMaterial.IRON, TrimMaterial.COPPER, TrimMaterial.GOLD,
                TrimMaterial.DIAMOND, TrimMaterial.EMERALD, TrimMaterial.LAPIS,
                TrimMaterial.NETHERITE, TrimMaterial.AMETHYST
        };
        Material[] materialIcons = {
                Material.IRON_INGOT, Material.COPPER_INGOT, Material.GOLD_INGOT,
                Material.DIAMOND, Material.EMERALD, Material.LAPIS_LAZULI,
                Material.NETHERITE_INGOT, Material.AMETHYST_SHARD
        };

        for (int i = 0; i < materials.length && i < materialIcons.length; i++) {
            if (item.getType() == materialIcons[i]) {
                TrimPattern pattern = plugin.getKitEditorGUI().getSelectedPattern(player.getUniqueId());
                if (pattern != null) {
                    plugin.getKitEditorGUI().applyTrimToSlot(player, pattern, materials[i]);
                    int armorSlot = plugin.getKitEditorGUI().getEditingArmorSlot(player.getUniqueId());
                    String slotName = switch (armorSlot) {
                        case 0 -> plugin.getLanguageManager().msgRaw(player, "gui_armor_boots");
                        case 1 -> plugin.getLanguageManager().msgRaw(player, "gui_armor_leggings");
                        case 2 -> plugin.getLanguageManager().msgRaw(player, "gui_armor_chestplate");
                        case 3 -> plugin.getLanguageManager().msgRaw(player, "gui_armor_helmet");
                        default -> "???";
                    };
                    String matName = formatTrimName(materials[i].getKey().getKey());
                    player.sendMessage(plugin.getLanguageManager().msg(player, "gui_trim_applied", "%slot%", slotName, "%pattern%", formatTrimName(pattern.getKey().getKey()), "%material%", matName));
                }
                navigating.add(player.getUniqueId());
                plugin.getKitEditorGUI().openArmorPieceSelector(player);
                return;
            }
        }
    }

    private String formatTrimName(String key) {
        if (key == null) return "???";
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (navigating.remove(uuid)) return;

        String cleanTitle = event.getView().getTitle().replaceAll("§[0-9a-fk-or]", "");

        boolean isKitEditor = cleanTitle.startsWith("Kit ") && !cleanTitle.equals(guiTitle(player, "gui_kit_editor_title"));

        if (isKitEditor) {
            DuelGameMode mode = plugin.getKitEditorGUI().getEditingMode(uuid);
            if (mode != null && !plugin.getDuelManager().isInDuel(player)) {
                Inventory topInv = event.getView().getTopInventory();
                if (topInv != null) {
                    plugin.getKitEditorGUI().saveKit(player, mode, topInv);
                }
            }
            plugin.getKitEditorGUI().removeEditingMode(uuid);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.getDuelManager().isInDuel(player)) return;
        if (event.getView().getTitle() == null) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDuelManager().isInDuel(player)) return;
        if (plugin.getQueueManager().isInAnyQueue(player)) {
            event.setCancelled(true);
            return;
        }
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (isLobbyItem(dropped)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getDuelManager().isInDuel(player)) return;
        if (plugin.getQueueManager().isInAnyQueue(player)) {
            event.setCancelled(true);
        }
    }

    private boolean isLobbyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType() == Material.NETHERITE_SWORD || item.getType() == Material.CRAFTING_TABLE || item.getType() == Material.NETHER_STAR || item.getType() == Material.GRINDSTONE;
    }

    private boolean isModeIcon(Material type) {
        return type == Material.DIAMOND_SWORD || type == Material.DIAMOND_AXE || type == Material.GOLDEN_APPLE
                || type == Material.MACE || type == Material.SHIELD || type == Material.CHORUS_FRUIT
                || type == Material.SPLASH_POTION || type == Material.NETHERITE_HELMET;
    }

    private Material getModeIcon(DuelGameMode mode) {
        return switch (mode) {
            case SWORD -> Material.DIAMOND_SWORD;
            case AXE -> Material.DIAMOND_AXE;
            case UHC -> Material.GOLDEN_APPLE;
            case MACE -> Material.MACE;
            case SMP -> Material.SHIELD;
            case DIASMP -> Material.CHORUS_FRUIT;
            case POT -> Material.SPLASH_POTION;
            case NETHPOT -> Material.NETHERITE_HELMET;
            case VANILLA -> Material.NETHERITE_SWORD;
        };
    }
}
