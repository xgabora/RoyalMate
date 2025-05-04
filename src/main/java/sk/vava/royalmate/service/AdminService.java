package sk.vava.royalmate.service;

import sk.vava.royalmate.data.*; // Import all DAOs from the package
import sk.vava.royalmate.model.*; // Import all models from the package
import sk.vava.royalmate.util.SessionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;


public class AdminService {
    private static final Logger LOGGER = Logger.getLogger(AdminService.class.getName());

    // DAOs - Final makes sure they are initialized in the constructor
    private final AccountDAO accountDAO;
    private final HomepageBannerDAO bannerDAO;
    private final ChatMessageDAO chatMessageDAO;
    private final GameDAO gameDAO;
    private final GameAssetDAO gameAssetDAO;

    public AdminService() {
        // Instantiate all DAOs
        this.accountDAO = new AccountDAO();
        this.bannerDAO = new HomepageBannerDAO();
        this.chatMessageDAO = new ChatMessageDAO();
        this.gameDAO = new GameDAO();
        this.gameAssetDAO = new GameAssetDAO();
    }

    // --- Player Management ---

    /**
     * Retrieves all accounts from the database, ordered by username.
     * @return A List of Account objects. Returns an empty list on error.
     */
    public List<Account> getAllPlayers() {
        LOGGER.fine("Fetching all players.");
        return accountDAO.findAll();
    }

    /**
     * Adds funds to a specific player's account.
     * @param targetAccountId The ID of the player account.
     * @param amount The positive amount to add.
     * @return true if successful, false otherwise.
     */
    public boolean addFundsToPlayer(int targetAccountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warning("Admin add funds failed: Invalid amount " + amount);
            return false;
        }
        LOGGER.info("Admin adding " + amount + " to account ID: " + targetAccountId);
        boolean success = accountDAO.updateBalance(targetAccountId, amount);
        // Consider refreshing session if the updated account is the currently logged-in user?
        // Not strictly necessary for admin actions on *other* users.
        return success;
    }

    /**
     * Subtracts funds from a specific player's account. Allows balance to go negative via admin action.
     * @param targetAccountId The ID of the player account.
     * @param amount The positive amount to subtract.
     * @return true if successful, false otherwise.
     */
    public boolean subtractFundsFromPlayer(int targetAccountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warning("Admin subtract funds failed: Invalid amount " + amount);
            return false;
        }
        LOGGER.info("Admin subtracting " + amount + " from account ID: " + targetAccountId);
        boolean success = accountDAO.updateBalance(targetAccountId, amount.negate());
        // Consider refreshing session if the updated account is the currently logged-in user?
        return success;
    }

    /**
     * Sets or removes admin privileges for a player account. Prevents self-demotion.
     * @param targetAccountId The ID of the player account.
     * @param makeAdmin True to grant admin, false to revoke.
     * @return true if successful, false otherwise.
     */
    public boolean setPlayerAdminStatus(int targetAccountId, boolean makeAdmin) {
        Account currentAdmin = SessionManager.getCurrentAccount();
        // Prevent admin from demoting themselves
        if(currentAdmin != null && currentAdmin.getId() == targetAccountId && !makeAdmin) {
            LOGGER.warning("Admin attempted to remove their own admin status. Action denied.");
            return false;
        }
        LOGGER.info("Admin setting admin status to " + makeAdmin + " for account ID: " + targetAccountId);
        return accountDAO.updateAdminStatus(targetAccountId, makeAdmin);
    }

    /**
     * Deletes a player account. Prevents self-deletion.
     * @param targetAccountId The ID of the player account to delete.
     * @return true if successful, false otherwise.
     */
    public boolean deletePlayerAccount(int targetAccountId) {
        Account currentAdmin = SessionManager.getCurrentAccount();
        // Prevent admin from deleting themselves
        if(currentAdmin != null && currentAdmin.getId() == targetAccountId) {
            LOGGER.warning("Admin attempted to delete their own account. Action denied.");
            return false;
        }
        LOGGER.warning("Admin deleting account ID: " + targetAccountId);
        return accountDAO.deleteAccount(targetAccountId);
    }

    // --- Chat Management ---

    /**
     * Gets the currently pinned chat message.
     * @return Optional containing the ChatMessage if found.
     */
    public Optional<ChatMessage> getPinnedMessage() {
        LOGGER.fine("Fetching pinned chat message.");
        return chatMessageDAO.findPinnedMessage();
    }

    /**
     * Updates or inserts the pinned chat message.
     * @param messageText The text of the pinned message.
     * @return true if successful, false otherwise.
     */
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

    // --- Banner Management ---

    /**
     * Gets the main homepage banner.
     * @return Optional containing the HomepageBanner if found.
     */
    public Optional<HomepageBanner> getMainBanner() {
        LOGGER.fine("Fetching main banner.");
        return bannerDAO.findMainBanner();
    }

    /**
     * Updates or inserts the main homepage banner.
     * @param bannerName A name for the banner (e.g., "main_banner").
     * @param imageData The image data as a byte array.
     * @return true if successful, false otherwise.
     */
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

    // --- Game Management ---

    /**
     * Gets all games sorted by creation date, including creator username and cover image.
     * @return List of Game objects.
     */
    public List<Game> getAllGames() {
        LOGGER.fine("Fetching all games.");
        return gameDAO.findAllSortedByDateDesc();
    }

    // --- NEW METHOD for Export ---
    public List<Game> getAllGamesWithStats() {
        LOGGER.fine("Fetching all games with stats for export.");
        return gameDAO.findAllWithStats(); // Call the new DAO method
    }

    /**
     * Creates a new game along with its cover image and symbol assets.
     * NOTE: Does not use transactions with raw JDBC for simplicity. Partial creation possible on error.
     *
     * @param gameData   The core game data (name, type, stakes etc.)
     * @param coverAsset The GameAsset object for the cover image. Must not be null.
     * @param symbolAssets List of GameAsset objects for symbols (can be empty).
     * @return true if game creation and essential asset saving was successful, false otherwise.
     */
    public boolean createGame(Game gameData, GameAsset coverAsset, List<GameAsset> symbolAssets) {
        Account currentAdmin = SessionManager.getCurrentAccount();
        if (currentAdmin == null || !currentAdmin.isAdmin()) {
            LOGGER.warning("Unauthorized attempt to create game.");
            return false;
        }
        if (coverAsset == null || coverAsset.getImageData() == null || coverAsset.getImageData().length == 0) {
            LOGGER.severe("Game creation failed: Cover image data is mandatory.");
            return false; // Require cover image
        }

        LOGGER.info("Admin ID " + currentAdmin.getId() + " attempting to create game: " + gameData.getName());

        // Set creator ID and default state
        gameData.setCreatedByAdminId(currentAdmin.getId());
        gameData.setActive(true);

        // Save the core game data to get the generated ID
        int gameId = gameDAO.save(gameData);
        if (gameId <= 0) {
            LOGGER.severe("Failed to save core game data for: " + gameData.getName());
            return false;
        }

        // Save the cover asset (mandatory)
        coverAsset.setGameId(gameId);
        coverAsset.setAssetType(AssetType.COVER);
        if (!gameAssetDAO.save(coverAsset)) {
            LOGGER.severe("Failed to save MANDATORY COVER asset for new game ID: " + gameId + ". Attempting cleanup.");
            // Try to delete the game row since cover failed (Best effort cleanup without transaction)
            gameDAO.delete(gameId);
            return false;
        }

        // Save symbol assets (optional)
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

        return true; // Game and mandatory cover saved
    }

    /**
     * Deletes a game and its associated assets (via DB cascade).
     * @param gameId ID of the game to delete.
     * @return true if successful, false otherwise.
     */
    public boolean deleteGame(int gameId) {
        Account currentAdmin = SessionManager.getCurrentAccount();
        if (currentAdmin == null || !currentAdmin.isAdmin()) {
            LOGGER.warning("Unauthorized attempt to delete game ID: " + gameId);
            return false;
        }
        LOGGER.warning("Admin ID " + currentAdmin.getId() + " attempting to delete game ID: " + gameId);
        // Deletion handles assets via ON DELETE CASCADE in DB schema
        return gameDAO.delete(gameId);
    }

    // --- NEW METHODS FOR EDITING ---

    /**
     * Gets the full details for a single game, including all its assets.
     * @param gameId The ID of the game.
     * @return Optional containing the Game object (with assets potentially populated) if found.
     */
    public Optional<Game> getGameDetails(int gameId) {
        LOGGER.fine("Fetching details for game ID: " + gameId);
        Optional<Game> gameOpt = gameDAO.findById(gameId);
        // Note: Assets aren't loaded here; the Controller will load them separately for editing.
        // If you wanted to always bundle assets with Game details, you'd fetch them here.
        return gameOpt;
    }

    /**
     * Gets all assets associated with a specific game ID.
     * @param gameId The game ID.
     * @return List of GameAsset objects.
     */
    public List<GameAsset> getGameAssets(int gameId) {
        LOGGER.fine("Fetching all assets for game ID: " + gameId);
        return gameAssetDAO.findByGameId(gameId);
    }


    /**
     * Updates an existing game and handles its assets.
     * Replaces cover image if a new one is provided.
     * Replaces ALL existing symbols with the ones provided in symbolAssets.
     *
     * @param gameData   The Game object with updated core data (must include ID).
     * @param newCoverAsset Optional containing the new cover asset data, or empty if not changed.
     * @param newSymbolAssets List of new symbol assets. Existing symbols WILL BE DELETED.
     * @return true if update was successful, false otherwise.
     */
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

        // 1. Update core game data
        boolean gameUpdated = gameDAO.update(gameData);
        if (!gameUpdated) {
            LOGGER.severe("Failed to update core game data for ID: " + gameId);
            return false; // Stop if core update fails
        }

        // 2. Handle Cover Image Update (if new one provided)
        if (newCoverAsset.isPresent()) {
            GameAsset cover = newCoverAsset.get();
            if (cover.getImageData() != null && cover.getImageData().length > 0) {
                LOGGER.fine("Updating cover image for game ID: " + gameId);
                // Need to delete existing cover first or use INSERT...ON DUPLICATE logic?
                // Simple approach: Just save/update (assuming only one cover allowed per game).
                // For raw JDBC without ON DUPLICATE for assets, might need delete then insert.
                // Let's assume a simple save handles potential replacement needs for now,
                // or adjust GameAssetDAO save method if needed.
                cover.setGameId(gameId);
                cover.setAssetType(AssetType.COVER);
                if (!gameAssetDAO.save(cover)) { // Adjust save method if needed
                    LOGGER.warning("Failed to update/save COVER asset for game ID: " + gameId);
                    // Continue, as core game data was updated.
                }
            }
        }

        // 3. Handle Symbol Updates (Replace ALL existing symbols)
        // Only perform symbol operations if the game type is SLOT
        if (gameData.getGameType() == GameType.SLOT) {
            LOGGER.fine("Replacing symbol assets for game ID: " + gameId);
            // 3a. Delete ALL existing symbols for this game
            boolean deletedOld = gameAssetDAO.deleteByGameIdAndType(gameId, AssetType.SYMBOL);
            if (!deletedOld) {
                LOGGER.warning("Failed to delete old SYMBOL assets for game ID: " + gameId + ". Update may be inconsistent.");
                // Decide: Stop update? Continue? Continue for now.
            }

            // 3b. Save new symbols (if any provided)
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
            // If game type changed away from SLOT, delete any existing symbols
            LOGGER.fine("Game type is not SLOT, ensuring no symbol assets exist for game ID: " + gameId);
            gameAssetDAO.deleteByGameIdAndType(gameId, AssetType.SYMBOL);
        }

        return true; // Core game data was updated
    }

    // --- End Game Management ---

    // --- Placeholder for Statistics (If needed by admin panel too) ---
    public Map<String, Long> getUserStatistics(int accountId) {
        LOGGER.fine("Fetching placeholder statistics for account ID: " + accountId);
        return Map.of(
                "totalSpins", 0L,
                "totalWins", 0L,
                "gamesPlayed", 0L
        );
    }
}