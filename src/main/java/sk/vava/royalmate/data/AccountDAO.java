package sk.vava.royalmate.data;

import sk.vava.royalmate.model.Account;

import java.sql.*;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AccountDAO {

    private static final Logger LOGGER = Logger.getLogger(AccountDAO.class.getName());

    // SQL Queries (using constants is good practice)
    private static final String FIND_BY_USERNAME_SQL = "SELECT * FROM accounts WHERE username = ?";
    private static final String FIND_BY_EMAIL_SQL = "SELECT * FROM accounts WHERE email = ?";
    private static final String INSERT_ACCOUNT_SQL = "INSERT INTO accounts (username, password_hash, email, balance, profile_picture_color, is_admin, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_LAST_LOGIN_SQL = "UPDATE accounts SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?";


    /**
     * Finds an account by its username.
     *
     * @param username The username to search for.
     * @return An Optional containing the Account if found, otherwise empty.
     */
    public Optional<Account> findByUsername(String username) {
        LOGGER.fine("Attempting to find account by username: " + username);
        // Use try-with-resources for Connection and PreparedStatement
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_BY_USERNAME_SQL)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Account account = mapResultSetToAccount(rs);
                    LOGGER.fine("Account found for username: " + username);
                    return Optional.of(account);
                } else {
                    LOGGER.fine("No account found for username: " + username);
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding account by username: " + username, e);
            return Optional.empty(); // Return empty on error
        }
    }

    /**
     * Finds an account by its email address.
     *
     * @param email The email to search for.
     * @return An Optional containing the Account if found, otherwise empty.
     */
    public Optional<Account> findByEmail(String email) {
        LOGGER.fine("Attempting to find account by email: " + email);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(FIND_BY_EMAIL_SQL)) {

            pstmt.setString(1, email);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Account account = mapResultSetToAccount(rs);
                    LOGGER.fine("Account found for email: " + email);
                    return Optional.of(account);
                } else {
                    LOGGER.fine("No account found for email: " + email);
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding account by email: " + email, e);
            return Optional.empty();
        }
    }


    /**
     * Saves a new account to the database.
     * Assumes the password in the Account object is already hashed.
     *
     * @param account The Account object to save (must have hashed password).
     * @return true if the insertion was successful, false otherwise.
     */
    public boolean save(Account account) {
        LOGGER.info("Attempting to save new account for username: " + account.getUsername());
        // IMPORTANT: Ensure passwordHash is set before calling this!
        if (account.getPasswordHash() == null || account.getPasswordHash().isEmpty()) {
            LOGGER.severe("Attempted to save account without a password hash for username: " + account.getUsername());
            return false;
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT_ACCOUNT_SQL)) {

            pstmt.setString(1, account.getUsername());
            pstmt.setString(2, account.getPasswordHash());
            pstmt.setString(3, account.getEmail());
            pstmt.setBigDecimal(4, account.getBalance()); // Should likely be 0 initially
            pstmt.setString(5, account.getProfilePictureColor()); // Default color
            pstmt.setBoolean(6, account.isAdmin()); // Should be false initially
            pstmt.setTimestamp(7, new Timestamp(System.currentTimeMillis())); // Set creation time

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                LOGGER.info("Successfully saved new account for username: " + account.getUsername());
                // Optionally retrieve and set the generated ID if needed later
                // try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) { ... }
                return true;
            } else {
                LOGGER.warning("Failed to save account, no rows affected for username: " + account.getUsername());
                return false;
            }
        } catch (SQLException e) {
            // Check for duplicate key violation (MySQL error code 1062)
            if (e.getErrorCode() == 1062) {
                LOGGER.warning("Failed to save account due to duplicate entry (username or email): " + account.getUsername() + " / " + account.getEmail());
            } else {
                LOGGER.log(Level.SEVERE, "Error saving account for username: " + account.getUsername(), e);
            }
            return false;
        }
    }

    /**
     * Updates the last login timestamp for a given account ID.
     *
     * @param accountId The ID of the account to update.
     * @return true if the update was successful, false otherwise.
     */
    public boolean updateLastLogin(int accountId) {
        LOGGER.fine("Updating last login time for account ID: " + accountId);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_LAST_LOGIN_SQL)) {

            pstmt.setInt(1, accountId);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                LOGGER.fine("Successfully updated last login time for account ID: " + accountId);
                return true;
            } else {
                LOGGER.warning("Failed to update last login time, account ID not found?: " + accountId);
                return false; // Account ID might not exist
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating last login time for account ID: " + accountId, e);
            return false;
        }
    }


    /**
     * Helper method to map a ResultSet row to an Account object.
     *
     * @param rs The ResultSet, positioned at a valid row.
     * @return The mapped Account object.
     * @throws SQLException If a database access error occurs.
     */
    private Account mapResultSetToAccount(ResultSet rs) throws SQLException {
        return Account.builder()
                .id(rs.getInt("id"))
                .username(rs.getString("username"))
                .passwordHash(rs.getString("password_hash")) // Corrected column name
                .email(rs.getString("email"))
                .balance(rs.getBigDecimal("balance"))
                .profilePictureColor(rs.getString("profile_picture_color")) // Corrected column name
                .isAdmin(rs.getBoolean("is_admin")) // Corrected column name
                .createdAt(rs.getTimestamp("created_at")) // Corrected column name
                .lastLoginAt(rs.getTimestamp("last_login_at")) // Corrected column name
                .build();
    }
}