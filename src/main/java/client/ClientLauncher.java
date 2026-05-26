package client;

import protocol.Message;
import protocol.MessageType;

import java.io.IOException;
import java.util.Scanner;

public class ClientLauncher {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter username: ");
        String username = scanner.nextLine();

        try {
            ClientConnection connection = new ClientConnection(new ClientConnection.ClientConnectionListener() {
                @Override
                public void onMessage(Message message) {
                    if (message.getType() == MessageType.SYSTEM) {
                        System.out.println("[SERVER] " + message.getText());
                    } else if (message.getType() == MessageType.TEXT) {
                        System.out.println(message.getFrom() + ": " + message.getText());
                    } else if (message.getType() == MessageType.USER_LIST) {
                        System.out.println("[ONLINE] " + message.getText());
                    } else if (message.getType() == MessageType.ERROR) {
                        System.out.println("[ERROR] " + message.getText());
                    }
                }

                @Override
                public void onDisconnected() {
                    System.out.println("Connection closed");
                }

                @Override
                public void onError(String error) {
                    System.out.println("Protocol error: " + error);
                }
            });

            connection.connect(SERVER_IP, SERVER_PORT, username);

            System.out.println("Connected to server");
            System.out.println("Commands:");
            System.out.println("/to username message - send private message");
            System.out.println("/exit - disconnect");

            while (true) {
                String text = scanner.nextLine();

                if (text.equalsIgnoreCase("/exit")) {
                    connection.disconnect();
                    break;
                }

                if (text.startsWith("/to ")) {
                    String payload = text.substring(4).trim();
                    int separator = payload.indexOf(' ');

                    if (separator <= 0 || separator == payload.length() - 1) {
                        System.out.println("Usage: /to username message");
                        continue;
                    }

                    String to = payload.substring(0, separator).trim();
                    String message = payload.substring(separator + 1).trim();
                    connection.sendText(to, message);
                } else {
                    System.out.println("Use /to username message");
                }
            }

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        }
    }
}
