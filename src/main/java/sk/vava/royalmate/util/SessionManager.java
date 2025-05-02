package sk.vava.royalmate.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import sk.vava.royalmate.model.Account;
import java.util.logging.Logger;

public class SessionManager {

    private static final Logger LOGGER = Logger.getLogger(SessionManager.class.getName());

    private static final ObjectProperty<Account> currentAccountProperty = new SimpleObjectProperty<>(null);

    private SessionManager() {}

    public static void setCurrentAccount(Account account) {
        String oldUser = isLoggedIn() ? getCurrentAccount().getUsername() : "null";
        String newUser = account != null ? account.getUsername() : "null";
        LOGGER.info("Setting current account in SessionManager. Old: " + oldUser + ", New: " + newUser);
        currentAccountProperty.set(account);
    }

    public static Account getCurrentAccount() {
        return currentAccountProperty.get();
    }

    public static ObjectProperty<Account> currentAccountProperty() {
        return currentAccountProperty;
    }

    public static boolean isLoggedIn() {
        return currentAccountProperty.get() != null;
    }

    public static void logout() {
        LOGGER.info("Logging out user in SessionManager.");
        setCurrentAccount(null);
    }

    public static boolean isAdmin() {
        Account account = currentAccountProperty.get();
        return account != null && account.isAdmin();
    }
}