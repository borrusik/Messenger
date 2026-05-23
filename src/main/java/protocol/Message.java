package protocol;

public class Message {
    private final MessageType type;
    private final String from;
    private final String text;

    public Message(MessageType type, String from, String text) {
        this.type = type;
        this.from = from;
        this.text = text;
    }

    public MessageType getType() {
        return type;
    }

    public String getFrom() {
        return from;
    }

    public String getText() {
        return text;
    }
}