package de.jns.countingbot;

import de.jns.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.internal.entities.emoji.UnicodeEmojiImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.text.DateFormat;
import java.util.*;

public class CountingBot extends ListenerAdapter {

    public static HashMap<String, ServerData> data = new HashMap<>();
    public static HashMap<String, ServerData> dataBackup = new HashMap<>();

    public JDA jda;
    private boolean bRememberLastCountMessage;

    public CountingBot(String token) throws InterruptedException {
        jda = JDABuilder.createLight(token,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(this)
                .build().awaitReady();
        Main.LOG("CountingBot Constructor");
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        ServerData serverData = data.get(event.getGuild().getId());
        if (serverData == null) return;
        if(event.getChannel().getId().equals(serverData.channelId) && event.getMessageId().equals(serverData.lastCountMessage.getId())) {
            event.getChannel().sendMessage(serverData.lastCountMessage.getContentRaw()).queue();
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        ServerData serverData = data.get(event.getGuild().getId());
        if (serverData == null) return;
        if(event.getAuthor().equals(jda.getSelfUser()) && serverData.lastCountMessage != null && event.getMessage().getContentRaw().equals(serverData.lastCountMessage.getContentRaw())) {
            String sReaction = "✅";

            if(event.getMessage().getContentRaw().equals(Integer.toString(serverData.highScore))) {
                sReaction = "\uD83C\uDFC6";
            }

            addReaction(event, sReaction);
        }

        if (event.getAuthor().isBot())
            return;
        if (!event.getChannel().getId().equals(serverData.channelId))
            return;

        String messageSent = event.getMessage().getContentRaw();

        int number;
        try {
            number = Integer.parseInt(messageSent);
        } catch (NumberFormatException e) {
            if (event.getMember().hasPermission(Permission.ADMINISTRATOR))
                return;
            event.getMessage().delete().queue();
            Main.LOG("Deleted invalid message: '" + event.getMessage().getContentRaw() + "' send by '"
                    + event.getAuthor().getName() + "'");
            return;
        }

        Date now = new Date();
        Locale locale = Locale.GERMAN;

        System.out.print(DateFormat.getDateInstance(DateFormat.DEFAULT, locale).format(now) + " " + DateFormat.getTimeInstance(DateFormat.DEFAULT, locale).format(now) + " || ");

        String fail = "❌";
        if (!Main.devMode) {
            if (event.getAuthor().getId().equals(serverData.lastUser)) {
                // game failed
                addReaction(event, fail);

                EmbedBuilder embedBuilder = new EmbedBuilder();

                embedBuilder.setColor(Color.RED);
                embedBuilder.setTitle("[Fail] Nicht zweimal hintereinander.");

                Main.LOG("[ " + " ] User '" + event.getAuthor().getName() + "' failed at "
                        + (serverData.curNum + 1) + ". They counted twice.");
                event.getChannel().sendMessageEmbeds(embedBuilder.build()).queue();
                bRememberLastCountMessage = false;

                ServerData dataCopy = new ServerData(serverData);
                dataBackup.put(event.getGuild().getId(), dataCopy);
                reset(serverData);
                return;
            }
        }

        if (number != (serverData.curNum + 1)) {
            // game failed
            addReaction(event, fail);

            EmbedBuilder embedBuilder = new EmbedBuilder();

            embedBuilder.setColor(Color.RED);
            embedBuilder.setTitle("[Fail] Fangt wieder bei 1 an.");
            embedBuilder.setFooter("Die erwartete Zahl war eigentlich: " + (serverData.curNum + 1));
            Main.LOG("User '" + event.getAuthor().getName() + "' failed at " + (serverData.curNum + 1)
                    + " with the number " + number + ". Wrong Number.");
            event.getChannel().sendMessageEmbeds(embedBuilder.build()).queue();
            bRememberLastCountMessage = false;

            ServerData dataCopy = new ServerData(serverData);
            dataBackup.put(event.getGuild().getId(), dataCopy);
            reset(serverData);
        } else if (number == (serverData.curNum + 1)) {

            if(event.getMember().hasPermission(Permission.ADMINISTRATOR))
                bRememberLastCountMessage = true;

            serverData.curNum = number;
            serverData.lastUser = event.getAuthor().getId();

            if(bRememberLastCountMessage) {
                serverData.lastCountMessage = event.getMessage();
            }

            serverData.save();
            Main.LOG("User '" + event.getAuthor().getName() + "' counted " + number + ".");

            if (number > serverData.highScore) {
                String trophy = "\uD83C\uDFC6";
                addReaction(event, trophy);
                serverData.highScore++;
            } else if (number != 404 && number <= serverData.highScore) {
                String check = "✅";
                addReaction(event, check);
            }
            serverData.save();

            if (number % 100 == 0) {
                String hundred = "\uD83D\uDCAF";
                addReaction(event, hundred);
            }

            // for switch
            String notFound = "❎";
            String heHe = "\uD83C\uDF46";
            String cookie = "\uD83C\uDF6A";
            String police = "\uD83D\uDE93";
            String fireForce = "\uD83D\uDE92";
            String ambulance = "\uD83D\uDE91";
            String computer = "\uD83D\uDCBB";
            String devil = "\uD83D\uDE08";
            String e = "\uD83C\uDDEA";
            String i = "\uD83C\uDDEE";
            String star = "⭐";
            String sponge = "\uD83E\uDDFD";
            String alien = "\uD83D\uDC7D";

            switch (number) {
                case 21 -> {
                    String nine = "9️⃣";
                    addReaction(event, nine);
                    String plus = "➕";
                    addReaction(event, plus);
                    String ten = "🔟";
                    addReaction(event, ten);
                }
                case 24 -> addReaction(event, sponge);
                case 25 -> addReaction(event, star);
                case 34 -> addReaction(event, heHe);
                case 42 -> {
                    String forty = "4️⃣";
                    addReaction(event, forty);
                    String two = "2️⃣";
                    addReaction(event, two);
                }
                case 51 -> addReaction(event, alien);
                case 69 -> {
                    String n = "\uD83C\uDDF3";
                    addReaction(event, n);
                    addReaction(event, i);
                    String c = "\uD83C\uDDE8";
                    addReaction(event, c);
                    addReaction(event, e);
                }
                case 404 -> addReaction(event, notFound);
                case 420 -> {
                    String l = "\uD83C\uDDF1";
                    addReaction(event, l);
                    addReaction(event, i);
                    String f = "\uD83C\uDDEB";
                    addReaction(event, f);
                    addReaction(event, e);
                }
                case 666 -> addReaction(event, devil);
                case 727 -> addReaction(event, cookie);
                case 110, 911 -> addReaction(event, police);
                case 112 -> {
                    addReaction(event, fireForce);
                    addReaction(event, ambulance);
                    addReaction(event, police);
                }
                case 1337 -> addReaction(event, computer);
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        ServerData serverData = data.get(event.getGuild().getId());
        String command = event.getName();

        if (command.equals("score")) {
            event.deferReply().queue();
            event.getHook().sendMessage("Der Highscore liegt aktuell bei " + serverData.highScore).queue();
            return;
        }

        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.getHook().sendMessage("Du bist kein Admin.").setEphemeral(true).queue();
            return;
        }

        EnumSet<Permission> perms = EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND);

        switch (command) {
            case "resetrun" -> {

                ServerData dataCopy = new ServerData(serverData);
                dataBackup.put(event.getGuild().getId(), dataCopy);
                reset(serverData);

                event.reply("done").setEphemeral(true).queue();
            }
            case "resetall" -> {
                resetAll(serverData);
                event.reply("done").setEphemeral(true).queue();
            }
            case "bann" -> {
                SlashCommandInteraction interaction = (SlashCommandInteraction) event.getHook().getInteraction();
                User user = Objects.requireNonNull(interaction.getOption("user")).getAsUser();

                TextChannel channel = jda.getChannelById(TextChannel.class, serverData.channelId);
                TextChannelManager manager = channel.getManager();

                channel.getIterableHistory().takeAsync(100).thenAccept(messages -> {
                    for (Message message : messages) {
                        if(message.getId().equals(serverData.lastCountMessage.getId())) {
                            break;
                        }
                        message.delete().queue();
                    }
                });

                try {

                ServerData backup = dataBackup.get(event.getGuild().getId());

                serverData.curNum = Integer.parseInt(backup.lastCountMessage.getContentRaw());
                serverData.lastUser = backup.lastCountMessage.getAuthor().getId();
                serverData.save();

                event.getChannel().sendMessage("Die aktuelle Zahl lautet: " + serverData.curNum).queue();
                } catch (Exception e) {
                    Main.LOG( "This shit did not work as intented, BUT it still banned the User" );
                    Main.LOG( "Exception: " );
                    e.printStackTrace();
                }

                manager.putMemberPermissionOverride(user.getIdLong(), null, perms);
                manager.queue();

                event.reply(user.getAsMention() + " gebannt").setEphemeral(true).queue();
            }
            case "unbann" -> {
                SlashCommandInteraction interaction = (SlashCommandInteraction) event.getHook().getInteraction();
                User user = Objects.requireNonNull(interaction.getOption("user")).getAsUser();

                TextChannel channel = jda.getChannelById(TextChannel.class, serverData.channelId);
                TextChannelManager manager = channel.getManager();

                manager.putMemberPermissionOverride(user.getIdLong(), perms, null);
                manager.queue();

                event.reply(user.getAsMention() + " entbannt").setEphemeral(true).queue();
            }
            case "setup" -> {
                ServerData imNewHere = new ServerData(event.getGuild().getId());
                imNewHere.channelId = event.getChannelId();
                imNewHere.save();
                data.put(event.getGuild().getId(), imNewHere);
                event.reply("Spiel eingerichtet!").setEphemeral(true).queue();

                event.getGuild().updateCommands().addCommands(
                        Commands.slash("bann", "Bannt einen User vom Zählen-Game")
                                .addOption(OptionType.USER, "user", "Verbrecher")
                        ,
                        Commands.slash("unbann", "Entbannt einen User vom Zählen-Game")
                                .addOption(OptionType.USER, "user", "Ex-Verbrecher")
                        ,
                        Commands.slash("score", "Zeigt dir den Highscore")
                        ,
                        Commands.slash("resetall", "Setzt alles zurück")
                        ,
                        Commands.slash("resetrun", "Setzt den aktuellen Durchlauf zurück")
                ).queue();
            }
            default -> event.getHook().sendMessage("unknown command").queue();
        }
    }

    public void reset(ServerData serverData) {
        serverData.curNum = 0;
        serverData.lastUser = null;
        serverData.lastCountMessage = null;
        serverData.save();
    }

    public void resetAll(ServerData serverData) {
        serverData.curNum = 0;
        serverData.lastUser = null;
        serverData.lastCountMessage = null;
        serverData.highScore = 0;
        serverData.save();
    }

    private void addReaction(MessageReceivedEvent event, String emoji) {
        event.getMessage().addReaction(new UnicodeEmojiImpl(emoji)).queue();
    }
}