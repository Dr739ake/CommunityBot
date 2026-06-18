package de.jns.pojo;

import net.dv8tion.jda.api.entities.Message;
import org.json.JSONObject;

import java.io.*;

public class ServerDataPOJO {
    public int curNum;
    public int highScore;
    public String lastUser;
    public String channelId;
    public Message lastCountMessage;
    private File file;

    public ServerDataPOJO(String guildId) {
        curNum = 0;
        highScore = 0;
        lastUser = "";
        file = new File("data/" + guildId + ".json");

        {
            File d = new File("data");
            if (!d.exists()) {
                d.mkdir();
            }
        }

        if (file.exists()) {
            String jsonString;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                jsonString = reader.readLine();

                JSONObject jsonObject = new JSONObject(jsonString);

                curNum = (Integer) jsonObject.get("curNum");
                highScore = (Integer) jsonObject.get("highScore");
                channelId = (String) jsonObject.get("channelId");
                lastUser = (String) jsonObject.get("lastUser");
            } catch (Exception ignored) {
                // nothing
            }
        } else {
            try {
                file.createNewFile();
            } catch (Exception ignored) {
                // nothing
            }
        }
        this.save();
    }

    public ServerDataPOJO(ServerDataPOJO serverDataPOJO) {
        this.curNum = serverDataPOJO.curNum;
        this.highScore = serverDataPOJO.highScore;
        this.lastUser = serverDataPOJO.lastUser;
        this.channelId = serverDataPOJO.channelId;
        this.lastCountMessage = serverDataPOJO.lastCountMessage;
        this.file = serverDataPOJO.file;
    }

    public void save() {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("curNum", curNum);
        jsonObject.put("highScore", highScore);
        jsonObject.put("lastUser", lastUser);
        jsonObject.put("channelId", channelId);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(jsonObject.toString());
        } catch (Exception ignored) {
            // nothing
        }
    }
}
