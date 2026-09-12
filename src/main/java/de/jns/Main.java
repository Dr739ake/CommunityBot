package de.jns;

import de.jns.pojo.ServerDataPOJO;
import de.jns.pojo.SupportChannelPOJO;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.*;
import java.sql.*;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Main {

    public static JDA jda;
    public static BotListenerAdapter commandListener = new BotListenerAdapter();

    public static String VERSION_NUMBER = "v1.6";

    public static boolean devMode;

    static Properties properties;
    static final String PROPERTIES_FILE = "bot.properties";

    static Connection connection;

    public static void LOG(String s) {
        String format = ZonedDateTime
                .now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")); //
        System.out.println(format + " [LOG] " + s);
    }

    public static ResultSet ExecuteQuery(String query) {
        Statement statement;
        try {
            String database = properties.getProperty("database", "bot");
            String url = "jdbc:sqlite:" + database + ".db";
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            try {
                if (connection == null || connection.isClosed())
                    connection = DriverManager.getConnection(url);

                statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query);
                return resultSet;
            } catch (SQLException e) {
                return null;
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static CountingBot countingBot;
    static BirthdayBot birthdayBot;
    static ModerationBot moderationBot;

    public static void main(String[] args) throws Exception {
        if (!new File(PROPERTIES_FILE).exists()) {
            BufferedWriter br = new BufferedWriter(new FileWriter(PROPERTIES_FILE));
            br.write("token=none\n");
            br.write("devMode=false\n");
            br.write("database=none\n");
            br.write("moderationLog=none\n");

            br.flush();
            br.close();
            LOG("Please configure in " + PROPERTIES_FILE);
            return;
        }
        properties = new Properties();
        properties.load(new FileInputStream(PROPERTIES_FILE));

        jda = JDABuilder.createDefault(properties.getProperty("token"))
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .enableIntents(GatewayIntent.GUILD_VOICE_STATES)
                .enableIntents(GatewayIntent.GUILD_MESSAGES)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .build().awaitReady();

        jda.addEventListener(commandListener);

        List<ListenerAdapter> listenerAdaptersList = setupListenerAdapters();

        try {
            for (ListenerAdapter listenerAdapter : listenerAdaptersList) {
                jda.addEventListener(listenerAdapter);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Bot-Version: " + VERSION_NUMBER);
        while (true) {
            if (areListenerAdaptersMissing()){
                List<ListenerAdapter> list = setupListenerAdapters();
                if  (!list.isEmpty()) {
                    for  (ListenerAdapter listenerAdapter : list) {
                        jda.addEventListener(listenerAdapter);
                    }
                }
            }
            Thread.sleep(Duration.ofSeconds(10).toMillis());
        }
    }

    private static boolean areListenerAdaptersMissing()
    {
        boolean r = false;
        if (countingBot == null) {
            r = true;
        }
        if (birthdayBot == null) {
            r = true;
        }
        if (moderationBot == null) {
            r = true;
        }
        return r;
    }

    private static List<ListenerAdapter> setupListenerAdapters() {
        List<ListenerAdapter> listenerAdaptersList = new ArrayList<>();

        if (countingBot == null) {
            try {
                countingBot = setupCountingBot();
                listenerAdaptersList.add(countingBot);
                // CountingBot Stuff
                for (Guild guild : jda.getGuilds()) {
                    ServerDataPOJO serverDataPOJO = new ServerDataPOJO(guild.getId());
                    CountingBot.data.put(guild.getId(), serverDataPOJO);

                    addCommands(guild, (serverDataPOJO.channelId != null && !serverDataPOJO.channelId.isEmpty()));
                }
            } catch (Exception e) {
                Main.LOG("countingBot failed to start: " + e.getMessage());
            }
        }

        if (birthdayBot == null) {
            try {
                birthdayBot = setupBirthdayBot();
                listenerAdaptersList.add(birthdayBot);
            } catch (Exception e) {
                Main.LOG("birthdayBot failed to start: " + e.getMessage());
            }
        }

        if (moderationBot == null) {
            try {
                moderationBot = setupModerationBot(properties.getProperty("moderationLog"));
                listenerAdaptersList.add(moderationBot);

                // SupportChannel Stuff
                ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM supportchannels");
                if (resultSet.next()) {
                    do {
                        SupportChannelPOJO blob = new SupportChannelPOJO();
                        blob.vc = jda.getVoiceChannelById(resultSet.getString(1));
                        blob.ping = jda.getTextChannelById(resultSet.getString(2));
                        blob.role = jda.getRoleById(resultSet.getString(3));
                        moderationBot.AddKnownChannel(blob.vc.getId(), blob);
                    } while (resultSet.next());
                }
            } catch (Exception e) {
                Main.LOG("moderationBot failed to start: " + e.getMessage());
            }
        }
        return listenerAdaptersList;
    }

    public static BirthdayBot setupBirthdayBot() {
        String channelId = properties.getProperty("birthdayChannel");
        BirthdayBot bot = new BirthdayBot(channelId);

        String[] createTableQuery = {
                "CREATE TABLE IF NOT EXISTS birthday_days ( id VARCHAR(255) PRIMARY KEY, day INT, month INT, was_celebrated INT);",
                "CREATE TABLE IF NOT EXISTS birthday_server_conf ( server_id VARCHAR(255) PRIMARY KEY, textChannelId VARCHAR(255) );"
        };

        for (String q : createTableQuery) {
            ExecuteQuery(q);
        }
        return bot;
    }

    public static ModerationBot setupModerationBot(String moderationLogChannelName) {
        return new ModerationBot(moderationLogChannelName);
    }

    public static CountingBot setupCountingBot() {
        try {
            devMode = Boolean.parseBoolean(properties.getProperty("devMode"));
            if (devMode) {
                Main.LOG("!!!DEVMODE ENABLED!!!");
            }
        } catch (Exception e) {
            devMode = false;
        }

        return new CountingBot();
    }

    public static void addCommands(Guild guild, boolean countingBotSetup) {
        if (countingBotSetup) {
            guild.updateCommands().addCommands(
                    /// CountingBot ///
                    Commands.slash("bann", "Bannt einen User vom Zählen-Game")
                            .addOption(OptionType.USER, "user", "Verbrecher")
                    ,
                    Commands.slash("unbann", "Entbannt einen User vom Zählen-Game")
                            .addOption(OptionType.USER, "user", "Ex-Verbrecher")
                    ,
                    /// BirthdayBot ///
                    Commands.slash("geburtstageintragen", "Trage deinen Geburtstag ein, dann können wir dich gemeinsam Feiern.")
                            .addOption(OptionType.INTEGER, "tag", "Tag", true)
                            .addOption(OptionType.INTEGER, "monat", "Monat", true)
                    ,
                    Commands.slash("geburtstagloeschen", "Du kannst deinen Geburtstag natürlich auch wieder löschen.")
                    ,
                    Commands.slash("setgeburtstagechannel", "Setzt den Channel, in dem die Geburtstage zelebriert werden.")
                            .addOption(OptionType.CHANNEL, "channel", "Textchannel", true)
                    ,
                    /// SupportChannel ///
                    Commands.slash("setsupportchannel", "Verbinde einen VoiceChannel mit einer Rolle.")
                            .addOption(OptionType.CHANNEL, "vc", "Voice-Channel", true)
                            .addOption(OptionType.CHANNEL, "ping", "Ping-Channel", true)
                            .addOption(OptionType.ROLE, "role", "Team-Rolle die gepingt werden soll.", true)
            ).queue();
        } else {
            guild.updateCommands().addCommands(
                    /// CountingBot ///
                    Commands.slash("setup", "Richte den Bot im Gewünschten Channel ein")
                    ,
                    /// BirthdayBot ///
                    Commands.slash("geburtstageintragen", "Trage deinen Geburtstag ein, dann können wir dich gemeinsam Feiern.")
                            .addOption(OptionType.INTEGER, "tag", "Tag", true)
                            .addOption(OptionType.INTEGER, "monat", "Monat", true)
                    ,
                    Commands.slash("geburtstagloeschen", "Trage deinen Geburtstag ein, dann können wir dich gemeinsam Feiern.")
                    ,
                    Commands.slash("setgeburtstagechannel", "Setzt den Channel, in dem die Geburtstage zelebriert werden.")
                            .addOption(OptionType.CHANNEL, "channel", "Textchannel", true)
                    ,
                    /// SupportChannel ///
                    Commands.slash("setsupportchannel", "Verbinde einen VoiceChannel mit einer Rolle.")
                            .addOption(OptionType.CHANNEL, "vc", "Voice-Channel", true)
                            .addOption(OptionType.CHANNEL, "ping", "Ping-Channel", true)
                            .addOption(OptionType.ROLE, "role", "Team-Rolle die gepingt werden soll.", true)
            ).queue();
        }
    }
}