package client;

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
import java.util.Base64;

public class ClientConnection {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    private volatile boolean closed;
    private volatile boolean manualDisconnect;

    private final ClientConnectionListener listener;

    public ClientConnection(ClientConnectionListener listener) {
        this.listener = listener;
    }

    public void connect(String host, int port, String username) throws IOException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        this.username = username.trim();
        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        closed = false;
        manualDisconnect = false;

        send(Message.login(this.username));
        readLoginResponse();
        startReading();
    }

    public void sendText(String to, String text) {
        sendText(to, text, "");
    }

    public void sendText(String to, String text, String id) {
        if (text == null || text.isBlank() || to == null || to.isBlank()) {
            return;
        }

        send(Message.text(username, to, text, id));
    }

    public void sendFile(String to, String fileName, String mimeType, byte[] data, String id) {
        if (to == null || to.isBlank() || data == null || data.length == 0) {
            return;
        }

        send(Message.file(username, to, fileName, mimeType, Base64.getEncoder().encodeToString(data), id));
    }

    public void sendAudio(String to, String fileName, byte[] data, String id) {
        if (to == null || to.isBlank() || data == null || data.length == 0) {
            return;
        }

        send(Message.audio(username, to, fileName, Base64.getEncoder().encodeToString(data), id));
    }

    public void sendBroadcast(String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        send(Message.broadcast(username, text));
    }

    public void rename(String newUsername) {
        if (newUsername == null || newUsername.isBlank()) {
            return;
        }

        send(Message.rename(username, newUsername));
    }

    public void sendReceipt(String to, String messageId, String status) {
        if (to == null || to.isBlank() || messageId == null || messageId.isBlank()) {
            return;
        }

        send(Message.receipt(username, to, messageId, status));
    }

    public void setUsername(String username) {
        if (username != null && !username.isBlank()) {
            this.username = username.trim();
        }
    }

    public void disconnect() {
        manualDisconnect = true;
        send(Message.disconnect(username));
        close();
    }

    private void readLoginResponse() throws IOException {
        String xml = reader.readLine();

        if (xml == null) {
            throw new IOException("Server closed connection");
        }

        Message message = XmlProtocol.fromXml(xml);

        if (message.getType() == MessageType.ERROR) {
            close();
            throw new IOException(message.getText());
        }

        listener.onMessage(message);
    }

    private void startReading() {
        Thread thread = new Thread(() -> {
            try {
                String xml;

                while ((xml = reader.readLine()) != null) {
                    Message message = XmlProtocol.fromXml(xml);
                    listener.onMessage(message);
                }

                if (!manualDisconnect) {
                    listener.onDisconnected();
                }
            } catch (IOException e) {
                if (!manualDisconnect) {
                    listener.onDisconnected();
                }
            } catch (IllegalArgumentException e) {
                listener.onError(e.getMessage());
            } finally {
                close();
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    private void send(Message message) {
        if (!closed && writer != null) {
            writer.println(XmlProtocol.toXml(message));
        }
    }

    private void close() {
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
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public interface ClientConnectionListener {
        void onMessage(Message message);

        void onDisconnected();

        void onError(String error);
    }
}
