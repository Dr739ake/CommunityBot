package de.jns;

import de.jns.pojo.CommandResult;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

import java.util.HashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public class BotListenerAdapter extends ListenerAdapter {
    static class Command {
        Predicate<SlashCommandInteractionEvent> hasPermissionCallback;
        //Predicate<SlashCommandInteractionEvent> executeCallback;
        Function<SlashCommandInteractionEvent, CommandResult> executeCallback;

        public Boolean hasPermission(SlashCommandInteractionEvent event) {
            return hasPermissionCallback.test(event);
        }

        public CommandResult execute(SlashCommandInteractionEvent event) {
            return executeCallback.apply(event);
        }
    }

    private final HashMap<String, Command> commands = new HashMap<>();

    public void RegisterCommand(String name, Predicate<SlashCommandInteractionEvent> permissionCheck,
                                Function<SlashCommandInteractionEvent, CommandResult> executor) {
        Command cmd = new Command();
        cmd.hasPermissionCallback = permissionCheck;
        cmd.executeCallback = executor;
        commands.put(name, cmd);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName() ;

        if (commands.containsKey(command)) {
            Command cmd = commands.get(command);
            boolean permission = cmd.hasPermission(event);
            CommandResult result = new CommandResult("No Reply", true);
            if (permission)
            {
                result = cmd.execute(event);
            }

            if (!permission) {
                event.reply("You don't have permission to use this command!").setEphemeral(true).queue();
            }
            else
            {
                ReplyCallbackAction reply = event.reply(result.message());
                if (result.ephemeral())
                    reply.setEphemeral(true);
                reply.queue();
            }

        }
    }
}
