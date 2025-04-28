package sk.vava.royalmate.data;

import sk.vava.royalmate.model.Gameplay;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameplayDAO {

    private static final Logger LOGGER = Logger.getLogger(GameplayDAO.class.getName());
    private static final String TABLE_NAME = "game_plays";
    private static final String ACCOUNT_TABLE_NAME = "accounts";

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

    /** Helper method to map ResultSet to Gameplay object */
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
        // Map joined username if present
        if (hasColumn(rs, "username")) {
            play.setUsername(rs.getString("username"));
        }
        return play;
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