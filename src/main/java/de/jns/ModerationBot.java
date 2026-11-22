package de.jns;

import de.jns.pojo.CommandResult;
import de.jns.pojo.SupportChannelPOJO;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ModerationBot extends ListenerAdapter {
    public JDA jda;
    private String moderationLog;
    private static final String HONEYPOD_WARNING = """
            ⚠️ HONEYPOT-WARNUNG
            Dieser Kanal ist ein automatisierter Honeypot, um Scam- und Spam-Bots zu erkennen. Schreibe hier keine Nachrichten.
            Jeder Account, der hier eine Nachricht sendet, wird automatisch von diesem Server gebannt.
            """;

    private record HoneypodConfig(String channelId, boolean createdByBot) {}

    private final Map<String, SupportChannelPOJO> knownSupportChannels;
    private final Map<String, VoiceChannel> activeVoiceChannel;
    private final Map<String, Long> joinCooldown;
    private final Map<String, HoneypodConfig> honeypodChannels;

    public ModerationBot(String logChannel) {
        jda = Main.jda;

        Main.LOG("ModerationBot Constructor");
        moderationLog = logChannel;

        knownSupportChannels = new HashMap<>();
        activeVoiceChannel = new HashMap<>();
        joinCooldown = new HashMap<>();
        honeypodChannels = new ConcurrentHashMap<>();
        loadHoneypodChannels();

        Main.commandListener.RegisterCommand("setsupportchannel", this::IsMemberAdmin, this::SetSupportChannel);
        Main.commandListener.RegisterCommand("sethoneypodchannel", this::IsMemberAdmin, this::SetHoneypodChannel);
        Main.commandListener.RegisterCommand("deletehoneypod", this::IsMemberAdmin, this::DeleteHoneypod);
    }

    private void loadHoneypodChannels() {
        try (Connection connection = openDatabaseConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS honeypod_channels "
                    + "(guild_id TEXT PRIMARY KEY, channel_id TEXT NOT NULL)");

            boolean hasCreatedByBotColumn = false;
            try (ResultSet columns = statement.executeQuery("PRAGMA table_info(honeypod_channels)")) {
                while (columns.next()) {
                    if ("created_by_bot".equals(columns.getString("name"))) {
                        hasCreatedByBotColumn = true;
                        break;
                    }
                }
            }
            if (!hasCreatedByBotColumn) {
                statement.executeUpdate("ALTER TABLE honeypod_channels "
                        + "ADD COLUMN created_by_bot INTEGER NOT NULL DEFAULT 0");
            }

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT guild_id, channel_id, created_by_bot FROM honeypod_channels")) {
                while (resultSet.next()) {
                    honeypodChannels.put(resultSet.getString("guild_id"),
                            new HoneypodConfig(resultSet.getString("channel_id"),
                                    resultSet.getInt("created_by_bot") != 0));
                }
            }
        } catch (SQLException e) {
            Main.LOG("Failed to load honeypod configuration from the SQLite database: " + e.getMessage());
        }
    }

    private Connection openDatabaseConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver is unavailable", e);
        }

        String database = Main.properties.getProperty("database", "bot");
        return DriverManager.getConnection("jdbc:sqlite:" + database + ".db");
    }

    private synchronized void saveHoneypodChannel(String guildId, String channelId, boolean createdByBot)
            throws SQLException {
        try (Connection connection = openDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO honeypod_channels (guild_id, channel_id, created_by_bot) VALUES (?, ?, ?) "
                             + "ON CONFLICT(guild_id) DO UPDATE SET channel_id = excluded.channel_id, "
                             + "created_by_bot = excluded.created_by_bot")) {
            statement.setString(1, guildId);
            statement.setString(2, channelId);
            statement.setInt(3, createdByBot ? 1 : 0);
            statement.executeUpdate();
        }
        honeypodChannels.put(guildId, new HoneypodConfig(channelId, createdByBot));
    }

    private synchronized void removeHoneypodChannel(String guildId, HoneypodConfig config) throws SQLException {
        try (Connection connection = openDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM honeypod_channels WHERE guild_id = ? AND channel_id = ?")) {
            statement.setString(1, guildId);
            statement.setString(2, config.channelId());
            statement.executeUpdate();
        }
        honeypodChannels.remove(guildId, config);
    }

    private CommandResult SetHoneypodChannel(SlashCommandInteractionEvent event) {
        if (!event.getGuild().getSelfMember().hasPermission(Permission.BAN_MEMBERS))
            return new CommandResult("I need the Ban Members permission before a honeypod can be enabled.", true);

        event.deferReply(true).queue(hook -> {
            if (event.getOption("channel") == null) {
                event.getGuild().createTextChannel("honeypod").queue(
                        channel -> announceHoneypod(event, channel, true, hook),
                        error -> {
                            Main.LOG("Failed to create honeypod channel for Guild: "
                                    + event.getGuild().getId() + " (" + error.getMessage() + ")");
                            hook.editOriginal("Could not create the honeypod channel. Check the bot's channel permissions.")
                                    .queue();
                        }
                );
                return;
            }

            var selectedChannel = event.getOption("channel").getAsChannel();
            if (selectedChannel.getType() != ChannelType.TEXT) {
                hook.editOriginal("Please select a standard text channel.").queue();
                return;
            }
            announceHoneypod(event, selectedChannel.asTextChannel(), false, hook);
        }, error -> Main.LOG("Failed to acknowledge honeypod setup command: " + error.getMessage()));
        return new CommandResult("", true);
    }

    private void announceHoneypod(SlashCommandInteractionEvent event, TextChannel channel,
                                  boolean createdByBot, InteractionHook hook) {
        channel.sendMessage(HONEYPOD_WARNING).queue(
                ignored -> {
                    try {
                        saveHoneypodChannel(event.getGuild().getId(), channel.getId(), createdByBot);
                        hook.editOriginal("Honeypod enabled in " + channel.getAsMention() + ".").queue();
                    } catch (SQLException e) {
                        Main.LOG("Failed to save honeypod channel for Guild: " + event.getGuild().getId()
                                + " (" + e.getMessage() + ")");
                        hook.editOriginal("The warning was posted, but the honeypod could not be enabled because its configuration could not be saved.")
                                .queue();
                    }
                },
                error -> {
                    Main.LOG("Failed to post honeypod warning for Guild: " + event.getGuild().getId()
                            + " (" + error.getMessage() + ")");
                    hook.editOriginal("Could not post the honeypod warning. Check the bot's channel permissions; the channel was not enabled.")
                            .queue();
                }
        );
    }

    private CommandResult DeleteHoneypod(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        HoneypodConfig config = honeypodChannels.get(guildId);
        if (config == null)
            return new CommandResult("No honeypod is configured for this server.", true);

        event.deferReply(true).queue(hook -> {
            if (!config.createdByBot()) {
                try {
                    removeHoneypodChannel(guildId, config);
                    hook.editOriginal("Honeypod disabled. The existing channel was left intact.").queue();
                } catch (SQLException e) {
                    Main.LOG("Failed to remove honeypod configuration for Guild: " + guildId
                            + " (" + e.getMessage() + ")");
                    hook.editOriginal("Could not remove the honeypod configuration from the database.")
                            .queue();
                }
                return;
            }

            TextChannel channel = event.getGuild().getTextChannelById(config.channelId());
            if (channel == null) {
                removeMissingHoneypod(guildId, config, hook);
                return;
            }

            channel.delete().reason("Admin removed the bot-created honeypod").queue(
                    ignored -> {
                        try {
                            removeHoneypodChannel(guildId, config);
                            hook.editOriginal("The bot-created honeypod channel was deleted.").queue();
                        } catch (SQLException e) {
                            honeypodChannels.remove(guildId, config);
                            Main.LOG("Deleted honeypod channel " + config.channelId()
                                    + " but failed to remove its database entry for Guild: " + guildId
                                    + " (" + e.getMessage() + ")");
                            hook.editOriginal("The channel was deleted, but its database entry could not be removed.")
                                    .queue();
                        }
                    },
                    error -> {
                        Main.LOG("Failed to delete bot-created honeypod channel " + config.channelId()
                                + " for Guild: " + guildId + " (" + error.getMessage() + ")");
                        hook.editOriginal("Could not delete the channel; the honeypod configuration remains active.")
                                .queue();
                    }
            );
        }, error -> Main.LOG("Failed to acknowledge honeypod deletion command: " + error.getMessage()));
        return new CommandResult("", true);
    }

    private void removeMissingHoneypod(String guildId, HoneypodConfig config, InteractionHook hook) {
        try {
            removeHoneypodChannel(guildId, config);
            hook.editOriginal("The bot-created honeypod channel no longer exists; its configuration was removed.")
                    .queue();
        } catch (SQLException e) {
            Main.LOG("Failed to remove missing honeypod configuration for Guild: " + guildId
                    + " (" + e.getMessage() + ")");
            hook.editOriginal("The channel no longer exists, but its database entry could not be removed.")
                    .queue();
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().getId().equals(jda.getSelfUser().getId()))
            return;

        HoneypodConfig config = honeypodChannels.get(event.getGuild().getId());
        if (config == null || !config.channelId().equals(event.getChannel().getId()))
            return;

        event.getGuild().ban(event.getAuthor(), 7, TimeUnit.DAYS)
                .reason("Posted a message in the honeypod channel; delete the last 7 days of messages")
                .queue(
                        ignored -> Main.LOG("Banned user " + event.getAuthor().getId()
                                + " for posting in honeypod channel " + config.channelId()),
                        error -> Main.LOG("Failed to ban user " + event.getAuthor().getId()
                                + " from honeypod channel " + config.channelId()
                                + " (" + error.getMessage() + ")")
                );
    }

    // Todo: Try onGuildMemberUpdateNickname sometime in the future.
    @Override
    public void onGuildMemberUpdate(GuildMemberUpdateEvent event) {
        Member member = event.getMember();
        TextChannel textChannelById = event.getGuild().getTextChannelById(moderationLog);

        if (textChannelById != null) {
            int checkName = isNicknameSuspicious(member.getNickname());

            if (checkName == 1) {
                member.modifyNickname(null).queue();
            } else if (checkName == 2) {
                textChannelById.sendMessage("⚠️ Fremdwerbung im Nickname erkannt: `" + member.getNickname() + "` bei " + member.getUser().getAsMention())
                        .queue();
                member.modifyNickname(null).queue();
            } else if (checkName == 3) {
                textChannelById.sendMessage("⚠️ Verdächtiger Nickname erkannt: `"
                                + member.getNickname() + "` bei " + member.getUser().getAsMention())
                        .queue();
            }
        }
    }

    /*
     * Return values
     * 1: Illegal name with blockedWords
     * 2: contains a link
     * 3: contains non-roman characters
     * 0: clean name
     */
    public int isNicknameSuspicious(String nickname) {
        if (nickname == null) return 0;

        // remove spaces from name
        String cleanNick = cleanString(nickname);

        // Verdächtige Wörter (kannst du beliebig erweitern)
        String[] blockedWords = {
                "nazi", "hitler", "sex", "porn", "nigger", "nigga", "negro", "fuck", "bitch",
                "cunt", "slut", "dick", "cock", "whore", "faggot", "retard",
                "neger", "hure", "wichser", "arsch", "idiot", "penis", "vagina"
        };

        // Auf enthaltene Wörter prüfen
        for (String word : blockedWords) {
            // Regex: erlaubt kleine Abstände zwischen Buchstaben (z. B. f.u.c.k oder f u c k)
            String pattern = String.join("\\W*", word.split(""));
            if (cleanNick.matches(".*" + pattern + ".*")) {
                return 1;
            }
        }

        // Werbung erkennen
        if (cleanNick.matches(".*(discord\\.gg|http|www\\.).*")) return 2;

        // Zu viele Sonderzeichen oder verdächtige Formatierung
        if (nickname.matches(".*[^a-zA-Z0-9 _\\-].*")) return 3;

        return 0;
    }

    @NotNull
    private static String cleanString(String nickname) {
        StringBuilder noSpaces = new StringBuilder();
        for (char c : nickname.toCharArray()) {
            if (c != ' ') {
                noSpaces.append(c);
            }
        }

        // Kleinbuchstaben für einfacheren Vergleich
        String lower = noSpaces.toString().toLowerCase();

        // Zeichen ersetzen, um "Leetspeak" abzufangen
        lower = lower
                .replace("@", "a")
                .replace("4", "a")
                .replace("3", "e")
                .replace("1", "i")
                .replace("!", "i")
                .replace("0", "o")
                .replace("$", "s")
                .replace("*", "")
                .replace(".", "")
                .replace(" ", "");
        return lower;
    }

    ///// Supportchannel

    boolean IsMemberAdmin(SlashCommandInteractionEvent event) {
        return Objects.requireNonNull(event.getMember()).hasPermission(Permission.ADMINISTRATOR);
    }

    public void AddKnownChannel(String key, SupportChannelPOJO blob) {
        knownSupportChannels.put(key, blob);
    }

    CommandResult SetSupportChannel(SlashCommandInteractionEvent event)
    {
        VoiceChannel voiceChannel = Objects.requireNonNull(event.getInteraction().getOption("vc")).getAsChannel().asVoiceChannel();
        TextChannel textChannel = Objects.requireNonNull(event.getInteraction().getOption("ping")).getAsChannel().asTextChannel();
        Role role = Objects.requireNonNull(event.getInteraction().getOption("role")).getAsRole();

        SupportChannelPOJO blob = new SupportChannelPOJO();
        blob.vc = voiceChannel;
        blob.ping = textChannel;
        blob.role = role;

        knownSupportChannels.put(voiceChannel.getId(), blob);

        String query = "INSERT INTO supportchannels (channelId, pingChannelId, roleId) VALUES ( '" + voiceChannel.getId() + "', '" + textChannel.getId() + "', '" + role.getId() + "' ) ON CONFLICT(channelId) DO UPDATE SET pingChannelId = excluded.pingChannelId, roleId = excluded.roleId;";
        Main.ExecuteQuery(query);
        return new CommandResult(role.getAsMention() + " wird nun in " + textChannel.getAsMention() + " gepingt, wenn ein User " + voiceChannel.getAsMention() + " betritt.", false);
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {

        AudioChannel joined = event.getChannelJoined();
        AudioChannel left = event.getChannelLeft();

        if (left != null) {
            VoiceChannel supportChannelDueToDelete = activeVoiceChannel.get(left.getId());
            if (supportChannelDueToDelete != null && supportChannelDueToDelete.getMembers().isEmpty()) {
                supportChannelDueToDelete.delete().queue();
            }
        }

        if (joined == null)
            return;

        SupportChannelPOJO blob = knownSupportChannels.get(joined.getId());

        if (blob == null)
            return;

        Member member = event.getMember();

        long now = System.currentTimeMillis();
        long cooldown = 10_000; // 10 Sekunden

        Long lastJoin = joinCooldown.get(member.getId());

        if (lastJoin != null && now - lastJoin < cooldown) {

            // User aus VC kicken
            event.getGuild()
                    .moveVoiceMember(member, null)
                    .queue();

            long secondsLeft = (cooldown - (now - lastJoin)) / 1000;

            member.getUser()
                    .openPrivateChannel()
                    .flatMap(channel ->
                            channel.sendMessage(
                                    "Bitte warte noch "
                                            + secondsLeft
                                            + " Sekunden bevor du erneut einen Supportchannel betrittst."
                            )
                    ).queue();

            return;
        }

        joinCooldown.put(member.getId(), now);

        Category category = blob.vc.getParentCategory();
        event.getGuild()
                .createVoiceChannel("﹕\uD83D\uDCDE┊Support - " + member.getEffectiveName())
                .setParent(category)
                .addPermissionOverride(
                        event.getGuild().getPublicRole(),
                        null,
                        EnumSet.of(
                                Permission.VIEW_CHANNEL,
                                Permission.VOICE_CONNECT
                        )
                )
                .addMemberPermissionOverride(
                        member.getIdLong(),
                        EnumSet.of(
                                Permission.VIEW_CHANNEL,
                                Permission.VOICE_CONNECT,
                                Permission.VOICE_SPEAK
                        ),
                        null
                )
                .addRolePermissionOverride(
                        blob.role.getIdLong(),
                        EnumSet.of(
                                Permission.VIEW_CHANNEL,
                                Permission.VOICE_CONNECT,
                                Permission.VOICE_SPEAK
                        ),
                        null
                )
                .queue(vc -> {
                    activeVoiceChannel.put(vc.getId(), vc);
                    event.getGuild()
                            .moveVoiceMember(member, vc)
                            .queue();
                    blob.ping.sendMessage(
                            blob.role.getAsMention()
                                    + " Support benötigt von "
                                    + member.getAsMention()
                                    + " in "
                                    + vc.getAsMention()
                    ).queue();
                });
    }
}
