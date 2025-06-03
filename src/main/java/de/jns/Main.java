package de.jns;

import de.jns.countingbot.CountingBot;
import de.jns.countingbot.ServerData;
import de.jns.multiban.MultiBanBot;
import de.jns.rollenmeister.RollenBot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.io.*;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Properties;
import java.util.Scanner;

public class Main {

    public static boolean devMode;
    public static HashMap<String, String> logChannels = new HashMap<>();
    public static HashMap<String, Role> adminRoles = new HashMap<>();
    static Properties properties;
    static final String PROPERTIES_FILE = "ggc.properties";

    private static final HashMap<String, Message> messages = new HashMap<>();

    public static void LOG(String s) {
        String format = ZonedDateTime
                .now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")); //
        System.out.println(format + " [LOG] " + s);
    }

    public static ResultSet ExecuteQuery(String query) {
        Statement statement;
        ResultSet resultSet = null;
        try {
            // Database credentials
            String url = "jdbc:mariadb://" + properties.getProperty("db-ip") + ":" + properties.getProperty("db-port") + "/" + properties.getProperty("database");
            String username = properties.getProperty("username");
            String password = properties.getProperty("password");

            // Establish the connection
            Connection connection = DriverManager.getConnection(url, username, password);

            // Begin Request
            connection.beginRequest();
            statement = connection.createStatement();
            resultSet = statement.executeQuery(query);
            // Main.LOG(query);
            connection.endRequest();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return resultSet;
    }

    static MultiBanBot multiBanBot;
    static RollenBot rollenBot;
    static CountingBot countingBot;

    public static void main(String[] args) throws Exception {
        if (!new File(PROPERTIES_FILE).exists()) {
            BufferedWriter br = new BufferedWriter(new FileWriter(PROPERTIES_FILE));
            br.write("token=none\n");
            br.write("devMode=false\n");
            br.write("db-ip=none\n");
            br.write("username=none\n");
            br.write("password=none\n");
            br.write("database=none\n");
            br.write("db-port=3306\n");
            br.write("port-web-server=none\n");

            br.flush();
            br.close();
            LOG("Please configure in " + PROPERTIES_FILE);
            return;
        }
        properties = new Properties();
        properties.load(new FileInputStream(PROPERTIES_FILE));

        rollenBot = setupRollenmeister();
        multiBanBot = setupMultiBan();
        countingBot = setupCountingBot();

        System.out.println("Hello World");
        boolean running = true;
        while (running) {
            Scanner sc = new Scanner(System.in);
            String in = sc.nextLine();
            if (in.equals("restart")) {
                LOG("restarting...");
                rollenBot.jda.shutdown();
                multiBanBot.jda.shutdown();
                countingBot.jda.shutdown();
                running = false;
                main(null);
            } else if (in.equals("stop")) {
                LOG("stopping...");
                rollenBot.jda.shutdown();
                multiBanBot.jda.shutdown();
                countingBot.jda.shutdown();
                running = false;
            } else {
                LOG("Unknown Command");
                LOG("available: \"restart\", \"stop\"");
            }
            sc.close();
        }
    }

    public static MultiBanBot setupMultiBan() throws Exception {
        MultiBanBot.communitys = MultiBanBot.readMapFromJsonFile(MultiBanBot.JSON_FILE);

        String token = properties.getProperty("token");

        MultiBanBot bot = new MultiBanBot(token);

        LOG("BOT-NAME: " + bot.jda.getSelfUser().getName());
        LOG("Bot Ready, should be ONLINE");
        LOG("Token: " + token);

        return bot;
    }

    public static RollenBot setupRollenmeister() throws Exception {
        String token = properties.getProperty("token");
        try {
            devMode = Boolean.parseBoolean(properties.getProperty("devMode"));
            if (devMode) {
                LOG("!!!DEVMODE ENABLED!!!");
            }
        } catch (Exception e) {
            devMode = false;
        }

        String[] createTableQuerys = {
                "CREATE TABLE IF NOT EXISTS servers ( id varchar(255) PRIMARY KEY, logchannel varchar(255), admin_role varchar(255) );",
                "CREATE TABLE IF NOT EXISTS groups ( id INT PRIMARY KEY AUTO_INCREMENT, name varchar(255) NOT NULL, serverId varchar(255), FOREIGN KEY (serverId) REFERENCES servers(id) );",
                "CREATE TABLE IF NOT EXISTS roles ( id varchar(255) PRIMARY KEY, name varchar(255) );",
                "CREATE TABLE IF NOT EXISTS groups_roles (group_id INT, role_id varchar(255), rolePos INT NOT NULL, isManager BOOLEAN, isGeneric BOOLEAN, PRIMARY KEY (group_id, role_id), FOREIGN KEY (group_id) REFERENCES groups(id), FOREIGN KEY (role_id) REFERENCES roles(id) );"
        };

        // Load and register MariaDB JDBC driver (optional in recent versions)
        Class.forName("org.mariadb.jdbc.Driver");
        Main.LOG("Connected to MariaDB!");

        for (String q : createTableQuerys) {
            ExecuteQuery(q);
        }

        RollenBot bot = new RollenBot(token);

        for (Guild guild : bot.jda.getGuilds()) {
            addCommands(guild, false);
            ExecuteQuery("INSERT IGNORE INTO servers (id) VALUES ('" + guild.getId() + "');");
        }

        // Load LogChannels from Database into Hashmap
        ResultSet resultSet = ExecuteQuery("SELECT * FROM servers;");
        while (resultSet.next()) {
            logChannels.put(resultSet.getString(1), resultSet.getString(2));
            String roleId = resultSet.getString(3);
            if (roleId != null)
                adminRoles.put(resultSet.getString(1), bot.jda.getRoleById(roleId));
        }

        LOG("BOT-NAME: " + bot.jda.getSelfUser().getName());
        LOG("Bot Ready, should be ONLINE");
        LOG("Token: " + token);

        return bot;
    }

    public static CountingBot setupCountingBot() throws Exception {
        String token = properties.getProperty("token");
        try {
            devMode = Boolean.parseBoolean(properties.getProperty("devMode"));
            if (devMode) {
                Main.LOG("!!!DEVMODE ENABLED!!!");
            }
        } catch (Exception e) {
            devMode = false;
        }

        CountingBot bot = new CountingBot(token);

        for(Guild guild: bot.jda.getGuilds()) {

            ServerData serverData = new ServerData(guild.getId());
            CountingBot.data.put(guild.getId(), serverData);

            addCommands(guild, (serverData.channelId != null && !serverData.channelId.isEmpty()));
        }
        return bot;
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
            ).queue();
        }
    }
}