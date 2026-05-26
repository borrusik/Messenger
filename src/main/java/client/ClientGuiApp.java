package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import protocol.Message;
import protocol.MessageType;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ClientGuiApp extends Application {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 8080;
    private static final String SERVER_CHAT = "Server Chat";
    private static final int MAX_ATTACHMENT_BYTES = 2 * 1024 * 1024;
    private static final AudioFormat VOICE_FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);

    private final ObservableList<String> users = FXCollections.observableArrayList();
    private final Map<String, ObservableList<ChatMessage>> histories = new HashMap<>();
    private final Map<String, Integer> unreadCounts = new HashMap<>();
    private final Set<String> knownUsers = new HashSet<>();
    private final Set<String> onlineUsers = new HashSet<>();

    private Stage stage;
    private ClientConnection connection;
    private ListView<ChatMessage> messageList;
    private ListView<String> userList;
    private TextField messageField;
    private Button sendButton;
    private Button attachButton;
    private Button recordButton;
    private Label chatStatusLabel;
    private Label selectedChatLabel;
    private Label onlineTitleLabel;
    private Label nicknameLabel;
    private String currentUsername;
    private String selectedUser;
    private Map<String, String> launchOptions;
    private volatile boolean recording;
    private TargetDataLine recordingLine;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        this.launchOptions = getParameters().getNamed();
        this.stage.setTitle("ICQ Client");
        showConnectScene();
        this.stage.show();
    }

    @Override
    public void stop() {
        stopRecording();

        if (connection != null) {
            connection.disconnect();
        }
    }

    private void showConnectScene() {
        currentUsername = null;
        selectedUser = null;

        TextField hostField = createTextField(DEFAULT_HOST, 18);
        TextField usernameField = createTextField("", 18);

        GridPane form = new GridPane();
        form.setHgap(20);
        form.setVgap(15);
        form.setPadding(new Insets(30));
        form.setAlignment(Pos.CENTER);
        form.addRow(0, createLabel("Server IP:"), hostField);
        form.addRow(1, createLabel("User name:"), usernameField);

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("button", "button-cancel");

        Button connectButton = new Button("Connect");
        connectButton.getStyleClass().addAll("button", "button-primary");

        HBox buttonsBox = new HBox(10, cancelButton, connectButton);
        buttonsBox.setAlignment(Pos.CENTER_RIGHT);
        buttonsBox.setPadding(new Insets(0, 30, 30, 0));

        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: red; -fx-padding: 0 0 0 30px;");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("login-root");
        root.setCenter(form);
        root.setBottom(new VBox(10, statusLabel, buttonsBox));

        BorderPane wrapper = new BorderPane(root);
        wrapper.setPadding(new Insets(40));
        wrapper.getStyleClass().add("root");

        cancelButton.setOnAction(event -> Platform.exit());
        connectButton.setOnAction(event -> connect(
                hostField.getText(),
                usernameField.getText(),
                statusLabel,
                connectButton
        ));
        usernameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                connectButton.fire();
            }
        });

        Scene scene = new Scene(wrapper, 520, 340);
        attachStyles(scene);
        stage.setScene(scene);
        usernameField.requestFocus();

        if (isAutoConnectEnabled()) {
            hostField.setText(launchOptions.getOrDefault("host", DEFAULT_HOST));
            usernameField.setText(launchOptions.getOrDefault("username", ""));
            Platform.runLater(connectButton::fire);
        }
    }

    private void showChatScene(String username) {
        currentUsername = username;
        selectedUser = null;

        messageList = new ListView<>();
        messageList.getStyleClass().add("list-view");
        messageList.setCellFactory(param -> new MessageCell());
        messageList.setPlaceholder(new Label("Select a user to start chatting"));

        userList = new ListView<>(users);
        userList.getStyleClass().add("list-view");
        userList.setCellFactory(param -> new UserListCell());
        userList.setPlaceholder(new Label("Server Chat"));

        messageField = createTextField("", 30);
        messageField.setPromptText("Type a message...");
        messageField.setDisable(true);

        sendButton = new Button("Send");
        sendButton.getStyleClass().addAll("button", "button-send");
        sendButton.setDisable(true);

        attachButton = new Button("+");
        attachButton.getStyleClass().addAll("button", "icon-button");
        attachButton.setDisable(true);

        recordButton = new Button("Rec");
        recordButton.getStyleClass().addAll("button", "button-record");
        recordButton.setDisable(true);

        nicknameLabel = createLabel(username);
        nicknameLabel.getStyleClass().add("nickname-label");

        Button renameButton = new Button("\u270E");
        renameButton.getStyleClass().addAll("button", "icon-button");

        Button logoutButton = new Button("\uD83D\uDEAA");
        logoutButton.getStyleClass().addAll("button", "icon-button");

        selectedChatLabel = createLabel("Select user");
        chatStatusLabel = createLabel("");
        onlineTitleLabel = createLabel("Online (0)");
        onlineTitleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #5a778c; -fx-padding: 15px;");

        HBox headerPanel = new HBox(15, selectedChatLabel);
        headerPanel.getStyleClass().add("chat-header");
        headerPanel.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(selectedChatLabel, Priority.ALWAYS);

        HBox.setHgrow(messageField, Priority.ALWAYS);
        HBox bottomPanel = new HBox(10, attachButton, recordButton, messageField, sendButton);
        bottomPanel.setPadding(new Insets(15));
        bottomPanel.setAlignment(Pos.CENTER);

        HBox profilePanel = new HBox(8, nicknameLabel, renameButton, logoutButton);
        profilePanel.getStyleClass().add("profile-panel");
        profilePanel.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nicknameLabel, Priority.ALWAYS);

        VBox rightPanel = new VBox(profilePanel, onlineTitleLabel, userList);
        rightPanel.getStyleClass().add("chat-right-panel");
        rightPanel.setPrefWidth(220);
        VBox.setVgrow(userList, Priority.ALWAYS);

        BorderPane leftPanel = new BorderPane();
        leftPanel.getStyleClass().add("chat-root");
        leftPanel.setTop(headerPanel);
        leftPanel.setCenter(messageList);
        leftPanel.setBottom(bottomPanel);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setCenter(leftPanel);
        root.setRight(rightPanel);

        sendButton.setOnAction(event -> sendMessage());
        attachButton.setOnAction(event -> sendFile());
        recordButton.setOnAction(event -> toggleRecording());
        renameButton.setOnAction(event -> showRenameDialog());
        logoutButton.setOnAction(event -> disconnect());
        messageField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                sendMessage();
            }
        });
        userList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> selectUser(newVal));

        Scene scene = new Scene(root, 820, 520);
        attachStyles(scene);
        stage.setScene(scene);
        ensureServerChat();
        userList.getSelectionModel().select(SERVER_CHAT);
    }

    private void connect(String host, String username, Label statusLabel, Button connectButton) {
        if (username == null || username.isBlank()) {
            statusLabel.setText("Username cannot be empty");
            return;
        }

        if (host == null || host.isBlank()) {
            statusLabel.setText("Server IP cannot be empty");
            return;
        }

        int port = getConfiguredPort();

        connectButton.setDisable(true);
        statusLabel.setText("Connecting...");
        users.clear();
        onlineUsers.clear();
        currentUsername = username.trim();

        String normalizedHost = host.trim();
        String normalizedUsername = username.trim();

        Thread thread = new Thread(() -> {
            try {
                connection = new ClientConnection(new GuiConnectionListener());
                connection.connect(normalizedHost, port, normalizedUsername);
                Platform.runLater(() -> showChatScene(normalizedUsername));
            } catch (IOException | IllegalArgumentException e) {
                Platform.runLater(() -> {
                    statusLabel.setText(e.getMessage());
                    connectButton.setDisable(false);
                });
            }
        }, "client-connect");
        thread.setDaemon(true);
        thread.start();
    }

    private void selectUser(String username) {
        if (username == null || username.equals(currentUsername)) {
            return;
        }

        selectedUser = username;
        histories.putIfAbsent(selectedUser, FXCollections.observableArrayList());
        messageList.setItems(histories.get(selectedUser));
        messageList.setPlaceholder(new Label("No messages yet with " + selectedUser));
        updateSelectedChatLabel();
        updateInputState();
        markChatAsRead(selectedUser);
        userList.refresh();
    }

    private void sendMessage() {
        if (selectedUser == null) {
            return;
        }

        String text = messageField.getText();

        if (text == null || text.isBlank()) {
            messageField.clear();
            return;
        }

        String trimmedText = text.trim();

        if (SERVER_CHAT.equals(selectedUser)) {
            addMessage(selectedUser, ChatMessage.outgoing("", currentUsername, trimmedText, ReceiptStatus.READ));
            connection.sendBroadcast(trimmedText);
        } else {
            String messageId = UUID.randomUUID().toString();
            addMessage(selectedUser, ChatMessage.outgoing(messageId, currentUsername, trimmedText, ReceiptStatus.SENT));
            connection.sendText(selectedUser, trimmedText, messageId);
        }

        messageField.clear();
    }

    private void sendFile() {
        if (!canSendPrivateAttachment()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose file to send");
        File file = chooser.showOpenDialog(stage);

        if (file == null) {
            return;
        }

        try {
            byte[] data = Files.readAllBytes(file.toPath());
            if (data.length > MAX_ATTACHMENT_BYTES) {
                chatStatusLabel.setText("File is too large. Limit is 2 MB.");
                return;
            }

            String fileName = file.getName();
            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "application/octet-stream";
            }

            String messageId = UUID.randomUUID().toString();
            addMessage(selectedUser, ChatMessage.outgoingAttachment(
                    messageId,
                    currentUsername,
                    MessageKind.FILE,
                    fileName,
                    mimeType,
                    data,
                    ReceiptStatus.SENT
            ));
            connection.sendFile(selectedUser, fileName, mimeType, data, messageId);
            chatStatusLabel.setText("");
        } catch (IOException e) {
            chatStatusLabel.setText("Cannot send file: " + e.getMessage());
        }
    }

    private void toggleRecording() {
        if (recording) {
            stopRecording();
        } else if (canSendPrivateAttachment()) {
            startRecording();
        }
    }

    private boolean canSendPrivateAttachment() {
        if (connection == null || selectedUser == null || SERVER_CHAT.equals(selectedUser)) {
            chatStatusLabel.setText("Files and voice messages are available in private chats.");
            return false;
        }

        if (!onlineUsers.contains(selectedUser)) {
            chatStatusLabel.setText(selectedUser + " is offline");
            return false;
        }

        return true;
    }

    private void startRecording() {
        Thread thread = new Thread(() -> {
            ByteArrayOutputStream rawAudio = new ByteArrayOutputStream();

            try {
                TargetDataLine line = AudioSystem.getTargetDataLine(VOICE_FORMAT);
                recordingLine = line;
                line.open(VOICE_FORMAT);
                line.start();
                recording = true;
                Platform.runLater(() -> {
                    recordButton.setText("Stop");
                    recordButton.getStyleClass().add("button-recording");
                    chatStatusLabel.setText("Recording voice message...");
                });

                byte[] buffer = new byte[4096];
                while (recording) {
                    int read = line.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        rawAudio.write(buffer, 0, read);
                    }
                }

                line.stop();
                line.close();
                byte[] wav = toWav(rawAudio.toByteArray());
                Platform.runLater(() -> sendRecordedAudio(wav));
            } catch (Exception e) {
                recording = false;
                Platform.runLater(() -> {
                    resetRecordButton();
                    chatStatusLabel.setText("Cannot record audio: " + e.getMessage());
                });
            } finally {
                recordingLine = null;
            }
        }, "voice-recorder");

        thread.setDaemon(true);
        thread.start();
    }

    private void stopRecording() {
        recording = false;

        if (recordingLine != null) {
            recordingLine.stop();
            recordingLine.close();
        }
    }

    private byte[] toWav(byte[] rawAudio) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(rawAudio);
             AudioInputStream audioStream = new AudioInputStream(
                     input,
                     VOICE_FORMAT,
                     rawAudio.length / VOICE_FORMAT.getFrameSize()
             );
             ByteArrayOutputStream wavOutput = new ByteArrayOutputStream()) {
            AudioSystem.write(audioStream, javax.sound.sampled.AudioFileFormat.Type.WAVE, wavOutput);
            return wavOutput.toByteArray();
        }
    }

    private void sendRecordedAudio(byte[] wav) {
        resetRecordButton();

        if (wav.length == 0) {
            chatStatusLabel.setText("Voice message is empty.");
            return;
        }

        if (wav.length > MAX_ATTACHMENT_BYTES) {
            chatStatusLabel.setText("Voice message is too long. Limit is 2 MB.");
            return;
        }

        if (!canSendPrivateAttachment()) {
            return;
        }

        String messageId = UUID.randomUUID().toString();
        String fileName = "voice-" + System.currentTimeMillis() + ".wav";
        addMessage(selectedUser, ChatMessage.outgoingAttachment(
                messageId,
                currentUsername,
                MessageKind.AUDIO,
                fileName,
                "audio/wav",
                wav,
                ReceiptStatus.SENT
        ));
        connection.sendAudio(selectedUser, fileName, wav, messageId);
        chatStatusLabel.setText("");
    }

    private void resetRecordButton() {
        if (recordButton != null) {
            recordButton.setText("Rec");
            recordButton.getStyleClass().remove("button-recording");
        }
    }

    private void disconnect() {
        stopRecording();

        if (connection != null) {
            connection.disconnect();
        }

        showConnectScene();
    }

    private void showRenameDialog() {
        if (connection == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(currentUsername);
        dialog.setTitle("Change nickname");
        dialog.setHeaderText("Change your nickname");
        dialog.setContentText("New nickname:");
        dialog.initOwner(stage);

        dialog.showAndWait().ifPresent(newUsername -> {
            if (newUsername == null || newUsername.isBlank()) {
                chatStatusLabel.setText("Username cannot be empty");
                return;
            }

            connection.rename(newUsername.trim());
        });
    }

    private void handleMessage(Message message) {
        if (message.getType() == MessageType.TEXT) {
            rememberUser(message.getFrom());
            ChatMessage chatMessage = ChatMessage.incoming(message.getId(), message.getFrom(), message.getText());
            addMessage(message.getFrom(), chatMessage);
            handleIncomingReadState(message.getFrom(), chatMessage);
        } else if (message.getType() == MessageType.FILE || message.getType() == MessageType.AUDIO) {
            rememberUser(message.getFrom());
            ChatMessage chatMessage = createIncomingAttachment(message);
            addMessage(message.getFrom(), chatMessage);
            handleIncomingReadState(message.getFrom(), chatMessage);
        } else if (message.getType() == MessageType.BROADCAST) {
            if (!message.getFrom().equals(currentUsername)) {
                addMessage(SERVER_CHAT, ChatMessage.incoming("", message.getFrom(), message.getText()));
                incrementUnreadIfNeeded(SERVER_CHAT);
            }
        } else if (message.getType() == MessageType.RECEIPT) {
            updateMessageReceipt(message.getId(), message.getText());
        } else if (message.getType() == MessageType.USER_LIST) {
            updateUserList(message.getText());
        } else if (message.getType() == MessageType.SYSTEM) {
            addMessage(SERVER_CHAT, ChatMessage.incoming("", "SYSTEM", message.getText()));
            updateUsersFromSystemMessage(message.getText());
        } else if (message.getType() == MessageType.ERROR) {
            showServerError(message.getText());
        }
    }

    private ChatMessage createIncomingAttachment(Message message) {
        try {
            byte[] data = Base64.getDecoder().decode(message.getText());
            MessageKind kind = message.getType() == MessageType.AUDIO ? MessageKind.AUDIO : MessageKind.FILE;
            return ChatMessage.incomingAttachment(
                    message.getId(),
                    message.getFrom(),
                    kind,
                    message.getFileName(),
                    message.getMimeType(),
                    data
            );
        } catch (IllegalArgumentException e) {
            return ChatMessage.incoming("", "SYSTEM", "Cannot open attachment from " + message.getFrom());
        }
    }

    private void addMessage(String chatWithUser, ChatMessage message) {
        histories.putIfAbsent(chatWithUser, FXCollections.observableArrayList());
        histories.get(chatWithUser).add(message);

        if (chatWithUser.equals(selectedUser) && messageList != null) {
            messageList.scrollTo(histories.get(selectedUser).size() - 1);
        }
    }

    private void handleIncomingReadState(String chatWithUser, ChatMessage message) {
        if (chatWithUser.equals(selectedUser)) {
            sendReadReceipt(message);
        } else {
            incrementUnreadIfNeeded(chatWithUser);
        }
    }

    private void incrementUnreadIfNeeded(String chatWithUser) {
        if (chatWithUser.equals(selectedUser)) {
            return;
        }

        unreadCounts.put(chatWithUser, unreadCounts.getOrDefault(chatWithUser, 0) + 1);
        userList.refresh();
    }

    private void markChatAsRead(String chatWithUser) {
        unreadCounts.remove(chatWithUser);

        ObservableList<ChatMessage> messages = histories.get(chatWithUser);
        if (messages != null) {
            for (ChatMessage message : messages) {
                sendReadReceipt(message);
            }
        }

        userList.refresh();
    }

    private void sendReadReceipt(ChatMessage message) {
        if (message.mine || message.id.isBlank() || message.readReceiptSent || connection == null) {
            return;
        }

        message.readReceiptSent = true;
        connection.sendReceipt(message.sender, message.id, ReceiptStatus.READ.name());
    }

    private void updateMessageReceipt(String messageId, String statusText) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }

        ReceiptStatus status = ReceiptStatus.fromText(statusText);

        for (ObservableList<ChatMessage> messages : histories.values()) {
            for (ChatMessage message : messages) {
                if (message.mine && messageId.equals(message.id)) {
                    message.status = message.status.max(status);
                    if (messageList != null) {
                        messageList.refresh();
                    }
                    return;
                }
            }
        }
    }

    private void updateUserList(String text) {
        onlineUsers.clear();

        if (text != null && !text.isBlank()) {
            Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(name -> !name.isBlank())
                    .filter(name -> !name.equals(currentUsername))
                    .forEach(name -> {
                        knownUsers.add(name);
                        onlineUsers.add(name);
                        histories.putIfAbsent(name, FXCollections.observableArrayList());
                    });
        }

        rebuildUserList();
        updateSelectedChatLabel();
        updateInputState();
        updateOnlineTitle();
    }

    private void updateUsersFromSystemMessage(String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        if (text.endsWith(" joined the chat")) {
            String username = text.substring(0, text.indexOf(" joined the chat")).trim();
            if (!username.equals(currentUsername)) {
                rememberUser(username);
                onlineUsers.add(username);
                rebuildUserList();
                updateSelectedChatLabel();
                updateInputState();
            }
        } else if (text.endsWith(" left the chat")) {
            String username = text.substring(0, text.indexOf(" left the chat")).trim();
            onlineUsers.remove(username);
            rememberUser(username);
            rebuildUserList();

            if (username.equals(selectedUser)) {
                selectedChatLabel.setText(username + " is offline");
            }

            updateInputState();
        } else if (text.contains(" changed nickname to ")) {
            handleRenameSystemMessage(text);
        }
    }

    private void handleRenameSystemMessage(String text) {
        int separator = text.indexOf(" changed nickname to ");

        if (separator <= 0) {
            return;
        }

        String oldUsername = text.substring(0, separator).trim();
        String newUsername = text.substring(separator + " changed nickname to ".length()).trim();

        if (oldUsername.isBlank() || newUsername.isBlank()) {
            return;
        }

        if (oldUsername.equals(currentUsername)) {
            currentUsername = newUsername;
            connection.setUsername(newUsername);
            chatStatusLabel.setText("Connected as " + newUsername);
            nicknameLabel.setText(newUsername);
        } else {
            if (knownUsers.remove(oldUsername)) {
                knownUsers.add(newUsername);
            }

            if (onlineUsers.remove(oldUsername)) {
                onlineUsers.add(newUsername);
            }

            ObservableList<ChatMessage> oldHistory = histories.remove(oldUsername);
            if (oldHistory != null) {
                histories.put(newUsername, oldHistory);
            }

            if (oldUsername.equals(selectedUser)) {
                selectedUser = newUsername;
                messageList.setItems(histories.get(newUsername));
            }
        }

        rebuildUserList();
        updateSelectedChatLabel();
        updateInputState();
        updateOnlineTitle();
    }

    private void showServerError(String text) {
        String errorText = text == null || text.isBlank() ? "Unknown server error" : text;

        if (chatStatusLabel != null) {
            chatStatusLabel.setText(errorText);
        }

        if (selectedUser != null) {
            addMessage(selectedUser, ChatMessage.incoming("", "SYSTEM", errorText));
        }
    }

    private void updateOnlineTitle() {
        if (onlineTitleLabel != null) {
            onlineTitleLabel.setText("Chats (" + users.size() + ")");
        }
    }

    private void ensureServerChat() {
        if (!users.contains(SERVER_CHAT)) {
            users.add(0, SERVER_CHAT);
        }

        histories.putIfAbsent(SERVER_CHAT, FXCollections.observableArrayList());
    }

    private void rememberUser(String username) {
        if (username == null || username.isBlank() || username.equals(currentUsername) || SERVER_CHAT.equals(username)) {
            return;
        }

        knownUsers.add(username);
        histories.putIfAbsent(username, FXCollections.observableArrayList());
    }

    private void rebuildUserList() {
        String selected = selectedUser;
        users.clear();
        ensureServerChat();

        knownUsers.stream()
                .filter(name -> !name.equals(currentUsername))
                .sorted((first, second) -> {
                    boolean firstOnline = onlineUsers.contains(first);
                    boolean secondOnline = onlineUsers.contains(second);

                    if (firstOnline != secondOnline) {
                        return firstOnline ? -1 : 1;
                    }

                    return first.compareToIgnoreCase(second);
                })
                .forEach(users::add);

        if (userList != null && selected != null && users.contains(selected)) {
            userList.getSelectionModel().select(selected);
        }
    }

    private void updateInputState() {
        if (messageField == null || sendButton == null) {
            return;
        }

        boolean canWrite = selectedUser != null
                && (SERVER_CHAT.equals(selectedUser) || onlineUsers.contains(selectedUser));
        boolean canAttach = selectedUser != null
                && !SERVER_CHAT.equals(selectedUser)
                && onlineUsers.contains(selectedUser);

        messageField.setDisable(!canWrite);
        sendButton.setDisable(!canWrite);
        attachButton.setDisable(!canAttach);
        recordButton.setDisable(!canAttach);

        if (selectedUser == null) {
            messageField.setPromptText("Select a chat...");
        } else if (SERVER_CHAT.equals(selectedUser)) {
            messageField.setPromptText("Type a message...");
        } else if (onlineUsers.contains(selectedUser)) {
            messageField.setPromptText("Type a message...");
        } else {
            messageField.setPromptText(selectedUser + " is offline");
        }
    }

    private void updateSelectedChatLabel() {
        if (selectedChatLabel == null || selectedUser == null) {
            return;
        }

        if (SERVER_CHAT.equals(selectedUser)) {
            selectedChatLabel.setText(SERVER_CHAT);
        } else if (onlineUsers.contains(selectedUser)) {
            selectedChatLabel.setText("Chat with " + selectedUser);
        } else {
            selectedChatLabel.setText(selectedUser + " is offline");
        }
    }

    private String getUserStatus(String username) {
        if (SERVER_CHAT.equals(username)) {
            return "Group chat";
        }

        return onlineUsers.contains(username) ? "Online" : "Offline";
    }

    private Color getUserStatusColor(String username) {
        if (SERVER_CHAT.equals(username)) {
            return Color.web("#5a9ecf");
        }

        return onlineUsers.contains(username) ? Color.web("#2fb344") : Color.web("#d94848");
    }

    private String getReceiptText(ReceiptStatus status) {
        if (status == ReceiptStatus.SENT) {
            return "\u2713";
        }

        return "\u2713\u2713";
    }

    private TextField createTextField(String text, int prefColumnCount) {
        TextField textField = new TextField(text);
        textField.getStyleClass().add("text-field");
        textField.setPrefColumnCount(prefColumnCount);
        return textField;
    }

    private Label createLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("label");
        return label;
    }

    private void attachStyles(Scene scene) {
        if (getClass().getResource("/client_style.css") != null) {
            scene.getStylesheets().add(getClass().getResource("/client_style.css").toExternalForm());
        }
    }

    private boolean isAutoConnectEnabled() {
        return Boolean.parseBoolean(launchOptions.getOrDefault("auto-connect", "false"));
    }

    private int getConfiguredPort() {
        String portText = launchOptions.getOrDefault("port", String.valueOf(DEFAULT_PORT));

        try {
            return Integer.parseInt(portText.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    private void saveAttachment(ChatMessage message) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save attachment");
        chooser.setInitialFileName(message.fileName);
        File target = chooser.showSaveDialog(stage);

        if (target == null) {
            return;
        }

        try {
            Files.write(target.toPath(), message.data);
            chatStatusLabel.setText("Saved: " + target.getName());
        } catch (IOException e) {
            chatStatusLabel.setText("Cannot save file: " + e.getMessage());
        }
    }

    private void playAudio(ChatMessage message) {
        Thread thread = new Thread(() -> {
            try (AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(message.data))) {
                Clip clip = AudioSystem.getClip();
                clip.open(stream);
                clip.start();
            } catch (Exception e) {
                Platform.runLater(() -> chatStatusLabel.setText("Cannot play audio: " + e.getMessage()));
            }
        }, "voice-player");

        thread.setDaemon(true);
        thread.start();
    }

    private enum ReceiptStatus {
        SENT,
        DELIVERED,
        READ;

        private ReceiptStatus max(ReceiptStatus other) {
            return ordinal() >= other.ordinal() ? this : other;
        }

        private static ReceiptStatus fromText(String text) {
            try {
                return ReceiptStatus.valueOf(text);
            } catch (IllegalArgumentException | NullPointerException e) {
                return SENT;
            }
        }
    }

    private enum MessageKind {
        TEXT,
        FILE,
        AUDIO
    }

    private static class ChatMessage {
        private final String id;
        private final String sender;
        private final String text;
        private final MessageKind kind;
        private final String fileName;
        private final String mimeType;
        private final byte[] data;
        private final boolean mine;
        private ReceiptStatus status;
        private boolean readReceiptSent;

        private ChatMessage(
                String id,
                String sender,
                String text,
                MessageKind kind,
                String fileName,
                String mimeType,
                byte[] data,
                boolean mine,
                ReceiptStatus status
        ) {
            this.id = id == null ? "" : id;
            this.sender = sender == null || sender.isBlank() ? "Unknown" : sender;
            this.text = text == null ? "" : text;
            this.kind = kind == null ? MessageKind.TEXT : kind;
            this.fileName = fileName == null || fileName.isBlank() ? "attachment" : fileName;
            this.mimeType = mimeType == null ? "" : mimeType;
            this.data = data == null ? new byte[0] : data;
            this.mine = mine;
            this.status = status;
        }

        private static ChatMessage outgoing(String id, String sender, String text, ReceiptStatus status) {
            return new ChatMessage(id, sender, text, MessageKind.TEXT, "", "", new byte[0], true, status);
        }

        private static ChatMessage incoming(String id, String sender, String text) {
            return new ChatMessage(id, sender, text, MessageKind.TEXT, "", "", new byte[0], false, ReceiptStatus.READ);
        }

        private static ChatMessage outgoingAttachment(
                String id,
                String sender,
                MessageKind kind,
                String fileName,
                String mimeType,
                byte[] data,
                ReceiptStatus status
        ) {
            return new ChatMessage(id, sender, "", kind, fileName, mimeType, data, true, status);
        }

        private static ChatMessage incomingAttachment(
                String id,
                String sender,
                MessageKind kind,
                String fileName,
                String mimeType,
                byte[] data
        ) {
            return new ChatMessage(id, sender, "", kind, fileName, mimeType, data, false, ReceiptStatus.READ);
        }
    }

    private class MessageCell extends ListCell<ChatMessage> {
        @Override
        protected void updateItem(ChatMessage message, boolean empty) {
            super.updateItem(message, empty);

            if (empty || message == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            VBox box = new VBox(4);
            Label nameLabel = new Label(message.sender);
            nameLabel.getStyleClass().add("message-author");

            Node content = createMessageContent(message);

            if (message.mine) {
                box.setAlignment(Pos.TOP_RIGHT);
                Label receiptLabel = new Label(getReceiptText(message.status));
                receiptLabel.getStyleClass().add(message.status == ReceiptStatus.READ ? "message-receipt-read" : "message-receipt");
                box.getChildren().addAll(nameLabel, content, receiptLabel);
            } else if ("SYSTEM".equals(message.sender)) {
                Label textLabel = new Label(message.text);
                textLabel.setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
                box.setAlignment(Pos.CENTER);
                box.getChildren().add(textLabel);
            } else {
                box.setAlignment(Pos.TOP_LEFT);
                box.getChildren().addAll(nameLabel, content);
            }

            setText(null);
            setGraphic(box);
        }

        private Node createMessageContent(ChatMessage message) {
            if (message.kind == MessageKind.TEXT) {
                Label textLabel = new Label(message.text);
                textLabel.setWrapText(true);
                textLabel.getStyleClass().add(message.mine ? "message-bubble-me" : "message-bubble-other");
                return textLabel;
            }

            VBox bubble = new VBox(8);
            bubble.getStyleClass().add(message.mine ? "message-bubble-me" : "message-bubble-other");

            Label titleLabel = new Label(message.kind == MessageKind.AUDIO ? "Voice message" : "File");
            titleLabel.getStyleClass().add("attachment-title");

            Label fileLabel = new Label(message.fileName);
            fileLabel.getStyleClass().add("attachment-name");
            fileLabel.setWrapText(true);

            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_LEFT);

            if (message.kind == MessageKind.AUDIO) {
                Button playButton = new Button("Play");
                playButton.getStyleClass().addAll("button", "attachment-button");
                playButton.setOnAction(event -> playAudio(message));
                actions.getChildren().add(playButton);
            }

            Button saveButton = new Button("Save");
            saveButton.getStyleClass().addAll("button", "attachment-button");
            saveButton.setOnAction(event -> saveAttachment(message));
            actions.getChildren().add(saveButton);

            bubble.getChildren().addAll(titleLabel, fileLabel, actions);
            return bubble;
        }
    }

    private class UserListCell extends ListCell<String> {
        @Override
        protected void updateItem(String username, boolean empty) {
            super.updateItem(username, empty);

            getStyleClass().remove("user-list-item-active");

            if (empty || username == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            VBox textBox = new VBox(2);
            textBox.getStyleClass().add("user-list-item");

            Label nameLabel = new Label(username);
            nameLabel.getStyleClass().add("user-list-name");

            Label statusLabel = new Label(getUserStatus(username));
            statusLabel.getStyleClass().add("user-list-status");

            int unread = unreadCounts.getOrDefault(username, 0);

            HBox nameRow = new HBox(8, nameLabel);
            if (unread > 0) {
                Label unreadLabel = new Label(String.valueOf(unread));
                unreadLabel.getStyleClass().add("unread-badge");
                nameRow.getChildren().add(unreadLabel);
            }
            nameRow.setAlignment(Pos.CENTER_LEFT);
            textBox.getChildren().addAll(nameRow, statusLabel);

            Circle statusDot = new Circle(5);
            statusDot.setFill(getUserStatusColor(username));

            HBox row = new HBox(8, statusDot, textBox);
            row.setAlignment(Pos.CENTER_LEFT);

            setText(null);
            setGraphic(row);
        }
    }

    private class GuiConnectionListener implements ClientConnection.ClientConnectionListener {
        @Override
        public void onMessage(Message message) {
            Platform.runLater(() -> handleMessage(message));
        }

        @Override
        public void onDisconnected() {
            Platform.runLater(() -> {
                if (chatStatusLabel != null) {
                    chatStatusLabel.setText("Disconnected");
                }

                if (messageField != null) {
                    messageField.setDisable(true);
                }

                if (sendButton != null) {
                    sendButton.setDisable(true);
                }

                if (attachButton != null) {
                    attachButton.setDisable(true);
                }

                if (recordButton != null) {
                    recordButton.setDisable(true);
                }
            });
        }

        @Override
        public void onError(String error) {
            Platform.runLater(() -> showServerError(error));
        }
    }
}
