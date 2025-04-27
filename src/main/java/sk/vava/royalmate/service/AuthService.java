package sk.vava.royalmate.service;

import sk.vava.royalmate.data.AccountDAO;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.util.PasswordUtil;
import sk.vava.royalmate.util.SessionManager;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;


public class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    private final AccountDAO accountDAO;

    // Basic email validation regex (adjust as needed for stricter validation)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    public AuthService() {
        this.accountDAO = new AccountDAO(); // Instantiate the DAO
    }

    // Constructor for dependency injection (optional, good for testing)
    public AuthService(AccountDAO accountDAO) {
        this.accountDAO = accountDAO;
    }

    /**
     * Authenticates a user based on username and password.
     *
     * @param username The username.
     * @param password The plain text password.
     * @return An Optional containing the authenticated Account if successful, otherwise empty.
     */
    public Optional<Account> authenticate(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            LOGGER.warning("Authentication attempt with empty username or password.");
            return Optional.empty();
        }

        Optional<Account> accountOpt = accountDAO.findByUsername(username);

        if (accountOpt.isPresent()) {
            Account account = accountOpt.get();
            if (PasswordUtil.checkPassword(password, account.getPasswordHash())) {
                LOGGER.info("Authentication successful for user: " + username);
                // Update last login time asynchronously (optional, prevents blocking login)
                // Consider using an ExecutorService for better thread management
                new Thread(() -> accountDAO.updateLastLogin(account.getId())).start();
                return Optional.of(account); // Return the authenticated account
            } else {
                LOGGER.warning("Authentication failed: Incorrect password for user: " + username);
                return Optional.empty();
            }
        } else {
            LOGGER.warning("Authentication failed: User not found: " + username);
            return Optional.empty();
        }
    }

    /**
     * Registers a new user account.
     *
     * @param email    User's email address.
     * @param username Desired username.
     * @param password Plain text password.
     * @return An Optional containing the newly created Account if successful, otherwise empty (e.g., if username/email exists).
     * @throws IllegalArgumentException if input validation fails (e.g., invalid email, weak password if check added).
     * @throws RuntimeException if a database error occurs during save.
     */
    public Optional<Account> register(String email, String username, String password) throws IllegalArgumentException, RuntimeException {
        // --- Input Validation ---
        if (username == null || username.trim().isEmpty() ||
                email == null || email.trim().isEmpty() ||
                password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Username, email, and password cannot be empty.");
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format.");
        }

        // Add password complexity rules here if desired (e.g., length, characters)
        // if (password.length() < 8) { throw new IllegalArgumentException("Password must be at least 8 characters long."); }

        // --- Check for Existing User/Email ---
        if (accountDAO.findByUsername(username).isPresent()) {
            LOGGER.warning("Registration failed: Username already exists: " + username);
            return Optional.empty(); // Indicate username conflict
        }
        if (accountDAO.findByEmail(email).isPresent()) {
            LOGGER.warning("Registration failed: Email already exists: " + email);
            return Optional.empty(); // Indicate email conflict
        }

        // --- Create Account ---
        String hashedPassword = PasswordUtil.hashPassword(password);

        Account newAccount = Account.builder()
                .username(username)
                .email(email)
                .passwordHash(hashedPassword)
                .balance(BigDecimal.ZERO) // Default balance
                .profilePictureColor("#CCCCCC") // Default color
                .isAdmin(false) // Default role
                // createdAt and lastLoginAt are set by DAO or DB default
                .build();

        boolean saved = accountDAO.save(newAccount);

        if (saved) {
            LOGGER.info("Registration successful for user: " + username);
            // Fetch the newly created account to return it (including ID, timestamps)
            // This assumes save doesn't return the full object, which is common.
            return accountDAO.findByUsername(username); // Re-fetch to get complete data
        } else {
            LOGGER.severe("Registration failed: Could not save account for user: " + username);
            // This might indicate a database issue beyond duplicate keys
            throw new RuntimeException("Failed to save the new account due to a database error.");
        }
    }

    // --- NEW METHODS ---

    /**
     * Attempts to withdraw funds from the user's account.
     *
     * @param accountId The ID of the account.
     * @param amount    The amount to withdraw (must be positive).
     * @return True if withdrawal successful, false otherwise (e.g., insufficient funds, invalid amount, DB error).
     * @throws IllegalArgumentException if amount is invalid (null, zero, negative).
     * @throws IllegalStateException if the current balance cannot be retrieved.
     */
    public boolean withdrawFunds(int accountId, BigDecimal amount) throws IllegalArgumentException, IllegalStateException {
        LOGGER.info("Withdrawal attempt for account ID: " + accountId + ", Amount: " + amount);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive.");
        }

        // Fetch the LATEST account details for accurate balance check
        Account currentAccount = accountDAO.findByUsername(SessionManager.getCurrentAccount().getUsername())
                .orElseThrow(() -> new IllegalStateException("Cannot retrieve current account data for withdrawal."));

        BigDecimal currentBalance = currentAccount.getBalance();
        if (currentBalance == null) {
            throw new IllegalStateException("Current account balance is unexpectedly null.");
        }

        // Check for sufficient funds
        if (currentBalance.compareTo(amount) < 0) {
            LOGGER.warning("Withdrawal failed for account ID: " + accountId + ". Insufficient funds.");
            return false; // Indicate insufficient funds
        }

        // Perform withdrawal by subtracting the amount (adding negative amount)
        boolean success = accountDAO.updateBalance(accountId, amount.negate());

        if (success) {
            LOGGER.info("Withdrawal successful for account ID: " + accountId + ", Amount: " + amount);
            // Refresh session data with the updated account info
            SessionManager.setCurrentAccount(accountDAO.findByUsername(currentAccount.getUsername()).orElse(null)); // Re-fetch
            return true;
        } else {
            LOGGER.severe("Withdrawal failed during database update for account ID: " + accountId);
            return false; // Indicate database error
        }
    }

    /**
     * Changes the user's password after verifying the old one.
     *
     * @param accountId        The ID of the account.
     * @param oldPasswordPlain The current plain text password.
     * @param newPasswordPlain The new plain text password.
     * @return True if password change was successful, false otherwise (e.g., old password incorrect, DB error).
     * @throws IllegalArgumentException if new password is null or empty.
     */
    public boolean changePassword(int accountId, String oldPasswordPlain, String newPasswordPlain) throws IllegalArgumentException {
        LOGGER.info("Password change attempt for account ID: " + accountId);

        if (newPasswordPlain == null || newPasswordPlain.isEmpty()) {
            throw new IllegalArgumentException("New password cannot be empty.");
        }
        // Add complexity checks for newPasswordPlain if needed here

        Account currentAccount = SessionManager.getCurrentAccount(); // Get from session first
        if (currentAccount == null || currentAccount.getId() != accountId) {
            // Fetch from DB if session is invalid or doesn't match (shouldn't happen ideally)
            currentAccount = accountDAO.findByUsername(SessionManager.getCurrentAccount().getUsername())
                    .filter(acc -> acc.getId() == accountId)
                    .orElse(null);
            if (currentAccount == null) {
                LOGGER.severe("Cannot find account to change password for ID: " + accountId);
                return false;
            }
        }


        // 1. Verify old password
        if (!PasswordUtil.checkPassword(oldPasswordPlain, currentAccount.getPasswordHash())) {
            LOGGER.warning("Password change failed for account ID: " + accountId + ". Old password incorrect.");
            return false; // Indicate wrong old password
        }

        // 2. Hash the new password
        String newPasswordHash = PasswordUtil.hashPassword(newPasswordPlain);

        // 3. Update in database
        boolean success = accountDAO.updatePasswordHash(accountId, newPasswordHash);

        if (success) {
            LOGGER.info("Password change successful for account ID: " + accountId);
            // Re-fetch and update session data as password hash changed
            SessionManager.setCurrentAccount(accountDAO.findByUsername(currentAccount.getUsername()).orElse(null));
            return true;
        } else {
            LOGGER.severe("Password change failed during database update for account ID: " + accountId);
            return false; // Indicate database error
        }
    }

    // Placeholder for Statistics (implement later)
    public java.util.Map<String, Long> getUserStatistics(int accountId) {
        LOGGER.fine("Fetching placeholder statistics for account ID: " + accountId);
        // Replace with actual data retrieval when gameplay is implemented
        return java.util.Map.of(
                "totalSpins", 0L,
                "totalWins", 0L,
                "gamesPlayed", 0L
        );
    }

    // --- END NEW METHODS ---

}