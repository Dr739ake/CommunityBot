package de.jns.birthdaybot;

import de.jns.Main;
import de.jns.multiban.MultiBanBot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
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

    public BirthdayBot(String token, String channelId) throws Exception {
        jda = JDABuilder.createLight(token,
                        GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(this)
                .build().awaitReady();
        Main.LOG("BirthdayBot Constructor");

        executeFunction(jda, channelId);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Runnable dailyTask = new Runnable() {
            @Override
            public void run() {
                try {
                    executeFunction(jda, channelId);
                } catch (SQLException e) {
                    System.out.println(e);
                }
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

    private static void executeFunction(JDA jda, String channelId) throws SQLException {
        System.out.println("Funktion ausgeführt um: " + new Date());

        LocalDateTime now = LocalDateTime.now();
        ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM `birthdays` WHERE day = " + now.getDayOfMonth() + " and month = " + now.getMonthValue() + ";");

        TextChannel textChannelById = jda.getTextChannelById(channelId);
        while (resultSet.next()) {
            String id = resultSet.getString("id");
            textChannelById.sendMessage(Objects.requireNonNull(  " <@"+id+"> Happy Birthday!")).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String command = event.getName();
        Member executor = event.getMember();
        event.deferReply();

        assert executor != null;
        switch (command) {
            case "geburtstageintragen":
            {
                int day = Objects.requireNonNull(event.getInteraction().getOption("tag")).getAsInt();
                int month = Objects.requireNonNull(event.getInteraction().getOption("monat")).getAsInt();
                Main.ExecuteQuery("INSERT INTO birthdays (id, day, month) VALUES ('" + executor.getId() + "', " + day + ", " + month + ") ON DUPLICATE KEY UPDATE day = VALUES(day), month = VALUES(month);");
                event.reply("Eingetragen").setEphemeral(true).queue();
            }
            break;
            case "geburtstagloeschen":
            {
                Main.ExecuteQuery("DELETE FROM birthdays WHERE id = '"+executor.getId()+"';");
                event.reply("Gelöscht").setEphemeral(true).queue();
            }
            break;
            default:
                break;
        }
    }
}
