package de.jns.pojo;

public class CommandResult {
    String message;
    Boolean isEphemeral;

    public CommandResult(String message, Boolean isEphemeral) {
        this.message = message;
        this.isEphemeral = isEphemeral;
    }

    public String message() {
        return message;
    }

    public Boolean ephemeral() {
        return isEphemeral;
    }
}
