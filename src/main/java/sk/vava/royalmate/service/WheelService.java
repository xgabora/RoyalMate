package sk.vava.royalmate.service;

import sk.vava.royalmate.data.AccountDAO;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.util.SessionManager;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WheelService {

    private static final Logger LOGGER = Logger.getLogger(WheelService.class.getName());
    private static final Duration WOF_COOLDOWN = Duration.ofHours(1);

    private final AccountDAO accountDAO;

    public WheelService() {
        this.accountDAO = new AccountDAO();
    }

    // Constructor for testing/dependency injection
    public WheelService(AccountDAO accountDAO) {
        this.accountDAO = accountDAO;
    }

    /**
     * Checks if the user is eligible to spin the Wheel of Fortune.
     *
     * @param account The user's Account object.
     * @return true if eligible, false otherwise.
     */
    public boolean isEligibleToSpin(Account account) {
        if (account == null) {
            return false;
        }
        Timestamp lastSpinTimestamp = account.getLastWofSpinAt();
        if (lastSpinTimestamp == null) {
            return true; // Never spun
        }
        Instant lastSpinInstant = lastSpinTimestamp.toInstant();
        Instant now = Instant.now();
        Duration timeSinceLastSpin = Duration.between(lastSpinInstant, now);
        return timeSinceLastSpin.compareTo(WOF_COOLDOWN) >= 0;
    }

    /**
     * Performs the spin actions: updates balance, updates spin time, refreshes session.
     * Assumes eligibility has already been checked by the caller (e.g., Controller).
     *
     * @param accountId   The ID of the user spinning.
     * @param prizeAmount The amount won.
     * @return true if all database updates were successful, false otherwise.
     */
    public boolean performSpin(int accountId, BigDecimal prizeAmount) {
        LOGGER.info("Performing WoF spin for account ID: " + accountId + ", Prize: " + prizeAmount);

        // 1. Update Balance
        boolean balanceUpdated = accountDAO.updateBalance(accountId, prizeAmount);
        if (!balanceUpdated) {
            LOGGER.severe("Failed to update balance during WoF spin for account ID: " + accountId);
            return false; // Stop if balance update fails
        }

        // 2. Update Last Spin Timestamp
        boolean spinTimeUpdated = accountDAO.updateLastWofSpinTimestamp(accountId);
        if (!spinTimeUpdated) {
            // This is less critical, but log a warning. The user got the money.
            // Potential inconsistency: user got money but might be able to spin again too soon.
            LOGGER.warning("Failed to update last WoF spin timestamp for account ID: " + accountId + " after balance update.");
            // Decide whether to return false here or allow it (returning false might confuse user who saw animation)
            // Let's return true for now, as the money was awarded, but log the inconsistency.
        }

        // 3. Refresh Session Data
        // Fetch the *latest* account data from DB to update the session accurately
        Optional<Account> updatedAccountOpt = accountDAO.findByUsername(SessionManager.getCurrentAccount().getUsername()); // Assuming username doesn't change
        if (updatedAccountOpt.isPresent()) {
            SessionManager.setCurrentAccount(updatedAccountOpt.get());
            LOGGER.info("Session data refreshed after WoF spin for account ID: " + accountId);
        } else {
            // Should ideally not happen if the user exists
            LOGGER.severe("Could not find account to refresh session after WoF spin for account ID: " + accountId);
            // Session might now be slightly out of date (timestamp/balance)
        }

        return true; // Indicate spin process (money award) completed
    }
}