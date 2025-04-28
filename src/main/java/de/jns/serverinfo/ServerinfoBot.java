package de.jns.serverinfo;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ServerinfoBot extends ListenerAdapter {
    public static final Logger log = LoggerFactory.getLogger(ServerinfoBot.class);
    public JDA jda;
    public TextChannel myChannel;

    public ServerinfoBot(String token, String channelId) {
        try {
            jda = JDABuilder.createLight(token,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(this)
                    .build().awaitReady();

            myChannel = jda.getTextChannelById(channelId);
            System.out.println("my Server is " + myChannel.getGuild().getName());
            System.out.println("my Channel is " + myChannel.getName());
        } catch ( Exception e ) {
            log.error("e: ", e);
        }
    }

    public Message UpdateMessage(Message message, MessageEmbed embed) {
        try
        {
            myChannel.retrieveMessageById(message.getId()).complete(); // to check if the message still exists
            message.editMessageEmbeds(embed).queue();
        }
        catch (Exception e)
        {
            System.out.println("The message was deleted, creating a new one.");
            message = CreateMessage(embed, myChannel);
        }

        return message;
    }

    public Message CreateMessage(MessageEmbed embed, TextChannel channel) {
        MessageCreateAction messageCreateAction = channel.sendMessageEmbeds(embed);
        return messageCreateAction.complete();
    }
}
