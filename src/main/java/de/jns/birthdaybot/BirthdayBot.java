package de.jns.birthdaybot;

import de.jns.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
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
        long delay = calculateDelayUntilNineAM();
        scheduler.scheduleAtFixedRate(dailyTask, delay, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);

    }

    private static long calculateDelayUntilNineAM() {
        Calendar now = Calendar.getInstance();
        Calendar nextRun = Calendar.getInstance();

        nextRun.set(Calendar.HOUR_OF_DAY, 9);
        nextRun.set(Calendar.MINUTE, 0);
        nextRun.set(Calendar.SECOND, 0);
        nextRun.set(Calendar.MILLISECOND, 0);

        if (now.after(nextRun)) {
            nextRun.add(Calendar.DAY_OF_YEAR, 1);
        }

        return nextRun.getTimeInMillis() - now.getTimeInMillis();
    }

    private static void executeFunction(JDA jda, String channelId) {
        Main.LOG("Funktion ausgeführt um: " + new Date());

        LocalDateTime now = LocalDateTime.now();
        ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM birthday_days WHERE day = " + now.getDayOfMonth() + " and month = " + now.getMonthValue() + ";");

        TextChannel textChannelById = jda.getTextChannelById(channelId);
        if (textChannelById != null) {
            try {
                while (resultSet.next()) {
                    String id = resultSet.getString("id");
                    textChannelById.sendMessage(" <@"+id+"> hat heute Geburtstag! Alles Gute!").queue();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
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
}
