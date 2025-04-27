package sk.vava.royalmate.service;

import sk.vava.royalmate.data.GameDAO;
import sk.vava.royalmate.model.Game;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameService {

    private static final Logger LOGGER = Logger.getLogger(GameService.class.getName());
    private final GameDAO gameDAO;

    public GameService() {
        this.gameDAO = new GameDAO();
    }

    // Constructor for testing/DI
    public GameService(GameDAO gameDAO) {
        this.gameDAO = gameDAO;
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

    // Add methods here later for getting specific game details for playing, etc.
}