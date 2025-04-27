package sk.vava.royalmate.data;

import sk.vava.royalmate.model.Account; // Need Account for mapping username later maybe
import sk.vava.royalmate.model.ChatMessage;

import java.sql.*;
import java.util.ArrayList; // Import ArrayList
import java.util.List; // Import List
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatMessageDAO {

    private static final Logger LOGGER = Logger.getLogger(ChatMessageDAO.class.getName());
    private static final String PINNED_MESSAGE_FUTURE_DATE = "2030-01-19 03:14:07"; // Marker date
    private static final String TABLE_NAME = "chat_messages"; // Underscore version if your table uses it
    private static final String ACCOUNT_TABLE_NAME = "accounts";

    // Ensure column names here match your DB schema EXACTLY
    private static final String FIND_PINNED_SQL = "SELECT cm.*, a.username as sender_username FROM " + TABLE_NAME + " cm JOIN " + ACCOUNT_TABLE_NAME + " a ON cm.sender_id = a.id WHERE cm.sent_at >= ? ORDER BY cm.sent_at DESC LIMIT 1";
    private static final String INSERT_PINNED_SQL = "INSERT INTO " + TABLE_NAME + " (sender_id, message_text, sent_at) VALUES (?, ?, ?)";
    private static final String UPDATE_PINNED_SQL = "UPDATE " + TABLE_NAME + " SET message_text = ?, sender_id = ? WHERE id = ?";

    private static final String FIND_RECENT_SQL = "SELECT cm.*, a.username as sender_username FROM " + TABLE_NAME + " cm JOIN " + ACCOUNT_TABLE_NAME + " a ON cm.sender_id = a.id WHERE cm.sent_at < ? ORDER BY cm.sent_at DESC LIMIT ?";
    // --------------------------------------------------------------------
    private static final String INSERT_REGULAR_SQL = "INSERT INTO " + TABLE_NAME + " (sender_id, message_text, sent_at) VALUES (?, ?, CURRENT_TIMESTAMP)";


    // --- Existing methods (findPinnedMessage, updatePinnedMessage, insertPinnedMessage) ---
    public Optional<ChatMessage> findPinnedMessage() {
        LOGGER.fine("Attempting to find pinned chat message.");
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_PINNED_SQL)) {

            pstmt.setTimestamp(1, Timestamp.valueOf(PINNED_MESSAGE_FUTURE_DATE));

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToChatMessage(rs)); // Use updated mapper
                } else {
                    LOGGER.fine("No pinned chat message found.");
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding pinned chat message.", e);
            return Optional.empty();
        }
    }
    /**
     * Updates the text and sender of an existing pinned message.
     *
     * @param messageId The ID of the pinned message to update.
     * @param text      The new message text.
     * @param adminId   The ID of the admin performing the update.
     * @return true if successful, false otherwise.
     */
    public boolean updatePinnedMessage(long messageId, String text, int adminId) {
        LOGGER.info("Attempting to update pinned message ID: " + messageId);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_PINNED_SQL)) {

            pstmt.setString(1, text);
            pstmt.setInt(2, adminId); // Update sender to current admin
            pstmt.setLong(3, messageId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                LOGGER.info("Successfully updated pinned message ID: " + messageId);
                return true;
            } else {
                LOGGER.warning("Failed to update pinned message, ID not found?: " + messageId);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating pinned message ID: " + messageId, e);
            return false;
        }
    }

    /**
     * Inserts a new pinned message (or replaces any existing one by virtue of the future date).
     * Consider deleting old pinned messages first if needed.
     *
     * @param text    The message text.
     * @param adminId The ID of the admin posting the message.
     * @return true if successful, false otherwise.
     */
    public boolean insertPinnedMessage(String text, int adminId) {
        LOGGER.info("Attempting to insert new pinned message by admin ID: " + adminId);
        // Optional: Delete any existing message with the future date first to ensure only one
        // deleteExistingPinnedMessage();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT_PINNED_SQL)) {

            pstmt.setInt(1, adminId);
            pstmt.setString(2, text);
            pstmt.setTimestamp(3, Timestamp.valueOf(PINNED_MESSAGE_FUTURE_DATE));

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                LOGGER.info("Successfully inserted new pinned message.");
                return true;
            } else {
                LOGGER.warning("Failed to insert new pinned message, no rows affected.");
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error inserting new pinned message.", e);
            return false;
        }
    }


    // --- NEW METHODS ---
    /**
     * Fetches the most recent regular chat messages (excluding the pinned one).
     * Includes the sender's username.
     *
     * @param limit The maximum number of messages to retrieve.
     * @return A List of ChatMessage objects, ordered most recent first. Empty list on error.
     */
    public List<ChatMessage> findRecentMessages(int limit) {
        LOGGER.fine("Finding last " + limit + " regular chat messages.");
        List<ChatMessage> messages = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_RECENT_SQL)) {

            pstmt.setTimestamp(1, Timestamp.valueOf(PINNED_MESSAGE_FUTURE_DATE));
            pstmt.setInt(2, limit); // Correctly applying limit

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToChatMessage(rs));
                }
            }
            LOGGER.fine("Found " + messages.size() + " recent messages.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding recent chat messages", e);
        }
        // Return newest first as fetched from DB (DESC order)
        return messages;
    }

    /**
     * Inserts a new regular chat message with the current timestamp.
     *
     * @param senderId    The ID of the user sending the message.
     * @param messageText The text content of the message.
     * @return true if successful, false otherwise.
     */
    public boolean insertRegularMessage(int senderId, String messageText) {
        LOGGER.fine("Attempting to insert regular chat message for sender ID: " + senderId);
        if (messageText == null || messageText.trim().isEmpty()) {
            LOGGER.warning("Attempted to insert empty chat message.");
            return false;
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT_REGULAR_SQL)) {

            pstmt.setInt(1, senderId);
            pstmt.setString(2, messageText.trim()); // Trim whitespace

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                LOGGER.fine("Successfully inserted regular chat message.");
                return true;
            } else {
                LOGGER.warning("Failed to insert regular chat message, no rows affected.");
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error inserting regular chat message for sender ID: " + senderId, e);
            return false;
        }
    }
    // --- END NEW METHODS ---


    /** Helper method to map ResultSet to ChatMessage object (MODIFIED) */
    private ChatMessage mapResultSetToChatMessage(ResultSet rs) throws SQLException {
        ChatMessage msg = ChatMessage.builder()
                .id(rs.getLong("id"))
                .senderId(rs.getInt("sender_id")) // Use underscore if needed
                .messageText(rs.getString("message_text")) // Use underscore if needed
                .sentAt(rs.getTimestamp("sent_at")) // Use underscore if needed
                .build();

        // Check if the joined username column exists and add it
        if (hasColumn(rs, "sender_username")) {
            msg.setSenderUsername(rs.getString("sender_username"));
        } else {
            msg.setSenderUsername("Unknown"); // Fallback if join fails or column missing
        }

        return msg;
    }

    /** Utility to check if a column exists in the ResultSet metadata */
    private boolean hasColumn(ResultSet rs, String columnName) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int columns = rsmd.getColumnCount();
        for (int x = 1; x <= columns; x++) {
            if (columnName.equalsIgnoreCase(rsmd.getColumnLabel(x))) {
                return true;
            }
        }
        return false;
    }
}