package sk.vava.royalmate.util;

import sk.vava.royalmate.bcrypt.BCrypt;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PasswordUtil {

    private static final Logger LOGGER = Logger.getLogger(PasswordUtil.class.getName());
    private static final int BCRYPT_WORKLOAD = 12; // Standard workload factor (adjust if needed)

    /**
     * Hashes a plain text password using BCrypt.
     *
     * @param plainPassword The password to hash.
     * @return The generated hash string.
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            LOGGER.warning("Attempted to hash null or empty password.");
            // Decide how to handle this: throw exception or return null/empty?
            // Throwing is often better to signal invalid input.
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        String salt = BCrypt.gensalt(BCRYPT_WORKLOAD);
        String hashedPassword = BCrypt.hashpw(plainPassword, salt);
        LOGGER.fine("Password hashed successfully.");
        return hashedPassword;
    }

    /**
     * Checks if a plain text password matches a stored BCrypt hash.
     *
     * @param plainPassword  The password entered by the user.
     * @param hashedPassword The hash stored in the database.
     * @return true if the password matches the hash, false otherwise.
     */
    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null || plainPassword.isEmpty() || hashedPassword.isEmpty()) {
            LOGGER.warning("Attempted to check null or empty password/hash.");
            return false; // Or throw an exception depending on desired behavior
        }
        try {
            boolean matches = BCrypt.checkpw(plainPassword, hashedPassword);
            LOGGER.fine("Password check performed. Match: " + matches);
            return matches;
        } catch (IllegalArgumentException e) {
            // BCrypt throws this if the hash is invalid format
            LOGGER.log(Level.WARNING, "Invalid hash format provided for password check.", e);
            return false;
        }
    }

    // Private constructor to prevent instantiation
    private PasswordUtil() {}
}