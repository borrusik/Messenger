package protocol;

public class Message {
    public static final String SERVER = "SERVER";
    public static final String ALL = "ALL";

    private final MessageType type;
    private final String from;
    private final String to;
    private final String text;
    private final String id;
    private final String fileName;
    private final String mimeType;

    public Message(MessageType type, String from, String to, String text) {
        this(type, from, to, text, "");
    }

    public Message(MessageType type, String from, String to, String text, String id) {
        this(type, from, to, text, id, "", "");
    }

    public Message(MessageType type, String from, String to, String text, String id, String fileName, String mimeType) {
        if (type == null) {
            throw new IllegalArgumentException("Message type cannot be null");
        }

        this.type = type;
        this.from = normalize(from);
        this.to = normalize(to);
        this.text = normalize(text);
        this.id = normalize(id);
        this.fileName = normalize(fileName);
        this.mimeType = normalize(mimeType);
    }

    public static Message login(String username) {
        return new Message(MessageType.LOGIN, username, SERVER, "");
    }

    public static Message rename(String from, String newUsername) {
        return new Message(MessageType.RENAME, from, SERVER, newUsername);
    }

    public static Message text(String from, String to, String text) {
        return new Message(MessageType.TEXT, from, to, text);
    }

    public static Message text(String from, String to, String text, String id) {
        return new Message(MessageType.TEXT, from, to, text, id);
    }

    public static Message file(String from, String to, String fileName, String mimeType, String base64Data, String id) {
        return new Message(MessageType.FILE, from, to, base64Data, id, fileName, mimeType);
    }

    public static Message audio(String from, String to, String fileName, String base64Data, String id) {
        return new Message(MessageType.AUDIO, from, to, base64Data, id, fileName, "audio/wav");
    }

    public static Message broadcast(String from, String text) {
        return new Message(MessageType.BROADCAST, from, ALL, text);
    }

    public static Message receipt(String from, String to, String id, String status) {
        return new Message(MessageType.RECEIPT, from, to, status, id);
    }

    public static Message system(String text) {
        return new Message(MessageType.SYSTEM, SERVER, ALL, text);
    }

    public static Message userList(String text) {
        return new Message(MessageType.USER_LIST, SERVER, ALL, text);
    }

    public static Message error(String to, String text) {
        return new Message(MessageType.ERROR, SERVER, to, text);
    }

    public static Message disconnect(String username) {
        return new Message(MessageType.DISCONNECT, username, SERVER, "");
    }

    public MessageType getType() {
        return type;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getText() {
        return text;
    }

    public String getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
