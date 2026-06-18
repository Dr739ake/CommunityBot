package de.jns;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BirthdayBot extends ListenerAdapter {
    public JDA jda;

    public BirthdayBot(String channelId) {
        jda = Main.jda;
        Main.LOG("BirthdayBot Constructor");

        String[] createTableQuerySQLite = {
                "CREATE TABLE IF NOT EXISTS birthday_days ( id VARCHAR(255) PRIMARY KEY, day INT, month INT, was_celebrated INT);",
                "CREATE TABLE IF NOT EXISTS birthday_server_conf ( server_id VARCHAR(255) PRIMARY KEY, textChannelId VARCHAR(255) );"
        };

        for (String q : createTableQuerySQLite) {
            Main.ExecuteQuery(q);
        }

        Main.commandListener.RegisterCommand("geburtstageintragen", this::StaticPermission, this::AddBDay);
        Main.commandListener.RegisterCommand("geburtstagloeschen", this::StaticPermission, this::RemoveBDay);
        Main.commandListener.RegisterCommand("setgeburtstagechannel", this::IsMemberAdmin, this::SetLogChannelCommand);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Runnable dailyTask = () -> executeFunction(jda, channelId);
        scheduler.scheduleAtFixedRate(dailyTask, 0, 1, TimeUnit.HOURS);
    }

    private static void executeFunction(JDA jda, String channelId) {
        LocalDateTime now = LocalDateTime.now();

        int day = now.getDayOfMonth();
        int month = now.getMonthValue();

        ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM birthday_days WHERE day = " + day + " AND month = " + month + " AND was_celebrated = 0;");
        Main.ExecuteQuery("UPDATE birthday_days SET was_celebrated = 0 WHERE NOT (day = " + day + " AND month = " + month + ");");

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
            message.append(":tada: :partying_face: :tada: <@" + birthday_people.get(0) + "> hat heute Geburtstag! Alles Gute! :tada: :partying_face: :tada: ");
        } else if (birthday_people.size() == 2) {
            message.append(":tada: :partying_face: :tada: <@" + birthday_people.get(0) + "> und <@" + birthday_people.get(1) + "> haben heute Geburtstag! Alles Gute! :tada: :partying_face: :tada: ");
        } else if (birthday_people.size() > 1) {
            message.append(":tada: :partying_face: :tada: ");
            for (int i = 0; i < birthday_people.size() - 2; i++) {
                message.append("<@" + birthday_people.get(i) + ">, ");
            }
            message.append("<@" + birthday_people.get(birthday_people.size() - 2) + ">, und <@" + birthday_people.get(birthday_people.size() - 1) + "> haben heute Geburtstag! Alles Gute! :tada: :partying_face: :tada: ");
        }
        if (!message.isEmpty()) {
            if (textChannelById != null) {
                textChannelById.sendMessage(message.toString()).queue();
            }
            Main.LOG(message.toString());
        }
        for (String id : birthday_people) {
            Main.ExecuteQuery("UPDATE birthday_days SET was_celebrated = 1 WHERE id = " + id + ";");
        }
        Main.LOG("Geburtstage gecheckt um: " + new Date());
    }

    boolean StaticPermission(SlashCommandInteractionEvent event) {
        return true;
    }

    boolean IsMemberAdmin(SlashCommandInteractionEvent event) {
        return Objects.requireNonNull(event.getMember()).hasPermission(Permission.ADMINISTRATOR);
    }

    boolean AddBDay(SlashCommandInteractionEvent event) {
        Member executor = event.getMember();
        int day = Objects.requireNonNull(event.getOption("tag")).getAsInt();
        int month = Objects.requireNonNull(event.getOption("monat")).getAsInt();

        Main.ExecuteQuery("INSERT INTO birthday_days (id, day, month) VALUES ('" +executor.getId()+ "', "+ day +", "+ month +") ON CONFLICT(id) DO UPDATE SET day = excluded.day, month = excluded.month, was_celebrated = 0;");

        event.getHook().sendMessage("Dein Geburtstag wurde eingetragen.").queue();
        return true;
    }

    boolean RemoveBDay(SlashCommandInteractionEvent event) {
        Member executor = event.getMember();

        Main.ExecuteQuery("DELETE FROM birthday_days WHERE id = '" + executor.getId() + "';");
        event.getHook().sendMessage("Dein Geburtstag wurde gelöscht.").queue();
        return true;
    }

    boolean SetLogChannelCommand(SlashCommandInteractionEvent event)
    {
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

        return true;
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        Main.ExecuteQuery("DELETE FROM birthday_days WHERE id = " + event.getUser().getId() + ";");
    }
}
