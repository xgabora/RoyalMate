package sk.vava.royalmate.data;

import sk.vava.royalmate.model.Game;
import sk.vava.royalmate.model.GameType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.math.BigDecimal; // Import BigDecimal

public class GameDAO {

    private static final Logger LOGGER = Logger.getLogger(GameDAO.class.getName());

    // Use exact table/column names from your schema
    private static final String TABLE_NAME = "games";
    private static final String ASSET_TABLE_NAME = "game_assets"; // Assuming game_assets
    private static final String ACCOUNT_TABLE_NAME = "accounts"; // Assuming accounts
    private static final String GAMEPLAYS_TABLE_NAME = "game_plays"; // <-- NEW


    // Updated INSERT_GAME_SQL with underscored column names
    private static final String INSERT_GAME_SQL = "INSERT INTO " + TABLE_NAME +
            " (name, description, game_type, min_stake, max_stake, volatility, background_color, created_by_admin_id, is_active, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    // Updated FIND_ALL_SORTED_SQL with underscored column names in SELECT, JOIN, and ORDER BY
    private static final String FIND_ALL_SORTED_SQL =
            "SELECT g.id, g.name, g.description, g.game_type, g.min_stake, g.max_stake, g.volatility, g.background_color, g.created_by_admin_id, g.is_active, g.created_at, " +
                    "a.username as admin_username, ga.image_data as cover_image " + // Use image_data for the alias
                    "FROM " + TABLE_NAME + " g " +
                    "JOIN " + ACCOUNT_TABLE_NAME + " a ON g.created_by_admin_id = a.id " +
                    "LEFT JOIN " + ASSET_TABLE_NAME + " ga ON g.id = ga.game_id AND ga.asset_type = 'COVER' " + // Use game_id and asset_type
                    "ORDER BY g.created_at DESC";

    // Updated FIND_BY_ID_SQL with underscored column names in SELECT and JOIN
    private static final String FIND_BY_ID_SQL =
            "SELECT g.id, g.name, g.description, g.game_type, g.min_stake, g.max_stake, g.volatility, g.background_color, g.created_by_admin_id, g.is_active, g.created_at, " +
                    "a.username as admin_username " +
                    "FROM " + TABLE_NAME + " g " +
                    "JOIN " + ACCOUNT_TABLE_NAME + " a ON g.created_by_admin_id = a.id " +
                    "WHERE g.id = ?"; // 'id' doesn't have an underscore

    // Updated UPDATE_GAME_SQL with underscored column names
    private static final String UPDATE_GAME_SQL = "UPDATE " + TABLE_NAME + " SET " +
            "name = ?, description = ?, game_type = ?, min_stake = ?, max_stake = ?, " +
            "volatility = ?, background_color = ?, is_active = ? " + // Removed createdByAdminId, createdAt as per original logic
            "WHERE id = ?";

    private static final String DELETE_GAME_SQL = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";

    private static final String FIND_TOP_GAMES_SQL =
            "SELECT g.id, g.name, g.description, g.game_type, g.min_stake, g.max_stake, g.volatility, g.background_color, g.created_by_admin_id, g.is_active, g.created_at, " +
                    "a.username as admin_username, " +
                    "ga.image_data as cover_image, " +
                    "COUNT(gp.id) as spin_count " + // Count gameplays as spin_count
                    "FROM " + TABLE_NAME + " g " +
                    "JOIN " + ACCOUNT_TABLE_NAME + " a ON g.created_by_admin_id = a.id " +
                    "LEFT JOIN " + ASSET_TABLE_NAME + " ga ON g.id = ga.game_id AND ga.asset_type = 'COVER' " +
                    "LEFT JOIN " + GAMEPLAYS_TABLE_NAME + " gp ON g.id = gp.game_id " + // Join with gameplays
                    "WHERE g.is_active = true " + // Only active games
                    "GROUP BY g.id " + // Group by game to count plays
                    "ORDER BY spin_count DESC " + // Order by the count
                    "LIMIT ?"; // Limit results

    // Find ALL ACTIVE games, joining for cover image and admin username (order can be added if needed)
    private static final String FIND_ALL_ACTIVE_WITH_COVERS_SQL =
            "SELECT g.id, g.name, g.description, g.game_type, g.min_stake, g.max_stake, g.volatility, g.background_color, g.created_by_admin_id, g.is_active, g.created_at, " +
                    "a.username as admin_username, " +
                    "ga.image_data as cover_image, " +
                    "COALESCE(gc.play_count, 0) as spin_count " + // Use COALESCE for games with 0 plays
                    "FROM " + TABLE_NAME + " g " +
                    "JOIN " + ACCOUNT_TABLE_NAME + " a ON g.created_by_admin_id = a.id " +
                    "LEFT JOIN " + ASSET_TABLE_NAME + " ga ON g.id = ga.game_id AND ga.asset_type = 'COVER' " +
                    // Subquery or Left Join to get play counts efficiently
                    "LEFT JOIN (SELECT game_id, COUNT(id) as play_count FROM " + GAMEPLAYS_TABLE_NAME + " GROUP BY game_id) gc ON g.id = gc.game_id " +
                    "WHERE g.is_active = true " + // Only active games
                    "ORDER BY g.name ASC"; // Default sort by name


    /**
     * Saves a new game and returns its generated ID.
     *
     * @param game The Game object to save (without ID).
     * @return The generated ID if successful, -1 otherwise.
     */
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

    /**
     * Retrieves all games, ordered by creation date descending.
     * Includes creator username and cover image data.
     *
     * @return List of Game objects.
     */
    public List<Game> findAllSortedByDateDesc() {
        LOGGER.fine("Finding all games sorted by date desc.");
        List<Game> games = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(FIND_ALL_SORTED_SQL)) {

            while (rs.next()) {
                games.add(mapResultSetToGame(rs, true)); // Map extra JOINed data
            }
            LOGGER.fine("Found " + games.size() + " games.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding all games", e);
        }
        return games;
    }

    /**
     * Finds a single game by its ID, including the creator's username.
     *
     * @param gameId The ID of the game.
     * @return Optional containing the Game if found.
     */
    public Optional<Game> findById(int gameId) {
        LOGGER.fine("Finding game by ID: " + gameId);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_BY_ID_SQL)) {

            pstmt.setInt(1, gameId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToGame(rs, true)); // Map joined username
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding game by ID: " + gameId, e);
        }
        return Optional.empty();
    }

    /**
     * Updates an existing game's details.
     * Does NOT update creator or creation timestamp.
     *
     * @param game The Game object with updated information (must have correct ID).
     * @return true if successful, false otherwise.
     */
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
            pstmt.setInt(9, game.getId()); // WHERE clause

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


    /**
     * Deletes a game by its ID. Assets are deleted via CASCADE constraint.
     *
     * @param gameId The ID of the game to delete.
     * @return true if successful, false otherwise.
     */
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


// --- NEW METHOD ---
    /**
     * Finds the top N most played active games.
     * Includes cover image and admin username.
     *
     * @param limit The maximum number of games to return.
     * @return A list of top Game objects.
     */
    public List<Game> findTopGames(int limit) {
        LOGGER.fine("Finding top " + limit + " games by play count.");
        List<Game> games = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_TOP_GAMES_SQL)) {

            pstmt.setInt(1, limit); // Set the LIMIT parameter

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    // Map including the new spin_count alias
                    games.add(mapResultSetToGame(rs, true));
                }
            }
            LOGGER.fine("Found " + games.size() + " top games.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding top games", e);
        }
        return games;
    }
    // --- END NEW METHOD ---

    // --- NEW METHOD ---
    /**
     * Retrieves all ACTIVE games, including creator username and cover image data.
     * Ordered by game name ascending.
     *
     * @return List of active Game objects.
     */
    public List<Game> findAllActiveWithCovers() {
        LOGGER.fine("Finding all active games with covers.");
        List<Game> games = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(FIND_ALL_ACTIVE_WITH_COVERS_SQL)) {

            while (rs.next()) {
                games.add(mapResultSetToGame(rs, true)); // Map includes cover, username, spins
            }
            LOGGER.fine("Found " + games.size() + " active games.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding all active games", e);
        }
        return games;
    }
    // --- END NEW METHOD ---



    /** Helper to map ResultSet to Game object (MODIFIED) */
    private Game mapResultSetToGame(ResultSet rs, boolean includeJoinData) throws SQLException {
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

        if(includeJoinData) {
            // Check if joined columns exist before trying to read them
            if (hasColumn(rs, "admin_username")) {
                game.setCreatedByAdminUsername(rs.getString("admin_username"));
            }
            if (hasColumn(rs, "cover_image")) {
                Blob coverBlob = rs.getBlob("cover_image");
                if(coverBlob != null) {
                    try {
                        game.setCoverImageData(coverBlob.getBytes(1, (int) coverBlob.length()));
                    } finally {
                        coverBlob.free();
                    }
                }
            }
            // <<<--- ADDED MAPPING FOR SPIN COUNT ---<<<
            if (hasColumn(rs, "spin_count")) {
                game.setTotalSpins(rs.getLong("spin_count"));
            }
            // >>>------------------------------------>>>
        }
        return game;
    }

    /** Utility to check if a column exists in the ResultSet metadata */
    private boolean hasColumn(ResultSet rs, String columnName) throws SQLException {
        // ... (Keep existing implementation) ...
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
