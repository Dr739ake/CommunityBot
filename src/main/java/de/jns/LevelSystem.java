package de.jns;

import de.jns.pojo.CommandResult;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class LevelSystem extends ListenerAdapter {
    static final int levelDivider = 1024;
    static final int maxEXPperMsg = 512;

    public LevelSystem() {
        Main.commandListener.RegisterCommand("seelevel", this::permission, this::seeLevel);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Guild guild = event.getGuild();
        User author = event.getAuthor();

        if (author != null && author.isBot() == false)
        {
            long exp = getMemberEXP(guild, author);
            setMemberEXP(guild, author, exp + getExpByText(event.getMessage().getContentRaw()));
        }
    }

    private long getMemberEXP(Guild guild, User user) {

        try {
            Files.createDirectories(Paths.get("data/exp/" + guild.getId()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (BufferedReader reader = new BufferedReader(new FileReader("data/exp/" + guild.getId() + "/" + user.getId() + ".exp"))) {
            String line = reader.readLine();
            return Integer.parseInt(line);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return 0;
    }

    private void setMemberEXP(Guild guild, User user, Long exp) {
        System.out.println("New XP = " + exp);
        try {
            Files.createDirectories(Paths.get("data/exp/" + guild.getId()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("data/exp/" + guild.getId() + "/" + user.getId() + ".exp"))) {
            writer.write(exp.toString());
        } catch (Exception ignored) {
            ignored.printStackTrace();
            Main.LOG("Failed to safe exp for User: " + user.getId() + " on Guild: " + guild.getId());
        }
    }

    private long getExpByText(String text) {
        System.out.println("len " + text.length());
        System.out.println("byte len " + text.getBytes().length);
        long exp = ((long) text.length() * text.getBytes().length) % maxEXPperMsg;
        System.out.println("exp " + exp);
        return exp;
    }

    private static int getLevel(long exp) {
        int level = (int) exp / levelDivider;
        if (level == 0)
            level = 1;

        return level;
    }

    public boolean permission(SlashCommandInteractionEvent event) {
        return true;
    }

    public CommandResult seeLevel(SlashCommandInteractionEvent event) {
        long memberEXP = getMemberEXP(event.getGuild(), event.getMember().getUser());
        String reply = "Your current level is: " + getLevel(memberEXP) + " (" + memberEXP + " xp)";
        return new CommandResult(reply, true);
    }
}
