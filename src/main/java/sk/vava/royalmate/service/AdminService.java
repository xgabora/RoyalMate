package sk.vava.royalmate.service;

import sk.vava.royalmate.data.AccountDAO;
import sk.vava.royalmate.data.ChatMessageDAO; // Placeholder DAO
import sk.vava.royalmate.data.HomepageBannerDAO; // Placeholder DAO
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.model.ChatMessage; // Placeholder Model
import sk.vava.royalmate.model.HomepageBanner; // Placeholder Model
import sk.vava.royalmate.util.SessionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class AdminService {
    private static final Logger LOGGER = Logger.getLogger(AdminService.class.getName());

    private final AccountDAO accountDAO;
    private final HomepageBannerDAO bannerDAO; // Placeholder
    private final ChatMessageDAO chatMessageDAO; // Placeholder

    public AdminService() {
        this.accountDAO = new AccountDAO();
        this.bannerDAO = new HomepageBannerDAO(); // Instantiate placeholders
        this.chatMessageDAO = new ChatMessageDAO(); // Instantiate placeholders
    }

    // --- Player Management ---
    public List<Account> getAllPlayers() {
        // Optionally filter out the current admin? For now, return all.
        return accountDAO.findAll();
    }

    public boolean addFundsToPlayer(int targetAccountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warning("Admin add funds failed: Invalid amount " + amount);
            return false;
        }
        LOGGER.info("Admin adding " + amount + " to account ID: " + targetAccountId);
        return accountDAO.updateBalance(targetAccountId, amount);
    }

    public boolean subtractFundsFromPlayer(int targetAccountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warning("Admin subtract funds failed: Invalid amount " + amount);
            return false;
        }
        // Fetch current balance to prevent negative balance if desired? Or allow it for admin?
        // For now, allow potential negative balance via admin action.
        LOGGER.info("Admin subtracting " + amount + " from account ID: " + targetAccountId);
        return accountDAO.updateBalance(targetAccountId, amount.negate());
    }

    public boolean setPlayerAdminStatus(int targetAccountId, boolean makeAdmin) {
        // Prevent admin from demoting themselves?
        Account currentAdmin = SessionManager.getCurrentAccount();
        if(currentAdmin != null && currentAdmin.getId() == targetAccountId && !makeAdmin) {
            LOGGER.warning("Admin attempted to remove their own admin status. Action denied.");
            return false;
        }
        LOGGER.info("Admin setting admin status to " + makeAdmin + " for account ID: " + targetAccountId);
        return accountDAO.updateAdminStatus(targetAccountId, makeAdmin);
    }

    public boolean deletePlayerAccount(int targetAccountId) {
        // Prevent admin from deleting themselves?
        Account currentAdmin = SessionManager.getCurrentAccount();
        if(currentAdmin != null && currentAdmin.getId() == targetAccountId) {
            LOGGER.warning("Admin attempted to delete their own account. Action denied.");
            return false;
        }
        LOGGER.warning("Admin deleting account ID: " + targetAccountId);
        return accountDAO.deleteAccount(targetAccountId);
    }

    // --- Chat Management ---
    public Optional<ChatMessage> getPinnedMessage() {
        LOGGER.fine("Fetching pinned chat message.");
        return chatMessageDAO.findPinnedMessage(); // Placeholder call
    }

    public boolean updatePinnedMessage(String messageText) {
        Account currentAdmin = SessionManager.getCurrentAccount();
        if (currentAdmin == null) return false; // Should have admin session

        Optional<ChatMessage> existingPinnedOpt = getPinnedMessage();
        if(existingPinnedOpt.isPresent()) {
            LOGGER.info("Updating existing pinned message.");
            return chatMessageDAO.updatePinnedMessage(existingPinnedOpt.get().getId(), messageText, currentAdmin.getId()); // Placeholder call
        } else {
            LOGGER.info("Inserting new pinned message.");
            return chatMessageDAO.insertPinnedMessage(messageText, currentAdmin.getId()); // Placeholder call
        }
    }

    // --- Banner Management ---
    public Optional<HomepageBanner> getMainBanner() {
        LOGGER.fine("Fetching main banner.");
        return bannerDAO.findMainBanner(); // Placeholder call
    }

    public boolean updateMainBanner(String bannerName, byte[] imageData) {
        Account currentAdmin = SessionManager.getCurrentAccount();
        if (currentAdmin == null) return false;
        LOGGER.info("Admin updating main banner.");
        return bannerDAO.saveOrUpdateMainBanner(bannerName, imageData, currentAdmin.getId()); // Placeholder call
    }
}