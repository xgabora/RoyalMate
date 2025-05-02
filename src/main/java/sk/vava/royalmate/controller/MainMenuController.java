package sk.vava.royalmate.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import sk.vava.royalmate.model.*;
import sk.vava.royalmate.service.GameService;
import sk.vava.royalmate.service.HomepageService;
import sk.vava.royalmate.util.ImageUtil;
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainMenuController {

    private static final Logger LOGGER = Logger.getLogger(MainMenuController.class.getName());

    @FXML private BorderPane rootPane;
    @FXML private StackPane bannerPane;
    @FXML private ImageView bannerImageView;
    @FXML private Label bannerErrorLabel;
    @FXML private Button slotsButton;
    @FXML private Button rouletteButton;
    @FXML private Button coinflipButton;
    @FXML private GridPane topGamesGridPane;
    @FXML private Button playMoreGamesButton;

    private final HomepageService homepageService;
    private final GameService gameService;

    public MainMenuController() {
        this.homepageService = new HomepageService();
        this.gameService = new GameService();
    }

    @FXML
    public void initialize() {
        if (SessionManager.getCurrentAccount() == null) {
            LOGGER.severe("Homepage loaded without user session. Redirecting to login.");
            Platform.runLater(this::navigateToLogin);
            return;
        }
        loadBanner();
        loadTopGames();
        LOGGER.info("MainMenuController (Homepage) initialized.");
    }

    private void loadBanner() {

        bannerErrorLabel.setVisible(false);
        bannerErrorLabel.setManaged(false);
        try {
            Optional<HomepageBanner> bannerOpt = homepageService.getMainBanner();
            if (bannerOpt.isPresent() && bannerOpt.get().getImageData() != null) {
                Image bannerImage = ImageUtil.byteArrayToImage(bannerOpt.get().getImageData());
                bannerImageView.setImage(bannerImage);
                bannerImageView.setVisible(true);
            } else {
                LOGGER.info("No main banner found or banner has no image data.");
                bannerImageView.setImage(null);
                bannerImageView.setVisible(false);
                bannerErrorLabel.setText(LocaleManager.getString("homepage.banner.missing"));
                bannerErrorLabel.setVisible(true);
                bannerErrorLabel.setManaged(true);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading banner image.", e);
            bannerImageView.setImage(null);
            bannerImageView.setVisible(false);
            bannerErrorLabel.setText("Error loading banner.");
            bannerErrorLabel.setVisible(true);
            bannerErrorLabel.setManaged(true);
        }
    }

    private void loadTopGames() {
        topGamesGridPane.getChildren().clear();
        try {

            List<Game> topGames = homepageService.getTopGames();
            if (topGames.isEmpty()) {
                LOGGER.info("No top games found to display.");
                Label noGamesMsg = new Label("No games available yet.");
                noGamesMsg.getStyleClass().add("message-label");

                topGamesGridPane.add(noGamesMsg, 0, 0, 4, 1);
                return;
            }

            int col = 0;
            int row = 0;
            int columnsInGrid = topGamesGridPane.getColumnConstraints().size();
            for (Game game : topGames) {
                if (col >= columnsInGrid) {
                    col = 0;
                    row++;
                }

                if (row >= 4) break;

                Node gameNode = createGameGridNode(game);
                topGamesGridPane.add(gameNode, col, row);

                GridPane.setHalignment(gameNode, HPos.CENTER);
                GridPane.setValignment(gameNode, VPos.CENTER);

                col++;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading top games.", e);
            Label errorMsg = new Label("Error loading top games.");
            errorMsg.getStyleClass().add("error-label");

            topGamesGridPane.add(errorMsg, 0, 0, 4, 1);
        }
    }

    private Node createGameGridNode(Game game) {
        ImageView coverImageView = new ImageView();

        coverImageView.setFitHeight(124);
        coverImageView.setFitWidth(220);

        coverImageView.setPreserveRatio(false);
        coverImageView.getStyleClass().add("game-cover-image");
        coverImageView.setCursor(Cursor.HAND);

        if (game.getCoverImageData() != null) {
            try {
                coverImageView.setImage(ImageUtil.byteArrayToImage(game.getCoverImageData()));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load cover image for game grid: " + game.getName(), e);
                coverImageView.setImage(null);
            }
        } else {
            LOGGER.warning("Missing cover image for game: " + game.getName());
            coverImageView.setImage(null);

        }

        coverImageView.setOnMouseClicked(event -> handleGameClick(game, event));

        return coverImageView;
    }

    @FXML private void handleFilterSlots(ActionEvent event) {  navigateToGameSearchWithFilter(Set.of(GameType.SLOT));}
    @FXML private void handleFilterRoulette(ActionEvent event) {  navigateToGameSearchWithFilter(Set.of(GameType.ROULETTE));}
    @FXML private void handleFilterCoinflip(ActionEvent event) {  navigateToGameSearchWithFilter(Set.of(GameType.COINFLIP));}
    @FXML private void handlePlayMore(ActionEvent event) {  navigateToGameSearchWithFilter(null);}

    private void handleGameClick(Game game, MouseEvent event) {
        LOGGER.info("Game clicked from homepage: " + game.getName() + " (ID: " + game.getId() + ")");
        navigateToGame(game);
    }

    private void navigateToGameSearchWithFilter(Set<GameType> initialFilters) {

        try {
            Scene scene = rootPane.getScene(); if (scene == null) { LOGGER.severe("Cannot get scene."); return; }
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/sk/vava/royalmate/view/game-search-view.fxml")), LocaleManager.getBundle());
            Parent gameSearchRoot = loader.load();
            GameSearchController controller = loader.getController();
            if (controller != null && initialFilters != null && !initialFilters.isEmpty()) { controller.applyInitialFilters(initialFilters); }
            scene.setRoot(gameSearchRoot);
            LOGGER.info("Navigated to Game Search" + (initialFilters != null ? " with filter: " + initialFilters : ""));
        } catch (IOException | NullPointerException e) { LOGGER.log(Level.SEVERE, "Failed to load game-search-view.fxml", e);  }
    }

    private void navigateToGame(Game game) {
        if (game == null) return;

        String fxmlPath;
        Object controllerInstance;

        switch (game.getGameType()) {
            case SLOT: fxmlPath = "/sk/vava/royalmate/view/slot-game-view.fxml"; break;
            case ROULETTE: fxmlPath = "/sk/vava/royalmate/view/roulette-game-view.fxml"; break;
            case COINFLIP: fxmlPath = "/sk/vava/royalmate/view/coinflip-game-view.fxml"; break;
            default: LOGGER.severe("Unknown game type for navigation: " + game.getGameType()); return;
        }

        List<GameAsset> assets = Collections.emptyList();
        if(game.getGameType() == GameType.SLOT) {
            assets = gameService.getGameAssets(game.getId(), AssetType.SYMBOL);
            if (assets.isEmpty()) { LOGGER.severe("Cannot load game " + game.getName() + ": No SYMBOL assets found."); new Alert(Alert.AlertType.ERROR, "Failed to load game assets.").showAndWait(); return; }
        }

        try {
            Scene scene = rootPane.getScene(); if (scene == null) { LOGGER.severe("Cannot get scene."); return; }
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource(fxmlPath)), LocaleManager.getBundle());
            Parent gameRoot = loader.load();
            controllerInstance = loader.getController();

            if (controllerInstance instanceof SlotGameController sc) { sc.initData(game, assets); }
            else if (controllerInstance instanceof RouletteGameController rc) { rc.initData(game); }
            else if (controllerInstance instanceof CoinflipGameController cc) { cc.initData(game); }
            else { LOGGER.severe("Loaded FXML but controller type mismatch or null: " + controllerInstance); return; }

            Object oldController = scene.getUserData();
            if(oldController instanceof SlotGameController oldSlot) oldSlot.cleanup();
            else if(oldController instanceof RouletteGameController oldRoulette) oldRoulette.cleanup();
            else if(oldController instanceof CoinflipGameController oldCoinflip) oldCoinflip.cleanup();

            scene.setRoot(gameRoot);
            scene.setUserData(controllerInstance);

            LOGGER.info("Navigated to game: " + game.getName());

        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to load game FXML: " + fxmlPath, e);
            new Alert(Alert.AlertType.ERROR, "Error loading game screen.").showAndWait();
        }
    }

    private void navigateToLogin() {

        if (rootPane == null || rootPane.getScene() == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/sk/vava/royalmate/view/login-view.fxml")), LocaleManager.getBundle());
            Parent nextRoot = loader.load();
            rootPane.getScene().setRoot(nextRoot);
        } catch (IOException | NullPointerException e) { LOGGER.log(Level.SEVERE, "Failed to navigate back to login screen.", e); }
    }
}