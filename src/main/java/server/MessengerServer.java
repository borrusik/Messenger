package server;

import protocol.Message;
import protocol.MessageType;
import protocol.XmlProtocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MessengerServer {
    public static final int DEFAULT_PORT = 8080;
    public static final String SERVER_CHAT = "Server Chat";

    private final ConcurrentMap<String, ClientHandler> clientsByUsername = new ConcurrentHashMap<>();
    private final ConversationRegistry conversations = new ConversationRegistry();
    private final DatabaseService database = new DatabaseService(Path.of("data", "messenger.db"));
    private final ServerListener listener;

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running;

    public MessengerServer(ServerListener listener) {
        this.listener = listener;
    }

    public synchronized void start(int port) {
        if (running) {
            log("Server is already running");
            return;
        }

        running = true;
        conversations.clear();
        database.start();
        restoreSavedConversations();
        serverThread = new Thread(() -> runServer(port), "messenger-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;
        closeServerSocket();
    }

    public boolean isRunning() {
        return running;
    }

    boolean registerClient(ClientHandler client, String username) {
        String normalizedUsername = normalizeUsername(username);

        if (normalizedUsername.isBlank()) {
            return false;
        }

        ClientHandler previous = clientsByUsername.putIfAbsent(normalizedUsername, client);

        if (previous != null) {
            return false;
        }

        client.setUsername(normalizedUsername);
        database.saveUser(normalizedUsername);
        log(normalizedUsername + " connected");
        broadcast(Message.system(normalizedUsername + " joined the chat"));
        sendUserList();
        return true;
    }

    void renameClient(ClientHandler client, String newUsername) {
        String oldUsername = client.getUsername();
        String normalizedUsername = normalizeUsername(newUsername);

        if (oldUsername == null || oldUsername.isBlank()) {
            client.sendMessage(Message.error("", "You are not logged in"));
            return;
        }

        if (normalizedUsername.isBlank()) {
            client.sendMessage(Message.error(oldUsername, "Username cannot be empty"));
            return;
        }

        if (oldUsername.equals(normalizedUsername)) {
            client.sendMessage(Message.system("You are already " + normalizedUsername));
            return;
        }

        ClientHandler previous = clientsByUsername.putIfAbsent(normalizedUsername, client);

        if (previous != null) {
            client.sendMessage(Message.error(oldUsername, "Username is already taken"));
            return;
        }

        boolean removed = clientsByUsername.remove(oldUsername, client);

        if (!removed) {
            clientsByUsername.remove(normalizedUsername, client);
            client.sendMessage(Message.error(oldUsername, "Cannot rename disconnected user"));
            return;
        }

        client.setUsername(normalizedUsername);
        database.saveRename(oldUsername, normalizedUsername);
        String renameText = oldUsername + " changed nickname to " + normalizedUsername;
        log(renameText);
        broadcast(Message.system(renameText));
        sendUserList();
    }

    void routePrivateMessage(ClientHandler sender, Message incomingMessage) {
        String senderName = sender.getUsername();
        String receiverName = incomingMessage.getTo();

        if (senderName == null || senderName.isBlank()) {
            sender.sendMessage(Message.error("", "You are not logged in"));
            return;
        }

        if (receiverName == null || receiverName.isBlank()) {
            sender.sendMessage(Message.error(senderName, "Recipient is empty"));
            return;
        }

        if (Message.ALL.equals(receiverName)
                && (incomingMessage.getType() == MessageType.FILE || incomingMessage.getType() == MessageType.AUDIO)) {
            routeBroadcastAttachment(senderName, incomingMessage);
            return;
        }

        ClientHandler receiver = clientsByUsername.get(receiverName);

        if (receiver == null) {
            sender.sendMessage(Message.error(senderName, "User is offline: " + receiverName));
            return;
        }

        Message routedMessage = new Message(
                incomingMessage.getType(),
                senderName,
                receiverName,
                incomingMessage.getText(),
                incomingMessage.getId(),
                incomingMessage.getFileName(),
                incomingMessage.getMimeType()
        );
        receiver.sendMessage(routedMessage);
        sender.sendMessage(Message.receipt(receiverName, senderName, incomingMessage.getId(), "DELIVERED"));
        String conversation = conversations.createConversationId(senderName, receiverName);
        String displayText = describeMessage(incomingMessage);
        String line = senderName + ": " + displayText;

        database.saveMessage(conversation, routedMessage.getType(), senderName, receiverName, displayText);
        log(senderName + " -> " + receiverName + ": " + displayText);
        notifyConversationMessage(conversation, line);

        if (conversations.register(conversation)) {
            notifyConversationsChanged();
        }
    }

    private void routeBroadcastAttachment(String senderName, Message incomingMessage) {
        Message routedMessage = new Message(
                incomingMessage.getType(),
                senderName,
                Message.ALL,
                incomingMessage.getText(),
                incomingMessage.getId(),
                incomingMessage.getFileName(),
                incomingMessage.getMimeType()
        );
        broadcast(routedMessage);

        String displayText = describeMessage(incomingMessage);
        String line = senderName + ": " + displayText;
        database.saveMessage(SERVER_CHAT, routedMessage.getType(), senderName, Message.ALL, displayText);
        log(senderName + " -> " + SERVER_CHAT + ": " + displayText);
        notifyConversationMessage(SERVER_CHAT, line);

        if (conversations.register(SERVER_CHAT)) {
            notifyConversationsChanged();
        }
    }

    private String describeMessage(Message message) {
        if (message.getType() == MessageType.FILE) {
            return "[file] " + message.getFileName();
        }

        if (message.getType() == MessageType.AUDIO) {
            return "[audio] " + message.getFileName();
        }

        return message.getText();
    }

    void routeReceipt(ClientHandler sender, Message receiptMessage) {
        String senderName = sender.getUsername();
        String receiverName = receiptMessage.getTo();

        if (senderName == null || senderName.isBlank() || receiverName == null || receiverName.isBlank()) {
            return;
        }

        ClientHandler receiver = clientsByUsername.get(receiverName);

        if (receiver != null) {
            receiver.sendMessage(Message.receipt(senderName, receiverName, receiptMessage.getId(), receiptMessage.getText()));
        }
    }

    void routeBroadcastMessage(ClientHandler sender, Message incomingMessage) {
        String senderName = sender.getUsername();

        if (senderName == null || senderName.isBlank()) {
            sender.sendMessage(Message.error("", "You are not logged in"));
            return;
        }

        Message routedMessage = Message.broadcast(senderName, incomingMessage.getText());
        broadcast(routedMessage);
        String line = senderName + ": " + incomingMessage.getText();

        database.saveMessage(SERVER_CHAT, routedMessage.getType(), senderName, Message.ALL, incomingMessage.getText());
        log(senderName + " -> " + SERVER_CHAT + ": " + incomingMessage.getText());
        notifyConversationMessage(SERVER_CHAT, line);

        if (conversations.register(SERVER_CHAT)) {
            notifyConversationsChanged();
        }
    }

    void removeClient(ClientHandler client) {
        String username = client.getUsername();

        if (username == null || username.isBlank()) {
            return;
        }

        boolean removed = clientsByUsername.remove(username, client);

        if (!removed) {
            return;
        }

        log(username + " disconnected");
        broadcast(Message.system(username + " left the chat"));
        sendUserList();
    }

    private void runServer(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            serverSocket = socket;
            log("Server started on port: " + port);
            notifyServerStarted(port);

            while (running) {
                acceptClient(socket);
            }
        } catch (SocketException e) {
            if (running) {
                log("Server socket error: " + e.getMessage());
            }
        } catch (IOException e) {
            log("Server error: " + e.getMessage());
        } finally {
            running = false;
            closeClients();
            database.close();
            notifyServerStopped();
            log("Server stopped");
        }
    }

    private void acceptClient(ServerSocket socket) throws IOException {
        Socket clientSocket = socket.accept();
        ClientHandler clientHandler = new ClientHandler(clientSocket, this);
        Thread clientThread = new Thread(clientHandler, "client-handler");
        clientThread.setDaemon(true);
        clientThread.start();
    }

    private void broadcast(Message message) {
        String xml = XmlProtocol.toXml(message);

        for (ClientHandler client : clientsByUsername.values()) {
            client.sendXml(xml);
        }
    }

    private void sendUserList() {
        List<String> usernames = getUsernames();
        broadcast(Message.userList(String.join(", ", usernames)));
        notifyUserListChanged(usernames);
    }

    private void restoreSavedConversations() {
        for (String conversation : database.loadConversations()) {
            conversations.register(conversation);
        }

        notifyConversationsChanged();
    }

    private List<String> getUsernames() {
        return clientsByUsername.keySet()
                .stream()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    private void closeServerSocket() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void closeClients() {
        for (ClientHandler client : clientsByUsername.values()) {
            client.close();
        }

        clientsByUsername.clear();
        conversations.clear();
        notifyUserListChanged(List.of());
        notifyConversationsChanged();
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private void log(String text) {
        System.out.println(text);

        if (listener != null) {
            listener.onLog(text);
        }
    }

    private void notifyUserListChanged(List<String> usernames) {
        if (listener != null) {
            listener.onUserListChanged(usernames);
        }
    }

    private void notifyConversationsChanged() {
        if (listener != null) {
            listener.onConversationsChanged(conversations.list());
        }
    }

    private void notifyConversationMessage(String conversation, String text) {
        if (listener != null) {
            listener.onConversationMessage(conversation, text);
        }
    }

    private void notifyServerStarted(int port) {
        if (listener != null) {
            listener.onServerStarted(port);
        }
    }

    private void notifyServerStopped() {
        if (listener != null) {
            listener.onServerStopped();
        }
    }
}
