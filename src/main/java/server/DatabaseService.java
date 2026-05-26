package server;

import protocol.MessageType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DatabaseService implements AutoCloseable {
    private final Path databasePath;
    private Connection connection;
    private boolean available;

    public DatabaseService(Path databasePath) {
        this.databasePath = databasePath;
    }

    public synchronized void start() {
        if (available) {
            return;
        }

        try {
            Files.createDirectories(databasePath.getParent());
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            createTables();
            available = true;
        } catch (ClassNotFoundException | SQLException | IOException | LinkageError e) {
            available = false;
            System.out.println("Database disabled: " + e.getMessage());
        }
    }

    public synchronized void saveUser(String username) {
        if (!available || username == null || username.isBlank()) {
            return;
        }

        String sql = """
                INSERT INTO users(username, created_at, last_seen_at)
                VALUES (?, ?, ?)
                ON CONFLICT(username) DO UPDATE SET last_seen_at = excluded.last_seen_at
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String now = now();
            statement.setString(1, username);
            statement.setString(2, now);
            statement.setString(3, now);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Cannot save user: " + e.getMessage());
        }
    }

    public synchronized void saveRename(String oldUsername, String newUsername) {
        if (!available) {
            return;
        }

        saveUser(newUsername);

        String sql = "INSERT INTO nickname_events(old_username, new_username, created_at) VALUES (?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, oldUsername);
            statement.setString(2, newUsername);
            statement.setString(3, now());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Cannot save nickname event: " + e.getMessage());
        }
    }

    public synchronized void saveMessage(String conversation, MessageType type, String sender, String recipient, String text) {
        if (!available) {
            return;
        }

        String sql = """
                INSERT INTO messages(conversation, message_type, sender, recipient, text, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, conversation);
            statement.setString(2, type.name());
            statement.setString(3, sender);
            statement.setString(4, recipient);
            statement.setString(5, text);
            statement.setString(6, now());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Cannot save message: " + e.getMessage());
        }
    }

    public synchronized List<String> loadConversations() {
        List<String> result = new ArrayList<>();

        if (!available) {
            return result;
        }

        String sql = "SELECT DISTINCT conversation FROM messages ORDER BY conversation";

        try (Statement statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                result.add(rows.getString("conversation"));
            }
        } catch (SQLException e) {
            System.out.println("Cannot load conversations: " + e.getMessage());
        }

        return result;
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException ignored) {
        } finally {
            connection = null;
            available = false;
        }
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL UNIQUE,
                        created_at TEXT NOT NULL,
                        last_seen_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS nickname_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        old_username TEXT NOT NULL,
                        new_username TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        conversation TEXT NOT NULL,
                        message_type TEXT NOT NULL,
                        sender TEXT NOT NULL,
                        recipient TEXT NOT NULL,
                        text TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
        }
    }

    private String now() {
        return LocalDateTime.now().toString();
    }
}
