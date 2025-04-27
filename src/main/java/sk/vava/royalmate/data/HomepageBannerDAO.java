package sk.vava.royalmate.data;

import sk.vava.royalmate.model.HomepageBanner;

import java.io.ByteArrayInputStream;
import java.sql.*;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HomepageBannerDAO {

    private static final Logger LOGGER = Logger.getLogger(HomepageBannerDAO.class.getName());

    // Assuming only one banner with a fixed ID (e.g., 1) for simplicity now
    private static final int MAIN_BANNER_ID = 1;
    private static final int MAIN_BANNER_POSITION = 1; // Fixed position for now

    private static final String FIND_BANNER_SQL = "SELECT * FROM homepage_banners WHERE id = ?";
    private static final String UPSERT_BANNER_SQL =
            "INSERT INTO homepage_banners (id, name, image_data, position, is_active, uploaded_by_admin_id, uploaded_at) " +
                    "VALUES (?, ?, ?, ?, true, ?, CURRENT_TIMESTAMP) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "name = VALUES(name), image_data = VALUES(image_data), uploaded_by_admin_id = VALUES(uploaded_by_admin_id), uploaded_at = VALUES(uploaded_at)";

    /**
     * Finds the main homepage banner (assuming ID = 1).
     *
     * @return Optional containing the HomepageBanner if found, empty otherwise.
     */
    public Optional<HomepageBanner> findMainBanner() {
        LOGGER.fine("Attempting to find main homepage banner (ID: " + MAIN_BANNER_ID + ")");
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_BANNER_SQL)) {

            pstmt.setInt(1, MAIN_BANNER_ID);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToHomepageBanner(rs));
                } else {
                    LOGGER.fine("Main homepage banner (ID: " + MAIN_BANNER_ID + ") not found.");
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding main homepage banner (ID: " + MAIN_BANNER_ID + ")", e);
            return Optional.empty();
        }
    }

    /**
     * Saves or updates the main banner (ID = 1).
     *
     * @param name       The name/description of the banner.
     * @param imageData  The image data as a byte array (BLOB).
     * @param adminId    The ID of the admin uploading/updating.
     * @return true if the operation was successful, false otherwise.
     */
    public boolean saveOrUpdateMainBanner(String name, byte[] imageData, int adminId) {
        LOGGER.info("Attempting to save/update main banner (ID: " + MAIN_BANNER_ID + ") by admin ID: " + adminId);
        if (imageData == null || imageData.length == 0) {
            LOGGER.warning("Attempted to save/update banner with empty image data.");
            return false;
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPSERT_BANNER_SQL)) {

            pstmt.setInt(1, MAIN_BANNER_ID); // Fixed ID
            pstmt.setString(2, name);
            // Set BLOB using InputStream
            try (ByteArrayInputStream bis = new ByteArrayInputStream(imageData)) {
                pstmt.setBinaryStream(3, bis, imageData.length);
            }
            pstmt.setInt(4, MAIN_BANNER_POSITION); // Fixed position
            pstmt.setInt(5, adminId);

            int affectedRows = pstmt.executeUpdate();

            // executeUpdate for INSERT...ON DUPLICATE KEY UPDATE returns:
            // 1 for a new row inserted
            // 2 for an existing row updated
            // 0 if the row wasn't updated (e.g., all values were the same - unlikely here)
            if (affectedRows > 0) {
                LOGGER.info("Successfully saved/updated main banner (ID: " + MAIN_BANNER_ID + "). Affected rows: " + affectedRows);
                return true;
            } else {
                LOGGER.warning("Failed to save/update main banner (ID: " + MAIN_BANNER_ID + "), no rows affected or value unchanged.");
                return false;
            }
        } catch (Exception e) { // Catch broader exceptions for InputStream handling etc.
            LOGGER.log(Level.SEVERE, "Error saving/updating main banner (ID: " + MAIN_BANNER_ID + ")", e);
            return false;
        }
    }

    /**
     * Helper method to map a ResultSet row to a HomepageBanner object.
     */
    private HomepageBanner mapResultSetToHomepageBanner(ResultSet rs) throws SQLException {
        HomepageBanner banner = HomepageBanner.builder()
                .id(rs.getInt("id"))
                .name(rs.getString("name"))
                .position(rs.getInt("position")) // Already int
                .isActive(rs.getBoolean("is_active"))
                .uploadedByAdminId(rs.getInt("uploaded_by_admin_id"))
                .uploadedAt(rs.getTimestamp("uploaded_at"))
                .build();

        // Retrieve BLOB data separately (can be large)
        Blob imageBlob = rs.getBlob("image_data");
        if (imageBlob != null) {
            banner.setImageData(imageBlob.getBytes(1, (int) imageBlob.length()));
            imageBlob.free(); // Release BLOB resources
        } else {
            banner.setImageData(null);
        }

        return banner;
    }
}