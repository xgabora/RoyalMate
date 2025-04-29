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
import sk.vava.royalmate.model.*; // Import all models needed
import sk.vava.royalmate.service.GameService; // Import GameService
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
    private final GameService gameService; // Add GameService

    public MainMenuController() {
        this.homepageService = new HomepageService();
        this.gameService = new GameService(); // Instantiate GameService
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
        // ... (keep existing implementation) ...
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

    /** Loads and displays the top games grid (updated for 4x4) */
    private void loadTopGames() {
        topGamesGridPane.getChildren().clear();
        try {
            // Service already fetches 16 games due to constant change
            List<Game> topGames = homepageService.getTopGames();
            if (topGames.isEmpty()) {
                LOGGER.info("No top games found to display.");
                Label noGamesMsg = new Label("No games available yet.");
                noGamesMsg.getStyleClass().add("message-label");
                // Adjust span for 4 columns
                topGamesGridPane.add(noGamesMsg, 0, 0, 4, 1);
                return;
            }

            int col = 0;
            int row = 0;
            int columnsInGrid = topGamesGridPane.getColumnConstraints().size(); // Get actual column count
            for (Game game : topGames) {
                if (col >= columnsInGrid) { // Use actual column count
                    col = 0;
                    row++;
                }
                // Break if we exceed 4 rows (index 3)
                if (row >= 4) break;

                Node gameNode = createGameGridNode(game);
                topGamesGridPane.add(gameNode, col, row);

                // --- Center the node within the GridPane cell ---
                GridPane.setHalignment(gameNode, HPos.CENTER);
                GridPane.setValignment(gameNode, VPos.CENTER);
                // ------------------------------------------------

                col++;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading top games.", e);
            Label errorMsg = new Label("Error loading top games.");
            errorMsg.getStyleClass().add("error-label");
            // Adjust span for 4 columns
            topGamesGridPane.add(errorMsg, 0, 0, 4, 1);
        }
    }

    /** Creates a clickable ImageView for the game grid (larger) */
    private Node createGameGridNode(Game game) {
        ImageView coverImageView = new ImageView();
        // --- Increased Size ---
        coverImageView.setFitHeight(124); // Larger image height
        coverImageView.setFitWidth(220); // Larger image width (adjust ratio if needed)
        // ----------------------
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
            // Add placeholder text/graphic within the ImageView size if needed
        }

        // Add click handler directly to the ImageView
        coverImageView.setOnMouseClicked(event -> handleGameClick(game, event));

        return coverImageView; // Return just the ImageView
    }


    // --- Event Handlers ---

    @FXML private void handleFilterSlots(ActionEvent event) { /* ... keep implementation ... */ navigateToGameSearchWithFilter(Set.of(GameType.SLOT));}
    @FXML private void handleFilterRoulette(ActionEvent event) { /* ... keep implementation ... */ navigateToGameSearchWithFilter(Set.of(GameType.ROULETTE));}
    @FXML private void handleFilterCoinflip(ActionEvent event) { /* ... keep implementation ... */ navigateToGameSearchWithFilter(Set.of(GameType.COINFLIP));}
    @FXML private void handlePlayMore(ActionEvent event) { /* ... keep implementation ... */ navigateToGameSearchWithFilter(null);}

    /** Action when a game image is clicked */
    private void handleGameClick(Game game, MouseEvent event) {
        LOGGER.info("Game clicked from homepage: " + game.getName() + " (ID: " + game.getId() + ")");
        navigateToGame(game); // Call the actual navigation method
    }


    // --- Navigation ---

    /** Navigates to the game search view, potentially passing initial filters */
    private void navigateToGameSearchWithFilter(Set<GameType> initialFilters) {
        // ... (keep existing implementation) ...
        try {
            Scene scene = rootPane.getScene(); if (scene == null) { LOGGER.severe("Cannot get scene."); return; }
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/sk/vava/royalmate/view/game-search-view.fxml")), LocaleManager.getBundle());
            Parent gameSearchRoot = loader.load();
            GameSearchController controller = loader.getController();
            if (controller != null && initialFilters != null && !initialFilters.isEmpty()) { controller.applyInitialFilters(initialFilters); }
            scene.setRoot(gameSearchRoot);
            LOGGER.info("Navigated to Game Search" + (initialFilters != null ? " with filter: " + initialFilters : ""));
        } catch (IOException | NullPointerException e) { LOGGER.log(Level.SEVERE, "Failed to load game-search-view.fxml", e); /* Alert */ }
    }

    /** Navigates to the appropriate game screen based on GameType (Copied/adapted from GameSearchController) */
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

        // Fetch assets if needed (only Slots currently)
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

            // Call initData based on controller type
            if (controllerInstance instanceof SlotGameController sc) { sc.initData(game, assets); }
            else if (controllerInstance instanceof RouletteGameController rc) { rc.initData(game); }
            else if (controllerInstance instanceof CoinflipGameController cc) { cc.initData(game); }
            else { LOGGER.severe("Loaded FXML but controller type mismatch or null: " + controllerInstance); return; }

            // Cleanup previous controller if necessary (important for stopping timers)
            Object oldController = scene.getUserData(); // Retrieve controller stored in user data
            if(oldController instanceof SlotGameController oldSlot) oldSlot.cleanup();
            else if(oldController instanceof RouletteGameController oldRoulette) oldRoulette.cleanup();
            else if(oldController instanceof CoinflipGameController oldCoinflip) oldCoinflip.cleanup();
            // Add other controllers here if they need cleanup

            scene.setRoot(gameRoot);
            scene.setUserData(controllerInstance); // Store the new controller instance

            LOGGER.info("Navigated to game: " + game.getName());

        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to load game FXML: " + fxmlPath, e);
            new Alert(Alert.AlertType.ERROR, "Error loading game screen.").showAndWait();
        }
    }

    // Helper to navigate to login if session is lost
    private void navigateToLogin() {
        // ... (keep existing implementation) ...
        if (rootPane == null || rootPane.getScene() == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/sk/vava/royalmate/view/login-view.fxml")), LocaleManager.getBundle());
            Parent nextRoot = loader.load();
            rootPane.getScene().setRoot(nextRoot);
        } catch (IOException | NullPointerException e) { LOGGER.log(Level.SEVERE, "Failed to navigate back to login screen.", e); }
    }
}