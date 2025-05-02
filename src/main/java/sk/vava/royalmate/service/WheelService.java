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

    public WheelService(AccountDAO accountDAO) {
        this.accountDAO = accountDAO;
    }

    public boolean isEligibleToSpin(Account account) {
        if (account == null) {
            return false;
        }
        Timestamp lastSpinTimestamp = account.getLastWofSpinAt();
        if (lastSpinTimestamp == null) {
            return true;
        }
        Instant lastSpinInstant = lastSpinTimestamp.toInstant();
        Instant now = Instant.now();
        Duration timeSinceLastSpin = Duration.between(lastSpinInstant, now);
        return timeSinceLastSpin.compareTo(WOF_COOLDOWN) >= 0;
    }

    public boolean performSpin(int accountId, BigDecimal prizeAmount) {
        LOGGER.info("Performing WoF spin for account ID: " + accountId + ", Prize: " + prizeAmount);

        boolean balanceUpdated = accountDAO.updateBalance(accountId, prizeAmount);
        if (!balanceUpdated) {
            LOGGER.severe("Failed to update balance during WoF spin for account ID: " + accountId);
            return false;
        }

        boolean spinTimeUpdated = accountDAO.updateLastWofSpinTimestamp(accountId);
        if (!spinTimeUpdated) {

            LOGGER.warning("Failed to update last WoF spin timestamp for account ID: " + accountId + " after balance update.");

        }

        Optional<Account> updatedAccountOpt = accountDAO.findByUsername(SessionManager.getCurrentAccount().getUsername());
        if (updatedAccountOpt.isPresent()) {
            SessionManager.setCurrentAccount(updatedAccountOpt.get());
            LOGGER.info("Session data refreshed after WoF spin for account ID: " + accountId);
        } else {

            LOGGER.severe("Could not find account to refresh session after WoF spin for account ID: " + accountId);

        }

        return true;
    }
}