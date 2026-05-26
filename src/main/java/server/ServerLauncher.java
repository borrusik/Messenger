package server;

public class ServerLauncher {
    public static void main(String[] args) {
        int port = getPort(args);
        MessengerServer server = new MessengerServer(null);
        server.start(port);
        waitUntilStopped(server);
    }

    private static int getPort(String[] args) {
        if (args.length == 0) {
            return MessengerServer.DEFAULT_PORT;
        }

        try {
            return Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port, using default: " + MessengerServer.DEFAULT_PORT);
            return MessengerServer.DEFAULT_PORT;
        }
    }

    private static void waitUntilStopped(MessengerServer server) {
        while (server.isRunning()) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.stop();
                return;
            }
        }
    }
}
