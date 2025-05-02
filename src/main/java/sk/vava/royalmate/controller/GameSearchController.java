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
    @FXML private TilePane gameGridPane;

    private final GameService gameService;
    private List<Game> allGames = new ArrayList<>();
    private Pattern searchPattern = null;

    public GameSearchController() {
        this.gameService = new GameService();
    }

    @FXML
    public void initialize() {
        if (SessionManager.getCurrentAccount() == null) {
            LOGGER.severe("Game Search loaded without user session.");

            return;
        }

        slotsFilterButton.setSelected(true);
        rouletteFilterButton.setSelected(true);
        coinflipFilterButton.setSelected(true);

        searchTextField.textProperty().addListener(this::onFilterChanged);
        slotsFilterButton.selectedProperty().addListener(this::onFilterChanged);
        rouletteFilterButton.selectedProperty().addListener(this::onFilterChanged);
        coinflipFilterButton.selectedProperty().addListener(this::onFilterChanged);

        loadAllGamesAndDisplay();

        LOGGER.info("GameSearchController initialized.");
    }

    public void applyInitialFilters(Set<GameType> initialFilters) {
        Platform.runLater(() -> {
            LOGGER.info("Applying initial filters: " + initialFilters);
            slotsFilterButton.setSelected(initialFilters.contains(GameType.SLOT));
            rouletteFilterButton.setSelected(initialFilters.contains(GameType.ROULETTE));
            coinflipFilterButton.setSelected(initialFilters.contains(GameType.COINFLIP));

        });
    }

    private void onFilterChanged(Observable observable) {
        updateSearchPattern();
        updateDisplayedGames();
    }

    private void updateSearchPattern() {
        String searchText = searchTextField.getText().trim();
        if (searchText.isEmpty()) {
            searchPattern = null;
        } else {
            try {

                searchPattern = Pattern.compile(searchText, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                LOGGER.fine("Search pattern updated: " + searchPattern.pattern());

            } catch (PatternSyntaxException e) {
                LOGGER.log(Level.WARNING, "Invalid regex pattern entered: " + searchText, e);
                searchPattern = null;

            }
        }
    }

    private void loadAllGamesAndDisplay() {

        this.allGames = gameService.getAllActiveGamesWithCovers();
        LOGGER.fine("Loaded " + allGames.size() + " active games from service.");
        updateDisplayedGames();
    }

    private void updateDisplayedGames() {
        Set<GameType> selectedTypes = getSelectedGameTypes();
        List<Game> filteredGames;

        filteredGames = allGames.stream()
                .filter(game -> {

                    boolean typeMatch = selectedTypes.contains(game.getGameType());
                    if (!typeMatch) return false;

                    if (searchPattern != null) {
                        Matcher nameMatcher = searchPattern.matcher(game.getName() != null ? game.getName() : "");
                        Matcher descMatcher = searchPattern.matcher(game.getDescription() != null ? game.getDescription() : "");
                        if (!nameMatcher.find() && !descMatcher.find()) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        LOGGER.fine("Filtering resulted in " + filteredGames.size() + " games.");

        Platform.runLater(() -> {
            updateGameCountLabel(filteredGames.size());
            gameGridPane.getChildren().clear();

            if (filteredGames.isEmpty()) {
                Label noGamesMsg = new Label(LocaleManager.getString("gamesearch.nogames.found"));
                noGamesMsg.getStyleClass().add("message-label");
                gameGridPane.getChildren().add(noGamesMsg);
            } else {
                for (Game game : filteredGames) {
                    Node gameCard = createGameCardNode(game);
                    gameGridPane.getChildren().add(gameCard);
                }
            }
        });
    }

    private Set<GameType> getSelectedGameTypes() {
        Set<GameType> selected = new HashSet<>();
        if (slotsFilterButton.isSelected()) selected.add(GameType.SLOT);
        if (rouletteFilterButton.isSelected()) selected.add(GameType.ROULETTE);
        if (coinflipFilterButton.isSelected()) selected.add(GameType.COINFLIP);
        return selected;
    }

    private void updateGameCountLabel(int count) {
        String messageKey = (count == 1) ? "gamesearch.count.label.one" : "gamesearch.count.label";
        String message = MessageFormat.format(LocaleManager.getString(messageKey), count);
        gameCountLabel.setText(message.toUpperCase());
    }

    private Node createGameCardNode(Game game) {
        StackPane cellPane = new StackPane();
        cellPane.getStyleClass().add("game-grid-cell");
        cellPane.setAlignment(Pos.CENTER);
        cellPane.setCursor(Cursor.HAND);

        ImageView coverImageView = new ImageView();

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

    private void handleGameClick(Game game, MouseEvent event) {
        LOGGER.info("Game card clicked: " + game.getName() + " (ID: " + game.getId() + ")");
        navigateToGame(game);
    }

    private void navigateToGame(Game game) {
        if (game == null) return;

        String fxmlPath;
        Object controllerInstance;

        switch (game.getGameType()) {
            case SLOT:
                fxmlPath = "/sk/vava/royalmate/view/slot-game-view.fxml";
                break;
            case ROULETTE:
                fxmlPath = "/sk/vava/royalmate/view/roulette-game-view.fxml";
                break;
            case COINFLIP:
                fxmlPath = "/sk/vava/royalmate/view/coinflip-game-view.fxml";
                break;
            default:
                LOGGER.severe("Unknown game type for navigation: " + game.getGameType());
                return;
        }

        List<GameAsset> assets = Collections.emptyList();
        if(game.getGameType() == GameType.SLOT) {
            assets = gameService.getGameAssets(game.getId(), AssetType.SYMBOL);
            if (assets.isEmpty()) {
                LOGGER.severe("Cannot load game " + game.getName() + ": No SYMBOL assets found.");
                new Alert(Alert.AlertType.ERROR, "Failed to load game assets.").showAndWait();
                return;
            }
        }

        try {
            Scene scene = rootPane.getScene();
            if (scene == null) { LOGGER.severe("Cannot get scene."); return; }

            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource(fxmlPath)), LocaleManager.getBundle());
            Parent gameRoot = loader.load();

            controllerInstance = loader.getController();

            if (controllerInstance instanceof SlotGameController slotController && game.getGameType() == GameType.SLOT) {
                slotController.initData(game, assets);
            } else if (controllerInstance instanceof RouletteGameController rouletteController && game.getGameType() == GameType.ROULETTE) {
                rouletteController.initData(game);
            } else if (controllerInstance instanceof CoinflipGameController coinflipController && game.getGameType() == GameType.COINFLIP) {
                coinflipController.initData(game);
            }
            else {
                LOGGER.severe("Loaded FXML ("+fxmlPath+") but controller type mismatch or null: " + (controllerInstance != null ? controllerInstance.getClass().getName() : "null"));
                return;
            }

            scene.setRoot(gameRoot);
            LOGGER.info("Navigated to game: " + game.getName());

        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to load game FXML: " + fxmlPath, e);
            new Alert(Alert.AlertType.ERROR, "Error loading game screen.").showAndWait();
        }
    }

    private void navigateTo(ActionEvent event, String fxmlPath) {  }

}