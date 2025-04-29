package sk.vava.royalmate.service;

import sk.vava.royalmate.data.GameplayDAO;
import sk.vava.royalmate.model.Gameplay;
import sk.vava.royalmate.model.GameType;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LeaderboardService {

    private static final Logger LOGGER = Logger.getLogger(LeaderboardService.class.getName());
    private static final int LEADERBOARD_LIMIT = 5; // Top 5 per category

    private final GameplayDAO gameplayDAO;

    public LeaderboardService() {
        this.gameplayDAO = new GameplayDAO();
    }

    // Constructor for testing/DI
    public LeaderboardService(GameplayDAO gameplayDAO) {
        this.gameplayDAO = gameplayDAO;
    }

    /**
     * Gets the top plays for a specific game type, ordered by payout amount.
     *
     * @param gameType The type of game.
     * @return List of top Gameplay objects.
     */
    public List<Gameplay> getTopPayouts(GameType gameType) {
        LOGGER.fine("LeaderboardService fetching top payouts for: " + gameType);
        try {
            return gameplayDAO.findTopPlays(gameType, "payout_amount", LEADERBOARD_LIMIT);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching top payouts for " + gameType, e);
            return Collections.emptyList();
        }
    }

    /**
     * Gets the top plays for a specific game type, ordered by multiplier (payout/stake).
     *
     * @param gameType The type of game.
     * @return List of top Gameplay objects.
     */
    public List<Gameplay> getTopMultipliers(GameType gameType) {
        LOGGER.fine("LeaderboardService fetching top multipliers for: " + gameType);
        try {
            return gameplayDAO.findTopPlays(gameType, "multiplier", LEADERBOARD_LIMIT);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching top multipliers for " + gameType, e);
            return Collections.emptyList();
        }
    }
}