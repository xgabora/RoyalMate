package sk.vava.royalmate.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent; // Keep for handleSendMessage if needed via Enter key
import javafx.fxml.FXML;
import javafx.geometry.Insets; // Keep for padding if needed elsewhere
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView; // Keep for pinned icon
import javafx.scene.input.MouseEvent; // Keep if any mouse events remain
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight; // Keep for styling
import javafx.util.Duration;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.model.ChatMessage;
import sk.vava.royalmate.service.ChatService;
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects; // Keep for resource loading
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ChatController {

    private static final Logger LOGGER = Logger.getLogger(ChatController.class.getName());
    // --- UPDATED REFRESH INTERVAL ---
    private static final Duration REFRESH_INTERVAL = Duration.seconds(2); // Refresh every 2s
    // ----------------------------------
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MESSAGE_LIMIT = 100;

    @FXML private BorderPane rootPane;
    @FXML private TextField messageInputField;
    @FXML private Button sendButton;
    @FXML private HBox pinnedMessageBar;
    @FXML private Label pinnedMessageLabel;
    @FXML private ScrollPane scrollPane;
    @FXML private VBox messageContainer;
    @FXML private VBox mainChatContent;

    private final ChatService chatService;
    private Timeline refreshTimeline;
    private boolean initialLoadComplete = false;
    private Account currentUser;

    public ChatController() {
        this.chatService = new ChatService();
    }

    @FXML
    public void initialize() {
        currentUser = SessionManager.getCurrentAccount();
        if (currentUser == null) {
            LOGGER.severe("Chat loaded without logged-in user!");
            messageInputField.setDisable(true);
            sendButton.setDisable(true);
            return;
        }

        pinnedMessageBar.setVisible(false);
        pinnedMessageBar.setManaged(false);

        // No scroll listener needed for top-loading

        messageInputField.setOnAction(event -> handleSendMessage());

        refreshChat(true); // True indicates initial load
        startPolling();

        LOGGER.info("ChatController initialized.");
    }

    private void startPolling() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        refreshTimeline = new Timeline(new KeyFrame(REFRESH_INTERVAL, event -> refreshChat(false)));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
        LOGGER.info("Chat polling started (interval: " + REFRESH_INTERVAL.toSeconds() + "s).");
    }

    public void stopPolling() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            LOGGER.info("Chat polling stopped.");
        }
    }

    private void refreshChat(boolean isInitial) {
        LOGGER.fine("Refreshing chat data...");
        Task<Void> refreshTask = new Task<>() {
            Optional<ChatMessage> pinnedMsgOpt = Optional.empty();
            List<ChatMessage> recentMsgs = List.of();

            @Override
            protected Void call() throws Exception {
                pinnedMsgOpt = chatService.getPinnedMessage();
                recentMsgs = chatService.getRecentMessages(); // DAO gets newest first
                return null;
            }

            @Override
            protected void succeeded() {
                updatePinnedMessageUI(pinnedMsgOpt);
                updateRecentMessagesUI(recentMsgs, isInitial); // Use updated method
                LOGGER.fine("Chat refresh UI update complete.");
            }

            @Override
            protected void failed() {
                LOGGER.log(Level.SEVERE, "Failed to refresh chat data.", getException());
            }
        };
        new Thread(refreshTask).start();
    }

    private void updatePinnedMessageUI(Optional<ChatMessage> pinnedOpt) {
        pinnedMessageBar.setVisible(pinnedOpt.isPresent());
        pinnedMessageBar.setManaged(pinnedOpt.isPresent());
        pinnedOpt.ifPresent(msg -> pinnedMessageLabel.setText(msg.getMessageText()));
    }

    /** Updates UI for recent messages (adds newest to TOP) */
    private void updateRecentMessagesUI(List<ChatMessage> messages, boolean isInitial) {
        messageContainer.getChildren().clear();

        if (messages.isEmpty() && isInitial) {
            LOGGER.info("No recent chat messages found.");
        } else {
            // --- ADD MESSAGES NEWEST FIRST ---
            messages.forEach(msg -> {
                Node messageNode = createMessageNode(msg);
                // Add to the beginning of the VBox children list
                messageContainer.getChildren().add(0, messageNode);
            });
            // ---------------------------------
        }

        if (isInitial || !messages.isEmpty()) {
            // --- SCROLL TO TOP ---
            // Scroll to TOP after adding messages if newest are at the top
            Platform.runLater(this::scrollToTop);
            // ---------------------
        }
        if (isInitial) {
            initialLoadComplete = true;
        }
    }

    /** Creates the UI Node for a single chat message */
    private Node createMessageNode(ChatMessage message) {
        HBox messageRow = new HBox();
        messageRow.setMaxWidth(Double.MAX_VALUE);

        VBox messageBubble = new VBox(3);
        boolean isCurrentUser = message.getSenderId() == currentUser.getId();
        messageBubble.getStyleClass().add(isCurrentUser ? "chat-bubble-user-new" : "chat-bubble-other-new");
        HBox.setHgrow(messageBubble, Priority.ALWAYS);

        Label senderLabel = new Label(message.getSenderUsername() != null ? message.getSenderUsername() : "Unknown");
        senderLabel.setUnderline(true);
        senderLabel.getStyleClass().add("chat-sender-label-new");
        senderLabel.setTextFill(isCurrentUser ? Color.DARKSLATEGRAY : Color.LIGHTSKYBLUE);

        Label messageTextLabel = new Label(message.getMessageText());
        messageTextLabel.setWrapText(true);
        messageTextLabel.getStyleClass().add("chat-message-label-new");

        Label timeLabel = new Label();
        if (message.getSentAt() != null) {
            try {
                // 1. Convert Timestamp to LocalDateTime in System Default Zone
                LocalDateTime originalLocalTime = message.getSentAt().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();

                // 2. Subtract 2 hours directly from the LocalDateTime
                LocalDateTime adjustedLocalTime = originalLocalTime.minusHours(2);

                // 3. Format the adjusted time
                timeLabel.setText(adjustedLocalTime.format(TIME_FORMATTER));

            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error adjusting or formatting timestamp: " + message.getSentAt(), e);
                timeLabel.setText("--:--"); // Fallback on error
            }
        } else {
            timeLabel.setText("--:--");
        }
        timeLabel.getStyleClass().add("chat-time-label-new");

        messageBubble.getChildren().addAll(senderLabel, messageTextLabel, timeLabel);

        messageRow.getChildren().add(messageBubble);
        if (isCurrentUser) {
            messageRow.setAlignment(Pos.CENTER_RIGHT);
            messageBubble.setAlignment(Pos.CENTER_RIGHT);
        } else {
            messageRow.setAlignment(Pos.CENTER_LEFT);
            messageBubble.setAlignment(Pos.CENTER_LEFT);
        }
        return messageRow;
    }


    @FXML
    void handleSendMessage() {
        String text = messageInputField.getText().trim();
        if (text.isEmpty() || !SessionManager.isLoggedIn()) {
            return; // Don't send empty or if not logged in
        }

        sendButton.setDisable(true);
        messageInputField.setDisable(true);

        // Send in background task
        Task<Boolean> sendTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return chatService.sendMessage(text);
            }
        };

        sendTask.setOnSucceeded(e -> {
            boolean success = sendTask.getValue();
            if (success) {
                LOGGER.fine("Message sent successfully.");
                messageInputField.clear();
                refreshChat(false); // Refresh immediately
            } else {
                LOGGER.warning("Failed to send message (backend).");
                showSendErrorAlert();
            }
            reenableInput();
        });

        sendTask.setOnFailed(e -> {
            LOGGER.log(Level.SEVERE, "Failed to send message (exception).", sendTask.getException());
            showSendErrorAlert();
            reenableInput();
        });

        new Thread(sendTask).start();
    }

    private void reenableInput() {
        Platform.runLater(() -> {
            messageInputField.setDisable(false);
            sendButton.setDisable(false);
            messageInputField.requestFocus();
        });
    }

    private void showSendErrorAlert() {
        Platform.runLater(() -> {
            Alert errorAlert = new Alert(Alert.AlertType.ERROR, LocaleManager.getString("chat.message.send.error"));
            errorAlert.showAndWait();
        });
    }

    /** Scrolls the chat ScrollPane to the bottom */
    private void scrollToBottom() {
        // Needs to run after layout has potentially changed
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    /** Scrolls the chat ScrollPane to the top */
    private void scrollToTop() {
        Platform.runLater(() -> scrollPane.setVvalue(0.0));
    }
}