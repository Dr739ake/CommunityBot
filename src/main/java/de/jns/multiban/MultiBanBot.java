package de.jns.multiban;

import de.jns.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MultiBanBot extends ListenerAdapter {
    public JDA jda;

    public MultiBanBot(String token) throws InterruptedException {
        jda = JDABuilder.createLight(token,
                        GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(this)
                .build().awaitReady();
        Main.LOG("MutliBanBot Constructor");
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (event.getName().equals("addtocommuntiy")) {
            List<Command.Choice> options;
            options = Stream.of(getCommunitys().toArray())
                    .filter(word -> ((String) word).startsWith(event.getFocusedOption().getValue()))
                    .map(word -> new Command.Choice((String) word, (String) word))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String command = event.getName();
        Member executor = event.getMember();
        Member target;

        switch (command) {
            case "globalban":
            {
                if (Objects.requireNonNull(executor).hasPermission(Permission.BAN_MEMBERS)) {
                    target = Objects.requireNonNull(event.getInteraction().getOption("user")).getAsMember();
                    Community community = getCommunityByServerId(Objects.requireNonNull(event.getGuild()).getId());
                    String reason = null;
                    try {
                        reason = event.getInteraction().getOption("reason").getAsString();
                    } catch (Exception e) {
                        reason = "Communityausschluss";
                    }

                    StringBuilder reply = new StringBuilder();
                    if (community != null) {
                        for (String serverId: community.servers) {
                            Guild guild = jda.getGuildById(serverId);
                            if ( guild != null && target != null ) {
                                Collection<UserSnowflake> users = new ArrayList<>();
                                users.add(target);
                                guild.ban(users, Duration.ZERO).reason(reason).queue();
                                reply.append(target.getAsMention()).append(" banned from ").append(guild.getName()).append("\n");
                            }
                        }
                    }

                    event.reply(reply.toString()).queue();
                } else {
                    event.reply("No Permission").queue();
                }
            }
            break;
            case "globalbanid":
            {
                if (Objects.requireNonNull(executor).hasPermission(Permission.BAN_MEMBERS)) {
                    String userid = event.getInteraction().getOption("userid").getAsString();
                    String reason = null;
                    try {
                        reason = event.getInteraction().getOption("reason").getAsString();
                    } catch (Exception e) {
                        reason = "Communityausschluss";
                    }
                    jda.retrieveUserById(userid).queue();
                    UserSnowflake userSnowflake = User.fromId(userid);

                    Community community = getCommunityByServerId(Objects.requireNonNull(event.getGuild()).getId());

                    StringBuilder reply = new StringBuilder();
                    if (community != null) {
                        for (String serverId : community.servers) {
                            Guild guild = jda.getGuildById(serverId);
                            if( guild != null ) {
                                Collection<UserSnowflake> users = new ArrayList<>();
                                users.add(userSnowflake);
                                guild.ban(users, Duration.ZERO).reason(reason).queue(
                                        success -> Main.LOG("Banned " + userSnowflake.getId()),
                                        failure -> Main.LOG("Failed to ban " + userSnowflake.getId() + "\n" + failure)
                                );
                                reply.append(userSnowflake.getAsMention()).append(" banned from ").append(guild.getName()).append("\n");
                            }
                        }
                    }

                    event.reply(reply.toString()).queue();
                } else {
                    event.reply("No Permission").queue();
                }
            }
            break;
            case "createcommuntiy":
            {
                String sCommunity = Objects.requireNonNull(event.getInteraction().getOption("community")).getAsString();
                String sPassword = Objects.requireNonNull(event.getInteraction().getOption("password")).getAsString();

                boolean result = createCommunity(sCommunity, sPassword);

                if (result) {
                    event.reply("Success").setEphemeral(true).queue();
                } else {
                    event.reply("Community already exists").setEphemeral(true).queue();
                }
            }
            break;
            case "addtocommuntiy":
            {
                String sCommunity = Objects.requireNonNull(event.getInteraction().getOption("community")).getAsString();
                String sPassword = Objects.requireNonNull(event.getInteraction().getOption("password")).getAsString();

                if (Objects.requireNonNull(executor).hasPermission(Permission.ADMINISTRATOR)) {
                    addServerToCommunity(sCommunity,sPassword, Objects.requireNonNull(event.getGuild()).getId());
                    event.reply("Success").queue();
                } else {
                    event.reply("No Permission. This needs to be done by the Server Owner").queue();
                }
            }
            break;
        }
    }

/// /// STATIC /// ///

    public static final String JSON_FILE = "multibanbot_data.json";
    static public HashMap<String, Community> communitys;

    // Funktion: HashMap<String, Community> -> JSON in Datei schreiben
    public static void writeMapToJsonFile(HashMap<String, Community> map, String pathToFile) throws IOException {
        JSONObject jsonObject = new JSONObject();

        for (Map.Entry<String, Community> entry : map.entrySet()) {
            JSONObject communityObject = new JSONObject();
            communityObject.put("password", entry.getValue().password);
            communityObject.put("servers", new JSONArray(entry.getValue().servers));

            jsonObject.put(entry.getKey(), communityObject);
        }

        try (FileWriter writer = new FileWriter(pathToFile)) {
            writer.write(jsonObject.toString(4)); // formatiert schreiben
        }
    }

    // Funktion: JSON aus Datei -> HashMap<String, Community>
    public static HashMap<String, Community> readMapFromJsonFile(String pathToFile) throws IOException {
        HashMap<String, Community> map = new HashMap<>();

        File file = new File(pathToFile);

        StringBuilder jsonString = new StringBuilder();
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonString.append(line);
                }
            }
        } else {
            jsonString.append("{}");
        }

        JSONObject jsonObject = new JSONObject(jsonString.toString());

        for (String key : jsonObject.keySet()) {
            JSONObject communityObject = jsonObject.getJSONObject(key);

            String password = communityObject.getString("password");
            Community community = new Community(password);

            JSONArray serversArray = communityObject.getJSONArray("servers");
            for (int i = 0; i < serversArray.length(); i++) {
                community.servers.add(serversArray.getString(i));
            }

            map.put(key, community);
        }

        return map;
    }

    static public Set<String> getCommunitys() {
        return communitys.keySet();
    }

    static public boolean createCommunity(String name, String password) {
        if (communitys.keySet().contains(name)) {
            return false;
        }
        communitys.put(name, new Community(password));

        try {
            writeMapToJsonFile(communitys, JSON_FILE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    static public boolean addServerToCommunity(String sCommunity, String password, String sId) {
        Community community = getCommunity(sCommunity);

        if (community != null && Objects.equals(community.password, password) && !community.servers.contains(sId)) {
            community.servers.add(sId);

            try {
                writeMapToJsonFile(communitys, JSON_FILE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    static Community getCommunity(String name) {
        return communitys.get(name);
    }

    static Community getCommunityByServerId(String serverId) {
        for(String key : communitys.keySet()) {
            Community cm = communitys.get(key);
            if (cm.servers.contains(serverId))
                return cm;
        }
        return null;
    }

    static class Community {
        public String password;
        public List<String> servers;

        Community(String password) {
            servers = new ArrayList<>();
            this.password = password;
        }
    }
}
