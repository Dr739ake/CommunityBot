package de.jns.moderation;

import de.jns.Main;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.jetbrains.annotations.NotNull;

public class ModerationBot extends ListenerAdapter {
    public JDA jda;
    private String moderationLog;

    public ModerationBot(String logChannel) {
        jda = Main.jda;
        Main.LOG("ModerationBot Constructor");
        moderationLog = logChannel;
    }

    // Todo: Try onGuildMemberUpdateNickname sometime in the future.
    @Override
    public void onGuildMemberUpdate(GuildMemberUpdateEvent event) {
        Member member = event.getMember();

        int checkName = isNicknameSuspicious(member.getNickname());

        if (checkName == 1) {
            member.modifyNickname(null).queue();
        } else if (checkName == 2) {
            event.getGuild().getTextChannelById(moderationLog)
                    .sendMessage("⚠️ Fremdwerbung im Nickname erkannt: `" + member.getNickname() + "` bei " + member.getUser().getAsMention())
                    .queue();
            member.modifyNickname(null).queue();
        } else if (checkName == 3) {
            event.getGuild().getTextChannelById(moderationLog)
                    .sendMessage("⚠️ Verdächtiger Nickname erkannt: `"
                            + member.getNickname() + "` bei " + member.getUser().getAsMention())
                    .queue();
        }
    }

    /*
     * Return values
     * 1: Illegal name with blockedWords
     * 2: contains a link
     * 3: contains non-roman characters
     * 0: clean name
     */
    public int isNicknameSuspicious(String nickname) {
        if (nickname == null) return 0;

        // remove spaces from name
        String cleanNick = cleanString(nickname);

        // Verdächtige Wörter (kannst du beliebig erweitern)
        String[] blockedWords = {
                "nazi", "hitler", "sex", "porn", "nigger", "nigga", "negro", "fuck", "bitch",
                "cunt", "slut", "dick", "cock", "whore", "faggot", "retard",
                "neger", "hure", "wichser", "arsch", "idiot", "penis", "vagina"
        };

        // Auf enthaltene Wörter prüfen
        for (String word : blockedWords) {
            // Regex: erlaubt kleine Abstände zwischen Buchstaben (z. B. f.u.c.k oder f u c k)
            String pattern = String.join("\\W*", word.split(""));
            if (cleanNick.matches(".*" + pattern + ".*")) {
                return 1;
            }
        }

        // Werbung erkennen
        if (cleanNick.matches(".*(discord\\.gg|http|www\\.).*")) return 2;

        // Zu viele Sonderzeichen oder verdächtige Formatierung
        if (nickname.matches(".*[^a-zA-Z0-9 _\\-].*")) return 3;

        return 0;
    }

    @NotNull
    private static String cleanString(String nickname) {
        StringBuilder noSpaces = new StringBuilder();
        for (char c : nickname.toCharArray()) {
            if (c != ' ') {
                noSpaces.append(c);
            }
        }

        // Kleinbuchstaben für einfacheren Vergleich
        String lower = noSpaces.toString().toLowerCase();

        // Zeichen ersetzen, um "Leetspeak" abzufangen
        lower = lower
                .replace("@", "a")
                .replace("4", "a")
                .replace("3", "e")
                .replace("1", "i")
                .replace("!", "i")
                .replace("0", "o")
                .replace("$", "s")
                .replace("*", "")
                .replace(".", "")
                .replace(" ", "");
        return lower;
    }

}
