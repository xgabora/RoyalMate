package sk.vava.royalmate.service;

import sk.vava.royalmate.data.AccountDAO;
import sk.vava.royalmate.data.GameplayDAO; // Import GameplayDAO
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.model.UserStatistics; // Import UserStatistics
import sk.vava.royalmate.util.PasswordUtil;
import sk.vava.royalmate.util.SessionManager;

import java.math.BigDecimal;
// Removed: import java.util.Collections;
// Removed: import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    private final AccountDAO accountDAO;
    private final GameplayDAO gameplayDAO;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    public AuthService() {
        this.accountDAO = new AccountDAO();
        this.gameplayDAO = new GameplayDAO();
    }

    public AuthService(AccountDAO accountDAO, GameplayDAO gameplayDAO) {
        this.accountDAO = accountDAO;
        this.gameplayDAO = gameplayDAO;
    }

    public Optional<Account> authenticate(String username, String password) {
        // ... keep existing implementation ...
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) { return Optional.empty(); }
        Optional<Account> accountOpt = accountDAO.findByUsername(username);
        if (accountOpt.isPresent()) {
            Account account = accountOpt.get();
            if (PasswordUtil.checkPassword(password, account.getPasswordHash())) {
                LOGGER.info("Authentication successful for user: " + username);
                new Thread(() -> accountDAO.updateLastLogin(account.getId())).start();
                return Optional.of(account);
            } else { LOGGER.warning("Authentication failed: Incorrect password for user: " + username); return Optional.empty(); }
        } else { LOGGER.warning("Authentication failed: User not found: " + username); return Optional.empty(); }
    }


    public Optional<Account> register(String email, String username, String password) throws IllegalArgumentException, RuntimeException {
        // ... keep existing implementation ...
        if (username == null || username.trim().isEmpty() || email == null || email.trim().isEmpty() || password == null || password.isEmpty()) { throw new IllegalArgumentException("Username, email, and password cannot be empty."); }
        if (!EMAIL_PATTERN.matcher(email).matches()) { throw new IllegalArgumentException("Invalid email format."); }
        if (accountDAO.findByUsername(username).isPresent()) { LOGGER.warning("Registration failed: Username already exists: " + username); return Optional.empty(); }
        if (accountDAO.findByEmail(email).isPresent()) { LOGGER.warning("Registration failed: Email already exists: " + email); return Optional.empty(); }
        String hashedPassword = PasswordUtil.hashPassword(password);
        Account newAccount = Account.builder().username(username).email(email).passwordHash(hashedPassword).balance(BigDecimal.ZERO).profilePictureColor("#CCCCCC").isAdmin(false).build();
        boolean saved = accountDAO.save(newAccount);
        if (saved) { LOGGER.info("Registration successful for user: " + username); return accountDAO.findByUsername(username); }
        else { LOGGER.severe("Registration failed: Could not save account for user: " + username); throw new RuntimeException("Failed to save the new account due to a database error."); }
    }


    /**
     * Retrieves calculated user statistics from the database.
     * THIS IS THE CORRECT METHOD.
     *
     * @param accountId The ID of the user.
     * @return An Optional containing the UserStatistics object, or empty if an error occurred.
     */
    public Optional<UserStatistics> getUserStatistics(int accountId) {
        LOGGER.fine("Fetching statistics via service for account ID: " + accountId);
        return gameplayDAO.getUserStatistics(accountId);
    }


    public boolean withdrawFunds(int accountId, BigDecimal amount) throws IllegalArgumentException, IllegalStateException {
        // ... keep existing implementation ...
        LOGGER.info("Withdrawal attempt for account ID: " + accountId + ", Amount: " + amount);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) { throw new IllegalArgumentException("Withdrawal amount must be positive."); }
        Account currentAccount = accountDAO.findByUsername(SessionManager.getCurrentAccount().getUsername()).orElseThrow(() -> new IllegalStateException("Cannot retrieve current account data for withdrawal."));
        BigDecimal currentBalance = currentAccount.getBalance(); if (currentBalance == null) { throw new IllegalStateException("Current account balance is unexpectedly null."); }
        if (currentBalance.compareTo(amount) < 0) { LOGGER.warning("Withdrawal failed for account ID: " + accountId + ". Insufficient funds."); return false; }
        boolean success = accountDAO.updateBalance(accountId, amount.negate());
        if (success) { LOGGER.info("Withdrawal successful for account ID: " + accountId + ", Amount: " + amount); SessionManager.setCurrentAccount(accountDAO.findByUsername(currentAccount.getUsername()).orElse(null)); return true; }
        else { LOGGER.severe("Withdrawal failed during database update for account ID: " + accountId); return false; }
    }


    public boolean changePassword(int accountId, String oldPasswordPlain, String newPasswordPlain) throws IllegalArgumentException {
        // ... keep existing implementation ...
        LOGGER.info("Password change attempt for account ID: " + accountId);
        if (newPasswordPlain == null || newPasswordPlain.isEmpty()) { throw new IllegalArgumentException("New password cannot be empty."); }
        Account currentAccount = SessionManager.getCurrentAccount();
        if (currentAccount == null || currentAccount.getId() != accountId) { currentAccount = accountDAO.findByUsername(SessionManager.getCurrentAccount().getUsername()).filter(acc -> acc.getId() == accountId).orElse(null); if (currentAccount == null) { LOGGER.severe("Cannot find account to change password for ID: " + accountId); return false; }}
        if (!PasswordUtil.checkPassword(oldPasswordPlain, currentAccount.getPasswordHash())) { LOGGER.warning("Password change failed for account ID: " + accountId + ". Old password incorrect."); return false; }
        String newPasswordHash = PasswordUtil.hashPassword(newPasswordPlain);
        boolean success = accountDAO.updatePasswordHash(accountId, newPasswordHash);
        if (success) { LOGGER.info("Password change successful for account ID: " + accountId); SessionManager.setCurrentAccount(accountDAO.findByUsername(currentAccount.getUsername()).orElse(null)); return true; }
        else { LOGGER.severe("Password change failed during database update for account ID: " + accountId); return false; }
    }


    // --- THIS IS THE OLD PLACEHOLDER METHOD TO REMOVE ---
    // public java.util.Map<String, Long> getUserStatistics(int accountId) {
    //     LOGGER.fine("Fetching placeholder statistics for account ID: " + accountId);
    //     return java.util.Map.of(
    //             "totalSpins", 0L,
    //             "totalWins", 0L,
    //             "gamesPlayed", 0L
    //     );
    // }
    // ------------------------------------------------------
}