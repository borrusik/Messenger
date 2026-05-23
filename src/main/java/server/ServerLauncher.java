package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ServerLauncher {
    public static final int PORT = 8080;
    public static final List<ClientHandler> clients = new ArrayList<>();

    static void main() {
        System.out.println("Server started on port: " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true){
                Socket socket = serverSocket.accept();

                ClientHandler clientHandler = new ClientHandler(socket);
                clients.add(clientHandler);

                Thread thread = new Thread(clientHandler);
                thread.start();
            }
        } catch (IOException e){
            System.out.println("Server error: " + e.getMessage());
        }
    }

    public static void sendToAll(String message, ClientHandler sender){
        for(ClientHandler c : clients){
            if(c != sender){
                c.sendMessage(message);
            }
        }
    }

    public static void removeClient(ClientHandler c){
        clients.remove(c);
    }


}
