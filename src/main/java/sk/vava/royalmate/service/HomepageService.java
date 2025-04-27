package sk.vava.royalmate.service;

import sk.vava.royalmate.data.GameDAO;
import sk.vava.royalmate.data.HomepageBannerDAO;
import sk.vava.royalmate.model.Game;
import sk.vava.royalmate.model.HomepageBanner;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class HomepageService {

    private static final Logger LOGGER = Logger.getLogger(HomepageService.class.getName());
    private static final int DEFAULT_TOP_GAMES_LIMIT = 9; // For 3x3 grid

    private final HomepageBannerDAO bannerDAO;
    private final GameDAO gameDAO;

    public HomepageService() {
        this.bannerDAO = new HomepageBannerDAO();
        this.gameDAO = new GameDAO();
    }

    // Constructor for testing/DI
    public HomepageService(HomepageBannerDAO bannerDAO, GameDAO gameDAO) {
        this.bannerDAO = bannerDAO;
        this.gameDAO = gameDAO;
    }

    /**
     * Gets the main banner for the homepage.
     * @return Optional containing the banner if found.
     */
    public Optional<HomepageBanner> getMainBanner() {
        LOGGER.fine("HomepageService fetching main banner.");
        return bannerDAO.findMainBanner(); // Assuming findMainBanner exists and works
    }

    /**
     * Gets the list of top games for the homepage grid.
     * @return List of top Game objects (up to the limit).
     */
    public List<Game> getTopGames() {
        LOGGER.fine("HomepageService fetching top " + DEFAULT_TOP_GAMES_LIMIT + " games.");
        return gameDAO.findTopGames(DEFAULT_TOP_GAMES_LIMIT); // Use the new DAO method
    }
}