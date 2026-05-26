package server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerGuiApp extends Application {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ObservableList<String> visibleMessages = FXCollections.observableArrayList();
    private final ObservableList<String> conversations = FXCollections.observableArrayList();
    private final Map<String, ObservableList<String>> histories = new HashMap<>();

    private MessengerServer server;
    private Label statusLabel;
    private Label selectedConversationLabel;
    private ListView<String> messageList;
    private ListView<String> conversationList;
    private Map<String, String> launchOptions;
    private String selectedConversation = "";

    @Override
    public void start(Stage stage) {
        launchOptions = getParameters().getNamed();
        stage.setTitle("ICQ Server");

        Scene scene = new Scene(createRoot(), 820, 520);
        attachStyles(scene);

        stage.setScene(scene);
        stage.show();
        Platform.runLater(this::startServer);
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    private BorderPane createRoot() {
        statusLabel = createLabel("Starting...");
        selectedConversationLabel = createLabel("No conversation selected");

        HBox topPanel = new HBox(
                12,
                createLabel("Server"),
                statusLabel,
                selectedConversationLabel
        );
        topPanel.setPadding(new Insets(10));
        topPanel.getStyleClass().add("top-panel");
        HBox.setHgrow(selectedConversationLabel, Priority.ALWAYS);

        messageList = new ListView<>(visibleMessages);
        messageList.getStyleClass().add("log-list");

        conversationList = new ListView<>(conversations);
        conversationList.getStyleClass().add("conversation-list");
        conversationList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                selectConversation(newValue);
            }
        });

        Label titleLabel = createLabel("CONVERSATIONS:");
        titleLabel.setStyle("-fx-padding: 15px;");

        VBox rightPanel = new VBox(titleLabel, conversationList);
        rightPanel.getStyleClass().add("right-panel");
        rightPanel.setPrefWidth(270);
        VBox.setVgrow(conversationList, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("server-root");
        root.setTop(topPanel);
        root.setCenter(messageList);
        root.setRight(rightPanel);

        resetHistories();
        return root;
    }

    private void startServer() {
        resetHistories();
        statusLabel.setText("Starting...");

        server = new MessengerServer(new GuiServerListener());
        server.start(getConfiguredPort());
    }

    private void resetHistories() {
        histories.clear();
        conversations.clear();
        visibleMessages.clear();
        selectedConversation = "";

        if (conversationList != null) {
            conversationList.getSelectionModel().clearSelection();
        }

        if (selectedConversationLabel != null) {
            selectedConversationLabel.setText("No conversation selected");
        }
    }

    private void selectConversation(String conversation) {
        selectedConversation = conversation;
        selectedConversationLabel.setText(conversation);
        visibleMessages.setAll(historyFor(conversation));
        messageList.scrollTo(Math.max(0, visibleMessages.size() - 1));
    }

    private void addEvent(String text) {
        // Server lifecycle events are intentionally not shown as chat tabs.
    }

    private void addMessage(String conversation, String text) {
        ObservableList<String> history = historyFor(conversation);
        history.add(LocalTime.now().format(TIME_FORMAT) + "  " + text);

        if (conversation.equals(selectedConversation)) {
            visibleMessages.setAll(history);
            messageList.scrollTo(Math.max(0, visibleMessages.size() - 1));
        }
    }

    private ObservableList<String> historyFor(String conversation) {
        return histories.computeIfAbsent(conversation, key -> FXCollections.observableArrayList());
    }

    private void setConversations(List<String> serverConversations) {
        String selected = selectedConversation;

        conversations.clear();

        for (String conversation : serverConversations) {
            if (!conversations.contains(conversation)) {
                conversations.add(conversation);
            }

            historyFor(conversation);
        }

        if (conversations.contains(selected)) {
            conversationList.getSelectionModel().select(selected);
        } else if (!conversations.isEmpty()) {
            conversationList.getSelectionModel().select(0);
        } else {
            selectedConversation = "";
            selectedConversationLabel.setText("No conversation selected");
            visibleMessages.clear();
        }
    }

    private int getConfiguredPort() {
        String portText = launchOptions.getOrDefault("port", String.valueOf(MessengerServer.DEFAULT_PORT));

        try {
            return Integer.parseInt(portText.trim());
        } catch (NumberFormatException e) {
            return MessengerServer.DEFAULT_PORT;
        }
    }

    private Label createLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("label");
        return label;
    }

    private void attachStyles(Scene scene) {
        if (getClass().getResource("/server_style.css") != null) {
            scene.getStylesheets().add(getClass().getResource("/server_style.css").toExternalForm());
        }
    }

    private class GuiServerListener implements ServerListener {
        @Override
        public void onLog(String text) {
            Platform.runLater(() -> addEvent(text));
        }

        @Override
        public void onUserListChanged(List<String> usernames) {
        }

        @Override
        public void onConversationsChanged(List<String> convs) {
            Platform.runLater(() -> setConversations(convs));
        }

        @Override
        public void onConversationMessage(String conversation, String text) {
            Platform.runLater(() -> addMessage(conversation, text));
        }

        @Override
        public void onServerStarted(int port) {
            Platform.runLater(() -> statusLabel.setText("Running on port " + port));
        }

        @Override
        public void onServerStopped() {
            Platform.runLater(() -> statusLabel.setText("Stopped"));
        }
    }
}
