package sk.vava.royalmate.data;

import sk.vava.royalmate.model.AssetType;
import sk.vava.royalmate.model.GameAsset;

import java.io.ByteArrayInputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.math.BigDecimal; // Import BigDecimal


public class GameAssetDAO {

    private static final Logger LOGGER = Logger.getLogger(GameAssetDAO.class.getName());
    private static final String TABLE_NAME = "game_assets"; // Ensure this matches your DB

    // Updated INSERT_ASSET_SQL with underscored column names
    private static final String INSERT_ASSET_SQL = "INSERT INTO " + TABLE_NAME +
            " (game_id, asset_type, asset_name, image_data, symbol_payout_multiplier, uploaded_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    // Updated FIND_BY_GAME_SQL with underscored column names
    private static final String FIND_BY_GAME_SQL = "SELECT * FROM " + TABLE_NAME + " WHERE game_id = ?";
    // Updated FIND_BY_GAME_AND_TYPE_SQL with underscored column names
    private static final String FIND_BY_GAME_AND_TYPE_SQL = "SELECT * FROM " + TABLE_NAME + " WHERE game_id = ? AND asset_type = ?";
    // Updated FIND_COVER_BY_GAME_SQL with underscored column names
    private static final String FIND_COVER_BY_GAME_SQL = "SELECT * FROM " + TABLE_NAME + " WHERE game_id = ? AND asset_type = 'COVER' LIMIT 1";
    // Updated DELETE_BY_GAME_AND_TYPE_SQL with underscored column names
    private static final String DELETE_BY_GAME_AND_TYPE_SQL = "DELETE FROM " + TABLE_NAME + " WHERE game_id = ? AND asset_type = ?";
    // Add delete by ID if needed for individual asset removal in future

    /**
     * Saves a new game asset (image).
     *
     * @param asset The GameAsset to save (gameId must be set).
     * @return true if successful, false otherwise.
     */
    public boolean save(GameAsset asset) {
        LOGGER.fine("Attempting to save game asset for game ID: " + asset.getGameId() + ", Type: " + asset.getAssetType());
        if (asset.getGameId() <= 0 || asset.getImageData() == null || asset.getImageData().length == 0) {
            LOGGER.warning("Cannot save asset: Invalid game ID or empty image data.");
            return false;
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT_ASSET_SQL)) {

            pstmt.setInt(1, asset.getGameId());
            pstmt.setString(2, asset.getAssetType().name());
            pstmt.setString(3, asset.getAssetName());

            try (ByteArrayInputStream bis = new ByteArrayInputStream(asset.getImageData())) {
                pstmt.setBinaryStream(4, bis, asset.getImageData().length);
            }

            if (asset.getAssetType() == AssetType.SYMBOL && asset.getSymbolPayoutMultiplier() != null) {
                pstmt.setBigDecimal(5, asset.getSymbolPayoutMultiplier());
            } else {
                pstmt.setNull(5, Types.DECIMAL);
            }

            pstmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                LOGGER.fine("Successfully saved game asset.");
                return true;
            } else {
                LOGGER.warning("Failed to save game asset, no rows affected.");
                return false;
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error saving game asset for game ID: " + asset.getGameId(), e);
            return false;
        }
    }

    /**
     * Finds the Cover image for a specific game.
     *
     * @param gameId The ID of the game.
     * @return Optional containing the GameAsset if found.
     */
    public Optional<GameAsset> findCoverByGameId(int gameId) {
        LOGGER.fine("Finding cover asset for game ID: " + gameId);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_COVER_BY_GAME_SQL)) {

            pstmt.setInt(1, gameId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToGameAsset(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding cover asset for game ID: " + gameId, e);
        }
        return Optional.empty();
    }

    /**
     * Finds all assets of a specific type for a given game.
     *
     * @param gameId The game ID.
     * @param type   The AssetType (e.g., SYMBOL).
     * @return A List of GameAsset objects (might be empty).
     */
    public List<GameAsset> findByGameIdAndType(int gameId, AssetType type) {
        LOGGER.fine("Finding assets for game ID: " + gameId + ", Type: " + type);
        List<GameAsset> assets = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_BY_GAME_AND_TYPE_SQL)) {

            pstmt.setInt(1, gameId);
            pstmt.setString(2, type.name());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    assets.add(mapResultSetToGameAsset(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding assets for game ID: " + gameId + ", Type: " + type, e);
        }
        return assets;
    }

    /**
     * Finds ALL assets associated with a specific game ID.
     * @param gameId The ID of the game.
     * @return A List of GameAsset objects.
     */
    public List<GameAsset> findByGameId(int gameId) {
        LOGGER.fine("Finding all assets for game ID: " + gameId);
        List<GameAsset> assets = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_BY_GAME_SQL)) { // Use FIND_BY_GAME_SQL

            pstmt.setInt(1, gameId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    assets.add(mapResultSetToGameAsset(rs));
                }
            }
            LOGGER.fine("Found " + assets.size() + " assets for game ID: " + gameId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding all assets for game ID: " + gameId, e);
        }
        return assets;
    }


    /**
     * Deletes all assets of a specific type for a given game.
     * Useful for replacing all symbols when updating a Slot game.
     *
     * @param gameId The game ID.
     * @param type   The AssetType to delete (e.g., SYMBOL).
     * @return true if deletion was successful (even if no rows were deleted), false on error.
     */
    public boolean deleteByGameIdAndType(int gameId, AssetType type) {
        LOGGER.warning("Attempting to DELETE assets for game ID: " + gameId + ", Type: " + type);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DELETE_BY_GAME_AND_TYPE_SQL)) {

            pstmt.setInt(1, gameId);
            pstmt.setString(2, type.name());

            pstmt.executeUpdate(); // No need to check affected rows, 0 is okay if none existed

            LOGGER.info("Deletion command executed for assets game ID: " + gameId + ", Type: " + type);
            return true; // Return true assuming the command executed without SQL error

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error deleting assets for game ID: " + gameId + ", Type: " + type, e);
            return false;
        }
    }


    /** Helper to map ResultSet to GameAsset object */
    private GameAsset mapResultSetToGameAsset(ResultSet rs) throws SQLException {
        GameAsset asset = GameAsset.builder()
                .id(rs.getInt("id")) // 'id' doesn't have an underscore
                .gameId(rs.getInt("game_id")) // Updated
                .assetType(AssetType.valueOf(rs.getString("asset_type"))) // Updated
                .assetName(rs.getString("asset_name")) // Updated
                .symbolPayoutMultiplier(rs.getBigDecimal("symbol_payout_multiplier")) // Updated
                .uploadedAt(rs.getTimestamp("uploaded_at")) // Updated
                .build();

        // Retrieve BLOB data
        Blob imageBlob = rs.getBlob("image_data"); // Updated
        if (imageBlob != null) {
            try {
                asset.setImageData(imageBlob.getBytes(1, (int) imageBlob.length()));
            } finally {
                imageBlob.free();
            }
        }
        return asset;
    }
}
