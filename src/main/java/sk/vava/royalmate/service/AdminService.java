package sk.vava.royalmate.service;

import sk.vava.royalmate.data.*;
import sk.vava.royalmate.model.*;
import sk.vava.royalmate.util.SessionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminService {
    private static final Logger LOGGER = Logger.getLogger(AdminService.class.getName());

    private final AccountDAO accountDAO;
    private final HomepageBannerDAO bannerDAO;
    private final ChatMessageDAO chatMessageDAO;
    private final GameDAO gameDAO;
    private final GameAssetDAO gameAssetDAO;

    public AdminService() {

        this.accountDAO = new AccountDAO();
        this.bannerDAO = new HomepageBannerDAO();
        this.chatMessageDAO = new ChatMessageDAO();
        this.gameDAO = new GameDAO();
        this.gameAssetDAO = new GameAssetDAO();
    }

    public List<Account> getAllPlayers() {
        LOGGER.fine("Fetching all players.");
        return accountDAO.findAll();
    }

    public boolean addFundsToPlayer(int targetAccountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warning("Admin add funds failed: Invalid amount " + amount);
            return false;
        }
        LOGGER.info("Admin adding " + amount + " to account ID: " + targetAccountId);
        boolean success = accountDAO.updateBalance(targetAccountId, amount);

        return success;
    }

    public boolean subtractFundsFromPlayer(int targetAccountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warning("Admin subtract funds failed: Invalid amount " + amount);
            return false;
        }
        LOGGER.info("Admin subtracting " + amount + " from account ID: " + targetAccountId);
        boolean success = accountDAO.updateBalance(targetAccountId, amount.negate());

        return success;
    }

    public boolean setPlayerAdminStatus(int targetAccountId, boolean makeAdmin) {
        Account currentAdmin = SessionManager.getCurrentAccount();

        if(currentAdmin != null && currentAdmin.getId() == targetAccountId && !makeAdmin) {
            LOGGER.warning("Admin attempted to remove their own admin status. Action denied.");
            return false;
        }
        LOGGER.info("Admin setting admin status to " + makeAdmin + " for account ID: " + targetAccountId);
        return accountDAO.updateAdminStatus(targetAccountId, makeAdmin);
    }

    public boolean deletePlayerAccount(int targetAccountId) {
        Account currentAdmin = SessionManager.getCurrentAccount();

        if(currentAdmin != null && currentAdmin.getId() == targetAccountId) {
            LOGGER.warning("Admin attempted to delete their own account. Action denied.");
            return false;
        }
        LOGGER.warning("Admin deleting account ID: " + targetAccountId);

        return accountDAO.deleteAccount(targetAccountId);
    }

    public Optional<ChatMessage> getPinnedMessage() {
        LOGGER.fine("Fetching pinned chat message.");
        return chatMessageDAO.findPinnedMessage();
    }

    public boolean updatePinnedMessage(String messageText) {
        Account currentAdmin = SessionManager.getCurrentAccount();
        if (currentAdmin == null || !currentAdmin.isAdmin()) {
            LOGGER.warning("Unauthorized attempt to update pinned message.");
            return false;
        }

        Optional<ChatMessage> existingPinnedOpt = getPinnedMessage();
        boolean success;
        if(existingPinnedOpt.isPresent()) {
            LOGGER.info("Updating existing pinned message ID: " + existingPinnedOpt.get().getId());
            success = chatMessageDAO.updatePinnedMessage(existingPinnedOpt.get().getId(), messageText, currentAdmin.getId());
        } else {
            LOGGER.info("Inserting new pinned message.");
            success = chatMessageDAO.insertPinnedMessage(messageText, currentAdmin.getId());
        }
        if (!success) {
            LOGGER.severe("Failed to update/insert pinned message in DAO layer.");
        }
        return success;
    }

    public Optional<HomepageBanner> getMainBanner() {
        LOGGER.fine("Fetching main banner.");
        return bannerDAO.findMainBanner();
    }

    public boolean updateMainBanner(String bannerName, byte[] imageData) {
        Account currentAdmin = SessionManager.getCurrentAccount();
        if (currentAdmin == null || !currentAdmin.isAdmin()) {
            LOGGER.warning("Unauthorized attempt to update main banner.");
            return false;
        }
        if (imageData == null || imageData.length == 0) {
            LOGGER.warning("Attempt to update banner with empty image data denied.");
            return false;
        }
        LOGGER.info("Admin ID " + currentAdmin.getId() + " updating main banner.");
        boolean success = bannerDAO.saveOrUpdateMainBanner(bannerName, imageData, currentAdmin.getId());
        if (!success) {
            LOGGER.severe("Failed to update/insert main banner in DAO layer.");
        }
        return success;
    }

    public List<Game> getAllGames() {
        LOGGER.fine("Fetching all games.");
        return gameDAO.findAllSortedByDateDesc();
    }

    public List<Game> getAllGamesWithStats() {
        LOGGER.fine("Fetching all games with stats for export.");
        return gameDAO.findAllWithStats();
    }

    public boolean createGame(Game gameData, GameAsset coverAsset, List<GameAsset> symbolAssets) {
        Account currentAdmin = SessionManager.getCurrentAccount();
        if (currentAdmin == null || !currentAdmin.isAdmin()) {
            LOGGER.warning("Unauthorized attempt to create game.");
            return false;
        }
        if (coverAsset == null || coverAsset.getImageData() == null || coverAsset.getImageData().length == 0) {
            LOGGER.severe("Game creation failed: Cover image data is mandatory.");
            return false;
        }

        LOGGER.info("Admin ID " + currentAdmin.getId() + " attempting to create game: " + gameData.getName());

        gameData.setCreatedByAdminId(currentAdmin.getId());
        gameData.setActive(true);

        int gameId = gameDAO.save(gameData);
        if (gameId <= 0) {
            LOGGER.severe("Failed to save core game data for: " + gameData.getName());
            return false;
        }

        coverAsset.setGameId(gameId);
        coverAsset.setAssetType(AssetType.COVER);
        if (!gameAssetDAO.save(coverAsset)) {
            LOGGER.severe("Failed to save MANDATORY COVER asset for new game ID: " + gameId + ". Attempting cleanup.");

            gameDAO.delete(gameId);
            return false;
        }

        if (symbolAssets != null && !symbolAssets.isEmpty()) {
            LOGGER.fine("Saving " + symbolAssets.size() + " symbol assets for game ID: " + gameId);
            int symbolsSavedCount = 0;
            for (GameAsset symbol : symbolAssets) {
                if (symbol != null && symbol.getImageData() != null) {
                    symbol.setGameId(gameId);
                    symbol.setAssetType(AssetType.SYMBOL);
                    if (gameAssetDAO.save(symbol)) {
                        symbolsSavedCount++;
                    } else {
                        LOGGER.warning("Failed to save a SYMBOL asset for game ID: " + gameId + ". Name: " + symbol.getAssetName());
                    }
                }
            }
            LOGGER.info("Saved " + symbolsSavedCount + "/" + symbolAssets.size() + " symbol assets for game ID: " + gameId);
        }

        return true;
    }

    public boolean deleteGame(int gameId) {
        Account currentAdmin = SessionManager.getCurrentAccount();
        if (currentAdmin == null || !currentAdmin.isAdmin()) {
            LOGGER.warning("Unauthorized attempt to delete game ID: " + gameId);
            return false;
        }
        LOGGER.warning("Admin ID " + currentAdmin.getId() + " attempting to delete game ID: " + gameId);

        return gameDAO.delete(gameId);
    }

    public Optional<Game> getGameDetails(int gameId) {
        LOGGER.fine("Fetching details for game ID: " + gameId);
        Optional<Game> gameOpt = gameDAO.findById(gameId);

        return gameOpt;
    }

    public List<GameAsset> getGameAssets(int gameId) {
        LOGGER.fine("Fetching all assets for game ID: " + gameId);
        return gameAssetDAO.findByGameId(gameId);
    }

    public boolean updateGame(Game gameData, Optional<GameAsset> newCoverAsset, List<GameAsset> newSymbolAssets) {
        Account currentAdmin = SessionManager.getCurrentAccount();
        if (currentAdmin == null || !currentAdmin.isAdmin()) {
            LOGGER.warning("Unauthorized attempt to update game.");
            return false;
        }
        if (gameData == null || gameData.getId() <= 0) {
            LOGGER.severe("Update game failed: Invalid game data or missing ID.");
            return false;
        }
        int gameId = gameData.getId();
        LOGGER.info("Admin ID " + currentAdmin.getId() + " attempting to update game ID: " + gameId);

        boolean gameUpdated = gameDAO.update(gameData);
        if (!gameUpdated) {
            LOGGER.severe("Failed to update core game data for ID: " + gameId);
            return false;
        }

        if (newCoverAsset.isPresent()) {
            GameAsset cover = newCoverAsset.get();
            if (cover.getImageData() != null && cover.getImageData().length > 0) {
                LOGGER.fine("Updating cover image for game ID: " + gameId);

                cover.setGameId(gameId);
                cover.setAssetType(AssetType.COVER);
                if (!gameAssetDAO.save(cover)) {
                    LOGGER.warning("Failed to update/save COVER asset for game ID: " + gameId);

                }
            }
        }

        if (gameData.getGameType() == GameType.SLOT) {
            LOGGER.fine("Replacing symbol assets for game ID: " + gameId);

            boolean deletedOld = gameAssetDAO.deleteByGameIdAndType(gameId, AssetType.SYMBOL);
            if (!deletedOld) {
                LOGGER.warning("Failed to delete old SYMBOL assets for game ID: " + gameId + ". Update may be inconsistent.");

            }

            if (newSymbolAssets != null && !newSymbolAssets.isEmpty()) {
                LOGGER.fine("Saving " + newSymbolAssets.size() + " new symbol assets for game ID: " + gameId);
                int symbolsSavedCount = 0;
                for (GameAsset symbol : newSymbolAssets) {
                    if (symbol != null && symbol.getImageData() != null) {
                        symbol.setGameId(gameId);
                        symbol.setAssetType(AssetType.SYMBOL);
                        if (gameAssetDAO.save(symbol)) {
                            symbolsSavedCount++;
                        } else {
                            LOGGER.warning("Failed to save a new SYMBOL asset during update for game ID: " + gameId + ". Name: " + symbol.getAssetName());
                        }
                    }
                }
                LOGGER.info("Saved " + symbolsSavedCount + "/" + newSymbolAssets.size() + " new symbol assets during update for game ID: " + gameId);
            }
        } else {

            LOGGER.fine("Game type is not SLOT, ensuring no symbol assets exist for game ID: " + gameId);
            gameAssetDAO.deleteByGameIdAndType(gameId, AssetType.SYMBOL);
        }

        return true;
    }

    public Map<String, Long> getUserStatistics(int accountId) {
        LOGGER.fine("Fetching placeholder statistics for account ID: " + accountId);
        return Map.of(
                "totalSpins", 0L,
                "totalWins", 0L,
                "gamesPlayed", 0L
        );
    }
}