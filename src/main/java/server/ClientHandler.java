package server;

import protocol.Message;
import protocol.MessageType;
import protocol.XmlProtocol;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );

            writer = new PrintWriter(socket.getOutputStream(), true);

            String loginXml = reader.readLine();
            Message loginMessage = XmlProtocol.fromXml(loginXml);

            username = loginMessage.getFrom();

            System.out.println(username + " connected");

            ServerLauncher.sendToAll(
                    XmlProtocol.toXml(new Message(
                            MessageType.SYSTEM,
                            "SERVER",
                            username + " joined the chat"
                    )),
                    this
            );

            String xml;
            while ((xml = reader.readLine()) != null) {
                Message message = XmlProtocol.fromXml(xml);

                if (message.getType() == MessageType.DISCONNECT) {
                    break;
                }

                if (message.getType() == MessageType.TEXT) {
                    String text = message.getText();

                    System.out.println(username + ": " + text);

                    Message messageToSend = new Message(
                            MessageType.TEXT,
                            username,
                            text
                    );

                    ServerLauncher.sendToAll(
                            XmlProtocol.toXml(messageToSend),
                            this
                    );
                }
            }

        } catch (IOException e) {
            System.out.println(username + " disconnected");
        } finally {
            ServerLauncher.removeClient(this);

            if (username != null) {
                ServerLauncher.sendToAll(
                        XmlProtocol.toXml(new Message(
                                MessageType.SYSTEM,
                                "SERVER",
                                username + " left the chat"
                        )),
                        this
                );
            }

            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }
}