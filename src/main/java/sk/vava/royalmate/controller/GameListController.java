package sk.vava.royalmate.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import sk.vava.royalmate.model.Game;
import sk.vava.royalmate.service.AdminService;
import sk.vava.royalmate.util.ImageUtil;
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import java.io.IOException;
import java.text.MessageFormat; // For confirmation message formatting
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;


public class GameListController {

    private static final Logger LOGGER = Logger.getLogger(GameListController.class.getName());

    @FXML private BorderPane rootPane;
    @FXML private Button addNewGameButton;
    @FXML private ScrollPane scrollPane;
    @FXML private VBox gameListContainer;
    @FXML private Label messageLabel;

    private final AdminService adminService;

    public GameListController() {
        this.adminService = new AdminService();
    }

    @FXML
    public void initialize() {
        if (!SessionManager.isAdmin()) {
            LOGGER.severe("Non-admin attempting to access Game List. Redirecting.");
            // Navigate back or show error
            Platform.runLater(this::navigateToAdminSettings); // Example redirect
            return;
        }
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);
        loadGameList();
        LOGGER.info("Game List Controller initialized.");
    }

    private void loadGameList() {
        LOGGER.fine("Loading game list...");
        gameListContainer.getChildren().clear(); // Clear previous list
        hideMessage();

        List<Game> games = adminService.getAllGames();

        if (games.isEmpty()) {
            LOGGER.info("No games found in the database.");
            Label noGamesLabel = new Label(LocaleManager.getString("admin.gamelist.nodata"));
            noGamesLabel.getStyleClass().add("message-label"); // Use message style
            noGamesLabel.setTextFill(Color.LIGHTGRAY);
            gameListContainer.getChildren().add(noGamesLabel);
            return;
        }

        LOGGER.fine("Found " + games.size() + " games. Creating UI rows...");
        boolean alternate = false;
        for (Game game : games) {
            try {
                Node gameRow = createGameRow(game);
                // Apply alternating row style
                if (alternate) {
                    gameRow.getStyleClass().add("game-row-alt");
                } else {
                    gameRow.getStyleClass().add("game-row");
                }
                gameListContainer.getChildren().add(gameRow);
                alternate = !alternate;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error creating row for game: " + game.getName(), e);
                // Optionally add an error placeholder row
            }
        }
    }

    /** Creates a UI node (HBox) representing a single game row. */
    private Node createGameRow(Game game) throws IOException {
        HBox row = new HBox(20); // Spacing between elements
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("game-row-base"); // Base style for padding etc.

        // 1. Thumbnail
        ImageView thumbnail = new ImageView();
        thumbnail.setFitHeight(60);
        thumbnail.setFitWidth(90); // ~16:9 aspect ratio
        thumbnail.setPreserveRatio(true);
        if (game.getCoverImageData() != null) {
            try {
                thumbnail.setImage(ImageUtil.byteArrayToImage(game.getCoverImageData()));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load cover image for game: " + game.getName(), e);
                thumbnail.setImage(null); // Set placeholder or default image?
                // Add a placeholder rectangle maybe?
                // thumbnail.getParent().getChildren().add(new javafx.scene.shape.Rectangle(90, 60, Color.DARKGREY));
            }
        } else {
            // Placeholder if no image data
            thumbnail.setImage(null); // Or set default placeholder image
        }

        // 2. Game Info (Name, Type, Creator, Spins)
        VBox infoBox = new VBox(2);
        Label nameLabel = new Label(game.getName());
        nameLabel.getStyleClass().add("game-name-label");

        HBox typeBox = new HBox(5); // HBox for Type Chip
        typeBox.setAlignment(Pos.CENTER_LEFT);
        Label typeLabel = new Label(game.getGameType().name()); // Use raw enum name
        typeLabel.getStyleClass().add("game-type-chip");
        typeBox.getChildren().add(typeLabel);

        Label creatorLabel = new Label(LocaleManager.getString("admin.gamelist.label.by") + " " + game.getCreatedByAdminUsername());
        creatorLabel.getStyleClass().add("game-sub-label");

        Label spinsLabel = new Label(game.getTotalSpins() + " " + LocaleManager.getString("admin.gamelist.label.spins")); // Use placeholder 0 for now
        spinsLabel.getStyleClass().add("game-sub-label");

        infoBox.getChildren().addAll(nameLabel, typeBox, creatorLabel, spinsLabel);
        HBox.setHgrow(infoBox, Priority.ALWAYS); // Make info box expand

        // 3. Action Buttons
        VBox buttonBox = new VBox(5);
        buttonBox.setAlignment(Pos.CENTER);
        Button editButton = new Button(LocaleManager.getString("admin.button.editgame"));
        editButton.getStyleClass().addAll("action-button", "warning"); // Use warning style
        editButton.setOnAction(e -> handleEditGame(game));

        Button deleteButton = new Button(LocaleManager.getString("admin.button.deletegame"));
        deleteButton.getStyleClass().addAll("action-button", "negative"); // Use negative style
        deleteButton.setOnAction(e -> handleDeleteGame(game));

        buttonBox.getChildren().addAll(editButton, deleteButton);

        // Add all parts to the row HBox
        row.getChildren().addAll(thumbnail, infoBox, buttonBox);
        row.setPadding(new Insets(10)); // Padding within the row

        return row;
    }


    // --- Action Handlers ---

    @FXML
    private void handleAddGame(ActionEvent event) {
        LOGGER.info("Add New Game button clicked");
        navigateTo(event, "/sk/vava/royalmate/view/add-game-view.fxml");
    }


    private void handleDeleteGame(Game game) {
        LOGGER.warning("Attempting to delete game: " + game.getName() + " (ID: " + game.getId() + ")");
        hideMessage();

        // Confirmation Dialog
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle(LocaleManager.getString("admin.gamelist.confirm.delete.title"));
        // Format header message with game name
        String headerText = MessageFormat.format(LocaleManager.getString("admin.gamelist.confirm.delete.header"), game.getName());
        confirmation.setHeaderText(headerText);
        confirmation.setContentText(LocaleManager.getString("admin.gamelist.confirm.delete.content"));

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            LOGGER.info("Deletion confirmed for game: " + game.getName());
            boolean success = adminService.deleteGame(game.getId());
            if (success) {
                showMessage(LocaleManager.getString("admin.gamelist.message.delete.success"), false);
                loadGameList(); // Refresh the list after deletion
            } else {
                showMessage(LocaleManager.getString("admin.gamelist.message.delete.error"), true);
            }
        } else {
            LOGGER.info("Deletion cancelled for game: " + game.getName());
        }
    }

    private void handleEditGame(Game game) {
        LOGGER.info("Edit Game clicked for: " + game.getName() + " (ID: " + game.getId() + ")");
        hideMessage();
        try {
            // Load the Add/Edit Game FXML
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource("/sk/vava/royalmate/view/add-game-view.fxml")),
                    LocaleManager.getBundle()
            );
            Parent editGameRoot = loader.load();

            // Get the controller instance of the loaded FXML
            AddGameController addGameController = loader.getController();
            if (addGameController != null) {
                // Call the method in AddGameController to populate data for editing
                addGameController.loadGameForEditing(game.getId()); // Pass only the ID

                // Get the current scene and set the new root
                Scene scene = rootPane.getScene(); // Use rootPane or any node from current scene
                if (scene != null) {
                    scene.setRoot(editGameRoot);
                } else {
                    LOGGER.severe("Could not get current scene to display edit game screen.");
                    showMessage(LocaleManager.getString("admin.gamelist.message.load.error"), true);
                }
            } else {
                LOGGER.severe("Could not get AddGameController instance from FXML loader.");
                showMessage(LocaleManager.getString("admin.gamelist.message.load.error"), true);
            }
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to load add-game-view.fxml for editing game ID: " + game.getId(), e);
            showMessage(LocaleManager.getString("admin.gamelist.message.load.error"), true);
        }
    }

    // --- UI Helpers ---
    private void showMessage(String message, boolean isError) {
        Platform.runLater(()-> {
            messageLabel.setText(message);
            messageLabel.setTextFill(isError ? Color.RED : Color.LIGHTGREEN);
            messageLabel.setVisible(true);
            messageLabel.setManaged(true);
        });
    }

    private void hideMessage() {
        Platform.runLater(()-> {
            messageLabel.setVisible(false);
            messageLabel.setManaged(false);
        });
    }

    // --- Navigation ---
    private void navigateTo(ActionEvent event, String fxmlPath) {
        Node source = (Node) event.getSource();
        try {
            Scene scene = source.getScene();
            if (scene == null) { /* ... error handling ... */ return; }
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource(fxmlPath)), LocaleManager.getBundle());
            Parent nextRoot = loader.load();
            scene.setRoot(nextRoot);
            LOGGER.info("Successfully navigated to: " + fxmlPath);
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to load FXML: " + fxmlPath, e);
            showMessage(LocaleManager.getString("admin.gamelist.message.load.error"), true); // Show error on current screen
        } catch (ClassCastException e) {
            LOGGER.log(Level.SEVERE, "Failed to cast event source to Node.", e);
        }
    }

    // Navigation for redirecting if non-admin accesses
    private void navigateToAdminSettings() {
        if (rootPane == null || rootPane.getScene() == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/sk/vava/royalmate/view/admin-settings-view.fxml")), LocaleManager.getBundle());
            Parent nextRoot = loader.load();
            rootPane.getScene().setRoot(nextRoot);
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to navigate back to admin settings.", e);
        }
    }
}