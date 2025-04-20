package sk.vava.royalmate.service;

import sk.vava.royalmate.data.AccountDAO;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.util.PasswordUtil;

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
}