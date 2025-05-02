package sk.vava.royalmate.data;

import sk.vava.royalmate.model.Account;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.ArrayList;
import java.util.List;

public class AccountDAO {

    private static final Logger LOGGER = Logger.getLogger(AccountDAO.class.getName());

    private static final String FIND_BY_USERNAME_SQL = "SELECT * FROM accounts WHERE username = ?";
    private static final String FIND_BY_EMAIL_SQL = "SELECT * FROM accounts WHERE email = ?";
    private static final String INSERT_ACCOUNT_SQL = "INSERT INTO accounts (username, password_hash, email, balance, profile_picture_color, is_admin, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_LAST_LOGIN_SQL = "UPDATE accounts SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?";
    private static final String UPDATE_LAST_WOF_SPIN_SQL = "UPDATE accounts SET last_wof_spin_at = CURRENT_TIMESTAMP WHERE id = ?";
    private static final String UPDATE_BALANCE_SQL = "UPDATE accounts SET balance = balance + ? WHERE id = ?";
    private static final String UPDATE_PASSWORD_HASH_SQL = "UPDATE accounts SET password_hash = ? WHERE id = ?";
    private static final String FIND_ALL_SQL = "SELECT * FROM accounts ORDER BY username ASC";
    private static final String UPDATE_ADMIN_STATUS_SQL = "UPDATE accounts SET is_admin = ? WHERE id = ?";
    private static final String DELETE_ACCOUNT_SQL = "DELETE FROM accounts WHERE id = ?";

    public Optional<Account> findByUsername(String username) {
        LOGGER.fine("Attempting to find account by username: " + username);

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
            return Optional.empty();
        }
    }

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

    public boolean save(Account account) {
        LOGGER.info("Attempting to save new account for username: " + account.getUsername());

        if (account.getPasswordHash() == null || account.getPasswordHash().isEmpty()) {
            LOGGER.severe("Attempted to save account without a password hash for username: " + account.getUsername());
            return false;
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT_ACCOUNT_SQL)) {

            pstmt.setString(1, account.getUsername());
            pstmt.setString(2, account.getPasswordHash());
            pstmt.setString(3, account.getEmail());
            pstmt.setBigDecimal(4, account.getBalance());
            pstmt.setString(5, account.getProfilePictureColor());
            pstmt.setBoolean(6, account.isAdmin());
            pstmt.setTimestamp(7, new Timestamp(System.currentTimeMillis()));

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                LOGGER.info("Successfully saved new account for username: " + account.getUsername());

                return true;
            } else {
                LOGGER.warning("Failed to save account, no rows affected for username: " + account.getUsername());
                return false;
            }
        } catch (SQLException e) {

            if (e.getErrorCode() == 1062) {
                LOGGER.warning("Failed to save account due to duplicate entry (username or email): " + account.getUsername() + " / " + account.getEmail());
            } else {
                LOGGER.log(Level.SEVERE, "Error saving account for username: " + account.getUsername(), e);
            }
            return false;
        }
    }

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
                return false;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating last login time for account ID: " + accountId, e);
            return false;
        }
    }

    public boolean updateLastWofSpinTimestamp(int accountId) {
        LOGGER.fine("Updating last WoF spin time for account ID: " + accountId);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_LAST_WOF_SPIN_SQL)) {

            pstmt.setInt(1, accountId);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                LOGGER.fine("Successfully updated last WoF spin time for account ID: " + accountId);

                return true;
            } else {
                LOGGER.warning("Failed to update last WoF spin time, account ID not found?: " + accountId);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating last WoF spin time for account ID: " + accountId, e);
            return false;
        }
    }

    public boolean updateBalance(int accountId, BigDecimal amountToAdd) {
        LOGGER.fine("Attempting to update balance for account ID: " + accountId + " by amount: " + amountToAdd);
        if (amountToAdd == null) {
            LOGGER.warning("Attempted to update balance with null amount for account ID: " + accountId);
            return false;
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_BALANCE_SQL)) {

            pstmt.setBigDecimal(1, amountToAdd);
            pstmt.setInt(2, accountId);

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                LOGGER.fine("Successfully updated balance for account ID: " + accountId);
                return true;
            } else {
                LOGGER.warning("Failed to update balance, account ID not found?: " + accountId);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating balance for account ID: " + accountId, e);
            return false;
        }
    }

    public boolean updatePasswordHash(int accountId, String newPasswordHash) {
        LOGGER.fine("Attempting to update password hash for account ID: " + accountId);
        if (newPasswordHash == null || newPasswordHash.isEmpty()) {
            LOGGER.warning("Attempted to update password with null or empty hash for account ID: " + accountId);
            return false;
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_PASSWORD_HASH_SQL)) {

            pstmt.setString(1, newPasswordHash);
            pstmt.setInt(2, accountId);

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                LOGGER.info("Successfully updated password hash for account ID: " + accountId);
                return true;
            } else {
                LOGGER.warning("Failed to update password hash, account ID not found?: " + accountId);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating password hash for account ID: " + accountId, e);
            return false;
        }
    }

    public List<Account> findAll() {
        LOGGER.fine("Attempting to find all accounts.");
        List<Account> accounts = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(FIND_ALL_SQL)) {

            while (rs.next()) {
                accounts.add(mapResultSetToAccount(rs));
            }
            LOGGER.fine("Found " + accounts.size() + " accounts.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding all accounts", e);

        }
        return accounts;
    }

    public boolean updateAdminStatus(int accountId, boolean isAdmin) {
        LOGGER.info("Attempting to set admin status to " + isAdmin + " for account ID: " + accountId);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_ADMIN_STATUS_SQL)) {

            pstmt.setBoolean(1, isAdmin);
            pstmt.setInt(2, accountId);

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                LOGGER.info("Successfully updated admin status for account ID: " + accountId);
                return true;
            } else {
                LOGGER.warning("Failed to update admin status, account ID not found?: " + accountId);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating admin status for account ID: " + accountId, e);
            return false;
        }
    }

    public boolean deleteAccount(int accountId) {
        LOGGER.warning("Attempting to DELETE account ID: " + accountId);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(DELETE_ACCOUNT_SQL)) {

            pstmt.setInt(1, accountId);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                LOGGER.info("Successfully deleted account ID: " + accountId);
                return true;
            } else {
                LOGGER.warning("Failed to delete account, account ID not found?: " + accountId);
                return false;
            }
        } catch (SQLException e) {

            LOGGER.log(Level.SEVERE, "Error deleting account ID: " + accountId + ". Possible FK constraints?", e);
            return false;
        }
    }

    private Account mapResultSetToAccount(ResultSet rs) throws SQLException {
        return Account.builder()
                .id(rs.getInt("id"))
                .username(rs.getString("username"))
                .passwordHash(rs.getString("password_hash"))
                .email(rs.getString("email"))
                .balance(rs.getBigDecimal("balance"))
                .profilePictureColor(rs.getString("profile_picture_color"))
                .isAdmin(rs.getBoolean("is_admin"))
                .createdAt(rs.getTimestamp("created_at"))
                .lastLoginAt(rs.getTimestamp("last_login_at"))
                .lastWofSpinAt(rs.getTimestamp("last_wof_spin_at"))
                .build();
    }
}