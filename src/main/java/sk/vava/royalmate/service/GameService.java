package sk.vava.royalmate.service;

import sk.vava.royalmate.data.AccountDAO;
import sk.vava.royalmate.data.GameAssetDAO;
import sk.vava.royalmate.data.GameDAO;
import sk.vava.royalmate.data.GameplayDAO;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.model.AssetType;
import sk.vava.royalmate.model.Game;
import sk.vava.royalmate.model.GameAsset;
import sk.vava.royalmate.model.Gameplay;
import sk.vava.royalmate.util.SessionManager;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GameService {

    private static final Logger LOGGER = Logger.getLogger(GameService.class.getName());
    private static final int LEADERBOARD_LIMIT = 10;

    private final GameDAO gameDAO;
    private final GameAssetDAO gameAssetDAO;
    private final AccountDAO accountDAO;
    private final GameplayDAO gameplayDAO;

    public GameService() {
        this.gameDAO = new GameDAO();
        this.gameAssetDAO = new GameAssetDAO();
        this.accountDAO = new AccountDAO();
        this.gameplayDAO = new GameplayDAO();
    }

    public List<Game> getAllActiveGamesWithCovers() {
        LOGGER.fine("GameService fetching all active games with covers.");
        try {
            return gameDAO.findAllActiveWithCovers();
        } catch (Exception e) {

            LOGGER.log(Level.SEVERE, "Error fetching active games from DAO", e);
            return Collections.emptyList();
        }
    }

    public Optional<Game> getGameDetails(int gameId) {
        LOGGER.fine("GameService fetching details for game ID: " + gameId);
        Optional<Game> gameOpt = gameDAO.findById(gameId);

        return gameOpt.filter(Game::isActive);
    }

    public List<GameAsset> getGameAssets(int gameId, AssetType assetType) {
        LOGGER.fine("GameService fetching " + assetType + " assets for game ID: " + gameId);
        List<GameAsset> assets = gameAssetDAO.findByGameIdAndType(gameId, assetType);

        assets.sort(Comparator.comparingInt(GameAsset::getId));
        return assets;
    }

    public long placeBet(int accountId, int gameId, BigDecimal stakeAmount) {
        LOGGER.info("Placing bet for account " + accountId + ", game " + gameId + ", stake " + stakeAmount);

        Account currentAccount = SessionManager.getCurrentAccount();
        if (currentAccount == null || currentAccount.getId() != accountId) {
            LOGGER.severe("Bet placement attempted without valid session for account ID " + accountId);
            return -1L;
        }

        Optional<Account> latestAccountOpt = accountDAO.findByUsername(currentAccount.getUsername());
        if(latestAccountOpt.isEmpty()) {
            LOGGER.severe("Could not fetch latest account data for balance check.");
            return -1L;
        }
        BigDecimal currentBalance = latestAccountOpt.get().getBalance();

        if (currentBalance == null || currentBalance.compareTo(stakeAmount) < 0) {
            LOGGER.warning("Bet placement failed for account " + accountId + ": insufficient funds.");
            return -1L;
        }

        boolean debitSuccess = accountDAO.updateBalance(accountId, stakeAmount.negate());
        if (!debitSuccess) {
            LOGGER.severe("Failed to debit stake from account " + accountId);
            return -1L;
        }

        SessionManager.setCurrentAccount(accountDAO.findByUsername(currentAccount.getUsername()).orElse(null));

        long gameplayId = gameplayDAO.saveInitialPlay(accountId, gameId, stakeAmount);
        if (gameplayId < 0) {
            LOGGER.severe("Failed to save initial gameplay record after debiting stake for account " + accountId + ", game " + gameId);

            return -1L;
        }

        LOGGER.info("Bet placed successfully, gameplay ID: " + gameplayId);
        return gameplayId;
    }

    public boolean recordResult(long gameplayId, String outcome, BigDecimal payoutAmount, int accountId) {
        LOGGER.info("Recording result for gameplay ID: " + gameplayId + ", payout: " + payoutAmount);

        if (payoutAmount != null && payoutAmount.compareTo(BigDecimal.ZERO) > 0) {
            boolean creditSuccess = accountDAO.updateBalance(accountId, payoutAmount);
            if (!creditSuccess) {
                LOGGER.severe("CRITICAL: Failed to credit winnings (" + payoutAmount + ") for account " + accountId + ", gameplay ID " + gameplayId);

            } else {
                LOGGER.info("Winnings credited successfully for gameplay ID: " + gameplayId);

                Account currentAccount = SessionManager.getCurrentAccount();
                if(currentAccount != null && currentAccount.getId() == accountId){
                    SessionManager.setCurrentAccount(accountDAO.findByUsername(currentAccount.getUsername()).orElse(null));
                }
            }
        }

        boolean updateSuccess = gameplayDAO.updatePlayResult(gameplayId, outcome, payoutAmount != null ? payoutAmount : BigDecimal.ZERO);
        if (!updateSuccess) {
            LOGGER.warning("Failed to update gameplay record result for ID: " + gameplayId);

            return false;
        }

        LOGGER.info("Gameplay result recorded successfully for ID: " + gameplayId);
        return true;
    }

    public List<Gameplay> getRecentGameWins(int gameId) {
        LOGGER.fine("GameService fetching recent wins for game ID: " + gameId);
        return gameplayDAO.findRecentWinsByGame(gameId, LEADERBOARD_LIMIT);
    }

}