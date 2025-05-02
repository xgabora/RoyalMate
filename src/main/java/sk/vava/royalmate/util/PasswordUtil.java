package sk.vava.royalmate.util;

import sk.vava.royalmate.bcrypt.BCrypt;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PasswordUtil {

    private static final Logger LOGGER = Logger.getLogger(PasswordUtil.class.getName());
    private static final int BCRYPT_WORKLOAD = 12;

    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            LOGGER.warning("Attempted to hash null or empty password.");

            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        String salt = BCrypt.gensalt(BCRYPT_WORKLOAD);
        String hashedPassword = BCrypt.hashpw(plainPassword, salt);
        LOGGER.fine("Password hashed successfully.");
        return hashedPassword;
    }

    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null || plainPassword.isEmpty() || hashedPassword.isEmpty()) {
            LOGGER.warning("Attempted to check null or empty password/hash.");
            return false;
        }
        try {
            boolean matches = BCrypt.checkpw(plainPassword, hashedPassword);
            LOGGER.fine("Password check performed. Match: " + matches);
            return matches;
        } catch (IllegalArgumentException e) {

            LOGGER.log(Level.WARNING, "Invalid hash format provided for password check.", e);
            return false;
        }
    }

    private PasswordUtil() {}
}