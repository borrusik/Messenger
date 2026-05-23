package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable{
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;

    public ClientHandler(Socket socket){
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            username = reader.readLine();

            System.out.println(username + " connected");
            ServerLauncher.sendToAll(username + " joined the chat", this);

            String message;
            while ((message = reader.readLine()) != null){
                String fullMessage = username + ": " + message;

                System.out.println(fullMessage);
                ServerLauncher.sendToAll(fullMessage, this);
            }
        } catch (IOException e) {
            System.out.println(username + " disconnected");
        } finally {
            ServerLauncher.removeClient(this);
            ServerLauncher.sendToAll(username + " left the chat", this);

            try {
                socket.close();
            } catch (IOException ignored){
            }
        }
    }

    public void sendMessage(String message) {
        writer.println(message);
    }
}
