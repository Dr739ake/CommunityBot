package de.jns;

import de.jns.pojo.CommandResult;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class LevelSystem extends ListenerAdapter {
    static final int levelDivider = 1024;
    static final int maxEXPperMsg = 512;
    private static final int LEADERBOARD_SIZE = 10;

    private record PlayerExperience(String userId, long exp) {}

    public LevelSystem() {
        Main.commandListener.RegisterCommand("seelevel", this::permission, this::seeLevel);
        Main.commandListener.RegisterCommand("leaderboard", this::permission, this::leaderboard);
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
            return Long.parseLong(line);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return 0;
    }

    private void setMemberEXP(Guild guild, User user, Long exp) {
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
        return ((long) text.length() * text.getBytes().length) % maxEXPperMsg;
    }

    private static long getLevel(long exp) {
        long level = exp / levelDivider;
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

    public CommandResult leaderboard(SlashCommandInteractionEvent event) {
        Path expDirectory = Paths.get("data/exp", event.getGuild().getId());
        if (Files.notExists(expDirectory))
            return new CommandResult("There are no levels to display yet.", true);

        List<PlayerExperience> scores = new ArrayList<>();

        try (Stream<Path> files = Files.list(expDirectory)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".exp"))
                    .forEach(file -> {
                        String userId = file.getFileName().toString();
                        userId = userId.substring(0, userId.length() - ".exp".length());
                        try {
                            long exp = Long.parseLong(Files.readString(file).trim());
                            scores.add(new PlayerExperience(userId, exp));
                        } catch (IOException | NumberFormatException e) {
                            Main.LOG("Failed to read exp for User: " + userId + " on Guild: "
                                    + event.getGuild().getId() + " (" + e.getMessage() + ")");
                        }
                    });
        } catch (IOException e) {
            Main.LOG("Failed to load level leaderboard for Guild: " + event.getGuild().getId()
                    + " (" + e.getMessage() + ")");
            return new CommandResult("Unable to load the leaderboard right now.", true);
        }

        if (scores.isEmpty())
            return new CommandResult("There are no levels to display yet.", true);

        scores.sort(Comparator.comparingLong(PlayerExperience::exp).reversed()
                .thenComparing(PlayerExperience::userId));
        StringBuilder reply = new StringBuilder("Level leaderboard:");
        for (int i = 0; i < Math.min(LEADERBOARD_SIZE, scores.size()); i++) {
            PlayerExperience score = scores.get(i);
            reply.append("\n").append(i + 1).append(". <@").append(score.userId())
                    .append("> - Level ").append(getLevel(score.exp()))
                    .append(" (").append(score.exp()).append(" xp)");
        }
        return new CommandResult(reply.toString(), true);
    }
}
