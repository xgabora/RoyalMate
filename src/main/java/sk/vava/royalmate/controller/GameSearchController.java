package sk.vava.royalmate.controller;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import sk.vava.royalmate.model.AssetType;
import sk.vava.royalmate.model.Game;
import sk.vava.royalmate.model.GameAsset;
import sk.vava.royalmate.model.GameType;
import sk.vava.royalmate.service.GameService;
import sk.vava.royalmate.util.ImageUtil;
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;


public class GameSearchController {

    private static final Logger LOGGER = Logger.getLogger(GameSearchController.class.getName());

    @FXML private BorderPane rootPane;
    @FXML private TextField searchTextField;
    @FXML private ToggleButton slotsFilterButton;
    @FXML private ToggleButton rouletteFilterButton;
    @FXML private ToggleButton coinflipFilterButton;
    @FXML private Label gameCountLabel;
    @FXML private TilePane gameGridPane; // Changed to TilePane

    private final GameService gameService;
    private List<Game> allGames = new ArrayList<>(); // Cache all games
    private Pattern searchPattern = null;

    public GameSearchController() {
        this.gameService = new GameService();
    }

    @FXML
    public void initialize() {
        if (SessionManager.getCurrentAccount() == null) {
            LOGGER.severe("Game Search loaded without user session.");
            // Optionally redirect, but maybe allow viewing games if desired?
            return;
        }

        // Set initial filter state (all selected)
        slotsFilterButton.setSelected(true);
        rouletteFilterButton.setSelected(true);
        coinflipFilterButton.setSelected(true);

        // Add listeners for search and filters
        searchTextField.textProperty().addListener(this::onFilterChanged);
        slotsFilterButton.selectedProperty().addListener(this::onFilterChanged);
        rouletteFilterButton.selectedProperty().addListener(this::onFilterChanged);
        coinflipFilterButton.selectedProperty().addListener(this::onFilterChanged);

        // Load all games initially
        loadAllGamesAndDisplay();

        LOGGER.info("GameSearchController initialized.");
    }

    /** Method called by navigating controller to set initial filters */
    public void applyInitialFilters(Set<GameType> initialFilters) {
        Platform.runLater(() -> { // Ensure runs after initialize
            LOGGER.info("Applying initial filters: " + initialFilters);
            slotsFilterButton.setSelected(initialFilters.contains(GameType.SLOT));
            rouletteFilterButton.setSelected(initialFilters.contains(GameType.ROULETTE));
            coinflipFilterButton.setSelected(initialFilters.contains(GameType.COINFLIP));
            // The listener attached in initialize will trigger updateDisplayedGames
        });
    }


    /** Listener method called when search text or filter toggles change */
    private void onFilterChanged(Observable observable) {
        updateSearchPattern();
        updateDisplayedGames();
    }

    /** Updates the regex pattern based on search text */
    private void updateSearchPattern() {
        String searchText = searchTextField.getText().trim();
        if (searchText.isEmpty()) {
            searchPattern = null; // No search pattern needed
        } else {
            try {
                // Simple contains search, case-insensitive, escapes special regex chars
                // Use Pattern.quote for safety if user input shouldn't be regex
                // String quotedSearchText = Pattern.quote(searchText);
                // searchPattern = Pattern.compile(quotedSearchText, Pattern.CASE_INSENSITIVE);

                // If allowing basic regex-like features from user (e.g., word boundary, start/end)
                // Be cautious with user-provided regex patterns for security/performance
                searchPattern = Pattern.compile(searchText, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                LOGGER.fine("Search pattern updated: " + searchPattern.pattern());

            } catch (PatternSyntaxException e) {
                LOGGER.log(Level.WARNING, "Invalid regex pattern entered: " + searchText, e);
                searchPattern = null; // Treat invalid regex as no search for now
                // Optionally show feedback to the user about invalid pattern
            }
        }
    }

    /** Fetches all games from the service and updates the display */
    private void loadAllGamesAndDisplay() {
        // Consider doing this in a background task if loading takes time
        this.allGames = gameService.getAllActiveGamesWithCovers();
        LOGGER.fine("Loaded " + allGames.size() + " active games from service.");
        updateDisplayedGames(); // Display initially loaded games
    }

    /** Filters the cached game list and updates the UI grid */
    private void updateDisplayedGames() {
        Set<GameType> selectedTypes = getSelectedGameTypes();
        List<Game> filteredGames;

        // Filter logic
        filteredGames = allGames.stream()
                .filter(game -> {
                    // Type filter: must match one of the selected types
                    boolean typeMatch = selectedTypes.contains(game.getGameType());
                    if (!typeMatch) return false;

                    // Search filter: must match pattern if pattern exists
                    if (searchPattern != null) {
                        Matcher nameMatcher = searchPattern.matcher(game.getName() != null ? game.getName() : "");
                        Matcher descMatcher = searchPattern.matcher(game.getDescription() != null ? game.getDescription() : "");
                        if (!nameMatcher.find() && !descMatcher.find()) { // Check if pattern is found anywhere
                            return false;
                        }
                    }
                    return true; // Passed all filters
                })
                .collect(Collectors.toList());

        LOGGER.fine("Filtering resulted in " + filteredGames.size() + " games.");

        // Update UI (must run on JavaFX thread)
        Platform.runLater(() -> {
            updateGameCountLabel(filteredGames.size());
            gameGridPane.getChildren().clear(); // Clear previous cards

            if (filteredGames.isEmpty()) {
                Label noGamesMsg = new Label(LocaleManager.getString("gamesearch.nogames.found"));
                noGamesMsg.getStyleClass().add("message-label");
                gameGridPane.getChildren().add(noGamesMsg); // Add message directly to TilePane
            } else {
                for (Game game : filteredGames) {
                    Node gameCard = createGameCardNode(game);
                    gameGridPane.getChildren().add(gameCard);
                }
            }
        });
    }

    /** Gets the set of currently selected GameTypes from the toggle buttons */
    private Set<GameType> getSelectedGameTypes() {
        Set<GameType> selected = new HashSet<>();
        if (slotsFilterButton.isSelected()) selected.add(GameType.SLOT);
        if (rouletteFilterButton.isSelected()) selected.add(GameType.ROULETTE);
        if (coinflipFilterButton.isSelected()) selected.add(GameType.COINFLIP);
        return selected;
    }

    /** Updates the "N GAMES FOUND" label */
    private void updateGameCountLabel(int count) {
        String messageKey = (count == 1) ? "gamesearch.count.label.one" : "gamesearch.count.label";
        String message = MessageFormat.format(LocaleManager.getString(messageKey), count);
        gameCountLabel.setText(message.toUpperCase()); // Make text uppercase as per design
    }

    /** Creates a clickable card node for a single game */
    private Node createGameCardNode(Game game) {
        StackPane cellPane = new StackPane();
        cellPane.getStyleClass().add("game-grid-cell"); // Use existing style from homepage
        cellPane.setAlignment(Pos.CENTER);
        cellPane.setCursor(Cursor.HAND);

        ImageView coverImageView = new ImageView();
        // Consistent size with homepage grid cards
        coverImageView.setFitHeight(112);
        coverImageView.setFitWidth(200);
        coverImageView.setPreserveRatio(false);
        coverImageView.getStyleClass().add("game-cover-image");

        if (game.getCoverImageData() != null) {
            try {
                coverImageView.setImage(ImageUtil.byteArrayToImage(game.getCoverImageData()));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load cover image for game grid: " + game.getName(), e);
                coverImageView.setImage(null);
                Label errorLabel = new Label("!");
                errorLabel.getStyleClass().add("error-label");
                cellPane.getChildren().add(errorLabel);
            }
        } else {
            coverImageView.setImage(null);
            Label noImageLabel = new Label(game.getName());
            noImageLabel.getStyleClass().add("message-label");
            cellPane.getChildren().add(noImageLabel);
        }

        cellPane.getChildren().add(0, coverImageView);
        cellPane.setOnMouseClicked(event -> handleGameClick(game, event));

        return cellPane;
    }

    /** Action when a game card is clicked */
    private void handleGameClick(Game game, MouseEvent event) {
        LOGGER.info("Game card clicked: " + game.getName() + " (ID: " + game.getId() + ")");
        navigateToGame(game); // Navigate to the specific game screen
    }

    /** Navigates to the appropriate game screen based on GameType */
    private void navigateToGame(Game game) {
        if (game == null) return;

        String fxmlPath;
        switch (game.getGameType()) {
            case SLOT:
                fxmlPath = "/sk/vava/royalmate/view/slot-game-view.fxml";
                break;
            case ROULETTE:
                // fxmlPath = "/sk/vava/royalmate/view/roulette-game-view.fxml"; // Future
                LOGGER.warning("Roulette screen not implemented yet.");
                new Alert(Alert.AlertType.INFORMATION, "Roulette coming soon!").showAndWait();
                return; // Don't navigate yet
            case COINFLIP:
                // fxmlPath = "/sk/vava/royalmate/view/coinflip-game-view.fxml"; // Future
                LOGGER.warning("Coinflip screen not implemented yet.");
                new Alert(Alert.AlertType.INFORMATION, "Coinflip coming soon!").showAndWait();
                return; // Don't navigate yet
            default:
                LOGGER.severe("Unknown game type for navigation: " + game.getGameType());
                return;
        }

        // --- Fetch required data BEFORE loading FXML ---
        // In background task if loading assets takes time? For now, synchronous.
        List<GameAsset> assets;
        if(game.getGameType() == GameType.SLOT) {
            assets = gameService.getGameAssets(game.getId(), AssetType.SYMBOL);
            if (assets.isEmpty()) {
                LOGGER.severe("Cannot load game " + game.getName() + ": No SYMBOL assets found.");
                new Alert(Alert.AlertType.ERROR, "Failed to load game assets.").showAndWait();
                return;
            }
        } else {
            // Fetch TABLE assets for Roulette/Coinflip later if needed
            assets = Collections.emptyList();
        }
        // ---------------------------------------------


        try {
            Scene scene = rootPane.getScene(); // Use rootPane from this controller
            if (scene == null) { LOGGER.severe("Cannot get scene."); return; }

            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource(fxmlPath)), LocaleManager.getBundle());
            Parent gameRoot = loader.load();

            // Get controller and pass data using initData method
            Object controller = loader.getController();
            if (controller instanceof SlotGameController slotController && game.getGameType() == GameType.SLOT) {
                slotController.initData(game, assets); // Pass game and symbols
            }
            // else if (controller instanceof RouletteGameController ...) { /* ... */ }
            // else if (controller instanceof CoinflipGameController ...) { /* ... */ }
            else {
                LOGGER.severe("Loaded FXML but controller type mismatch or null: " + controller);
                return;
            }

            scene.setRoot(gameRoot);
            LOGGER.info("Navigated to game: " + game.getName());

        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to load game FXML: " + fxmlPath, e);
            new Alert(Alert.AlertType.ERROR, "Error loading game screen.").showAndWait();
        }
    }
    // --- Navigation (Consider utility class) ---
    private void navigateTo(ActionEvent event, String fxmlPath) { /* ... existing helper ... */ }
    // Add navigateTo for MouseEvent if needed, or refactor to use Node source
}