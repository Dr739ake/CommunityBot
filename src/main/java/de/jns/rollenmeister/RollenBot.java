package de.jns.rollenmeister;

import de.jns.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RollenBot extends ListenerAdapter {
    public JDA jda;

    public RollenBot(String token) throws InterruptedException {
        jda = JDABuilder.createLight(token,
                        GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(this)
                .build().awaitReady();
        Main.LOG("RollenBot Constructor");
    }

    public void WriteToLogChannel(Guild guild, String content) {
        TextChannel channel = null;
        try {
            channel = jda.getTextChannelById(Main.logChannels.get(guild.getId()));
        } catch (Exception e) {
            System.out.println("Logchannel missing for " + guild.getName());
        }
        if (channel != null)
            channel.sendMessage(content).queue();
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        String command = event.getName();
        Member executor = event.getMember();

        boolean bMemberIsAdmin = Objects.requireNonNull(event.getMember()).hasPermission(Permission.ADMINISTRATOR);
        List<Command.Choice> options;

        switch (command) {
            case "createGroup":
                break;
            case "addrole":
                ResultSet resultSetAddRole = Main.ExecuteQuery("SELECT * FROM groups WHERE serverId = '" + event.getGuild().getId() + "';");

                ArrayList<String> array = new ArrayList<>();
                try {
                    resultSetAddRole.first();
                    array.add(resultSetAddRole.getString("name"));
                    while (resultSetAddRole.next()) {
                        array.add(resultSetAddRole.getString("name"));
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                options = Stream.of(array.toArray())
                        .filter(word -> ((String) word).startsWith(event.getFocusedOption().getValue()))
                        .map(word -> new Command.Choice((String) word, (String) word))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
                break;
            case "groupinfo":
            case "cleargroup":
            case "deletegroup":
            case "promote":
            case "demote":
            case "remove":
            case "add": {
                List<Role> executorRoles = executor.getRoles();
                ArrayList<Integer> groupIds = new ArrayList<>();
                if (bMemberIsAdmin || ( Main.adminRoles.get(event.getGuild().getId()) != null &&  executor.getRoles().contains(Main.adminRoles.get(event.getGuild().getId())))) {
                    ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM groups WHERE serverId = '"+event.getGuild().getId()+"';");

                    try {
                        while (resultSet.next()) {
                            int groupId = resultSet.getInt("id");
                            groupIds.add(groupId);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    for (Role r : executorRoles) {
                        ResultSet resultSetAdd = Main.ExecuteQuery("SELECT group_id FROM groups_roles WHERE role_id = '" + r.getId() + "' AND isManager = True;");

                        try {
                            while (resultSetAdd.next()) {
                                int groupId = resultSetAdd.getInt("group_id");
                                groupIds.add(groupId);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                ArrayList<String> groupNames = new ArrayList<>();

                for (Integer groupId : groupIds) {
                    ResultSet resultSet = Main.ExecuteQuery("SELECT name FROM groups WHERE id = " + groupId + " AND serverId = '" + event.getGuild().getId() + "'");
                    try {
                        resultSet.next();
                        String name = resultSet.getString("name");
                        groupNames.add(name);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                if (command.equals("groupinfo")) {
                    groupNames.add("all");
                }

                options = Stream.of(groupNames.toArray())
                        .filter(word -> ((String) word).startsWith(event.getFocusedOption().getValue()))
                        .map(word -> new Command.Choice((String) word, (String) word))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            break;
            default:
                break;

        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String command = event.getName();
        Member executor = event.getMember();
        Member target;
        String groupName;
        List<MessageEmbed> embeds = new ArrayList<>();
        String logEntry = "";

        boolean bIsExecutorAdmin = Objects.requireNonNull(executor).hasPermission(Permission.ADMINISTRATOR);

        if (!bIsExecutorAdmin) {
            bIsExecutorAdmin = ( Main.adminRoles.get(event.getGuild().getId()) != null &&  executor.getRoles().contains(Main.adminRoles.get(event.getGuild().getId())));
        }

        event.deferReply();

        switch (command) {
            case "creategroup":
                if (bIsExecutorAdmin) {
                    String groupname = Objects.requireNonNull(event.getInteraction().getOption("groupname")).getAsString();
                    String serverid = Objects.requireNonNull(event.getGuild()).getId();

                    if (groupname.contains("'") || groupname.contains("\"") || groupname.contains(";") || groupname.contains("DELETE") || groupname.contains("DROP") || groupname.contains("´") || groupname.contains("`")) {
                        EmbedBuilder eb = new EmbedBuilder();
                        eb.setTitle( "groupname invalid");
                        embeds.add(eb.build());
                        break;
                    }

                    ResultSet resultSet1 = Main.ExecuteQuery("SELECT * FROM servers WHERE ID = '" + serverid + "';");
                    ResultSet resultSet2 = Main.ExecuteQuery("SELECT * FROM groups WHERE serverId = '" + serverid + "' AND name = '" + groupname + "';");

                    try {
                        resultSet1.last();
                        resultSet2.last();

                        if (resultSet1.getRow() == 1 && resultSet2.getRow() == 0) {
                            Main.ExecuteQuery("INSERT IGNORE INTO groups (name, serverId) VALUES ('" + groupname + "', '" + serverid + "')");
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle( "group created");
                            embeds.add(eb.build());
                        } else {
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle( "group with this name already exists");
                            embeds.add(eb.build());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle( "Not permitted");
                    embeds.add(eb.build());
                }
                break;
            case "addrole":
                if (bIsExecutorAdmin) {
                    String serverid = Objects.requireNonNull(event.getGuild()).getId();
                    String groupname = Objects.requireNonNull(event.getInteraction().getOption("team")).getAsString();
                    Role role = Objects.requireNonNull(event.getInteraction().getOption("role")).getAsRole();

                    int rolePos = 0;
                    boolean isManager = false;
                    boolean isGeneric = false;

                    try {
                        rolePos = event.getInteraction().getOption("position").getAsInt();
                    } catch (Exception e) {
                    }

                    try {
                        isManager = event.getInteraction().getOption("ismanager").getAsBoolean();
                    } catch (Exception e) {
                    }

                    try {
                        isGeneric = event.getInteraction().getOption("isgeneric").getAsBoolean();
                    } catch (Exception e) {
                    }

                    ResultSet resultSet1 = Main.ExecuteQuery("SELECT * FROM servers WHERE ID = '" + serverid + "';");
                    ResultSet resultSet2 = Main.ExecuteQuery("SELECT * FROM groups WHERE serverId = '" + serverid + "' AND name = '" + groupname + "';");

                    try {
                        resultSet1.last();
                        resultSet2.last();

                        if (resultSet1.getRow() == 1 && resultSet2.getRow() == 1) { // Server AND Group exist
                            int groupId = resultSet2.getInt("id");

                            if (rolePos == 0 && !isGeneric) {
                                ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + " ORDER BY rolePos ASC;");
                                while (resultSet.next()) {
                                    int pos = resultSet.getInt("rolePos");
                                    if (pos > rolePos) {
                                        rolePos = pos;
                                    }
                                }
                                rolePos++;
                            }

                            Main.ExecuteQuery("INSERT IGNORE INTO roles (id, name) VALUES ('" + role.getId() + "', '" + role.getName() + "');");
                            ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + " AND role_id = '" + role.getId() + "';");
                            if (resultSet.first() == false)
                            {
                                Main.ExecuteQuery("INSERT INTO groups_roles (group_id, role_id, rolePos, isManager, isGeneric) VALUES (" + groupId + ", '" + role.getId() + "', " + rolePos + ", " + isManager + ", " + isGeneric + ");");
                            }
                            else
                            {
                                EmbedBuilder eb = new EmbedBuilder();
                                eb.setTitle( "Role already part of this group");
                                embeds.add(eb.build());
                                break;
                            }

                            // Logging
                            logEntry = executor.getAsMention() + " added role " + role.getAsMention() + " to group " + groupname + " at Position " + rolePos;
                            if (isManager) {
                                logEntry += " isManager = True";
                            }
                            if (isGeneric) {
                                logEntry += " isGeneric = True";
                            }

                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle( "Role added");
                            embeds.add(eb.build());
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle( "Not permitted");
                    embeds.add(eb.build());
                }
                break;
            case "add": {
                groupName = Objects.requireNonNull(event.getInteraction().getOption("team")).getAsString();
                target = Objects.requireNonNull(event.getInteraction().getOption("user")).getAsMember();

                List<Role> executorRoles = executor.getRoles();
                if (target != null) {
                    int groupId = -1;
                    ResultSet resultSet = Main.ExecuteQuery("SELECT id FROM groups WHERE name = '" + groupName + "' AND serverId = '" + event.getGuild().getId() + "';");
                    try {
                        resultSet.next();
                        groupId = resultSet.getInt("id");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (groupId != -1) {
                        boolean isPermitted = false;
                        resultSet = Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + " AND isManager = True;");
                        try {
                            while (resultSet.next()) {
                                Role role = jda.getRoleById(resultSet.getString("role_id"));
                                if (executorRoles.contains(role)) {
                                    isPermitted = true;
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        if (isPermitted || bIsExecutorAdmin) {
                            resultSet = Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + " AND (rolePos < 2 OR isGeneric = True);");

                            try {
                                while (resultSet.next()) {
                                    Role role = jda.getRoleById(resultSet.getString("role_id"));
                                    event.getGuild().addRoleToMember(target, role).queue();

                                    EmbedBuilder eb = new EmbedBuilder();
                                    eb.setTitle( "User added");
                                    embeds.add(eb.build());

                                    logEntry = executor.getAsMention() + " added " + target.getAsMention() + " to '" + groupName + "'";
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle( "Not permitted");
                            embeds.add(eb.build());
                        }
                    }
                }
            }
            break;
            case "remove": {
                groupName = Objects.requireNonNull(event.getInteraction().getOption("team")).getAsString();
                target = Objects.requireNonNull(event.getInteraction().getOption("user")).getAsMember();
                if (target != null) {
                    List<Role> executorRoles = executor.getRoles();
                    int groupId = -1;
                    ResultSet resultSet = Main.ExecuteQuery("SELECT id FROM groups WHERE name = '" + groupName + "' AND serverId = '" + event.getGuild().getId() + "'");
                    try {
                        resultSet.next();
                        groupId = resultSet.getInt("id");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (groupId != -1) {
                        boolean isPermitted = false;
                        resultSet = Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + " AND isManager = True");
                        try {
                            while (resultSet.next()) {
                                Role role = jda.getRoleById(resultSet.getString("role_id"));
                                if (executorRoles.contains(role)) {
                                    isPermitted = true;
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        if (isPermitted || bIsExecutorAdmin) {
                            resultSet = Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + ";");
                            try {
                                while (resultSet.next()) {
                                    Role role = jda.getRoleById(resultSet.getString("role_id"));
                                    event.getGuild().removeRoleFromMember(target, role).queue();

                                    EmbedBuilder eb = new EmbedBuilder();
                                    eb.setTitle( "User remove");
                                    embeds.add(eb.build());

                                    logEntry = executor.getAsMention() + " removed " + target.getAsMention() + " from '" + groupName + "'";
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle( "Not permitted");
                            embeds.add(eb.build());
                        }
                    }
                }
            }
            break;
            case "promote":
            case "demote": {
                groupName = Objects.requireNonNull(event.getInteraction().getOption("team")).getAsString();
                target = Objects.requireNonNull(event.getInteraction().getOption("user")).getAsMember();

                if (target != null) {
                    logEntry = null;
                    List<Role> targetRoles = target.getRoles();
                    List<Role> executorRoles = executor.getRoles();
                    int groupId = -1;
                    ResultSet resultSet = Main.ExecuteQuery("SELECT id FROM groups WHERE name = '" + groupName + "' AND serverId = '" + event.getGuild().getId() + "'");
                    try {
                        resultSet.next();
                        groupId = resultSet.getInt("id");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (groupId != -1) {
                        boolean isPermitted = false;
                        resultSet = Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + " AND isManager = True");
                        try {
                            while (resultSet.next()) {
                                Role role = jda.getRoleById(resultSet.getString("role_id"));
                                if (executorRoles.contains(role)) {
                                    isPermitted = true;
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        if (isPermitted || bIsExecutorAdmin) {
                            String q = "SELECT * FROM groups_roles WHERE group_id = " + groupId + " ORDER BY rolePos ASC;";
                            if (command.equals("demote")) {
                                q = "SELECT * FROM groups_roles WHERE group_id = " + groupId + " ORDER BY rolePOS DESC;";
                            }

                            ResultSet resultSet1 = Main.ExecuteQuery(q);

                            try {
                                Role previousRole = null;
                                boolean roleWasThere = false;
                                while (resultSet1.next()) {
                                    String roleId = resultSet1.getString("role_id");
                                    boolean isGeneric = resultSet1.getBoolean("isGeneric");
                                    Role role = jda.getRoleById(roleId);

                                    if (roleWasThere && previousRole != null && role != null && !isGeneric) {
                                        event.getGuild().addRoleToMember(target, role).queue();
                                        event.getGuild().removeRoleFromMember(target, previousRole).queue();

                                        EmbedBuilder eb = new EmbedBuilder();
                                        eb.setTitle( "Done");
                                        embeds.add(eb.build());

                                        logEntry = executor.getAsMention() + " promoted " + target.getAsMention() + " to " + role.getName() + "'" + groupName + "'";
                                        break;
                                    }

                                    if (targetRoles.contains(role) && !isGeneric) {
                                        roleWasThere = true;
                                        previousRole = role;
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle( "Not permitted");
                            embeds.add(eb.build());
                        }
                    }
                }
            }
                break;
            case "setlogchannel": {
                if (bIsExecutorAdmin) {
                    TextChannel logchannel = Objects.requireNonNull(event.getInteraction().getOption("logchannel")).getAsChannel().asTextChannel();
                    String serverId = Objects.requireNonNull(event.getGuild()).getId();
                    Main.ExecuteQuery("UPDATE servers SET logchannel = '" + logchannel.getId() + "' WHERE id = '" + serverId + "';");

                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle( "logchannel updated");
                    embeds.add(eb.build());

                    Main.logChannels.put(serverId, logchannel.getId());
                    logEntry = "Hello Logchannel :v";
                } else {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle( "Not permitted");
                    embeds.add(eb.build());
                }
            }
                break;
            case "setadminrole": {
                if (bIsExecutorAdmin) {
                    Role role = Objects.requireNonNull(event.getInteraction().getOption("role")).getAsRole();
                    String serverId = Objects.requireNonNull(event.getGuild()).getId();
                    Main.ExecuteQuery("UPDATE servers SET admin_role = '" + role.getId() + "' WHERE id = '" + serverId + "';");

                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle( role.getAsMention() + " set as AdminRole");
                    embeds.add(eb.build());

                    logEntry = executor.getAsMention() + " set " + role.getAsMention() + " as AdminRole.";
                    Main.adminRoles.put(event.getGuild().getId(), role);
                } else {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle( "Not permitted");
                    embeds.add(eb.build());
                }
            }
                break;
            case "cleargroup": {
                if (bIsExecutorAdmin) {
                    String groupname = Objects.requireNonNull(event.getInteraction().getOption("groupname")).getAsString();
                    String serverId = Objects.requireNonNull(event.getGuild()).getId();

                    ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM groups WHERE name = '" + groupname + "' AND serverId = '" + serverId + "';");
                    int groupId = -1;
                    try {
                        resultSet.next();
                        groupId = resultSet.getInt("id");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Main.ExecuteQuery("DELETE FROM groups_roles WHERE group_id = " + groupId + ";");

                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle( groupname + " cleared");
                    embeds.add(eb.build());

                    logEntry = executor.getAsMention() + " cleared Group '" + groupname + "'";
                } else {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle( "Not permitted");
                    embeds.add(eb.build());
                }
            }
                break;
            case "deletegroup": {
                if (bIsExecutorAdmin) {
                    String groupname = Objects.requireNonNull(event.getInteraction().getOption("groupname")).getAsString();
                    String serverId = Objects.requireNonNull(event.getGuild()).getId();

                    ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM groups WHERE name = '" + groupname + "' AND serverId = '" + serverId + "';");
                    int groupId = -1;
                    try {
                        resultSet.next();
                        groupId = resultSet.getInt("id");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Main.ExecuteQuery("DELETE FROM groups_roles WHERE group_id = " + groupId + ";");
                    Main.ExecuteQuery("DELETE FROM groups WHERE id = " + groupId + ";");

                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle(groupname + " deleted");
                    embeds.add(eb.build());

                    logEntry = executor.getAsMention() + " deleted Group '" + groupname + "'";
                } else {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("Not permitted");
                    embeds.add(eb.build());
                }
            }
                break;
            case "groupinfo": {
                String serverId = Objects.requireNonNull(event.getGuild()).getId();
                String groupname = Objects.requireNonNull(event.getInteraction().getOption("groupname")).getAsString();

                ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM groups WHERE name = '" + groupname + "' AND serverId = '" + serverId + "';");
                int groupId = -1;
                try {
                    resultSet.next();
                    groupId = resultSet.getInt("id");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                resultSet = Main.ExecuteQuery("SELECT r.id as roleId, g.rolePos, g.isManager, g.isGeneric  FROM groups_roles g JOIN roles r ON r.id = g.role_id WHERE group_id = " + groupId + " ORDER BY rolePos ASC;");
                StringBuilder sb = new StringBuilder();
                try {
                    if(!resultSet.first()) {
                        sb.append("Keine Rollen gefunden");
                    } else {
                        while (resultSet.next()) {

                            RoleStruct r = new RoleStruct();
                            r.id = resultSet.getString("roleId");
                            r.isManager = resultSet.getBoolean("isManager");
                            r.isGeneric = resultSet.getBoolean("isGeneric");
                            r.pos = resultSet.getInt("rolePos");

                            sb.append(r).append("\n");
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                EmbedBuilder eb = new EmbedBuilder();
                eb.addField(groupname, sb.toString(), false);
                embeds.add(eb.build());
            }
            break;
            case "serverinfo": {
                String serverId = Objects.requireNonNull(event.getGuild()).getId();
                ResultSet resultSet;

                HashMap<String, List<RoleStruct>> group_roles = new HashMap<>();
                try {
                    resultSet = Main.ExecuteQuery("SELECT * FROM servers WHERE id = '" + serverId + "';");
                    resultSet.first();
                    {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Logchannel: ")
                                .append(jda.getChannelById(TextChannel.class, resultSet.getString("logchannel")).getAsMention())
                                .append("\nAdminRole: ").append(jda.getRoleById(resultSet.getString("admin_role")).getAsMention());
                        EmbedBuilder eb = new EmbedBuilder();
                        eb.addField("Serverinfo", sb.toString(), false);
                        embeds.add(eb.build());
                    }

                    resultSet = Main.ExecuteQuery("SELECT * FROM groups WHERE serverId = '" + serverId + "';");
                    List<String> groups = new ArrayList<>();

                    while (resultSet.next()) {
                        String name = resultSet.getString("name");
                        group_roles.put(name, new ArrayList<>());
                        groups.add(name);
                    }

                    resultSet = Main.ExecuteQuery("SELECT * FROM groups WHERE serverId = '" + serverId + "';");

                    if(!resultSet.first()) {
                        EmbedBuilder eb = new EmbedBuilder();
                        eb.setTitle("Keine Gruppen gefunden");
                        embeds.add(eb.build());
                    } else {
                        resultSet = Main.ExecuteQuery("SELECT g.name, role_id, rolePos, isManager, isGeneric FROM groups_roles gr JOIN groups g ON g.id = gr.group_id JOIN servers s ON g.serverId = s.id WHERE s.id = " + serverId + " ORDER BY  name, rolePos ASC;");

                        while (resultSet.next()) {
                            RoleStruct r = new RoleStruct();
                            r.id = resultSet.getString("role_id");
                            r.isManager = resultSet.getBoolean("isManager");
                            r.isGeneric = resultSet.getBoolean("isGeneric");
                            r.pos = resultSet.getInt("rolePos");

                            group_roles.get(resultSet.getString("name")).add(r);
                        }

                        for (String group : groups) {
                            StringBuilder sb = new StringBuilder();

                            List<RoleStruct> roles = group_roles.get(group);
                            for (RoleStruct role : roles) {
                                sb.append(role.toString()).append("\n");
                            }

                            EmbedBuilder eb = new EmbedBuilder();
                            eb.addField(group, sb.toString(), false);
                            embeds.add(eb.build());
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            default:
                break;

        }

        // Logging
        if (!embeds.isEmpty())
            event.replyEmbeds(embeds).setEphemeral(true).queue();

        if (logEntry != null && !logEntry.equals(""))
            WriteToLogChannel(event.getGuild(), logEntry);
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        System.out.println("Joined new server: " + event.getGuild().getName());
        Main.addCommands(event.getGuild(), false);
        Main.ExecuteQuery("INSERT IGNORE INTO servers (id) VALUES ('" + event.getGuild().getId() + "');");
    }

    class RoleStruct {
        String id;
        int pos;
        boolean isManager;
        boolean isGeneric;

        @Override
        public String toString() {
            return  jda.getRoleById(id).getAsMention() + " Position = " + pos +
                    ", isManager = " + isManager +
                    ", isGeneric = " + isGeneric ;
        }
    }
}
