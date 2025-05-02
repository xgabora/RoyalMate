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
import java.math.BigDecimal;

public class GameAssetDAO {

    private static final Logger LOGGER = Logger.getLogger(GameAssetDAO.class.getName());
    private static final String TABLE_NAME = "game_assets";

    private static final String INSERT_ASSET_SQL = "INSERT INTO " + TABLE_NAME +
            " (game_id, asset_type, asset_name, image_data, symbol_payout_multiplier, uploaded_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String FIND_BY_GAME_SQL = "SELECT * FROM " + TABLE_NAME + " WHERE game_id = ?";

    private static final String FIND_BY_GAME_AND_TYPE_SQL = "SELECT * FROM " + TABLE_NAME + " WHERE game_id = ? AND asset_type = ?";

    private static final String FIND_COVER_BY_GAME_SQL = "SELECT * FROM " + TABLE_NAME + " WHERE game_id = ? AND asset_type = 'COVER' LIMIT 1";

    private static final String DELETE_BY_GAME_AND_TYPE_SQL = "DELETE FROM " + TABLE_NAME + " WHERE game_id = ? AND asset_type = ?";

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

    public List<GameAsset> findByGameId(int gameId) {
        LOGGER.fine("Finding all assets for game ID: " + gameId);
        List<GameAsset> assets = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_BY_GAME_SQL)) {

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

    public boolean deleteByGameIdAndType(int gameId, AssetType type) {
        LOGGER.warning("Attempting to DELETE assets for game ID: " + gameId + ", Type: " + type);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DELETE_BY_GAME_AND_TYPE_SQL)) {

            pstmt.setInt(1, gameId);
            pstmt.setString(2, type.name());

            pstmt.executeUpdate();

            LOGGER.info("Deletion command executed for assets game ID: " + gameId + ", Type: " + type);
            return true;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error deleting assets for game ID: " + gameId + ", Type: " + type, e);
            return false;
        }
    }

    private GameAsset mapResultSetToGameAsset(ResultSet rs) throws SQLException {
        GameAsset asset = GameAsset.builder()
                .id(rs.getInt("id"))
                .gameId(rs.getInt("game_id"))
                .assetType(AssetType.valueOf(rs.getString("asset_type")))
                .assetName(rs.getString("asset_name"))
                .symbolPayoutMultiplier(rs.getBigDecimal("symbol_payout_multiplier"))
                .uploadedAt(rs.getTimestamp("uploaded_at"))
                .build();

        Blob imageBlob = rs.getBlob("image_data");
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