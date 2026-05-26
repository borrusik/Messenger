package server;

import protocol.Message;
import protocol.MessageType;
import protocol.XmlProtocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final MessengerServer server;

    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    private volatile boolean closed;

    public ClientHandler(Socket socket, MessengerServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            openStreams();
            handleLogin();
            readMessages();
        } catch (IOException e) {
            if (!closed && username != null) {
                System.out.println(username + " disconnected unexpectedly");
            }
        } catch (IllegalArgumentException e) {
            sendMessage(Message.error(username, e.getMessage()));
        } finally {
            close();
            server.removeClient(this);
        }
    }

    public String getUsername() {
        return username;
    }

    void setUsername(String username) {
        this.username = username;
    }

    void sendMessage(Message message) {
        sendXml(XmlProtocol.toXml(message));
    }

    void sendXml(String xml) {
        if (!closed && writer != null) {
            writer.println(xml);
        }
    }

    public void close() {
        if (closed) {
            return;
        }

        closed = true;

        if (writer != null) {
            writer.close();
        }

        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {
        }

        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void openStreams() throws IOException {
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    private void handleLogin() throws IOException {
        String xml = reader.readLine();

        if (xml == null) {
            throw new IOException("Client disconnected before login");
        }

        Message loginMessage = XmlProtocol.fromXml(xml);

        if (loginMessage.getType() != MessageType.LOGIN) {
            throw new IllegalArgumentException("First message must be LOGIN");
        }

        if (!server.registerClient(this, loginMessage.getFrom())) {
            sendMessage(Message.error(loginMessage.getFrom(), "Username is empty or already taken"));
            throw new IOException("Login rejected");
        }
    }

    private void readMessages() throws IOException {
        String xml;

        while ((xml = reader.readLine()) != null) {
            Message message;

            try {
                message = XmlProtocol.fromXml(xml);
            } catch (IllegalArgumentException e) {
                sendMessage(Message.error(username, "Invalid XML message"));
                continue;
            }

            handleMessage(message);
        }
    }

    private void handleMessage(Message message) {
        if (message.getType() == MessageType.DISCONNECT) {
            close();
            return;
        }

        if (message.getType() == MessageType.TEXT
                || message.getType() == MessageType.FILE
                || message.getType() == MessageType.AUDIO) {
            server.routePrivateMessage(this, message);
            return;
        }

        if (message.getType() == MessageType.RENAME) {
            server.renameClient(this, message.getText());
            return;
        }

        if (message.getType() == MessageType.RECEIPT) {
            server.routeReceipt(this, message);
            return;
        }

        if (message.getType() == MessageType.BROADCAST) {
            server.routeBroadcastMessage(this, message);
            return;
        }

        sendMessage(Message.error(username, "Unsupported message type: " + message.getType()));
    }
}
