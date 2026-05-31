package de.jns;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.HashMap;
import java.util.function.Predicate;

public class GGCListenerAdapter extends ListenerAdapter {
    static class Command {
        Predicate<SlashCommandInteractionEvent> hasPermissionCallback;
        Predicate<SlashCommandInteractionEvent> executeCallback;

        public Boolean hasPermission(SlashCommandInteractionEvent event) {
            return hasPermissionCallback.test(event);
        }

        public boolean execute(SlashCommandInteractionEvent event) {
            return executeCallback.test(event);
        }
    }

    private final HashMap<String, Command> commands = new HashMap<>();

    public void RegisterCommand(String name, Predicate<SlashCommandInteractionEvent> permissionCheck, Predicate<SlashCommandInteractionEvent> executor) {
        Command cmd = new Command();
        cmd.hasPermissionCallback = permissionCheck;
        cmd.executeCallback = executor;
        commands.put(name, cmd);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        //event.deferReply().queue();

        if (commands.containsKey(command)) {
            Command cmd = commands.get(command);
            boolean result = false;
            if (cmd.hasPermission(event))
            {
                result = cmd.execute(event);
            }

            if (!result) {
                event.reply("You don't have permission to use this command!").queue();
            }
        }
    }
}
