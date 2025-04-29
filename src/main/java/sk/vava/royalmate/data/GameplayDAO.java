package sk.vava.royalmate.data;

import sk.vava.royalmate.model.GameType;
import sk.vava.royalmate.model.Gameplay;
import sk.vava.royalmate.model.UserStatistics; // Import UserStatistics

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameplayDAO {

    private static final Logger LOGGER = Logger.getLogger(GameplayDAO.class.getName());
    private static final String TABLE_NAME = "game_plays";
    private static final String ACCOUNT_TABLE_NAME = "accounts";
    private static final String GAME_TABLE_NAME = "games"; // <-- ADDED
    private static final String ASSET_TABLE_NAME = "game_assets"; // <-- ADDED



    // SQL using column names from your schema
    private static final String INSERT_INITIAL_PLAY_SQL = "INSERT INTO " + TABLE_NAME +
            " (account_id, game_id, stake_amount, outcome, payout_amount, timestamp) " +
            "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

    private static final String UPDATE_PLAY_RESULT_SQL = "UPDATE " + TABLE_NAME + " SET " +
            "outcome = ?, payout_amount = ? WHERE id = ?";

    private static final String FIND_RECENT_WINS_BY_GAME_SQL = "SELECT gp.*, a.username " +
            "FROM " + TABLE_NAME + " gp JOIN " + ACCOUNT_TABLE_NAME + " a ON gp.account_id = a.id " +
            "WHERE gp.game_id = ? AND gp.payout_amount > 0 " +
            "ORDER BY gp.timestamp DESC LIMIT ?";

    // --- NEW SQL ---
    // Query to calculate all required statistics for a user in one go
    private static final String GET_USER_STATS_SQL =
            "SELECT " +
                    "COUNT(id) as total_spins, " +
                    "COALESCE(SUM(stake_amount), 0) as total_wagered, " + // Use COALESCE to handle NULL if no plays
                    "COALESCE(SUM(payout_amount), 0) as total_won, " +
                    "COUNT(DISTINCT game_id) as distinct_games_played " +
                    "FROM " +
                    TABLE_NAME + " " +
                    "WHERE " +
                    "account_id = ?";

    // --- REVISED FIND_TOP_PLAYS_SQL_TEMPLATE ---
    // Added JOIN for game_assets to get cover image
    private static final String FIND_TOP_PLAYS_SQL_TEMPLATE =
            "SELECT " +
                    "gp.id, gp.account_id, gp.game_id, gp.stake_amount, gp.outcome, gp.payout_amount, gp.timestamp, " +
                    "a.username, " +
                    "g.name as game_name, " +
                    "ga.image_data as cover_image, " + // <-- SELECT cover image
                    "CASE " +
                    "WHEN gp.stake_amount IS NULL OR gp.stake_amount = 0 THEN 0.00 " +
                    "ELSE gp.payout_amount / gp.stake_amount " +
                    "END as multiplier " +
                    "FROM " +
                    TABLE_NAME + " gp " +
                    "JOIN " +
                    ACCOUNT_TABLE_NAME + " a ON gp.account_id = a.id " +
                    "JOIN " +
                    GAME_TABLE_NAME + " g ON gp.game_id = g.id " +
                    "LEFT JOIN " + // <-- LEFT JOIN to still show plays if game has no cover
                    ASSET_TABLE_NAME + " ga ON g.id = ga.game_id AND ga.asset_type = 'COVER' " +
                    "WHERE " +
                    "g.game_type = ? " +
                    "AND gp.payout_amount > 0 " +
                    "ORDER BY " +
                    "{orderByClause} DESC, gp.timestamp DESC " +
                    "LIMIT ?";



    /**
     * Saves the initial state of a gameplay record (when the bet is placed).
     * Outcome and payout are set to default/pending values.
     *
     * @param accountId   The ID of the player.
     * @param gameId      The ID of the game being played.
     * @param stakeAmount The amount staked.
     * @return The generated ID of the new gameplay record, or -1L on failure.
     */
    public long saveInitialPlay(int accountId, int gameId, BigDecimal stakeAmount) {
        LOGGER.fine("Saving initial play for account " + accountId + ", game " + gameId + ", stake " + stakeAmount);
        ResultSet generatedKeys = null;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT_INITIAL_PLAY_SQL, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, accountId);
            pstmt.setInt(2, gameId);
            pstmt.setBigDecimal(3, stakeAmount);
            pstmt.setString(4, "PENDING"); // Initial outcome status
            pstmt.setBigDecimal(5, BigDecimal.ZERO); // Initial payout

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                generatedKeys = pstmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    long generatedId = generatedKeys.getLong(1);
                    LOGGER.fine("Initial play saved with ID: " + generatedId);
                    return generatedId;
                } else {
                    LOGGER.severe("Failed to retrieve generated ID after saving initial play.");
                    return -1L;
                }
            } else {
                LOGGER.warning("Failed to save initial play, no rows affected.");
                return -1L;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error saving initial play for account " + accountId + ", game " + gameId, e);
            return -1L;
        } finally {
            if (generatedKeys != null) try { generatedKeys.close(); } catch (SQLException logOrIgnore) {}
        }
    }

    /**
     * Updates an existing gameplay record with the outcome and payout amount.
     *
     * @param gameplayId   The ID of the gameplay record to update.
     * @param outcome      A String representation of the final result (e.g., grid state as JSON).
     * @param payoutAmount The amount won.
     * @return true if the update was successful, false otherwise.
     */
    public boolean updatePlayResult(long gameplayId, String outcome, BigDecimal payoutAmount) {
        LOGGER.fine("Updating play result for gameplay ID: " + gameplayId + ", payout: " + payoutAmount);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_PLAY_RESULT_SQL)) {

            pstmt.setString(1, outcome);
            pstmt.setBigDecimal(2, payoutAmount);
            pstmt.setLong(3, gameplayId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                LOGGER.fine("Successfully updated play result for gameplay ID: " + gameplayId);
                return true;
            } else {
                LOGGER.warning("Failed to update play result, gameplay ID not found?: " + gameplayId);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating play result for gameplay ID: " + gameplayId, e);
            return false;
        }
    }

    /**
     * Finds recent winning plays for a specific game, including the player's username.
     *
     * @param gameId The ID of the game.
     * @param limit  The maximum number of records to return.
     * @return A list of Gameplay objects, ordered most recent first.
     */
    public List<Gameplay> findRecentWinsByGame(int gameId, int limit) {
        LOGGER.fine("Finding recent " + limit + " wins for game ID: " + gameId);
        List<Gameplay> wins = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_RECENT_WINS_BY_GAME_SQL)) {

            pstmt.setInt(1, gameId);
            pstmt.setInt(2, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    wins.add(mapResultSetToGameplay(rs));
                }
            }
            LOGGER.fine("Found " + wins.size() + " recent wins for game ID: " + gameId);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding recent wins for game ID: " + gameId, e);
        }
        return wins;
    }

    public List<Gameplay> findTopPlays(GameType gameType, String orderByColumn, int limit) {
        String orderByClause;
        // Validate orderByColumn and map to SQL equivalent
        if ("multiplier".equalsIgnoreCase(orderByColumn)) {
            // ORDER BY the calculated alias 'multiplier'
            orderByClause = "multiplier";
        } else { // Default to payout_amount
            orderByClause = "gp.payout_amount"; // Use alias gp.
        }

        LOGGER.fine("Finding top " + limit + " plays for type " + gameType + ", ordered by " + orderByClause);
        List<Gameplay> topPlays = new ArrayList<>();
        // Safely replace placeholder in the template
        String finalSQL = FIND_TOP_PLAYS_SQL_TEMPLATE.replace("{orderByClause}", orderByClause);

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(finalSQL)) {

            pstmt.setString(1, gameType.name());
            pstmt.setInt(2, limit);

            long startTime = System.currentTimeMillis(); // Start timer
            try (ResultSet rs = pstmt.executeQuery()) {
                long queryEndTime = System.currentTimeMillis();
                LOGGER.fine("SQL query execution time: " + (queryEndTime - startTime) + " ms");

                while (rs.next()) {
                    topPlays.add(mapResultSetToGameplay(rs)); // Use updated mapper
                }
                long mapEndTime = System.currentTimeMillis();
                LOGGER.fine("Result set mapping time: " + (mapEndTime - queryEndTime) + " ms");
            }
            LOGGER.info("Found " + topPlays.size() + " top plays for type " + gameType + " ordered by " + orderByClause); // Changed level to INFO

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding top plays for type " + gameType + " ordered by " + orderByClause, e);
        }
        return topPlays;
    }

    /** Helper method to map ResultSet to Gameplay object (MODIFIED) */
    private Gameplay mapResultSetToGameplay(ResultSet rs) throws SQLException {
        Gameplay play = Gameplay.builder()
                .id(rs.getLong("id"))
                .accountId(rs.getInt("account_id"))
                .gameId(rs.getInt("game_id"))
                .stakeAmount(rs.getBigDecimal("stake_amount"))
                .outcome(rs.getString("outcome"))
                .payoutAmount(rs.getBigDecimal("payout_amount"))
                .timestamp(rs.getTimestamp("timestamp"))
                .build();

        if (hasColumn(rs, "username")) { play.setUsername(rs.getString("username")); }
        if (hasColumn(rs, "game_name")) { play.setGameName(rs.getString("game_name")); }
        if (hasColumn(rs, "multiplier")) {
            play.setMultiplier(rs.getBigDecimal("multiplier").setScale(2, RoundingMode.HALF_UP));
        } else { /* Calculate fallback */ }

        // --- ADDED: Map Cover Image ---
        if (hasColumn(rs, "cover_image")) {
            Blob coverBlob = rs.getBlob("cover_image");
            if (coverBlob != null) {
                try {
                    play.setCoverImageData(coverBlob.getBytes(1, (int) coverBlob.length()));
                } finally {
                    coverBlob.free(); // Release resources
                }
            }
        }
        // ------------------------------

        return play;
    }

    // --- NEW METHOD ---
    /**
     * Calculates aggregate statistics for a specific user account.
     *
     * @param accountId The ID of the user.
     * @return An Optional containing UserStatistics if successful, empty otherwise.
     */
    public Optional<UserStatistics> getUserStatistics(int accountId) {
        LOGGER.fine("Calculating statistics for account ID: " + accountId);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(GET_USER_STATS_SQL)) {

            pstmt.setInt(1, accountId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // Build the statistics object directly from the result set
                    UserStatistics stats = UserStatistics.builder()
                            .totalSpins(rs.getLong("total_spins"))
                            .totalWagered(rs.getBigDecimal("total_wagered"))
                            .totalWon(rs.getBigDecimal("total_won"))
                            .distinctGamesPlayed(rs.getLong("distinct_games_played"))
                            .build();
                    LOGGER.fine("Statistics calculated successfully for account ID: " + accountId);
                    return Optional.of(stats);
                } else {
                    // This case should ideally not happen with aggregate functions unless the user truly has 0 plays,
                    // but COALESCE handles NULL SUMs. If the query somehow returns no rows, return empty stats.
                    LOGGER.warning("Query for user statistics returned no rows for account ID: " + accountId + ". Returning empty stats.");
                    // Return default zero stats if no rows found (or user has 0 plays)
                    return Optional.of(UserStatistics.builder()
                            .totalSpins(0L)
                            .totalWagered(BigDecimal.ZERO)
                            .totalWon(BigDecimal.ZERO)
                            .distinctGamesPlayed(0L)
                            .build());
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error calculating statistics for account ID: " + accountId, e);
            return Optional.empty(); // Return empty on SQL error
        }
    }
    // --- END NEW METHOD --


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