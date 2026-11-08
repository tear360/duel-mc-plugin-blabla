package fr.duelplugin.listeners;

import fr.duelplugin.DuelPlugin;
import fr.duelplugin.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class PlayerListener implements Listener {

    private final DuelPlugin plugin;

    public PlayerListener(DuelPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (plugin.getBanManager().isBanned(event.getPlayer().getUniqueId())) {
            event.setResult(PlayerLoginEvent.Result.KICK_BANNED);
            event.kickMessage(plugin.getBanManager().buildBanScreen(event.getPlayer().getUniqueId()));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerManager().getDuelPlayer(player.getUniqueId());

        if (plugin.getLobbyManager().isLobbySet()) {
            plugin.getLobbyManager().teleportToLobby(player);
            plugin.getScoreboardManager().createLobbyScoreboard(player, null, null);
        }

        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFlying(false);
        player.setAllowFlight(false);

        giveLobbyItems(player);

        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false, false));

        event.setJoinMessage(null);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            if (plugin.getFriendsManager().isFriend(online.getUniqueId(), player.getUniqueId())) {
                online.sendMessage(plugin.getLanguageManager().msgRaw(online, "lobby_join", "%player%", player.getName()));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getQueueManager().leaveQueue(player);
        plugin.getDuelManager().handleDisconnect(player);
        plugin.getScoreboardManager().removeScoreboard(player);
        plugin.getTabManager().onPlayerQuit(player.getUniqueId());
        plugin.setBuildMode(player.getUniqueId(), false);
        event.setQuitMessage(null);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            if (plugin.getFriendsManager().isFriend(online.getUniqueId(), player.getUniqueId())) {
                online.sendMessage(plugin.getLanguageManager().msgRaw(online, "lobby_quit", "%player%", player.getName()));
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!plugin.getDuelManager().isInDuel(player)) return;

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setDeathMessage(null);

        var duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null) return;

        if (duel.isBotDuel()) {
            plugin.getDuelManager().endDuel(player.getUniqueId(), duel.getPlayer2(), player.getUniqueId());
            return;
        }

        Player killer = player.getKiller();
        if (killer != null) {
            var killerDuel = plugin.getDuelManager().getDuel(killer.getUniqueId());
            boolean sameDuel = killerDuel != null && (duel.isFFA()
                    ? killerDuel.isFFA() && java.util.Objects.equals(duel.getFFAId(), killerDuel.getFFAId())
                    : killer.getUniqueId().equals(duel.getOpponent(player.getUniqueId())));
            if (!sameDuel) killer = null;
        }

        if (killer == null) {
            killer = plugin.getServer().getPlayer(duel.getOpponent(player.getUniqueId()));
        }

        if (killer != null) {
            plugin.getDuelManager().endDuel(killer.getUniqueId(), killer.getUniqueId(), player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getDuelManager().isInDuel(player)) return;
        if (plugin.isBuildMode(player.getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.getDuelManager().isInDuel(player)) return;

        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() == player.getInventory()) {
            ItemStack item = event.getCurrentItem();
            if (isLobbyItem(item) || isVipLobbyItem(item)) {
                event.setCancelled(true);
            }
        }
        int slot = event.getRawSlot();
        if (slot == 38 || slot == 39 || slot == 40 || slot == 41) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.getDuelManager().isInDuel(player)) return;

        for (int slot : event.getRawSlots()) {
            if (slot == 38 || slot == 39 || slot == 40 || slot == 41) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onWindChargeUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDuelManager().isInDuel(player)) return;

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType() == Material.WIND_CHARGE && plugin.getRankManager().isVip(player.getUniqueId())) {
            if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && plugin.getLobbyManager().isLobbySet() && !plugin.getDuelManager().isInDuel(player)) {
                        player.getInventory().setItemInOffHand(new ItemStack(Material.WIND_CHARGE, 64));
                    }
                }, 15L);
            }
        }

        ItemStack offhandCheck = player.getInventory().getItemInOffHand();
        if (offhandCheck.getType() == Material.WIND_CHARGE) {
            return;
        }

        ItemStack hand = event.getItem();
        if (hand != null && hand.getType() == Material.ELYTRA) {
            event.setCancelled(true);
        }

        ItemStack chest = player.getInventory().getChestplate();
        if (chest != null && chest.getType() == Material.ELYTRA) {
            event.setCancelled(true);
        }
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
        if (isLobbyItem(dropped) || isVipLobbyItem(dropped)) {
            event.setCancelled(true);
        }
        if (dropped.getType() == Material.WIND_CHARGE && plugin.getRankManager().isVip(player.getUniqueId())) {
            event.setCancelled(true);
        }
        if (dropped.getType() == Material.ELYTRA && plugin.getRankManager().isVip(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private boolean isLobbyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType() == Material.NETHERITE_SWORD || item.getType() == Material.CRAFTING_TABLE || item.getType() == Material.NETHER_STAR || item.getType() == Material.GRINDSTONE;
    }

    private boolean isVipLobbyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType() == Material.WIND_CHARGE) return true;
        if (item.getType() == Material.ELYTRA) return true;
        return false;
    }

    public static void giveLobbyItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);

        DuelPlugin plugin = DuelPlugin.getInstance();

        player.getInventory().setItem(0, new ItemBuilder(Material.NETHERITE_SWORD)
                .name(plugin.getLanguageManager().msgRaw(player, "lobby_item_queue"))
                .lore("", plugin.getLanguageManager().msgRaw(player, "lobby_item_queue_lore"), "").build());

        player.getInventory().setItem(2, new ItemBuilder(Material.NETHER_STAR)
                .name(plugin.getLanguageManager().msgRaw(player, "lobby_item_party"))
                .lore("", plugin.getLanguageManager().msgRaw(player, "lobby_item_party_lore"), "").build());

        player.getInventory().setItem(6, new ItemBuilder(Material.CRAFTING_TABLE)
                .name(plugin.getLanguageManager().msgRaw(player, "lobby_item_kits"))
                .lore("", plugin.getLanguageManager().msgRaw(player, "lobby_item_kits_lore"), "").build());

        player.getInventory().setItem(8, new ItemBuilder(Material.GRINDSTONE)
                .name(plugin.getLanguageManager().msgRaw(player, "lobby_item_settings"))
                .lore("", plugin.getLanguageManager().msgRaw(player, "lobby_item_settings_lore"), "").build());

        if (plugin != null && plugin.getRankManager().isVip(player.getUniqueId())) {
            ItemStack windCharge = new ItemStack(Material.WIND_CHARGE, 64);
            player.getInventory().setItemInOffHand(windCharge);

            ItemStack elytra = new ItemStack(Material.ELYTRA);
            ItemMeta elytraMeta = elytra.getItemMeta();
            if (elytraMeta != null) {
                elytraMeta.displayName(net.kyori.adventure.text.Component.text(plugin.getLanguageManager().msgRaw(player, "lobby_item_elysta"), net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
                elytraMeta.lore(java.util.Arrays.asList(
                        net.kyori.adventure.text.Component.text(plugin.getLanguageManager().msgRaw(player, "lobby_item_elysta_lore1")),
                        net.kyori.adventure.text.Component.text(plugin.getLanguageManager().msgRaw(player, "lobby_item_elysta_lore2"))
                ));
                elytraMeta.setUnbreakable(true);
                elytra.setItemMeta(elytraMeta);
            }
            player.getInventory().setChestplate(elytra);
        }
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (plugin.getDuelManager().isInDuel(player)) return;
        if (plugin.isBuildMode(player.getUniqueId())) return;
        if (event.getClickedBlock() == null) return;
        if (event.getItem() != null && event.getItem().getType() == Material.ELYTRA) return;
        Material type = event.getClickedBlock().getType();
        if (type.name().endsWith("_DOOR") || type.name().endsWith("_TRAPDOOR") || type.name().endsWith("_GATE")
                || type.name().endsWith("_FENCE_GATE")
                || type == Material.LEVER || type.name().endsWith("_BUTTON")
                || type == Material.CHEST || type == Material.TRAPPED_CHEST
                || type == Material.BARREL || type == Material.BREWING_STAND
                || type == Material.CRAFTING_TABLE || type == Material.ENCHANTING_TABLE
                || type == Material.ANVIL || type == Material.CHIPPED_ANVIL || type == Material.DAMAGED_ANVIL
                || type == Material.ENDER_CHEST || type == Material.JUKEBOX
                || type == Material.NOTE_BLOCK
                || type.name().contains("SHULKER_BOX") || type == Material.BELL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDuelManager().isInDuel(player)) return;
        if (plugin.isBuildMode(player.getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDuelManager().isInDuel(player)) return;
        if (plugin.isBuildMode(player.getUniqueId())) return;
        event.setCancelled(true);
    }
}
