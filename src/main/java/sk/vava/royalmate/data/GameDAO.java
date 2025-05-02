package sk.vava.royalmate.data;

import sk.vava.royalmate.model.Game;
import sk.vava.royalmate.model.GameType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class GameDAO {

    private static final Logger LOGGER = Logger.getLogger(GameDAO.class.getName());

    private static final String TABLE_NAME = "games";
    private static final String ASSET_TABLE_NAME = "game_assets";
    private static final String ACCOUNT_TABLE_NAME = "accounts";
    private static final String GAMEPLAYS_TABLE_NAME = "game_plays";

    private static final String INSERT_GAME_SQL = "INSERT INTO " + TABLE_NAME +
            " (name, description, game_type, min_stake, max_stake, volatility, background_color, created_by_admin_id, is_active, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String FIND_ALL_SORTED_SQL =
            "SELECT g.id, g.name, g.description, g.game_type, g.min_stake, g.max_stake, g.volatility, g.background_color, g.created_by_admin_id, g.is_active, g.created_at, " +
                    "a.username as admin_username, ga.image_data as cover_image " +
                    "FROM " + TABLE_NAME + " g " +
                    "JOIN " + ACCOUNT_TABLE_NAME + " a ON g.created_by_admin_id = a.id " +
                    "LEFT JOIN " + ASSET_TABLE_NAME + " ga ON g.id = ga.game_id AND ga.asset_type = 'COVER' " +
                    "ORDER BY g.created_at DESC";

    private static final String FIND_BY_ID_SQL =
            "SELECT g.id, g.name, g.description, g.game_type, g.min_stake, g.max_stake, g.volatility, g.background_color, g.created_by_admin_id, g.is_active, g.created_at, " +
                    "a.username as admin_username " +
                    "FROM " + TABLE_NAME + " g " +
                    "JOIN " + ACCOUNT_TABLE_NAME + " a ON g.created_by_admin_id = a.id " +
                    "WHERE g.id = ?";

    private static final String UPDATE_GAME_SQL = "UPDATE " + TABLE_NAME + " SET " +
            "name = ?, description = ?, game_type = ?, min_stake = ?, max_stake = ?, " +
            "volatility = ?, background_color = ?, is_active = ? " +
            "WHERE id = ?";

    private static final String DELETE_GAME_SQL = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";

    private static final String FIND_TOP_GAMES_SQL =
            "SELECT g.id, g.name, g.description, g.game_type, g.min_stake, g.max_stake, g.volatility, g.background_color, g.created_by_admin_id, g.is_active, g.created_at, " +
                    "ANY_VALUE(a.username) as admin_username, " +
                    "ANY_VALUE(ga.image_data) as cover_image, " +
                    "COUNT(gp.id) as spin_count " +
                    "FROM " + TABLE_NAME + " g " +
                    "JOIN " + ACCOUNT_TABLE_NAME + " a ON g.created_by_admin_id = a.id " +
                    "LEFT JOIN " + ASSET_TABLE_NAME + " ga ON g.id = ga.game_id AND ga.asset_type = 'COVER' " +
                    "LEFT JOIN " + GAMEPLAYS_TABLE_NAME + " gp ON g.id = gp.game_id " +
                    "WHERE g.is_active = true " +
                    "GROUP BY g.id " +
                    "ORDER BY spin_count DESC " +
                    "LIMIT ?";

    private static final String FIND_ALL_ACTIVE_WITH_COVERS_SQL =
            "SELECT g.id, g.name, g.description, g.game_type, g.min_stake, g.max_stake, g.volatility, g.background_color, g.created_by_admin_id, g.is_active, g.created_at, " +
                    "a.username as admin_username, " +
                    "ga.image_data as cover_image, " +
                    "COALESCE(gc.play_count, 0) as spin_count " +
                    "FROM " + TABLE_NAME + " g " +
                    "JOIN " + ACCOUNT_TABLE_NAME + " a ON g.created_by_admin_id = a.id " +
                    "LEFT JOIN " + ASSET_TABLE_NAME + " ga ON g.id = ga.game_id AND ga.asset_type = 'COVER' " +

                    "LEFT JOIN (SELECT game_id, COUNT(id) as play_count FROM " + GAMEPLAYS_TABLE_NAME + " GROUP BY game_id) gc ON g.id = gc.game_id " +
                    "WHERE g.is_active = true " +
                    "ORDER BY g.name ASC";

    private static final String FIND_ALL_WITH_STATS_SQL =
            "SELECT g.id, g.name, g.description, g.game_type, g.min_stake, g.max_stake, g.volatility, g.background_color, g.created_by_admin_id, g.is_active, g.created_at, " +
                    "a.username as admin_username, " +
                    "COALESCE(gc.play_count, 0) as total_spins, " +
                    "COALESCE(gc.max_payout, 0.00) as max_payout " +
                    "FROM " + TABLE_NAME + " g " +
                    "JOIN " + ACCOUNT_TABLE_NAME + " a ON g.created_by_admin_id = a.id " +

                    "LEFT JOIN (" +
                    "SELECT " +
                    "game_id, " +
                    "COUNT(id) as play_count, " +
                    "MAX(payout_amount) as max_payout " +
                    "FROM " + GAMEPLAYS_TABLE_NAME + " " +
                    "GROUP BY game_id" +
                    ") gc ON g.id = gc.game_id " +
                    "ORDER BY g.id ASC";

    public int save(Game game) {
        LOGGER.info("Attempting to save new game: " + game.getName());
        ResultSet generatedKeys = null;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT_GAME_SQL, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, game.getName());
            pstmt.setString(2, game.getDescription());
            pstmt.setString(3, game.getGameType().name());
            pstmt.setBigDecimal(4, game.getMinStake());
            pstmt.setBigDecimal(5, game.getMaxStake());
            pstmt.setInt(6, game.getVolatility());
            pstmt.setString(7, game.getBackgroundColor());
            pstmt.setInt(8, game.getCreatedByAdminId());
            pstmt.setBoolean(9, game.isActive());
            pstmt.setTimestamp(10, new Timestamp(System.currentTimeMillis()));

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                generatedKeys = pstmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int generatedId = generatedKeys.getInt(1);
                    LOGGER.info("Successfully saved new game '" + game.getName() + "' with ID: " + generatedId);
                    return generatedId;
                } else {
                    LOGGER.severe("Failed to retrieve generated ID after saving game: " + game.getName());
                    return -1;
                }
            } else {
                LOGGER.warning("Failed to save game, no rows affected for: " + game.getName());
                return -1;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error saving game: " + game.getName(), e);
            return -1;
        } finally {
            if (generatedKeys != null) try { generatedKeys.close(); } catch (SQLException logOrIgnore) {}
        }
    }

    public List<Game> findAllSortedByDateDesc() {
        LOGGER.fine("Finding all games sorted by date desc.");
        List<Game> games = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(FIND_ALL_SORTED_SQL)) {

            while (rs.next()) {
                games.add(mapResultSetToGame(rs, true));
            }
            LOGGER.fine("Found " + games.size() + " games.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding all games", e);
        }
        return games;
    }

    public Optional<Game> findById(int gameId) {
        LOGGER.fine("Finding game by ID: " + gameId);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_BY_ID_SQL)) {

            pstmt.setInt(1, gameId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToGame(rs, true));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding game by ID: " + gameId, e);
        }
        return Optional.empty();
    }

    public boolean update(Game game) {
        LOGGER.info("Attempting to update game ID: " + game.getId());
        if (game.getId() <= 0) {
            LOGGER.warning("Update failed: Invalid game ID provided.");
            return false;
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_GAME_SQL)) {

            pstmt.setString(1, game.getName());
            pstmt.setString(2, game.getDescription());
            pstmt.setString(3, game.getGameType().name());
            pstmt.setBigDecimal(4, game.getMinStake());
            pstmt.setBigDecimal(5, game.getMaxStake());
            pstmt.setInt(6, game.getVolatility());
            pstmt.setString(7, game.getBackgroundColor());
            pstmt.setBoolean(8, game.isActive());
            pstmt.setInt(9, game.getId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                LOGGER.info("Successfully updated game ID: " + game.getId());
                return true;
            } else {
                LOGGER.warning("Failed to update game, ID not found?: " + game.getId());
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating game ID: " + game.getId(), e);
            return false;
        }
    }

    public boolean delete(int gameId) {
        LOGGER.warning("Attempting to DELETE game ID: " + gameId);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DELETE_GAME_SQL)) {

            pstmt.setInt(1, gameId);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                LOGGER.info("Successfully deleted game ID: " + gameId);
                return true;
            } else {
                LOGGER.warning("Failed to delete game, ID not found?: " + gameId);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error deleting game ID: " + gameId, e);
            return false;
        }
    }

    public List<Game> findTopGames(int limit) {
        LOGGER.fine("Finding top " + limit + " games by play count.");
        List<Game> games = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_TOP_GAMES_SQL)) {

            pstmt.setInt(1, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {

                    games.add(mapResultSetToGame(rs, true));
                }
            }
            LOGGER.fine("Found " + games.size() + " top games.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding top games", e);
        }
        return games;
    }

    public List<Game> findAllActiveWithCovers() {
        LOGGER.fine("Finding all active games with covers.");
        List<Game> games = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(FIND_ALL_ACTIVE_WITH_COVERS_SQL)) {

            while (rs.next()) {
                games.add(mapResultSetToGame(rs, true));
            }
            LOGGER.fine("Found " + games.size() + " active games.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding all active games", e);
        }
        return games;
    }

    public List<Game> findAllWithStats() {
        LOGGER.fine("Finding all games with stats for export.");
        List<Game> games = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(FIND_ALL_WITH_STATS_SQL)) {

            while (rs.next()) {

                games.add(mapResultSetToGame(rs, true, true));
            }
            LOGGER.fine("Found " + games.size() + " games with stats.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding all games with stats", e);
        }
        return games;
    }

    private Game mapResultSetToGame(ResultSet rs, boolean includeJoinData) throws SQLException {

        return mapResultSetToGame(rs, includeJoinData, false);
    }

    private Game mapResultSetToGame(ResultSet rs, boolean includeJoinData, boolean includeStatsData) throws SQLException {
        Game game = Game.builder()
                .id(rs.getInt("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .gameType(GameType.valueOf(rs.getString("game_type")))
                .minStake(rs.getBigDecimal("min_stake"))
                .maxStake(rs.getBigDecimal("max_stake"))
                .volatility(rs.getInt("volatility"))
                .backgroundColor(rs.getString("background_color"))
                .createdByAdminId(rs.getInt("created_by_admin_id"))
                .isActive(rs.getBoolean("is_active"))
                .createdAt(rs.getTimestamp("created_at"))
                .build();

        if (includeJoinData) {
            if (hasColumn(rs, "admin_username")) {
                game.setCreatedByAdminUsername(rs.getString("admin_username"));
            }

            if (!includeStatsData && hasColumn(rs, "cover_image")) {
                Blob coverBlob = rs.getBlob("cover_image");
                if (coverBlob != null) {
                    try { game.setCoverImageData(coverBlob.getBytes(1, (int) coverBlob.length())); }
                    finally { coverBlob.free(); }
                }
            }
        }

        if (includeStatsData) {
            if (hasColumn(rs, "total_spins")) {
                game.setTotalSpins(rs.getLong("total_spins"));
            }
            if (hasColumn(rs, "max_payout")) {
                game.setMaxPayout(rs.getBigDecimal("max_payout"));
            }
        } else if (includeJoinData && hasColumn(rs, "spin_count")) {

            game.setTotalSpins(rs.getLong("spin_count"));
        }

        return game;
    }

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