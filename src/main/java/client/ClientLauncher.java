package client;

import protocol.Message;
import protocol.MessageType;
import protocol.XmlProtocol;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ClientLauncher {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter username: ");
        String username = scanner.nextLine();

        try {
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );

            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            Message loginMessage = new Message(MessageType.LOGIN, username, "");
            writer.println(XmlProtocol.toXml(loginMessage));

            System.out.println("Connected to server");
            System.out.println("Write message or /exit:");

            Thread readThread = new Thread(() -> {
                try {
                    String xml;

                    while ((xml = reader.readLine()) != null) {
                        Message message = XmlProtocol.fromXml(xml);

                        if (message.getType() == MessageType.SYSTEM) {
                            System.out.println("[SERVER] " + message.getText());
                        } else if (message.getType() == MessageType.TEXT) {
                            System.out.println(message.getFrom() + ": " + message.getText());
                        }
                    }

                } catch (IOException e) {
                    System.out.println("Connection closed");
                }
            });

            readThread.start();

            while (true) {
                String text = scanner.nextLine();

                if (text.equalsIgnoreCase("/exit")) {
                    Message disconnectMessage = new Message(MessageType.DISCONNECT, username, "");
                    writer.println(XmlProtocol.toXml(disconnectMessage));

                    socket.close();
                    break;
                }

                Message textMessage = new Message(MessageType.TEXT, username, text);
                writer.println(XmlProtocol.toXml(textMessage));
            }

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        }
    }
}