package sk.vava.royalmate.util; // Adjust package if necessary

import sk.vava.royalmate.model.Account;

public class SessionManager {

    private static Account currentAccount = null;

    // Private constructor to prevent instantiation
    private SessionManager() {}

    public static void setCurrentAccount(Account account) {
        currentAccount = account;
    }

    public static Account getCurrentAccount() {
        return currentAccount;
    }

    public static boolean isLoggedIn() {
        return currentAccount != null;
    }

    public static void logout() {
        currentAccount = null;
    }

    public static boolean isAdmin() {
        return currentAccount != null && currentAccount.isAdmin();
    }
}
