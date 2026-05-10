package de.jns.supportchannel;

import de.jns.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SupportChannelBot extends ListenerAdapter {
    public JDA jda;

    private Map<String, SupportChannelBLOB> knownSupportChannels;
    private Map<String, VoiceChannel> activeVoiceChannel;
    private Map<String, Long> joinCooldown;

    public SupportChannelBot() {
        jda = Main.jda;

        knownSupportChannels = new HashMap<>();
        activeVoiceChannel = new HashMap<>();
        joinCooldown = new HashMap<>();

        Main.LOG("SupportChannel Constructor");
    }

    public void AddKnownChannel(String key, SupportChannelBLOB blob) {
        knownSupportChannels.put(key, blob);
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String command = event.getName();
        Member executor = event.getMember();

        if (executor == null || !executor.hasPermission(Permission.ADMINISTRATOR)) {
            event.getHook().sendMessage("Keine Berechtigung.").queue();
            return;
        }

        if (command.equals("setsupportchannel")) {
            VoiceChannel voiceChannel = Objects.requireNonNull(event.getInteraction().getOption("vc")).getAsChannel().asVoiceChannel();
            TextChannel textChannel = Objects.requireNonNull(event.getInteraction().getOption("ping")).getAsChannel().asTextChannel();
            Role role = Objects.requireNonNull(event.getInteraction().getOption("role")).getAsRole();

            SupportChannelBLOB blob = new SupportChannelBLOB();
            blob.vc = voiceChannel;
            blob.ping = textChannel;
            blob.role = role;

            knownSupportChannels.put(voiceChannel.getId(), blob);

            String query = "INSERT INTO supportchannels (channelId, pingChannelId, roleId) VALUES ( '" + voiceChannel.getId() + "', '" + textChannel.getId() + "', '" + role.getId() + "' ) ON DUPLICATE KEY UPDATE pingChannelId = VALUES(pingChannelId), roleId = VALUES(roleId);";
            Main.ExecuteQuery(query);
            event.getHook().sendMessage(role.getAsMention() + " wird nun in " + textChannel.getAsMention() + " gepingt, wenn ein User " + voiceChannel.getAsMention() + " betritt.").queue();
        }
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

        SupportChannelBLOB blob = knownSupportChannels.get(joined.getId());

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

        // Cooldown setzen
        joinCooldown.put(member.getId(), now);

        // Kategorie vom Support-Channel übernehmen
        Category category = blob.vc.getParentCategory();

        event.getGuild()
                .createVoiceChannel("﹕\uD83D\uDCDE┊Support - " + member.getEffectiveName())
                .setParent(category)

                // @everyone darf nicht joinen
                .addPermissionOverride(
                        event.getGuild().getPublicRole(),
                        null,
                        EnumSet.of(
                                Permission.VIEW_CHANNEL,
                                Permission.VOICE_CONNECT
                        )
                )

                // User darf rein
                .addMemberPermissionOverride(
                        member.getIdLong(),
                        EnumSet.of(
                                Permission.VIEW_CHANNEL,
                                Permission.VOICE_CONNECT,
                                Permission.VOICE_SPEAK
                        ),
                        null
                )

                // Support-Rolle darf rein
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

                    // User moven
                    event.getGuild()
                            .moveVoiceMember(member, vc)
                            .queue();

                    // Ping senden
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
