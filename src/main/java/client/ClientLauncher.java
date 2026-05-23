package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ClientLauncher {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 8080;

    static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter username: ");
        String username = scanner.nextLine();

        try {
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );

            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            writer.println(username);

            System.out.println("Connected to server");
            System.out.println("Write message or /exit:");

            Thread readThread = new Thread(() -> {
                try {
                    String message;

                    while ((message = reader.readLine()) != null) {
                        System.out.println(message);
                    }

                } catch (IOException e) {
                    System.out.println("Connection closed");
                }
            });

            readThread.start();

            while (true) {
                String message = scanner.nextLine();

                if (message.equalsIgnoreCase("/exit")) {
                    socket.close();
                    break;
                }

                writer.println(message);
            }

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        }
    }
}