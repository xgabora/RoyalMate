package sk.vava.royalmate.service;

import sk.vava.royalmate.data.AccountDAO; // Need AccountDAO
import sk.vava.royalmate.data.GameAssetDAO; // Need GameAssetDAO
import sk.vava.royalmate.data.GameDAO;
import sk.vava.royalmate.data.GameplayDAO; // Need GameplayDAO
import sk.vava.royalmate.model.Account; // Need Account
import sk.vava.royalmate.model.AssetType;
import sk.vava.royalmate.model.Game;
import sk.vava.royalmate.model.GameAsset; // Need GameAsset
import sk.vava.royalmate.model.Gameplay; // Need Gameplay
import sk.vava.royalmate.util.SessionManager; // Need SessionManager

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator; // For sorting symbols
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors; // For sorting symbols

public class GameService {

    private static final Logger LOGGER = Logger.getLogger(GameService.class.getName());
    private static final int LEADERBOARD_LIMIT = 10; // For recent wins display

    private final GameDAO gameDAO;
    private final GameAssetDAO gameAssetDAO; // Added
    private final AccountDAO accountDAO; // Added
    private final GameplayDAO gameplayDAO; // Added

    // Constructor for testing/DI
    public GameService() {
        this.gameDAO = new GameDAO();
        this.gameAssetDAO = new GameAssetDAO(); // Instantiate
        this.accountDAO = new AccountDAO();     // Instantiate
        this.gameplayDAO = new GameplayDAO();   // Instantiate
    }

    /**
     * Gets all active games suitable for display in the user game list/search.
     * Includes cover image data.
     *
     * @return A list of active games, or an empty list on error.
     */
    public List<Game> getAllActiveGamesWithCovers() {
        LOGGER.fine("GameService fetching all active games with covers.");
        try {
            return gameDAO.findAllActiveWithCovers();
        } catch (Exception e) {
            // Catch potential exceptions from DAO layer if they are thrown
            LOGGER.log(Level.SEVERE, "Error fetching active games from DAO", e);
            return Collections.emptyList(); // Return empty list on error
        }
    }

// --- NEW METHODS ---

    /**
     * Gets the full details for a specific game by its ID.
     *
     * @param gameId The ID of the game.
     * @return Optional containing the Game object if found and active.
     */
    public Optional<Game> getGameDetails(int gameId) {
        LOGGER.fine("GameService fetching details for game ID: " + gameId);
        Optional<Game> gameOpt = gameDAO.findById(gameId);
        // Ensure game is active before returning
        return gameOpt.filter(Game::isActive);
    }

    /**
     * Gets all assets of a specific type for a game, sorted by ID ascending.
     * Sorting by ID is crucial for determining symbol rarity/payout order.
     *
     * @param gameId    The ID of the game.
     * @param assetType The type of assets to retrieve (e.g., SYMBOL).
     * @return A sorted list of GameAsset objects.
     */
    public List<GameAsset> getGameAssets(int gameId, AssetType assetType) {
        LOGGER.fine("GameService fetching " + assetType + " assets for game ID: " + gameId);
        List<GameAsset> assets = gameAssetDAO.findByGameIdAndType(gameId, assetType);
        // Sort by ID - assumes lower ID means lower rarity/payout for symbols
        assets.sort(Comparator.comparingInt(GameAsset::getId));
        return assets;
    }

    /**
     * Places a bet: Debits the user's balance and records the initial gameplay entry.
     *
     * @param accountId   ID of the player.
     * @param gameId      ID of the game.
     * @param stakeAmount Amount being staked.
     * @return The ID of the newly created gameplay record, or -1L if the bet failed (e.g., insufficient funds, DB error).
     */
    public long placeBet(int accountId, int gameId, BigDecimal stakeAmount) {
        LOGGER.info("Placing bet for account " + accountId + ", game " + gameId + ", stake " + stakeAmount);

        // 1. Check balance (re-fetch for accuracy)
        Account currentAccount = SessionManager.getCurrentAccount(); // Use session as primary check
        if (currentAccount == null || currentAccount.getId() != accountId) {
            LOGGER.severe("Bet placement attempted without valid session for account ID " + accountId);
            return -1L; // Should not happen if called from controller correctly
        }
        // Fetch latest balance from DB for critical check
        Optional<Account> latestAccountOpt = accountDAO.findByUsername(currentAccount.getUsername());
        if(latestAccountOpt.isEmpty()) {
            LOGGER.severe("Could not fetch latest account data for balance check.");
            return -1L;
        }
        BigDecimal currentBalance = latestAccountOpt.get().getBalance();

        if (currentBalance == null || currentBalance.compareTo(stakeAmount) < 0) {
            LOGGER.warning("Bet placement failed for account " + accountId + ": insufficient funds.");
            return -1L; // Indicate insufficient funds
        }

        // 2. Debit stake
        boolean debitSuccess = accountDAO.updateBalance(accountId, stakeAmount.negate());
        if (!debitSuccess) {
            LOGGER.severe("Failed to debit stake from account " + accountId);
            return -1L; // Database error during debit
        }

        // 3. Refresh session balance immediately after successful debit
        SessionManager.setCurrentAccount(accountDAO.findByUsername(currentAccount.getUsername()).orElse(null));


        // 4. Record initial play
        long gameplayId = gameplayDAO.saveInitialPlay(accountId, gameId, stakeAmount);
        if (gameplayId < 0) {
            LOGGER.severe("Failed to save initial gameplay record after debiting stake for account " + accountId + ", game " + gameId);
            // CRITICAL: Stake was debited but play wasn't recorded. Manual intervention might be needed.
            // Attempt to refund? Or just log heavily. For now, log and return failure.
            // accountDAO.updateBalance(accountId, stakeAmount); // Attempt refund (might also fail)
            return -1L;
        }

        LOGGER.info("Bet placed successfully, gameplay ID: " + gameplayId);
        return gameplayId; // Return the ID for later result update
    }

    /**
     * Records the result of a gameplay: Credits winnings and updates the gameplay record.
     *
     * @param gameplayId   The ID of the gameplay record to update.
     * @param outcome      String representation of the result (e.g., final grid symbols).
     * @param payoutAmount The amount won (can be BigDecimal.ZERO).
     * @param accountId    The ID of the player (needed for balance update).
     * @return true if the result was recorded successfully (including crediting winnings), false otherwise.
     */
    public boolean recordResult(long gameplayId, String outcome, BigDecimal payoutAmount, int accountId) {
        LOGGER.info("Recording result for gameplay ID: " + gameplayId + ", payout: " + payoutAmount);

        // 1. Credit winnings (if any)
        if (payoutAmount != null && payoutAmount.compareTo(BigDecimal.ZERO) > 0) {
            boolean creditSuccess = accountDAO.updateBalance(accountId, payoutAmount);
            if (!creditSuccess) {
                LOGGER.severe("CRITICAL: Failed to credit winnings (" + payoutAmount + ") for account " + accountId + ", gameplay ID " + gameplayId);
                // Proceed to update gameplay record anyway, but log this failure.
                // Don't return false here, as the play *did* happen.
            } else {
                LOGGER.info("Winnings credited successfully for gameplay ID: " + gameplayId);
                // Refresh session balance after crediting win
                Account currentAccount = SessionManager.getCurrentAccount();
                if(currentAccount != null && currentAccount.getId() == accountId){
                    SessionManager.setCurrentAccount(accountDAO.findByUsername(currentAccount.getUsername()).orElse(null));
                }
            }
        }

        // 2. Update gameplay record
        boolean updateSuccess = gameplayDAO.updatePlayResult(gameplayId, outcome, payoutAmount != null ? payoutAmount : BigDecimal.ZERO);
        if (!updateSuccess) {
            LOGGER.warning("Failed to update gameplay record result for ID: " + gameplayId);
            // Return false if updating the record itself failed.
            return false;
        }

        LOGGER.info("Gameplay result recorded successfully for ID: " + gameplayId);
        return true;
    }

    /**
     * Gets recent winning plays for the leaderboard for a specific game.
     *
     * @param gameId The ID of the game.
     * @return List of Gameplay objects representing recent wins.
     */
    public List<Gameplay> getRecentGameWins(int gameId) {
        LOGGER.fine("GameService fetching recent wins for game ID: " + gameId);
        return gameplayDAO.findRecentWinsByGame(gameId, LEADERBOARD_LIMIT);
    }
// --- END NEW METHODS ---}
}