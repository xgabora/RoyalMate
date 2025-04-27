package sk.vava.royalmate.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos; // Import Pos
import javafx.scene.Cursor; // Import Cursor
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent; // Import MouseEvent
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import sk.vava.royalmate.model.Game;
import sk.vava.royalmate.model.GameType;
import sk.vava.royalmate.model.HomepageBanner;
import sk.vava.royalmate.service.HomepageService; // Import HomepageService
import sk.vava.royalmate.util.ImageUtil;
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainMenuController {

    private static final Logger LOGGER = Logger.getLogger(MainMenuController.class.getName());

    // --- FXML Injections ---
    @FXML private BorderPane rootPane;
    // Inject NavbarController if interaction is needed (e.g., refresh balance)
    // @FXML private NavbarController navbarComponentController;

    // Homepage specific elements
    @FXML private StackPane bannerPane;
    @FXML private ImageView bannerImageView;
    @FXML private Label bannerErrorLabel;
    @FXML private Button slotsButton;
    @FXML private Button rouletteButton;
    @FXML private Button coinflipButton;
    @FXML private GridPane topGamesGridPane;
    @FXML private Button playMoreGamesButton;
    // Removed: @FXML private Label welcomeLabel; // Replaced by homepage content

    private final HomepageService homepageService;

    public MainMenuController() {
        this.homepageService = new HomepageService(); // Instantiate service
    }

    @FXML
    public void initialize() {
        if (SessionManager.getCurrentAccount() == null) {
            LOGGER.severe("Homepage loaded without user session. Redirecting to login.");
            // This ideally shouldn't happen if navigation guards are proper
            Platform.runLater(this::navigateToLogin);
            return;
        }

        // Load dynamic content
        loadBanner();
        loadTopGames();

        // Set button texts via FXML %key or manually (FXML preferred)
        // slotsButton.setText(LocaleManager.getString("homepage.button.slots"));
        // etc.

        LOGGER.info("MainMenuController (Homepage) initialized.");
    }

    /** Loads and displays the main banner */
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
                bannerImageView.setImage(null); // Clear any previous image
                bannerImageView.setVisible(false); // Hide ImageView
                bannerErrorLabel.setText(LocaleManager.getString("homepage.banner.missing"));
                bannerErrorLabel.setVisible(true);
                bannerErrorLabel.setManaged(true);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading banner image.", e);
            bannerImageView.setImage(null);
            bannerImageView.setVisible(false);
            bannerErrorLabel.setText("Error loading banner."); // Provide generic error
            bannerErrorLabel.setVisible(true);
            bannerErrorLabel.setManaged(true);
        }
    }

    /** Loads and displays the top games grid */
    private void loadTopGames() {
        topGamesGridPane.getChildren().clear(); // Clear previous games
        try {
            List<Game> topGames = homepageService.getTopGames();
            if (topGames.isEmpty()) {
                LOGGER.info("No top games found to display.");
                // Optionally display a message in the grid area
                Label noGamesMsg = new Label("No games available yet.");
                noGamesMsg.getStyleClass().add("message-label");
                topGamesGridPane.add(noGamesMsg, 0, 0, 3, 1); // Span across 3 columns
                return;
            }

            int col = 0;
            int row = 0;
            for (Game game : topGames) {
                if (col >= topGamesGridPane.getColumnConstraints().size()) {
                    col = 0;
                    row++;
                }
                if (row >= 3) break; // Limit to 3 rows max (9 games)

                Node gameNode = createGameGridCell(game);
                topGamesGridPane.add(gameNode, col, row);
                col++;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading top games.", e);
            // Display an error message in the grid area
            Label errorMsg = new Label("Error loading top games.");
            errorMsg.getStyleClass().add("error-label");
            topGamesGridPane.add(errorMsg, 0, 0, 3, 1);
        }
    }

    /** Creates a clickable cell for the game grid */
    private Node createGameGridCell(Game game) {
        StackPane cellPane = new StackPane();
        cellPane.getStyleClass().add("game-grid-cell");
        cellPane.setAlignment(Pos.CENTER);
        cellPane.setCursor(Cursor.HAND);

        ImageView coverImageView = new ImageView();
        coverImageView.setFitHeight(112); // Approx 16:9 for 200 width
        coverImageView.setFitWidth(200);
        coverImageView.setPreserveRatio(false); // Allow slight stretch/squash
        coverImageView.getStyleClass().add("game-cover-image"); // For effects like rounded corners

        if (game.getCoverImageData() != null) {
            try {
                coverImageView.setImage(ImageUtil.byteArrayToImage(game.getCoverImageData()));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load cover image for game grid: " + game.getName(), e);
                // Display placeholder / error visual
                coverImageView.setImage(null); // Or a default image
                Label errorLabel = new Label("!");
                errorLabel.getStyleClass().add("error-label");
                cellPane.getChildren().add(errorLabel);
            }
        } else {
            // Handle missing cover image
            coverImageView.setImage(null);
            Label noImageLabel = new Label(game.getName()); // Show name if no image
            noImageLabel.getStyleClass().add("message-label");
            cellPane.getChildren().add(noImageLabel);
        }

        cellPane.getChildren().add(0, coverImageView); // Add image behind potential labels

        // Add click handler (placeholder navigation)
        cellPane.setOnMouseClicked(event -> handleGameClick(game, event));

        return cellPane;
    }


    // --- Event Handlers ---

    @FXML
    private void handleFilterSlots(ActionEvent event) {
        LOGGER.info("Filter Slots clicked");
        navigateToGameSearchWithFilter(Set.of(GameType.SLOT)); // Pass filter type
    }

    @FXML
    private void handleFilterRoulette(ActionEvent event) {
        LOGGER.info("Filter Roulette clicked");
        navigateToGameSearchWithFilter(Set.of(GameType.ROULETTE));
    }

    @FXML
    private void handleFilterCoinflip(ActionEvent event) {
        LOGGER.info("Filter Coinflip clicked");
        navigateToGameSearchWithFilter(Set.of(GameType.COINFLIP));
    }

    @FXML
    private void handlePlayMore(ActionEvent event) {
        LOGGER.info("Play More Games clicked");
        navigateToGameSearchWithFilter(null); // No filter
    }

    /** Navigates to the game search view, potentially passing initial filters */
    private void navigateToGameSearchWithFilter(Set<GameType> initialFilters) {
        try {
            Scene scene = rootPane.getScene();
            if (scene == null) { LOGGER.severe("Cannot get scene."); return; }

            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource("/sk/vava/royalmate/view/game-search-view.fxml")), // Correct path
                    LocaleManager.getBundle()
            );
            Parent gameSearchRoot = loader.load();

            // Get controller and set filter *before* showing the scene
            GameSearchController gameSearchController = loader.getController();
            if (gameSearchController != null && initialFilters != null && !initialFilters.isEmpty()) {
                gameSearchController.applyInitialFilters(initialFilters);
                LOGGER.info("Applying initial filter: " + initialFilters + " to GameSearchController");
            } else if (gameSearchController == null) {
                LOGGER.warning("Could not get GameSearchController instance to apply filter.");
            }

            scene.setRoot(gameSearchRoot);
            LOGGER.info("Navigated to Game Search" + (initialFilters != null ? " with filter: " + initialFilters : ""));

        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to load game-search-view.fxml", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error loading game list.");
            alert.showAndWait();
        }
    }


    private void handleGameClick(Game game, MouseEvent event) {
        LOGGER.info("Game clicked: " + game.getName() + " (ID: " + game.getId() + ") - Placeholder Action");
        // TODO: Navigate to the actual game screen for 'game'
        // navigateToGame(game.getId());
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Navigate to game: " + game.getName());
        alert.showAndWait();
    }


    // --- Navigation ---

    /** Navigates to the game list view, potentially passing a filter */
    private void navigateToGameListWithFilter(GameType filterType) {
        try {
            Scene scene = rootPane.getScene();
            if (scene == null) { LOGGER.severe("Cannot get scene."); return; }

            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource("/sk/vava/royalmate/view/game-list-view.fxml")), // Use Game List FXML
                    LocaleManager.getBundle()
            );
            Parent gameListRoot = loader.load();

            // Get controller and set filter *before* showing the scene
            GameListController gameListController = loader.getController();
            if (gameListController != null) {
                // gameListController.applyFilter(filterType); // Add this method to GameListController later
                LOGGER.info("Applying filter: " + filterType + " to GameListController (Not Implemented Yet)");
            } else {
                LOGGER.warning("Could not get GameListController instance to apply filter.");
            }

            scene.setRoot(gameListRoot);
            LOGGER.info("Navigated to Game List" + (filterType != null ? " with filter: " + filterType : ""));

        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to load game-list-view.fxml", e);
            // Show error message on current screen
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error loading game list.");
            alert.showAndWait();
        }
    }

    // Helper to navigate to login if session is lost
    private void navigateToLogin() {
        if (rootPane == null || rootPane.getScene() == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/sk/vava/royalmate/view/login-view.fxml")), LocaleManager.getBundle());
            Parent nextRoot = loader.load();
            rootPane.getScene().setRoot(nextRoot);
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to navigate back to login screen.", e);
        }
    }
}