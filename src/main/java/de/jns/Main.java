package de.jns;

import de.jns.birthdaybot.BirthdayBot;
import de.jns.countingbot.CountingBot;
import de.jns.countingbot.ServerData;
import de.jns.dbtranslator.RoleManagerDB;
import de.jns.gitlabissues.GitLabIssueCreator;
import de.jns.moderation.ModerationBot;
import de.jns.multiban.MultiBanBot;
import de.jns.supportchannel.SupportChannelBLOB;
import de.jns.supportchannel.SupportChannelBot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
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
    public static GGCListenerAdapter commandListener = new GGCListenerAdapter();

    public static String VERSION_NUMBER = "v1.5.2-dirty";
    private static int issueCreatorRetryCounter = 0;

    public static boolean devMode;
    public static boolean isSQLite = false;
    public static HashMap<String, String> logChannels = new HashMap<>();
    public static HashMap<String, Role> adminRoles = new HashMap<>();

    static Properties properties;
    static final String PROPERTIES_FILE = "ggc.properties";

    public static void LOG(String s) {
        String format = ZonedDateTime
                .now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")); //
        System.out.println(format + " [LOG] " + s);
    }

    public static ResultSet ExecuteQuery_NOLOG(String query) {
        System.out.println(query);
        return _ExecuteQuery(query, false);
    }

    public static ResultSet ExecuteQuery(String query) {
        System.out.println(query);
        return _ExecuteQuery(query, true);
    }

    private static ResultSet _ExecuteQuery(String query, boolean log) {
        Statement statement;
        ResultSet resultSet = null;
        try {
            String dbType = properties.getProperty("db-type", "sqlite");

            if (dbType.equals("sqlite")) {
                String database = properties.getProperty("database", "bot");
                String url = "jdbc:sqlite:" + database + ".db";

                // Load SQLite JDBC driver
                Class.forName("org.sqlite.JDBC");

                try {
                    Connection connection = DriverManager.getConnection(url);

                    // Begin Request
                    statement = connection.createStatement();
                    resultSet = statement.executeQuery(query);
                    if (log) {
                        Main.LOG(query);
                    }
                    connection.close();
                } catch (SQLException e) {
                    return null;
                }
            } else {
                // MariaDB connection (existing logic)
                String url = "jdbc:mariadb://" + properties.getProperty("db-ip") + ":" +
                        properties.getProperty("db-port") + "/" + properties.getProperty("database");
                String username = properties.getProperty("username");
                String password = properties.getProperty("password");

                try {
                    Connection connection = DriverManager.getConnection(url, username, password);
                    // Begin Request
                    connection.beginRequest();
                    statement = connection.createStatement();
                    statement.executeQuery(query);
                    resultSet = statement.getResultSet();
                    if (log) {
                        Main.LOG(query);
                    }
                    connection.endRequest();
                } catch (SQLException e) {
                    return null;
                }
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return resultSet;
    }

    static MultiBanBot multiBanBot;
    static RoleManager rollenBot;
    static CountingBot countingBot;
    static BirthdayBot birthdayBot;
    static ModerationBot moderationBot;
    static SupportChannelBot supportChannelBot;
    static GitLabIssueCreator issueCreator;

    public static void main(String[] args) throws Exception {
        if (!new File(PROPERTIES_FILE).exists()) {
            BufferedWriter br = new BufferedWriter(new FileWriter(PROPERTIES_FILE));
            br.write("token=none\n");
            br.write("devMode=false\n");
            br.write("db-type=sqlite\n");
            br.write("db-ip=none\n");
            br.write("db-port=3306\n");
            br.write("username=none\n");
            br.write("password=none\n");
            br.write("database=none\n");
            br.write("moderationLog=none\n");

            br.flush();
            br.close();
            LOG("Please configure in " + PROPERTIES_FILE);
            return;
        }
        properties = new Properties();
        properties.load(new FileInputStream(PROPERTIES_FILE));

        isSQLite = properties.getProperty("db-type", "sqlite").equals("sqlite");

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
        if (multiBanBot == null) {
            r = true;
        }
        if (rollenBot == null) {
            r = true;
        }
        if (countingBot == null) {
            r = true;
        }
        if (birthdayBot == null) {
            r = true;
        }
        if (moderationBot == null) {
            r = true;
        }
        if (supportChannelBot == null) {
            r = true;
        }
        if (issueCreator == null) {
            r = true;
        }
        return r;
    }

    private static List<ListenerAdapter> setupListenerAdapters() {
        List<ListenerAdapter> listenerAdaptersList = new ArrayList<>();

        if (rollenBot == null) {
            try {
                rollenBot = setupRollenmeister();
                listenerAdaptersList.add(rollenBot);

                // RollenBot Stuff
                for (Guild guild : jda.getGuilds()) {
                    addCommands(guild, false);
                    if (isSQLite) {
                        ExecuteQuery("INSERT OR IGNORE INTO servers (id) VALUES ('" + guild.getId() + "');");
                    } else {
                        ExecuteQuery("INSERT IGNORE INTO servers (id) VALUES ('" + guild.getId() + "');");
                    }
                }
                // Load LogChannels from Database into Hashmap
                ResultSet resultSet = ExecuteQuery("SELECT * FROM servers;");
                while (resultSet.next()) {
                    logChannels.put(resultSet.getString(1), resultSet.getString(2));
                    String roleId = resultSet.getString(3);
                    if (roleId != null)
                        adminRoles.put(resultSet.getString(1), jda.getRoleById(roleId));
                }
            } catch (Exception e) {
                Main.LOG("rollenBot failed to start: " + e.getMessage());
            }
        }

        if (multiBanBot == null) {
            try {
                multiBanBot = setupMultiBan();
                listenerAdaptersList.add(multiBanBot);
            } catch (Exception e) {
                Main.LOG("multiBanBot failed to start: " + e.getMessage());
            }
        }

        if (countingBot == null) {
            try {
                countingBot = setupCountingBot();
                listenerAdaptersList.add(countingBot);
                // CountingBot Stuff
                for (Guild guild : jda.getGuilds()) {
                    ServerData serverData = new ServerData(guild.getId());
                    CountingBot.data.put(guild.getId(), serverData);

                    addCommands(guild, (serverData.channelId != null && !serverData.channelId.isEmpty()));
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
            } catch (Exception e) {
                Main.LOG("moderationBot failed to start: " + e.getMessage());
            }
        }

        if (supportChannelBot == null) {
            try {
                supportChannelBot = setupSupportChannelBot();
                listenerAdaptersList.add(supportChannelBot);

                // SupportChannel Stuff
                ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM supportchannels");
                while (resultSet.next()) {
                    SupportChannelBLOB blob = new SupportChannelBLOB();
                    blob.vc = jda.getVoiceChannelById(resultSet.getString(1));
                    blob.ping = jda.getTextChannelById(resultSet.getString(2));
                    blob.role = jda.getRoleById(resultSet.getString(3));
                    supportChannelBot.AddKnownChannel(blob.vc.getId(), blob);
                }
            } catch (Exception e) {
                Main.LOG("supportChannelBot failed to start: " + e.getMessage());
            }
        }

        if (issueCreator == null && issueCreatorRetryCounter < 1) {
            try {
                issueCreator = setupGitLabIssueCreator();
                if (issueCreator != null) {
                    listenerAdaptersList.add(issueCreator);
                }
            } catch (Exception e) {
                Main.LOG("issueCreator failed to start: " + e.getMessage());
            }
        }
        return listenerAdaptersList;
    }

    public static BirthdayBot setupBirthdayBot() throws Exception {
        String channelId = properties.getProperty("birthdayChannel");
        BirthdayBot bot = new BirthdayBot(channelId);

        String[] createTableQueryMariaDB = {
                "CREATE TABLE IF NOT EXISTS birthday_days ( id VARCHAR(255) PRIMARY KEY, day INT, month INT, was_celebrated INT);",
                "CREATE TABLE IF NOT EXISTS birthday_server_conf ( server_id VARCHAR(255) PRIMARY KEY, textChannelId VARCHAR(255) );"
        };

        String[] createTableQuerySQLite = {
                "CREATE TABLE IF NOT EXISTS birthday_days ( id VARCHAR(255) PRIMARY KEY, day INT, month INT, was_celebrated INT);",
                "CREATE TABLE IF NOT EXISTS birthday_server_conf ( server_id VARCHAR(255) PRIMARY KEY, textChannelId VARCHAR(255) );"
        };

        String[] createTableQuery;

        if (isSQLite) {
            createTableQuery = createTableQuerySQLite;
        } else {
            createTableQuery = createTableQueryMariaDB;
        }

        for (String q : createTableQuery) {
            ExecuteQuery(q);
        }
        return bot;
    }

    public static ModerationBot setupModerationBot(String moderationLogChannelname) {
        return new ModerationBot(moderationLogChannelname);
    }

    public static SupportChannelBot setupSupportChannelBot() {
        return new SupportChannelBot();
    }

    public static GitLabIssueCreator setupGitLabIssueCreator() {
        GitLabIssueCreator bot = null;
        try {
            bot = new  GitLabIssueCreator(properties.getProperty("gitlabUrl"), properties.getProperty("gitlabToken"));
        }  catch (Exception e) {
            Main.LOG("setupGitLabIssueCreator failed: " + e.getMessage());
            issueCreatorRetryCounter++;
        }
        return bot;
    }

    public static MultiBanBot setupMultiBan() throws Exception {
        MultiBanBot.communitys = MultiBanBot.readMapFromJsonFile(MultiBanBot.JSON_FILE);
        return new MultiBanBot();
    }

    public static RoleManager setupRollenmeister() throws Exception {
        try {
            devMode = Boolean.parseBoolean(properties.getProperty("devMode"));
            if (devMode) {
                LOG("!!!DEVMODE ENABLED!!!");
            }
        } catch (Exception e) {
            devMode = false;
        }
        RoleManagerDB.CreateTablesIfNotExist();
        return new RoleManager();
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
                    Commands.slash("score", "Zeigt dir den Highscore")
                    ,
                    Commands.slash("resetall", "Setzt alles zurück")
                    ,
                    Commands.slash("resetrun", "Setzt den aktuellen Durchlauf zurück")
                    ,
                    /// Rollenmeister ///
                    Commands.slash("creategroup", "Erstelle eine neue Rollengruppe (z.B. einen neues Team)")
                            .addOption(OptionType.STRING, "groupname", "Name der Rollengruppe/Teams", true, false)
                    ,
                    Commands.slash("addrole", "Füge einen Rolle zu einem Team hinzu.")
                            .addOption(OptionType.STRING, "team", "Team", true, true)
                            .addOption(OptionType.ROLE, "role", "Role to add", true)
                            .addOption(OptionType.INTEGER, "position", "The Position of the Role in the chain.", false, false)
                            .addOption(OptionType.BOOLEAN, "isgeneric", "Is this Role a Generic group for everyone.", false)
                            .addOption(OptionType.BOOLEAN, "ismanager", "Is this Role a Group-Manager.", false)
                    ,
                    Commands.slash("add", "Füge einen User zu deinem Team hinzu. Das ist nur für die Rollen auf dem Discord")
                            .addOption(OptionType.USER, "user", "Neues Mitglied", true)
                            .addOption(OptionType.STRING, "team", "Team", true, true)
                    ,
                    Commands.slash("remove", "Entferne einen User aus deinem Team. Das ist nur für die Rollen auf dem Discord")
                            .addOption(OptionType.USER, "user", "Ex-Mitglied", true)
                            .addOption(OptionType.STRING, "team", "Team", true, true)
                    ,
                    Commands.slash("promote", "Promote einen User in deinem Team. Das ist nur für die Rollen auf dem Discord")
                            .addOption(OptionType.USER, "user", "Mitglied", true)
                            .addOption(OptionType.STRING, "team", "Team", true, true)
                    ,
                    Commands.slash("demote", "Demote einen User in deinem Team. Das ist nur für die Rollen auf dem Discord")
                            .addOption(OptionType.USER, "user", "Mitglied", true)
                            .addOption(OptionType.STRING, "team", "Team", true, true)
                    ,
                    Commands.slash("setlogchannel", "Füge einen Logchannel hinzu")
                            .addOption(OptionType.CHANNEL, "logchannel", "Logchannel", true)
                    ,
                    Commands.slash("setadminrole", "Setze eine Adminrolle")
                            .addOption(OptionType.ROLE, "role", "Rolle", true)
                    ,
                    Commands.slash("cleargroup", "Entfernt alle Rollen aus einer Gruppe")
                            .addOption(OptionType.STRING, "groupname", "Name der Rollengruppe/Teams", true, true)
                    ,
                    Commands.slash("deletegroup", "Löscht eine Gruppe")
                            .addOption(OptionType.STRING, "groupname", "Name der Rollengruppe/Teams", true, true)
                    ,
                    Commands.slash("groupinfo", "Info über die Gruppe")
                            .addOption(OptionType.STRING, "groupname", "Name der Rollengruppe/Teams", true, true)
                    ,
                    Commands.slash("serverinfo", "Info über den Server")
                    ,
                    /// MutliBanBot ///
                    Commands.slash("globalban", "Bannt einen User auf allen verfügbaren Servern")
                            .addOption(OptionType.USER, "user", "Mitglied", true)
                            .addOption(OptionType.STRING, "reason", "Banngrund: Default = CBann")
                    ,
                    Commands.slash("globalbanid", "Bannt einen User auf allen verfügbaren Servern")
                            .addOption(OptionType.STRING, "userid", "Mitglied", true)
                            .addOption(OptionType.STRING, "reason", "Banngrund: Default = CBann")
                    ,
                    Commands.slash("addtocommuntiy", "Fügt den Server zu einer Community hinzu")
                            .addOption(OptionType.STRING, "community", "Community", true, true)
                            .addOption(OptionType.STRING, "password", "Password", true)
                    ,
                    Commands.slash("createcommuntiy", "Erstellt eine Community")
                            .addOption(OptionType.STRING, "community", "Community", true)
                            .addOption(OptionType.STRING, "password", "Password", true)
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
                    ,
                    /// Gitlab Issues ///
                    Commands.slash("sendissueembed", "Verbinde einen VoiceChannel mit einer Rolle.")
                            .addOption(OptionType.CHANNEL, "channel", "Ping-Channel", true)
            ).queue();
        } else {
            guild.updateCommands().addCommands(
                    /// CountingBot ///
                    Commands.slash("setup", "Richte den Bot im Gewünschten Channel ein")
                    ,
                    /// Rollenmeister ///
                    Commands.slash("creategroup", "Erstelle eine neue Rollengruppe (z.B. einen neues Team)")
                            .addOption(OptionType.STRING, "groupname", "Name der Rollengruppe/Teams", true, false)
                    ,
                    Commands.slash("addrole", "Füge einen Rolle zu einem Team hinzu.")
                            .addOption(OptionType.STRING, "team", "Team", true, true)
                            .addOption(OptionType.ROLE, "role", "Role to add", true)
                            .addOption(OptionType.INTEGER, "position", "The Position of the Role in the chain.", false, false)
                            .addOption(OptionType.BOOLEAN, "isgeneric", "Is this Role a Generic group for everyone.", false)
                            .addOption(OptionType.BOOLEAN, "ismanager", "Is this Role a Group-Manager.", false)
                    ,
                    Commands.slash("add", "Füge einen User zu deinem Team hinzu. Das ist nur für die Rollen auf dem Discord")
                            .addOption(OptionType.USER, "user", "Neues Mitglied", true)
                            .addOption(OptionType.STRING, "team", "Team", true, true)
                    ,
                    Commands.slash("remove", "Entferne einen User aus deinem Team. Das ist nur für die Rollen auf dem Discord")
                            .addOption(OptionType.USER, "user", "Ex-Mitglied", true)
                            .addOption(OptionType.STRING, "team", "Team", true, true)
                    ,
                    Commands.slash("promote", "Promote einen User in deinem Team. Das ist nur für die Rollen auf dem Discord")
                            .addOption(OptionType.USER, "user", "Mitglied", true)
                            .addOption(OptionType.STRING, "team", "Team", true, true)
                    ,
                    Commands.slash("demote", "Demote einen User in deinem Team. Das ist nur für die Rollen auf dem Discord")
                            .addOption(OptionType.USER, "user", "Mitglied", true)
                            .addOption(OptionType.STRING, "team", "Team", true, true)
                    ,
                    Commands.slash("setlogchannel", "Füge einen Logchannel hinzu")
                            .addOption(OptionType.CHANNEL, "logchannel", "Logchannel", true)
                    ,
                    Commands.slash("setadminrole", "Setze eine Adminrolle")
                            .addOption(OptionType.ROLE, "role", "Rolle", true)
                    ,
                    Commands.slash("cleargroup", "Entfernt alle Rollen aus einer Gruppe")
                            .addOption(OptionType.STRING, "groupname", "Name der Rollengruppe/Teams", true, true)
                    ,
                    Commands.slash("deletegroup", "Löscht eine Gruppe")
                            .addOption(OptionType.STRING, "groupname", "Name der Rollengruppe/Teams", true, true)
                    ,
                    Commands.slash("groupinfo", "Info über die Gruppe")
                            .addOption(OptionType.STRING, "groupname", "Name der Rollengruppe/Teams", true, true)
                    ,
                    Commands.slash("serverinfo", "Info über den Server")
                    ,
                    /// MutliBanBot ///
                    Commands.slash("globalban", "Bannt einen User auf allen verfügbaren Servern")
                            .addOption(OptionType.USER, "user", "Mitglied", true)
                            .addOption(OptionType.STRING, "reason", "Banngrund: Default = CBann")
                    ,
                    Commands.slash("globalbanid", "Bannt einen User auf allen verfügbaren Servern")
                            .addOption(OptionType.STRING, "userid", "Mitglied", true)
                            .addOption(OptionType.STRING, "reason", "Banngrund: Default = CBann")
                    ,
                    Commands.slash("addtocommuntiy", "Fügt den Server zu einer Community hinzu")
                            .addOption(OptionType.STRING, "community", "Community", true, true)
                            .addOption(OptionType.STRING, "password", "Password", true)
                    ,
                    Commands.slash("createcommuntiy", "Erstellt eine Community")
                            .addOption(OptionType.STRING, "community", "Community", true)
                            .addOption(OptionType.STRING, "password", "Password", true)
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
                    ,
                    /// Gitlab Issues ///
                    Commands.slash("sendissueembed", "Verbinde einen VoiceChannel mit einer Rolle.")
                            .addOption(OptionType.CHANNEL, "channel", "Ping-Channel", true)
            ).queue();
        }
    }
}