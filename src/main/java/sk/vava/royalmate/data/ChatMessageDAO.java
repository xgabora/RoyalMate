package sk.vava.royalmate.data;

import sk.vava.royalmate.model.ChatMessage;

import java.sql.*;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatMessageDAO {

    private static final Logger LOGGER = Logger.getLogger(ChatMessageDAO.class.getName());
    private static final String PINNED_MESSAGE_FUTURE_DATE = "2030-01-19 03:14:07"; // Marker date

    // SQL Queries
    private static final String FIND_PINNED_SQL = "SELECT * FROM chat_messages WHERE sent_at >= ? ORDER BY sent_at DESC LIMIT 1";
    private static final String INSERT_PINNED_SQL = "INSERT INTO chat_messages (sender_id, message_text, sent_at) VALUES (?, ?, ?)";
    private static final String UPDATE_PINNED_SQL = "UPDATE chat_messages SET message_text = ?, sender_id = ? WHERE id = ?";
    // Add FIND_ALL_RECENT_SQL, INSERT_REGULAR_SQL etc. as needed for general chat

    /**
     * Finds the currently pinned message (marked by a future date).
     *
     * @return Optional containing the ChatMessage if a pinned one exists, empty otherwise.
     */
    public Optional<ChatMessage> findPinnedMessage() {
        LOGGER.fine("Attempting to find pinned chat message.");
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_PINNED_SQL)) {

            pstmt.setTimestamp(1, Timestamp.valueOf(PINNED_MESSAGE_FUTURE_DATE));

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToChatMessage(rs));
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

    // --- Helper method to map ResultSet ---
    private ChatMessage mapResultSetToChatMessage(ResultSet rs) throws SQLException {
        return ChatMessage.builder()
                .id(rs.getLong("id"))
                .senderId(rs.getInt("sender_id"))
                .messageText(rs.getString("message_text"))
                .sentAt(rs.getTimestamp("sent_at"))
                .build();
    }

    // Add methods for fetching recent regular messages, inserting regular messages etc.
}