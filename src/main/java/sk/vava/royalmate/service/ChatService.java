package sk.vava.royalmate.service;

import sk.vava.royalmate.data.ChatMessageDAO;
import sk.vava.royalmate.model.ChatMessage;
import sk.vava.royalmate.util.SessionManager;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatService {

    private static final Logger LOGGER = Logger.getLogger(ChatService.class.getName());
    private static final int RECENT_MESSAGES_LIMIT = 100;

    private final ChatMessageDAO chatMessageDAO;

    public ChatService() {
        this.chatMessageDAO = new ChatMessageDAO();
    }

    public ChatService(ChatMessageDAO chatMessageDAO) {
        this.chatMessageDAO = chatMessageDAO;
    }

    public Optional<ChatMessage> getPinnedMessage() {
        return chatMessageDAO.findPinnedMessage();
    }

    public List<ChatMessage> getRecentMessages() {
        List<ChatMessage> messages = chatMessageDAO.findRecentMessages(RECENT_MESSAGES_LIMIT);
        Collections.reverse(messages);
        return messages;
    }

    public boolean sendMessage(String messageText) {
        if (!SessionManager.isLoggedIn()) {
            LOGGER.warning("Attempted to send chat message while not logged in.");
            return false;
        }
        if (messageText == null || messageText.trim().isEmpty()) {
            LOGGER.warning("Attempted to send empty chat message.");
            return false;
        }

        int senderId = SessionManager.getCurrentAccount().getId();
        LOGGER.fine("Sending message from user ID: " + senderId);
        return chatMessageDAO.insertRegularMessage(senderId, messageText);
    }
}