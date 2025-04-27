package sk.vava.royalmate.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import sk.vava.royalmate.model.Account;
import java.util.logging.Logger;


public class SessionManager {

    private static final Logger LOGGER = Logger.getLogger(SessionManager.class.getName());

    // --- Use JavaFX Property ---
    // Holds the current account, notifies listeners on change. Initialized to null.
    private static final ObjectProperty<Account> currentAccountProperty = new SimpleObjectProperty<>(null);
    // --------------------------

    // Private constructor to prevent instantiation
    private SessionManager() {}

    /**
     * Sets the currently logged-in account. This will notify any listeners
     * attached to the currentAccountProperty.
     *
     * @param account The account object of the logged-in user, or null to log out.
     */
    public static void setCurrentAccount(Account account) {
        String oldUser = isLoggedIn() ? getCurrentAccount().getUsername() : "null";
        String newUser = account != null ? account.getUsername() : "null";
        LOGGER.info("Setting current account in SessionManager. Old: " + oldUser + ", New: " + newUser);
        currentAccountProperty.set(account); // Set the property value
    }

    /**
     * Gets the currently logged-in account object.
     * @return The Account object, or null if no user is logged in.
     */
    public static Account getCurrentAccount() {
        return currentAccountProperty.get(); // Get value from property
    }

    /**
     * Returns the JavaFX property holding the current account.
     * Controllers can add listeners to this property to react to login/logout/updates.
     * @return The ObjectProperty wrapping the current Account.
     */
    public static ObjectProperty<Account> currentAccountProperty() {
        return currentAccountProperty; // Return the property itself
    }


    /**
     * Checks if a user is currently logged in.
     * @return true if a user session exists, false otherwise.
     */
    public static boolean isLoggedIn() {
        return currentAccountProperty.get() != null; // Check property's value
    }

    /**
     * Logs out the current user by setting the account property to null.
     */
    public static void logout() {
        LOGGER.info("Logging out user in SessionManager.");
        setCurrentAccount(null); // Calls set which updates the property
    }

    /**
     * Checks if the currently logged-in user is an administrator.
     * @return true if logged in and the user is an admin, false otherwise.
     */
    public static boolean isAdmin() {
        Account account = currentAccountProperty.get(); // Get value
        return account != null && account.isAdmin();
    }
}