package de.jns;

import de.jns.dbtranslator.RoleManagerDB;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RoleManager extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(RoleManager.class);
    public JDA jda;

    public RoleManager() {
        jda = Main.jda;
        Main.LOG("RollenBot Constructor");
        Main.commandListener.RegisterCommand("addrole", this::IsMemberAdmin, this::AddRoleCommand);
        Main.commandListener.RegisterCommand("creategroup", this::IsMemberAdmin, this::CreateGroupCommand);
        Main.commandListener.RegisterCommand("setlogchannel", this::IsMemberAdmin, this::SetLogChannelCommand);
        Main.commandListener.RegisterCommand("setadminrole", this::IsMemberAdmin, this::SetAdminRoleCommand);
        Main.commandListener.RegisterCommand("cleargroup", this::IsMemberAdmin, this::ClearGroupCommand);
        Main.commandListener.RegisterCommand("deletegroup", this::IsMemberAdmin, this::DeleteGroupCommand);
        Main.commandListener.RegisterCommand("groupinfo", this::IsMemberAdmin, this::GroupInfoCommand);
        Main.commandListener.RegisterCommand("serverinfo", this::IsMemberAdmin, this::ServerInfoCommand);
        Main.commandListener.RegisterCommand("add", this::RoleManagerPermissionCheck, this::AddUserCommand);
        Main.commandListener.RegisterCommand("remove", this::RoleManagerPermissionCheck, this::RemoveUserCommand);
        Main.commandListener.RegisterCommand("promote", this::RoleManagerPermissionCheck, this::PromoteDemoteUserCommand);
        Main.commandListener.RegisterCommand("demote", this::RoleManagerPermissionCheck, this::PromoteDemoteUserCommand);
    }

    public void WriteToLogChannel(Guild guild, String content) {
        TextChannel channel = null;
        try {
            channel = jda.getTextChannelById(Main.logChannels.get(guild.getId()));
        } catch (Exception e) {
            Main.LOG("Log-Channel missing for " + guild.getName());
        }
        if (channel != null)
            channel.sendMessage(content).queue();
    }

    void LogAndAnswer(SlashCommandInteractionEvent event, List<MessageEmbed> embeds, String logEntry, String answer ) {
        if (!embeds.isEmpty())
            event.replyEmbeds(embeds).setEphemeral(true).queue();
        else
            event.reply(answer).setEphemeral(true).queue();

        if (!logEntry.isEmpty())
            WriteToLogChannel(event.getGuild(), logEntry);
    }

    boolean IsMemberAdmin(SlashCommandInteractionEvent event) {
        return Objects.requireNonNull(event.getMember()).hasPermission(Permission.ADMINISTRATOR);
    }

    boolean AddRoleCommand(SlashCommandInteractionEvent event) {
        Member executor = event.getMember();
        List<MessageEmbed> embeds = new ArrayList<>();
        String logEntry = "";
        String answer = "";

        String serverId = Objects.requireNonNull(event.getGuild()).getId();
        String groupName = Objects.requireNonNull(event.getInteraction().getOption("team")).getAsString();
        Role role = Objects.requireNonNull(event.getInteraction().getOption("role")).getAsRole();

        int rolePos = Objects.requireNonNull(event.getInteraction().getOption("position")).getAsInt();
        boolean isManager = Objects.requireNonNull(event.getInteraction().getOption("ismanager")).getAsBoolean();
        boolean isGeneric = Objects.requireNonNull(event.getInteraction().getOption("isgeneric")).getAsBoolean();

        ResultSet resultSet1 = RoleManagerDB.getServerById(serverId);
        ResultSet resultSet2 = RoleManagerDB.getGroupByServerAndName(serverId, groupName);

        try {
            resultSet1.last();
            resultSet2.last();

            if (resultSet1.getRow() == 1 && resultSet2.getRow() == 1) { // Server AND Group exist
                int groupId = resultSet2.getInt("id");

                if (rolePos == 0 && !isGeneric) {
                    ResultSet resultSet = RoleManagerDB.getGroupRoles(groupId, false);
                    while (resultSet.next()) {
                        int pos = resultSet.getInt("rolePos");
                        if (pos > rolePos) {
                            rolePos = pos;
                        }
                    }
                    rolePos++;
                }

                // add the role to database
                RoleManagerDB.insertRole(role.getIdLong(), role.getName());
                // check if the role has already a connection to the group
                ResultSet resultSet = RoleManagerDB.getGroupRoleConnection(groupId, role.getIdLong());
                if (!resultSet.first()) {
                    // if not, establish the connection
                    RoleManagerDB.connectRoleToGroup(groupId, role.getIdLong(), rolePos, isManager, isGeneric);
                } else {
                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("Role already part of this group");
                    embeds.add(embed.build());
                    return true;
                }

                // Logging
                logEntry = executor.getAsMention() + " added role " + role.getAsMention() + " to group " + groupName + " at Position " + rolePos;
                if (isManager) {
                    logEntry += " isManager = True";
                }
                if (isGeneric) {
                    logEntry += " isGeneric = True";
                }

                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("Role added");
                embeds.add(eb.build());
            }
        } catch (Exception e) {
            Main.LOG(e.toString());
        }
        LogAndAnswer(event, embeds, logEntry, answer);
        return true;
    }

    boolean CreateGroupCommand(SlashCommandInteractionEvent event) {
        List<MessageEmbed> embeds = new ArrayList<>();
        String logEntry = "";
        String answer = "";

        String groupName = Objects.requireNonNull(event.getInteraction().getOption("groupname")).getAsString();
        String serverId = Objects.requireNonNull(event.getGuild()).getId();

        if (groupName.contains("'") || groupName.contains("\"") || groupName.contains(";") || groupName.contains("DELETE") || groupName.contains("DROP") || groupName.contains("´") || groupName.contains("`")) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("Unbekannter Gruppenname");
            embeds.add(eb.build());
            return true;
        }

        ResultSet resultSet1 = RoleManagerDB.getServerById(serverId);
        ResultSet resultSet2 = RoleManagerDB.getGroupByServerAndName(serverId, groupName);

        try {
            resultSet1.next();
            resultSet2.next();

            if (resultSet1.getRow() == 1 && resultSet2.getRow() == 0) {
                RoleManagerDB.insertGroup(serverId, groupName);
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("group created");
                embeds.add(eb.build());
            } else {
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("group with this name already exists");
                embeds.add(eb.build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        LogAndAnswer(event, embeds, logEntry, answer);
        return true;
    }

    boolean RoleManagerPermissionCheck(SlashCommandInteractionEvent event) {
        Member executor = Objects.requireNonNull(event.getMember());
        List<Role> executorRoles = executor.getRoles();

        if (executor.hasPermission(Permission.ADMINISTRATOR))
            return true;

        String groupName = Objects.requireNonNull(event.getInteraction().getOption("team")).getAsString();
        int groupId = -1;
        ResultSet resultSet = RoleManagerDB.getGroupId(groupName, Objects.requireNonNull(event.getGuild()).getIdLong());
        try {
            resultSet.next();
            groupId = resultSet.getInt("id");
        } catch (Exception e) {
            Main.LOG(e.toString());
        }

        if (groupId == -1)
            return false;

        boolean isPermitted = false;
        resultSet = RoleManagerDB.getGroupRoleManagers(groupId);
        try {
            while (resultSet.next()) {
                Role role = jda.getRoleById(resultSet.getString("role_id"));
                if (executorRoles.contains(role)) {
                    isPermitted = true;
                }
            }
        } catch (Exception e) {
            Main.LOG(e.toString());
        }

        return isPermitted;
    }

    boolean AddUserCommand(SlashCommandInteractionEvent event) {
        Member executor = Objects.requireNonNull(event.getMember());
        Guild guild = Objects.requireNonNull(event.getGuild());
        Member target = Objects.requireNonNull(event.getInteraction().getOption("user")).getAsMember();
        String groupName = Objects.requireNonNull(event.getInteraction().getOption("team")).getAsString();
        List<MessageEmbed> embeds = new ArrayList<>();
        String logEntry = "";
        String answer = "";

        if (target != null) {
            int groupId = -1;
            ResultSet resultSet = RoleManagerDB.getGroupId(groupName, guild.getIdLong());
            try {
                resultSet.next();
                groupId = resultSet.getInt("id");
            } catch (Exception e) {
                Main.LOG(e.toString());
            }

            if (groupId != -1) {
                resultSet = RoleManagerDB.getGroupRolesCustom(groupId, "AND rolePos < 2");
                try {
                    while (resultSet.next()) {
                        Role role = Objects.requireNonNull(jda.getRoleById(resultSet.getString("role_id")));
                        guild.addRoleToMember(target, role).queue();

                        EmbedBuilder eb = new EmbedBuilder();
                        eb.setTitle("User added");
                        embeds.add(eb.build());

                        logEntry = executor.getAsMention() + " added " + target.getAsMention() + " to '" + groupName + "'";
                    }
                } catch (Exception e) {
                    Main.LOG(e.toString());
                }
            }
        }
        LogAndAnswer(event, embeds, logEntry, answer);
        return true;
    }

    boolean RemoveUserCommand(SlashCommandInteractionEvent event) {
        Member executor = Objects.requireNonNull(event.getMember());
        Guild guild = Objects.requireNonNull(event.getGuild());
        Member target = Objects.requireNonNull(event.getInteraction().getOption("user")).getAsMember();
        String groupName = Objects.requireNonNull(event.getInteraction().getOption("team")).getAsString();
        List<MessageEmbed> embeds = new ArrayList<>();
        String logEntry = "";
        String answer = "";

        if (target != null) {
            int groupId = -1;
            ResultSet resultSet = RoleManagerDB.getGroupId(groupName, guild.getIdLong());
            try {
                resultSet.next();
                groupId = resultSet.getInt("id");
            } catch (Exception e) {
                Main.LOG(e.toString());
            }

            if (groupId != -1) {
                resultSet = RoleManagerDB.getGroupRoles(groupId, false);
                try {
                    while (resultSet.next()) {
                        Role role = Objects.requireNonNull(jda.getRoleById(resultSet.getString("role_id")));
                        event.getGuild().removeRoleFromMember(target, role).queue();

                        EmbedBuilder eb = new EmbedBuilder();
                        eb.setTitle("User remove");
                        embeds.add(eb.build());

                        logEntry = executor.getAsMention() + " removed " + target.getAsMention() + " from '" + groupName + "'";
                    }
                } catch (Exception e) {
                    Main.LOG(e.toString());
                }
            }
        }
        LogAndAnswer(event, embeds, logEntry, answer);
        return true;
    }

    boolean PromoteDemoteUserCommand(SlashCommandInteractionEvent event) {
        Member executor = Objects.requireNonNull(event.getMember());
        Guild guild = Objects.requireNonNull(event.getGuild());
        String command = event.getName();
        Member target = Objects.requireNonNull(event.getInteraction().getOption("user")).getAsMember();
        String groupName = Objects.requireNonNull(event.getInteraction().getOption("team")).getAsString();
        List<MessageEmbed> embeds = new ArrayList<>();
        String logEntry = "";
        String answer = "";

        if (target != null) {
            logEntry = null;
            List<Role> targetRoles = target.getRoles();
            int groupId = -1;
            ResultSet resultSet = RoleManagerDB.getGroupId(groupName, guild.getIdLong());
            try {
                resultSet.next();
                groupId = resultSet.getInt("id");
            } catch (Exception e) {
                Main.LOG(e.toString());
            }

            if (groupId != -1) {
                ResultSet resultSet1 = RoleManagerDB.getGroupRoles(groupId, command.equals("demote"));

                try {
                    Role previousRole = null;
                    boolean roleWasThere = false;
                    int prev_pos = -1;
                    while (resultSet1.next()) {
                        String roleId = resultSet1.getString("role_id");
                        Role role = jda.getRoleById(roleId);

                        if (roleWasThere && previousRole != null && role != null) {
                            event.getGuild().addRoleToMember(target, role).queue();

                            ResultSet resultSet2 = Main.ExecuteQuery("SELECT r.name, r.id, rolePos, isManager, isGeneric FROM groups_roles join roles AS r on groups_roles.role_id = r.id where group_id = " + groupId + " and rolePos = 0 and isGeneric = 1 r.id = " + previousRole.getId() + " order by rolePos DESC;");
                            if (!resultSet2.first()) {
                                event.getGuild().removeRoleFromMember(target, previousRole).queue();
                            }

                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle("Done");
                            embeds.add(eb.build());

                            logEntry = executor.getEffectiveName() + " " + command + "d " + target.getEffectiveName()
                                    + " to " + role.getName() + "'" + groupName + "'";
                            prev_pos = resultSet1.getInt("rolePos");
                            break;
                        }

                        if (targetRoles.contains(role)) {
                            roleWasThere = true;
                            previousRole = role;
                        }
                    }

                    ResultSet genericRoles_resultset = Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + " AND isGeneric = true;");
                    ResultSet resultSet2 = Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + " AND isGeneric = true AND rolePos <= " + prev_pos + " ORDER BY rolePos DESC;");

                    List<String> roleId_rm = new ArrayList<>();
                    List<String> roleId_add = new ArrayList<>();

                    while (genericRoles_resultset.next()) {
                        String roleId = genericRoles_resultset.getString("role_id");
                        roleId_rm.add(roleId);
                    }

                    if (resultSet2.next()) {
                        String roleId = resultSet2.getString("role_id");
                        roleId_add.add(roleId);
                        roleId_rm.remove(roleId);
                    }

                    for (String id : roleId_rm) {
                        Role role = jda.getRoleById(id);
                        event.getGuild().removeRoleFromMember(target, role).queue();
                    }

                    for (String id : roleId_add) {
                        Role role = jda.getRoleById(id);
                        event.getGuild().addRoleToMember(target, role).queue();
                    }

                } catch (Exception e) {
                    Main.LOG(e.toString());
                }
            }
        }
        LogAndAnswer(event, embeds, logEntry, answer);
        return true;
    }

    boolean SetLogChannelCommand(SlashCommandInteractionEvent event) {
        List<MessageEmbed> embeds = new ArrayList<>();
        String logEntry;
        String answer = "";

        TextChannel logchannel = Objects.requireNonNull(event.getInteraction().getOption("logchannel")).getAsChannel().asTextChannel();
        String serverId = Objects.requireNonNull(event.getGuild()).getId();
        Main.ExecuteQuery("UPDATE servers SET logchannel = '" + logchannel.getId() + "' WHERE id = '" + serverId + "';");

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("logchannel updated");
        embeds.add(eb.build());

        Main.logChannels.put(serverId, logchannel.getId());
        logEntry = "Hello Logchannel :v";
        LogAndAnswer(event, embeds, logEntry, answer);
        return true;
    }

    boolean SetAdminRoleCommand(SlashCommandInteractionEvent event) {
        Member executor = event.getMember();
        List<MessageEmbed> embeds = new ArrayList<>();
        String logEntry = "";
        String answer = "";

        Role role = Objects.requireNonNull(event.getInteraction().getOption("role")).getAsRole();
        String serverId = Objects.requireNonNull(event.getGuild()).getId();
        Main.ExecuteQuery("UPDATE servers SET admin_role = '" + role.getId() + "' WHERE id = '" + serverId + "';");

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(role.getAsMention() + " set as AdminRole");
        embeds.add(eb.build());

        logEntry = executor.getAsMention() + " set " + role.getAsMention() + " as AdminRole.";
        Main.adminRoles.put(event.getGuild().getId(), role);

        LogAndAnswer(event, embeds, logEntry, answer);
        return true;
    }

    boolean ClearGroupCommand(SlashCommandInteractionEvent event) {
        Member executor = event.getMember();
        List<MessageEmbed> embeds = new ArrayList<>();
        String logEntry = "";

        String groupName = Objects.requireNonNull(event.getInteraction().getOption("groupname")).getAsString();
        String serverId = Objects.requireNonNull(event.getGuild()).getId();

        ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM groups WHERE name = '" + groupName + "' AND serverId = '" + serverId + "';");
        int groupId = -1;
        try {
            resultSet.next();
            groupId = resultSet.getInt("id");
        } catch (Exception e) {
            Main.LOG(e.toString());
        }
        Main.ExecuteQuery("DELETE FROM groups_roles WHERE group_id = " + groupId + ";");

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(groupName + " cleared");
        embeds.add(eb.build());

        logEntry = executor.getAsMention() + " cleared Group '" + groupName + "'";

        LogAndAnswer(event, embeds, logEntry, "");
        return true;
    }

    boolean DeleteGroupCommand(SlashCommandInteractionEvent event) {
        Member executor = event.getMember();
        List<MessageEmbed> embeds = new ArrayList<>();
        String logEntry;
        String groupName = Objects.requireNonNull(event.getInteraction().getOption("groupname")).getAsString();
        String serverId = Objects.requireNonNull(event.getGuild()).getId();

        ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM groups WHERE name = '" + groupName + "' AND serverId = '" + serverId + "';");
        int groupId = -1;
        try {
            resultSet.next();
            groupId = resultSet.getInt("id");
        } catch (Exception e) {
            Main.LOG(e.toString());
        }
        Main.ExecuteQuery("DELETE FROM groups_roles WHERE group_id = " + groupId + ";");
        Main.ExecuteQuery("DELETE FROM groups WHERE id = " + groupId + ";");

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(groupName + " deleted");
        embeds.add(eb.build());

        logEntry = executor.getAsMention() + " deleted Group '" + groupName + "'";
        LogAndAnswer(event, embeds, logEntry, "");
        return true;
    }

    boolean GroupInfoCommand(SlashCommandInteractionEvent event) {
        List<MessageEmbed> embeds = new ArrayList<>();
        String groupName = Objects.requireNonNull(event.getInteraction().getOption("groupname")).getAsString();
        String serverId = Objects.requireNonNull(event.getGuild()).getId();
        String answer;

        ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM groups WHERE name = '" + groupName + "' AND serverId = '" + serverId + "';");
        int groupId = -1;
        try {
            resultSet.next();
            groupId = resultSet.getInt("id");
        } catch (Exception e) {
            Main.LOG(e.toString());
        }

        resultSet = Main.ExecuteQuery("SELECT r.id as roleId, g.rolePos, g.isManager, g.isGeneric  FROM groups_roles g JOIN roles r ON r.id = g.role_id WHERE group_id = " + groupId + " ORDER BY rolePos ASC;");
        StringBuilder sb = new StringBuilder();
        try {
            if (!resultSet.first()) {
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
        answer = sb.toString();
        LogAndAnswer(event, embeds, "", answer);
        return true;
    }

    boolean ServerInfoCommand(SlashCommandInteractionEvent event) {
        List<MessageEmbed> embeds = new ArrayList<>();
        String serverId = Objects.requireNonNull(event.getGuild()).getId();
        ResultSet resultSet;

        HashMap<String, List<RoleStruct>> group_roles = new HashMap<>();
        try {
            resultSet = RoleManagerDB.getServerById(serverId);
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

            if (!resultSet.first()) {
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
            Main.LOG(e.toString());
        }
        LogAndAnswer(event, embeds, "", "");
        return true;
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        String command = event.getName();
        Member executor = event.getMember();

        boolean bMemberIsAdmin = Objects.requireNonNull(event.getMember()).hasPermission(Permission.ADMINISTRATOR);
        List<Command.Choice> options;

        switch (command) {
            case "creategroup":
                break;
            case "addrole": {
                    ResultSet resultSetAddRole = Main.ExecuteQuery("SELECT * FROM groups WHERE serverId = '" + event.getGuild().getId() + "';");

                    ArrayList<String> array = new ArrayList<>();
                    try {
                        resultSetAddRole.first();
                        array.add(resultSetAddRole.getString("name"));
                        while (resultSetAddRole.next()) {
                            array.add(resultSetAddRole.getString("name"));
                        }

                    } catch (Exception e) {
                        Main.LOG(e.toString());
                    }
                    options = Stream.of(array.toArray())
                            .filter(word -> ((String) word).startsWith(event.getFocusedOption().getValue()))
                            .map(word -> new Command.Choice((String) word, (String) word))
                            .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
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
                if (bMemberIsAdmin || (Main.adminRoles.get(event.getGuild().getId()) != null && executor.getRoles().contains(Main.adminRoles.get(event.getGuild().getId())))) {
                    ResultSet resultSet = Main.ExecuteQuery("SELECT * FROM groups WHERE serverId = '" + event.getGuild().getId() + "';");

                    try {
                        while (resultSet.next()) {
                            int groupId = resultSet.getInt("id");
                            groupIds.add(groupId);
                        }
                    } catch (Exception e) {
                        Main.LOG(e.toString());
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
                            Main.LOG(e.toString());
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
    public void onGuildJoin(GuildJoinEvent event) {
        Main.LOG("Joined new server: " + event.getGuild().getName());
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
            return jda.getRoleById(id).getAsMention() + " Position = " + pos +
                    ", isManager = " + isManager +
                    ", isGeneric = " + isGeneric;
        }
    }
}
