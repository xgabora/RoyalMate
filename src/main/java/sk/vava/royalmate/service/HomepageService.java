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
    private static final int DEFAULT_TOP_GAMES_LIMIT = 16;

    private final HomepageBannerDAO bannerDAO;
    private final GameDAO gameDAO;

    public HomepageService() {
        this.bannerDAO = new HomepageBannerDAO();
        this.gameDAO = new GameDAO();
    }

    public HomepageService(HomepageBannerDAO bannerDAO, GameDAO gameDAO) {
        this.bannerDAO = bannerDAO;
        this.gameDAO = gameDAO;
    }

    public Optional<HomepageBanner> getMainBanner() {
        LOGGER.fine("HomepageService fetching main banner.");
        return bannerDAO.findMainBanner();
    }

    public List<Game> getTopGames() {
        LOGGER.fine("HomepageService fetching top " + DEFAULT_TOP_GAMES_LIMIT + " games.");
        return gameDAO.findTopGames(DEFAULT_TOP_GAMES_LIMIT);
    }
}