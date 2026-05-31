package de.jns.gitlabissues;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class GitLabIssueCreator extends ListenerAdapter {

    private Map<String, String> projects = new HashMap<>();

    private String gitlabUrl;
    private String privateToken;

    public GitLabIssueCreator(String gitlabUrl, String privateToken) throws Exception {
        this.gitlabUrl = gitlabUrl;
        this.privateToken = privateToken;

        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gitlabUrl + "/api/v4/projects/"))
                    .header("PRIVATE-TOKEN", privateToken)
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();

            response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (IOException | InterruptedException e) {
            throw e;
        }

        JSONArray array = new JSONArray(response.body());

        System.out.println("Gitlab Projects:");
        for (int i = 0; i < array.length(); i++)
        {
            JSONObject json = new JSONObject(array.get(i).toString());

            String id = json.get("id").toString();
            String name = json.get("name").toString();

            System.out.println(id + " => " + name);
            projects.put(name, id);
        }
    }

    // Button Klick behandeln
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getComponentId().equals("open_ticket_modal")) {
            TextInput server = TextInput.create("server", TextInputStyle.SHORT)
                    .setPlaceholder("MRP / SCP / TTT")
                    .setMinLength(2)
                    .setMaxLength(3)
                    .build();

            TextInput title = TextInput.create("title", TextInputStyle.SHORT)
                    .setPlaceholder("Beschreibe kurz was dir aufgefallen ist")
                    .setMinLength(1)
                    .setMaxLength(100) // or setRequiredRange(10, 100)
                    .build();

            TextInput body = TextInput.create("body", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Bitte teile uns mit, wie man auf den Fehler stößt bzw. ihn Reproduzieren kann.")
                    .setMinLength(0)
                    .setMaxLength(1000)
                    .build();

            Modal modal = Modal.create("bugreport", "Bugreport Einrechen")
                    .addComponents(Label.of("Server", server), Label.of("Title", title), Label.of("Beschreibung", body))
                    .build();

            event.replyModal(modal).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        Member executor = event.getMember();

        if (executor != null && command.equals("sendissueembed") && executor.hasPermission(Permission.ADMINISTRATOR)) {
            TextChannel textChannel = Objects.requireNonNull(event.getInteraction().getOption("channel")).getAsChannel().asTextChannel();
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("Fehlermeldung Einreichen");
            embed.setDescription("Klicke auf den Button, um einen Bugreport zu erstellen.");
            embed.setColor(Color.BLUE);

            textChannel.sendMessageEmbeds(embed.build())
                    .addComponents(ActionRow.of(
                        Button.primary("open_ticket_modal", "Einreichen")))
                    .queue();

            event.reply("Done").queue();
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getModalId().equals("bugreport")) {
            String title = event.getValue("title").getAsString();
            Member member = event.getMember();
            String projectId = null;

            String server = event.getValue("server").getAsString();
            if (server.toLowerCase().equals("mrp")) {
                projectId = projects.get("MilitaryRP");
            } else if (server.toLowerCase().equals("scp")) {
                projectId = projects.get("SCP-RP");
            }

            String reply;
            if (projectId != null)
            {
                try {
                    String response = createIssue(gitlabUrl, privateToken, projectId, title, event.getValue("body").getAsString());
                    JSONObject obj = new JSONObject(response);
                    String iid = obj.get("iid").toString();
                    String comment = "Author: " + member.getEffectiveName() + " (" + member.getId() + ")";

                    addComment(gitlabUrl, privateToken, projectId, iid, comment);
                    reply = "Danke für die Meldung.";
                } catch (IOException | InterruptedException e) {
                    event.reply("Etwas ist sehr Schief gegangen, versuch es bitte Später erneut.").setEphemeral(true).queue();
                    throw new RuntimeException(e);
                }
            } else {
                reply = "Projekt nicht gefunden!";
            }
            event.reply(reply).setEphemeral(true).queue();
        }
    }

    public static String createIssue(
            String gitlabUrl,
            String privateToken,
            String projectId,
            String title,
            String description
    ) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        String body = "title=" + URLEncoder.encode(title, StandardCharsets.UTF_8)
                + "&description=" + URLEncoder.encode(description, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gitlabUrl + "/api/v4/projects/" + projectId + "/issues"))
                .header("PRIVATE-TOKEN", privateToken)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() == 201) {
            System.out.println("Issue erfolgreich erstellt!");
            System.out.println(response.body());
        } else {
            System.out.println("Fehler beim Erstellen:");
            System.out.println("HTTP " + response.statusCode());
            System.out.println(response.body());
        }
        return response.body();
    }

    public static void addComment(
            String gitlabUrl,
            String privateToken,
            String projectId,
            String issueIid,
            String comment
    ) throws IOException, InterruptedException {

        HttpClient client = HttpClient.newHttpClient();

        String body = "body=" + URLEncoder.encode(
                comment,
                StandardCharsets.UTF_8
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        gitlabUrl
                                + "/api/v4/projects/"
                                + projectId
                                + "/issues/"
                                + issueIid
                                + "/notes"
                ))
                .header("PRIVATE-TOKEN", privateToken)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() == 201) {
            System.out.println("Kommentar hinzugefügt!");
            System.out.println(response.body());
        } else {
            System.out.println("Fehler:");
            System.out.println(response.statusCode());
            System.out.println(response.body());
        }
    }
}