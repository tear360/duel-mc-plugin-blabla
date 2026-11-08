package fr.duelplugin.managers;

import fr.duelplugin.DuelPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class DiscordBotManager extends ListenerAdapter {

    private final DuelPlugin plugin;
    private JDA jda;
    private final String bugReportChannelId;
    private final String duelResultChannelId;
    private final String reportChannelId;
    private static final String CLOSE_EMOJI = "\u2705";

    private static final int MAX_DISPLAY_NAME_LENGTH = 64;
    private static final int MAX_REPORT_TEXT_LENGTH = 1000;
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern UNSAFE_NAME_CHARS = Pattern.compile("[^A-Za-z0-9_.-]");
    private static final String MARKDOWN_SPECIAL = "\\*_~`|>";

    public DiscordBotManager(DuelPlugin plugin) {
        this.plugin = plugin;
        this.bugReportChannelId = plugin.getConfig().getString("discord.bug-report-channel-id", "");
        this.duelResultChannelId = plugin.getConfig().getString("discord.duel-result-channel-id", "");
        this.reportChannelId = plugin.getConfig().getString("discord.report-channel-id", "");
        initBot();
    }

    private void initBot() {
        String token = plugin.getConfig().getString("discord.bot-token", "");
        if (token == null || token.isBlank()) {
            plugin.getLogger().info("[Discord] No bot token configured, Discord bot disabled.");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token,
                    EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS))
                    .addEventListeners(this)
                    .build();
            plugin.getLogger().info("[Discord] Bot connected successfully.");
        } catch (Exception e) {
            plugin.getLogger().severe("[Discord] Failed to start bot: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdownNow();
        }
    }

    public boolean isEnabled() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    // ─── DUEL RESULT ───────────────────────────────────────────

    public void sendDuelResult(String winnerName, String loserName, String modeName, long durationMs) {
        if (!isEnabled() || duelResultChannelId.isBlank()) return;

        CompletableFuture.runAsync(() -> {
            try {
                TextChannel channel = jda.getTextChannelById(duelResultChannelId);
                if (channel == null) return;

                String duration = formatDuration(durationMs);
                String timestamp = Instant.now().atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_INSTANT);

                channel.sendMessageEmbeds(new net.dv8tion.jda.api.EmbedBuilder()
                        .setTitle("\u2694 Duel Result")
                        .setColor(0x959066)
                        .addField("\ud83c\udfc6 Winner", sanitizeInline(winnerName), true)
                        .addField("\ud83d\udc80 Loser", sanitizeInline(loserName), true)
                        .addField("\ud83c\udfae Mode", sanitizeInline(modeName), true)
                        .addField("\u23f1 Duration", duration, true)
                        .setFooter(plugin.getServerName())
                        .setTimestamp(Instant.parse(timestamp))
                        .build())
                        .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                        .queue();
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] Failed to send duel result: " + e.getMessage());
            }
        });
    }

    // ─── BUG REPORT FORUM POST ────────────────────────────────

    public void createBugReportThread(String playerName, String playerUuid, String bug) {
        if (!isEnabled() || bugReportChannelId.isBlank()) return;

        CompletableFuture.runAsync(() -> {
            try {
                ForumChannel forum = jda.getForumChannelById(bugReportChannelId);
                if (forum == null) return;

                String postName = "bug-" + sanitizeName(playerName) + "-" + System.currentTimeMillis();
                String safePlayer = sanitizeInline(playerName);
                String safeBug = sanitizeContent(bug);

                MessageCreateData content = new MessageCreateBuilder()
                        .addContent("\ud83d\udc1b **Bug report by " + safePlayer + "**\n\n" + safeBug)
                        .addEmbeds(new net.dv8tion.jda.api.EmbedBuilder()
                                .setTitle("\ud83d\udc1b Bug Report")
                                .setColor(0xFF0000)
                                .addField("\ud83d\udc64 Player", safePlayer, true)
                                .addField("\ud83d\udccb Bug", safeBug, false)
                                .setFooter(plugin.getServerName() + " - Bug Report")
                                .setTimestamp(Instant.now())
                                .build())
                        .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                        .build();

                forum.createForumPost(postName, content).queue(post ->
                        post.getMessage().addReaction(Emoji.fromUnicode(CLOSE_EMOJI)).queue());
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] Failed to create bug report forum post: " + e.getMessage());
            }
        });
    }

    // ─── REPORT FORUM POST ─────────────────────────────────────

    public void createReportForumPost(String reporterName, String reportedName, String reason, int reportId) {
        if (!isEnabled() || reportChannelId.isBlank()) return;

        CompletableFuture.runAsync(() -> {
            try {
                ForumChannel forum = jda.getForumChannelById(reportChannelId);
                if (forum == null) return;

                String postName = "report-" + sanitizeName(reportedName) + "-" + reportId;
                String safeReporter = sanitizeInline(reporterName);
                String safeReported = sanitizeInline(reportedName);
                String safeReason = sanitizeContent(reason);

                MessageCreateData content = new MessageCreateBuilder()
                        .addContent("\ud83d\udcdd **Report #" + reportId + " by " + safeReporter + "**\n\n" + safeReason)
                        .addEmbeds(new net.dv8tion.jda.api.EmbedBuilder()
                                .setTitle("\ud83d\udcdd Report #" + reportId)
                                .setColor(0xFFA500)
                                .addField("\ud83d\udc64 Reported player", safeReported, true)
                                .addField("\ud83d\udc64 Reported by", safeReporter, true)
                                .addField("\ud83d\udccb Reason", safeReason, false)
                                .setFooter(plugin.getServerName() + " - Report")
                                .setTimestamp(Instant.now())
                                .build())
                        .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                        .build();

                forum.createForumPost(postName, content).queue(post ->
                        post.getMessage().addReaction(Emoji.fromUnicode(CLOSE_EMOJI)).queue());
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] Failed to create report forum post: " + e.getMessage());
            }
        });
    }

    // ─── REACTION LISTENER ─────────────────────────────────────

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) return;
        if (!event.getEmoji().equals(Emoji.fromUnicode(CLOSE_EMOJI))) return;

        net.dv8tion.jda.api.entities.Member member = event.getMember();
        if (member == null || !member.hasPermission(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) return;

        long channelId = event.getChannel().getIdLong();

        if (bugReportChannelId.isBlank() && reportChannelId.isBlank()) return;

        try {
            if (event.getChannelType() == net.dv8tion.jda.api.entities.channel.ChannelType.GUILD_PUBLIC_THREAD) {
                ThreadChannel thread = event.getChannel().asThreadChannel();
                long parentId = thread.getParentChannel().getIdLong();
                boolean isBug = String.valueOf(parentId).equals(bugReportChannelId);
                boolean isReport = String.valueOf(parentId).equals(reportChannelId);
                if (isBug || isReport) {
                    thread.sendMessage("\uD83D\uDEAB Report handled by **" + sanitizeInline(event.getUser().getName()) + "**")
                            .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                            .queue();
                    thread.getManager().setLocked(true).queue();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        try {
                            thread.delete().queue();
                        } catch (Exception ignored) {}
                    }, 100L);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Discord] Failed to handle reaction: " + e.getMessage());
        }
    }

    // ─── UTILS ─────────────────────────────────────────────────

    private String sanitizeName(String input) {
        if (input == null) return "unknown";
        String cleaned = UNSAFE_NAME_CHARS.matcher(input.trim()).replaceAll("-");
        cleaned = cleaned.replaceAll("-{2,}", "-");
        if (cleaned.isEmpty() || cleaned.equals("-")) cleaned = "unknown";
        if (cleaned.length() > MAX_DISPLAY_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_DISPLAY_NAME_LENGTH);
        }
        return cleaned;
    }

    private String sanitizeInline(String input) {
        if (input == null) return "unknown";
        String cleaned = CONTROL_CHARS.matcher(input).replaceAll("")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
        cleaned = escapeMarkdown(defuseMentions(cleaned));
        if (cleaned.isEmpty()) cleaned = "unknown";
        if (cleaned.length() > MAX_DISPLAY_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_DISPLAY_NAME_LENGTH);
        }
        return cleaned;
    }

    private String sanitizeContent(String input) {
        if (input == null) return "(empty)";
        String cleaned = input.replace("\r\n", "\n").replace('\r', '\n');
        cleaned = CONTROL_CHARS.matcher(cleaned).replaceAll("");
        cleaned = escapeMarkdown(defuseMentions(cleaned));
        if (cleaned.isBlank()) cleaned = "(empty)";
        if (cleaned.length() > MAX_REPORT_TEXT_LENGTH) {
            cleaned = cleaned.substring(0, MAX_REPORT_TEXT_LENGTH) + "...";
        }
        return cleaned;
    }

    private String defuseMentions(String input) {
        return input.replace("@", "@\u200B");
    }

    private String escapeMarkdown(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (MARKDOWN_SPECIAL.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
