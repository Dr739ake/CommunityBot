package de.jns.birthdaybot;

import de.jns.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BirthdayBot extends ListenerAdapter {
    public JDA jda;

    public BirthdayBot(String token, String channelId) throws SQLException, InterruptedException {
        jda = JDABuilder.createLight(token,
                        GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(this)
                .build().awaitReady();
        Main.LOG("BirthdayBot Constructor");

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Runnable dailyTask = new Runnable() {
            @Override
            public void run() {
                executeFunction(jda, channelId);
            }
        };
        scheduler.scheduleAtFixedRate(dailyTask, 0,1, TimeUnit.HOURS);
    }

    private static void executeFunction(JDA jda, String channelId) {
        Main.LOG("Geburtstage gecheckt um: " + new Date());

        LocalDateTime now = LocalDateTime.now();

        int day = now.getDayOfMonth();
        int month = now.getMonthValue();

        ResultSet resultSet = Main.ExecuteQuery_NOLOG("SELECT * FROM birthday_days WHERE day = " + day + " and month = " + month + " AND was_selebrated = 0;");
        Main.ExecuteQuery_NOLOG("UPDATE birthday_days SET was_selebrated = 0 WHERE NOT (day = " + day + " AND month = " + month + ");");

        TextChannel textChannelById = jda.getTextChannelById(channelId);
        List<String> birthday_people = new ArrayList<>();
        if (textChannelById != null) {
            try {
                while (resultSet.next()) {
                    String id = resultSet.getString("id");
                    birthday_people.add(id);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        StringBuilder message = new StringBuilder();
        if (birthday_people.size() == 1) {
            message.append(":tada: :partying_face: :tada: <@"+birthday_people.get(0)+"> hat heute Geburtstag! Alles Gute! :tada: :partying_face: :tada: ");
        } else if (birthday_people.size() == 2) {
            message.append(":tada: :partying_face: :tada: <@"+birthday_people.get(0)+"> und <@" + birthday_people.get(1) + "> haben heute Geburtstag! Alles Gute! :tada: :partying_face: :tada: ");
        } else if (birthday_people.size() > 1) {
            message.append(":tada: :partying_face: :tada: ");
            for(int i = 0; i < birthday_people.size() -2; i++) {
                message.append("<@"+birthday_people.get(i)+">, ");
            }
            message.append("<@"+birthday_people.get( birthday_people.size()-2 )+">, und <@" + birthday_people.get( birthday_people.size()-1 )+ "> haben heute Geburtstag! Alles Gute! :tada: :partying_face: :tada: ");
        }
        if (!message.isEmpty()) {
            if (textChannelById != null) {
                textChannelById.sendMessage(message.toString()).queue();
            }
            Main.LOG(message.toString());
        }
        for(String id : birthday_people) {
            Main.ExecuteQuery_NOLOG("UPDATE birthday_days SET was_selebrated = 1 WHERE id = "+id+";");
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String command = event.getName();
        Member executor = event.getMember();
        event.deferReply(true).queue();
        if (executor != null) {
            switch (command) {
                case "geburtstageintragen": {
                    int day = Objects.requireNonNull(event.getOption("tag")).getAsInt();
                    int month = Objects.requireNonNull(event.getOption("monat")).getAsInt();

                    Main.ExecuteQuery(
                            "INSERT INTO birthday_days (id, day, month) VALUES ('"
                                    + executor.getId() + "', " + day + ", " + month + ") "
                                    + "ON DUPLICATE KEY UPDATE day = VALUES(day), month = VALUES(month);"
                    );

                    event.getHook().sendMessage("Dein Geburtstag wurde eingetragen.").queue();
                }
                break;
                case "geburtstagloeschen": {
                    Main.ExecuteQuery("DELETE FROM birthday_days WHERE id = '" + executor.getId() + "';");
                    event.getHook().sendMessage("Dein Geburtstag wurde gelöscht.").queue();
                }
                break;
                case "setgeburtstagechannel": {
                    String channelId = Objects.requireNonNull(event.getOption("channel")).getAsString();
                    TextChannel textChannelById = jda.getTextChannelById(channelId);

                    if (textChannelById != null) {
                        Main.ExecuteQuery(
                                "INSERT INTO birthday_server_conf (server_id, textChannelId) VALUES ('"
                                        + event.getGuild().getId() + "', '" + channelId + "') "
                                        + "ON DUPLICATE KEY UPDATE server_id = VALUES(server_id), textChannelId = VALUES(textChannelId);"
                        );

                        event.getHook().sendMessage("Channel eingetragen").queue();
                    } else {
                        event.getHook().sendMessage("Dieser Channel existiert nicht.").queue();
                    }
                }
                break;
            }
        } else {
            event.getHook().sendMessage("Du exestierst scheinbar garnicht o.o? Melde dich bei @dr739ake... oder lass es.").queue();
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        Main.ExecuteQuery_NOLOG("DELETE FROM birthday_days WHERE id = "+event.getUser().getId()+";");
    }
}
