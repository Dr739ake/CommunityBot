package de.jns.dbtranslator;

import de.jns.Main;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;

public class RoleManagerDB {
    public static void CreateTablesIfNotExist()
    {
        String[] createTableQueryMariaDB = {
            "CREATE TABLE IF NOT EXISTS servers ( id varchar(255) PRIMARY KEY, logchannel varchar(255), admin_role varchar(255) );",
            "CREATE TABLE IF NOT EXISTS groups ( id INT PRIMARY KEY AUTO_INCREMENT, name varchar(255) NOT NULL, serverId varchar(255), FOREIGN KEY (serverId) REFERENCES servers(id) );",
            "CREATE TABLE IF NOT EXISTS roles ( id varchar(255) PRIMARY KEY, name varchar(255) );",
            "CREATE TABLE IF NOT EXISTS groups_roles (group_id INT, role_id varchar(255), rolePos INT NOT NULL, isManager BOOLEAN, isGeneric BOOLEAN, PRIMARY KEY (group_id, role_id), FOREIGN KEY (group_id) REFERENCES groups(id), FOREIGN KEY (role_id) REFERENCES roles(id) );",
            "CREATE TABLE IF NOT EXISTS supportchannels ( channelId VARCHAR(255) PRIMARY KEY, pingChannelId VARCHAR(255), roleId VARCHAR(255) );"
        };

        String[] createTableQuerySQLite = {
            "CREATE TABLE IF NOT EXISTS servers ( id varchar(255) PRIMARY KEY, logchannel varchar(255), admin_role varchar(255) );",
            "CREATE TABLE IF NOT EXISTS groups ( id INTEGER PRIMARY KEY AUTOINCREMENT, name varchar(255) NOT NULL, serverId varchar(255), FOREIGN KEY (serverId) REFERENCES servers(id) );",
            "CREATE TABLE IF NOT EXISTS roles ( id varchar(255) PRIMARY KEY, name varchar(255) );",
            "CREATE TABLE IF NOT EXISTS groups_roles (group_id INT, role_id varchar(255), rolePos INT NOT NULL, isManager BOOLEAN, isGeneric BOOLEAN, PRIMARY KEY (group_id, role_id), FOREIGN KEY (group_id) REFERENCES groups(id), FOREIGN KEY (role_id) REFERENCES roles(id) );",
            "CREATE TABLE IF NOT EXISTS supportchannels ( channelId VARCHAR(255) PRIMARY KEY, pingChannelId VARCHAR(255), roleId VARCHAR(255) );"
        };

        if (Main.isSQLite) {
            for (String q : createTableQuerySQLite) {
                Main.ExecuteQuery(q);
            }
        } else {
            for (String q : createTableQueryMariaDB) {
                Main.ExecuteQuery(q);
            }
        }
    }

    public static void insertGroup(String serverId, String groupName)
    {
        if (Main.isSQLite)
            Main.ExecuteQuery("INSERT OR IGNORE INTO groups (name, serverId) VALUES ('" + groupName + "', '" + serverId + "')");
        else
            Main.ExecuteQuery("INSERT IGNORE INTO groups (name, serverId) VALUES ('" + groupName + "', '" + serverId + "')");
    }

    public static ResultSet getServerById(String serverId)
    {
        return Main.ExecuteQuery("SELECT * FROM servers WHERE ID = '" + serverId + "';");
    }

    public static ResultSet getGroupByServerAndName(String serverId, String groupName) {
        return Main.ExecuteQuery("SELECT * FROM groups WHERE serverId = '" + serverId + "' AND name = '" + groupName + "';");
    }

    public static ResultSet getGroupRoles(int groupId, boolean desc) {
        if (desc)
            return Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + " ORDER BY rolePos DESC;");
        else
            return Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + " ORDER BY rolePos ASC;");
    }

    public static ResultSet getGroupRoleManagers(int groupId) {
        return Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + " AND isManager = True;");
    }

    public static void insertRole(long roleId, String roleName) {
        if (Main.isSQLite)
            Main.ExecuteQuery("INSERT OR IGNORE INTO roles (id, name) VALUES ('" + roleId + "', '" + roleName + "');");
        else
            Main.ExecuteQuery("INSERT IGNORE INTO roles (id, name) VALUES ('" + roleId + "', '" + roleName + "');");
    }

    public static ResultSet getGroupRoleConnection(int groupId, long roleId) {
        return Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + " AND role_id = '" + roleId + "';");
    }

    public static void connectRoleToGroup(int groupId, long roleid, int rolePos, boolean isManager, boolean isGeneric) {
        Main.ExecuteQuery("INSERT INTO groups_roles (group_id, role_id, rolePos, isManager, isGeneric) VALUES (" + groupId + ", '" + roleid + "', " + rolePos + ", " + isManager + ", " + isGeneric + ");");
    }

    public static ResultSet getGroupId(String groupName, long serverId) {
        return Main.ExecuteQuery("SELECT id FROM groups WHERE name = '" + groupName + "' AND serverId = '" + serverId + "';");
    }

    public static ResultSet getGroupRolesCustom(int groupId, String custom) {
        return Main.ExecuteQuery("SELECT * FROM groups_roles WHERE group_id = " + groupId + " " + custom + ";");
    }

}
