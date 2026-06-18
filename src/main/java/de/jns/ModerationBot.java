package de.jns;

import de.jns.pojo.SupportChannelPOJO;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ModerationBot extends ListenerAdapter {
    public JDA jda;
    private String moderationLog;

    private final Map<String, SupportChannelPOJO> knownSupportChannels;
    private final Map<String, VoiceChannel> activeVoiceChannel;
    private final Map<String, Long> joinCooldown;

    public ModerationBot(String logChannel) {
        jda = Main.jda;

        Main.LOG("ModerationBot Constructor");
        moderationLog = logChannel;

        knownSupportChannels = new HashMap<>();
        activeVoiceChannel = new HashMap<>();
        joinCooldown = new HashMap<>();

        Main.commandListener.RegisterCommand("setsupportchannel", this::IsMemberAdmin, this::SetSupportChannel);
    }

    // Todo: Try onGuildMemberUpdateNickname sometime in the future.
    @Override
    public void onGuildMemberUpdate(GuildMemberUpdateEvent event) {
        Member member = event.getMember();

        int checkName = isNicknameSuspicious(member.getNickname());

        if (checkName == 1) {
            member.modifyNickname(null).queue();
        } else if (checkName == 2) {
            event.getGuild().getTextChannelById(moderationLog)
                    .sendMessage("⚠️ Fremdwerbung im Nickname erkannt: `" + member.getNickname() + "` bei " + member.getUser().getAsMention())
                    .queue();
            member.modifyNickname(null).queue();
        } else if (checkName == 3) {
            event.getGuild().getTextChannelById(moderationLog)
                    .sendMessage("⚠️ Verdächtiger Nickname erkannt: `"
                            + member.getNickname() + "` bei " + member.getUser().getAsMention())
                    .queue();
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

    boolean SetSupportChannel(SlashCommandInteractionEvent event)
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
        event.getHook().sendMessage(role.getAsMention() + " wird nun in " + textChannel.getAsMention() + " gepingt, wenn ein User " + voiceChannel.getAsMention() + " betritt.").queue();
        return true;
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
