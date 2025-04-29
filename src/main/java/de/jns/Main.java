package de.jns;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.jns.countingbot.CountingBot;
import de.jns.countingbot.ServerData;
import de.jns.multiban.MultiBanBot;
import de.jns.rollenmeister.RollenBot;
import de.jns.serverinfo.ServerinfoBot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.RestAction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;

public class Main implements HttpHandler {

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
            // System.out.println(query);
            connection.endRequest();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return resultSet;
    }

    static MultiBanBot multiBanBot;
    static RollenBot rollenBot;
    static CountingBot countingBot;
    static ServerinfoBot statusinfoBot;

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
        statusinfoBot = setupServerinfoBot();

        boolean running = true;
        while (running) {
            Scanner sc = new Scanner(System.in);
            String in = sc.nextLine();
            if (in.equals("restart")) {
                LOG("restarting...");
                rollenBot.jda.shutdown();
                multiBanBot.jda.shutdown();
                countingBot.jda.shutdown();
                statusinfoBot.jda.shutdown();
                running = false;
                main(null);
            } else if (in.equals("stop")) {
                LOG("stopping...");
                rollenBot.jda.shutdown();
                multiBanBot.jda.shutdown();
                countingBot.jda.shutdown();
                statusinfoBot.jda.shutdown();
                running = false;
            } else {
                LOG("Unknown Command");
                LOG("available: \"restart\", \"stop\"");
            }
            sc.close();
        }
    }

    static File dataDIR;

    public static ServerinfoBot setupServerinfoBot() throws IOException {
        int port;
        String portStr;

        String botToken;
        String botChannel;

        portStr = (String) properties.get("port-web-server");
        port = Integer.parseInt(portStr);
        botToken = properties.getProperty("token");
        botChannel = properties.getProperty("channelId");

        System.out.println("Port: " + port);

        ServerinfoBot bot = new ServerinfoBot(botToken, botChannel);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new Main());
        server.setExecutor(null);
        server.start();
        System.out.println("HTTP Server Ready");
        System.out.println("BOT-Name: " + bot.jda.getSelfUser().getName());

        dataDIR = new File("nvram");
        if (!dataDIR.exists()){
            dataDIR.mkdirs();
        }

        for(String file: Objects.requireNonNull(dataDIR.list())) {
            String s = Files.readString(Path.of("nvram/" + file));
            s = s.replaceAll("(\\r|\\n)", "");
            //System.out.println(file + " -> " + s);
            RestAction<Message> messageRestAction = bot.myChannel.retrieveMessageById(s);
            Message complete = null;
            int counter = 0;

            while (complete == null && counter < 10)
            {
                try {
                    counter++;
                    complete = messageRestAction.complete();
                } catch (Exception ignore) {}
            }

            if (complete != null) {
                messages.put(file, complete);
            } else {
                System.out.println("Message was deleted in the meantime.");
            }
        }
        return bot;
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
        System.out.println("Connected to MariaDB!");

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
                System.out.println("!!!DEVMODE ENABLED!!!");
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

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        System.out.println("Received POST from: " + httpExchange.getRemoteAddress());
        InputStreamReader isr = new InputStreamReader(httpExchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        String serverId = httpExchange.getRequestHeaders().get("serverid").get(0);
        System.out.println("ServerId = " + serverId);


        int b;
        StringBuilder stringBuilder = new StringBuilder(1024);
        while ( (b = br.read()) != -1 )
        {
            stringBuilder.append((char) b);
        }

        JSONObject json = new JSONObject(stringBuilder.toString());
        try
        {
            String name = json.getString("name");
            String map = json.getString("map");
            String ip = json.getString("ip");
            int maxPlayers = json.getInt("maxPlayers");
            JSONObject rgb = json.getJSONObject("color");

            Color color = new Color(rgb.getInt("red"), rgb.getInt("green"), rgb.getInt("blue"));
            JSONArray players = json.getJSONArray("players");

            //System.out.println("Content: ");
            System.out.println("name: "+ name);
            //System.out.println("map: "+ map);
            //System.out.println("ip: "+ ip);
            //System.out.println("maxPlayers: "+ maxPlayers);

            EmbedBuilder builder = new EmbedBuilder();

            builder.setTitle( name );
            builder.setColor( color );

            StringBuilder playerStrBuilder = new StringBuilder();
            int iPlayerCnt = 0;
            for (int i = 0; i < players.length(); i++)
            {
                playerStrBuilder.append(players.get(i));
                if (i < players.length()-1)
                {
                    playerStrBuilder.append(", ");
                }
                /*
                if (playerStrBuilder.length() % 40 == 0)
                {
                    playerStrBuilder.append("\n");
                }
                */
                iPlayerCnt++;
            }

            builder.addField("Map", map, true);
            builder.addField("IP-Adresse", ip, true);
            String sConnectionLink = "steam://connect/" + ip;
            builder.addField("Connection-Link", "["+sConnectionLink+"](https://"+sConnectionLink+")", true);

            builder.addField("Players " + iPlayerCnt + "/" + maxPlayers + " (" + (int) ( ( (double)iPlayerCnt / (double)maxPlayers ) * 100) + "%)", playerStrBuilder.toString(), false);

            ZoneId zoneId = ZoneId.of("Europe/Berlin");
            ZonedDateTime zonedDateTime = ZonedDateTime.now(zoneId);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss | dd.MM.yyyy");
            String formattedTime = zonedDateTime.format(formatter);

            builder.setFooter(formattedTime);

            MessageEmbed embed = builder.build();

            if (messages.containsKey(serverId))
            {
                Message message = messages.get(serverId);
                Message newMessage = statusinfoBot.UpdateMessage(message, embed);

                if (message != newMessage)
                {
                    messages.put(serverId, newMessage);
                    PrintWriter out = new PrintWriter("nvram/" + serverId);
                    out.println(message.getId());
                    out.flush();
                }
            }
            else
            {
                Message message = statusinfoBot.CreateMessage(embed, statusinfoBot.myChannel);
                messages.put(serverId, message);
                PrintWriter out = new PrintWriter("nvram/" + serverId);
                out.println(message.getId());
                out.flush();
            }
        }
        catch (Exception e)
        {
            String response = e.toString();
            httpExchange.sendResponseHeaders(500, response.length());
            OutputStream os = httpExchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
        br.close();
        isr.close();

        String response = "Success";
        httpExchange.sendResponseHeaders(200, response.length());
        OutputStream os = httpExchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}