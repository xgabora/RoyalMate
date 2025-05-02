package sk.vava.royalmate.data;

import sk.vava.royalmate.model.GameType;
import sk.vava.royalmate.model.Gameplay;
import sk.vava.royalmate.model.UserStatistics;

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
    private static final String GAME_TABLE_NAME = "games";
    private static final String ASSET_TABLE_NAME = "game_assets";

    private static final String INSERT_INITIAL_PLAY_SQL = "INSERT INTO " + TABLE_NAME +
            " (account_id, game_id, stake_amount, outcome, payout_amount, timestamp) " +
            "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

    private static final String UPDATE_PLAY_RESULT_SQL = "UPDATE " + TABLE_NAME + " SET " +
            "outcome = ?, payout_amount = ? WHERE id = ?";

    private static final String FIND_RECENT_WINS_BY_GAME_SQL = "SELECT gp.*, a.username " +
            "FROM " + TABLE_NAME + " gp JOIN " + ACCOUNT_TABLE_NAME + " a ON gp.account_id = a.id " +
            "WHERE gp.game_id = ? AND gp.payout_amount > 0 " +
            "ORDER BY gp.timestamp DESC LIMIT ?";

    private static final String GET_USER_STATS_SQL =
            "SELECT " +
                    "COUNT(id) as total_spins, " +
                    "COALESCE(SUM(stake_amount), 0) as total_wagered, " +
                    "COALESCE(SUM(payout_amount), 0) as total_won, " +
                    "COUNT(DISTINCT game_id) as distinct_games_played " +
                    "FROM " +
                    TABLE_NAME + " " +
                    "WHERE " +
                    "account_id = ?";

    private static final String FIND_TOP_PLAYS_SQL_TEMPLATE =
            "SELECT " +
                    "gp.id, gp.account_id, gp.game_id, gp.stake_amount, gp.outcome, gp.payout_amount, gp.timestamp, " +
                    "a.username, " +
                    "g.name as game_name, " +
                    "ga.image_data as cover_image, " +
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
                    "LEFT JOIN " +
                    ASSET_TABLE_NAME + " ga ON g.id = ga.game_id AND ga.asset_type = 'COVER' " +
                    "WHERE " +
                    "g.game_type = ? " +
                    "AND gp.payout_amount > 0 " +
                    "ORDER BY " +
                    "{orderByClause} DESC, gp.timestamp DESC " +
                    "LIMIT ?";

    public long saveInitialPlay(int accountId, int gameId, BigDecimal stakeAmount) {
        LOGGER.fine("Saving initial play for account " + accountId + ", game " + gameId + ", stake " + stakeAmount);
        ResultSet generatedKeys = null;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT_INITIAL_PLAY_SQL, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, accountId);
            pstmt.setInt(2, gameId);
            pstmt.setBigDecimal(3, stakeAmount);
            pstmt.setString(4, "PENDING");
            pstmt.setBigDecimal(5, BigDecimal.ZERO);

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

        if ("multiplier".equalsIgnoreCase(orderByColumn)) {

            orderByClause = "multiplier";
        } else {
            orderByClause = "gp.payout_amount";
        }

        LOGGER.fine("Finding top " + limit + " plays for type " + gameType + ", ordered by " + orderByClause);
        List<Gameplay> topPlays = new ArrayList<>();

        String finalSQL = FIND_TOP_PLAYS_SQL_TEMPLATE.replace("{orderByClause}", orderByClause);

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(finalSQL)) {

            pstmt.setString(1, gameType.name());
            pstmt.setInt(2, limit);

            long startTime = System.currentTimeMillis();
            try (ResultSet rs = pstmt.executeQuery()) {
                long queryEndTime = System.currentTimeMillis();
                LOGGER.fine("SQL query execution time: " + (queryEndTime - startTime) + " ms");

                while (rs.next()) {
                    topPlays.add(mapResultSetToGameplay(rs));
                }
                long mapEndTime = System.currentTimeMillis();
                LOGGER.fine("Result set mapping time: " + (mapEndTime - queryEndTime) + " ms");
            }
            LOGGER.info("Found " + topPlays.size() + " top plays for type " + gameType + " ordered by " + orderByClause);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding top plays for type " + gameType + " ordered by " + orderByClause, e);
        }
        return topPlays;
    }

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
        } else {  }

        if (hasColumn(rs, "cover_image")) {
            Blob coverBlob = rs.getBlob("cover_image");
            if (coverBlob != null) {
                try {
                    play.setCoverImageData(coverBlob.getBytes(1, (int) coverBlob.length()));
                } finally {
                    coverBlob.free();
                }
            }
        }

        return play;
    }

    public Optional<UserStatistics> getUserStatistics(int accountId) {
        LOGGER.fine("Calculating statistics for account ID: " + accountId);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(GET_USER_STATS_SQL)) {

            pstmt.setInt(1, accountId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {

                    UserStatistics stats = UserStatistics.builder()
                            .totalSpins(rs.getLong("total_spins"))
                            .totalWagered(rs.getBigDecimal("total_wagered"))
                            .totalWon(rs.getBigDecimal("total_won"))
                            .distinctGamesPlayed(rs.getLong("distinct_games_played"))
                            .build();
                    LOGGER.fine("Statistics calculated successfully for account ID: " + accountId);
                    return Optional.of(stats);
                } else {

                    LOGGER.warning("Query for user statistics returned no rows for account ID: " + accountId + ". Returning empty stats.");

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
            return Optional.empty();
        }
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