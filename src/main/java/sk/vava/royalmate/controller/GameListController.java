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
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;


public class GameListController {

    private static final Logger LOGGER = Logger.getLogger(GameListController.class.getName());
    private static final DateTimeFormatter XML_TIMESTAMP_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @FXML private BorderPane rootPane;
    @FXML private Button addNewGameButton;
    @FXML private Button exportDataButton;
    @FXML private ScrollPane scrollPane;
    @FXML private VBox gameListContainer;
    @FXML private Label messageLabel;

    private final AdminService adminService;
    private FileChooser xmlFileChooser;

    public GameListController() {
        this.adminService = new AdminService();
    }

    @FXML
    public void initialize() {
        if (!SessionManager.isAdmin()) {
            LOGGER.severe("Non-admin attempting to access Game List. Redirecting.");

            Platform.runLater(this::navigateToAdminSettings);
            return;
        }
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);
        setupFileChooser();
        loadGameList();
        LOGGER.info("Game List Controller initialized.");
    }

    private void setupFileChooser() {
        xmlFileChooser = new FileChooser();
        xmlFileChooser.setTitle(LocaleManager.getString("admin.export.title"));
        xmlFileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XML Files (*.xml)", "*.xml")
        );
        xmlFileChooser.setInitialFileName("royalmate_games_export.xml");
    }

    private void loadGameList() {
        LOGGER.fine("Loading game list...");
        gameListContainer.getChildren().clear();
        hideMessage();

        List<Game> games = adminService.getAllGames();

        if (games.isEmpty()) {
            LOGGER.info("No games found in the database.");
            Label noGamesLabel = new Label(LocaleManager.getString("admin.gamelist.nodata"));
            noGamesLabel.getStyleClass().add("message-label");
            noGamesLabel.setTextFill(Color.LIGHTGRAY);
            gameListContainer.getChildren().add(noGamesLabel);
            return;
        }

        LOGGER.fine("Found " + games.size() + " games. Creating UI rows...");
        boolean alternate = false;
        for (Game game : games) {
            try {
                Node gameRow = createGameRow(game);

                if (alternate) {
                    gameRow.getStyleClass().add("game-row-alt");
                } else {
                    gameRow.getStyleClass().add("game-row");
                }
                gameListContainer.getChildren().add(gameRow);
                alternate = !alternate;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error creating row for game: " + game.getName(), e);

            }
        }
    }

    private Node createGameRow(Game game) throws IOException {
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("game-row-base");

        ImageView thumbnail = new ImageView();
        thumbnail.setFitHeight(60);
        thumbnail.setFitWidth(90);
        thumbnail.setPreserveRatio(true);
        if (game.getCoverImageData() != null) {
            try {
                thumbnail.setImage(ImageUtil.byteArrayToImage(game.getCoverImageData()));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load cover image for game: " + game.getName(), e);
                thumbnail.setImage(null);

            }
        } else {

            thumbnail.setImage(null);
        }

        VBox infoBox = new VBox(2);
        Label nameLabel = new Label(game.getName());
        nameLabel.getStyleClass().add("game-name-label");

        HBox typeBox = new HBox(5);
        typeBox.setAlignment(Pos.CENTER_LEFT);
        Label typeLabel = new Label(game.getGameType().name());
        typeLabel.getStyleClass().add("game-type-chip");
        typeBox.getChildren().add(typeLabel);

        Label creatorLabel = new Label(LocaleManager.getString("admin.gamelist.label.by") + " " + game.getCreatedByAdminUsername());
        creatorLabel.getStyleClass().add("game-sub-label");

        Label spinsLabel = new Label(game.getTotalSpins() + " " + LocaleManager.getString("admin.gamelist.label.spins"));
        spinsLabel.getStyleClass().add("game-sub-label");

        infoBox.getChildren().addAll(nameLabel, typeBox, creatorLabel, spinsLabel);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        VBox buttonBox = new VBox(5);
        buttonBox.setAlignment(Pos.CENTER);
        Button editButton = new Button(LocaleManager.getString("admin.button.editgame"));
        editButton.getStyleClass().addAll("action-button", "warning");
        editButton.setOnAction(e -> handleEditGame(game));

        Button deleteButton = new Button(LocaleManager.getString("admin.button.deletegame"));
        deleteButton.getStyleClass().addAll("action-button", "negative");
        deleteButton.setOnAction(e -> handleDeleteGame(game));

        buttonBox.getChildren().addAll(editButton, deleteButton);

        row.getChildren().addAll(thumbnail, infoBox, buttonBox);
        row.setPadding(new Insets(10));

        return row;
    }

    @FXML
    private void handleAddGame(ActionEvent event) {
        LOGGER.info("Add New Game button clicked");
        navigateTo(event, "/sk/vava/royalmate/view/add-game-view.fxml");
    }

    @FXML
    private void handleExportData(ActionEvent event) {
        hideMessage();
        LOGGER.info("Export Data button clicked.");

        List<Game> gamesToExport;
        try {
            gamesToExport = adminService.getAllGamesWithStats();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch game data for export.", e);
            showMessage(LocaleManager.getString("admin.export.error.fetch"), true);
            return;
        }

        if (gamesToExport == null || gamesToExport.isEmpty()) {
            LOGGER.info("No game data found to export.");
            showMessage(LocaleManager.getString("admin.export.nodata"), false);
            return;
        }

        Document xmlDoc;
        try {
            xmlDoc = createGamesXmlDocument(gamesToExport);
        } catch (ParserConfigurationException e) {
            LOGGER.log(Level.SEVERE, "Error creating XML document.", e);
            showMessage(LocaleManager.getString("admin.export.error.generate"), true);
            return;
        }

        Window window = rootPane.getScene().getWindow();
        File selectedFile = xmlFileChooser.showSaveDialog(window);

        if (selectedFile != null) {

            if (!selectedFile.getName().toLowerCase().endsWith(".xml")) {
                selectedFile = new File(selectedFile.getParentFile(), selectedFile.getName() + ".xml");
            }

            if (writeXmlToFile(xmlDoc, selectedFile)) {
                String successMsg = MessageFormat.format(LocaleManager.getString("admin.export.success"), selectedFile.getName());
                showMessage(successMsg, false);
                LOGGER.info("Successfully exported game data to: " + selectedFile.getAbsolutePath());
            }

        } else {
            LOGGER.info("XML Export cancelled by user.");
        }
    }

    private Document createGamesXmlDocument(List<Game> games) throws ParserConfigurationException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();

        Element rootElement = doc.createElement("games");
        doc.appendChild(rootElement);

        for (Game game : games) {

            Element gameElement = doc.createElement("game");

            gameElement.setAttribute("id", String.valueOf(game.getId()));
            gameElement.setAttribute("name", game.getName() != null ? game.getName() : "");
            gameElement.setAttribute("type", game.getGameType() != null ? game.getGameType().name() : "");
            gameElement.setAttribute("active", String.valueOf(game.isActive()));
            rootElement.appendChild(gameElement);

            appendChildElement(doc, gameElement, "description", game.getDescription());
            appendChildElement(doc, gameElement, "minStake", formatBigDecimal(game.getMinStake()));
            appendChildElement(doc, gameElement, "maxStake", formatBigDecimal(game.getMaxStake()));
            appendChildElement(doc, gameElement, "volatility", String.valueOf(game.getVolatility()));
            appendChildElement(doc, gameElement, "backgroundColor", game.getBackgroundColor());
            appendChildElement(doc, gameElement, "createdByAdminUsername", game.getCreatedByAdminUsername());
            appendChildElement(doc, gameElement, "createdAt", formatTimestamp(game.getCreatedAt()));

            appendChildElement(doc, gameElement, "totalSpins", String.valueOf(game.getTotalSpins()));
            appendChildElement(doc, gameElement, "maxPayout", formatBigDecimal(game.getMaxPayout()));
        }
        return doc;
    }

    private void appendChildElement(Document doc, Element parent, String tagName, String textContent) {
        if (textContent != null) {
            Element element = doc.createElement(tagName);
            element.appendChild(doc.createTextNode(textContent));
            parent.appendChild(element);
        }
    }

    private String formatBigDecimal(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString();
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return null;

        return timestamp.toInstant()
                .atZone(ZoneId.systemDefault())
                .format(XML_TIMESTAMP_FORMAT);
    }

    private boolean writeXmlToFile(Document xmlDoc, File file) {
        try {

            TransformerFactory transformerFactory = TransformerFactory.newInstance();

            transformerFactory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = transformerFactory.newTransformer();

            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            DOMSource source = new DOMSource(xmlDoc);

            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                StreamResult result = new StreamResult(writer);
                transformer.transform(source, result);
                return true;
            }

        } catch (TransformerException | IOException e) {
            LOGGER.log(Level.SEVERE, "Error writing XML file: " + file.getAbsolutePath(), e);
            String errorMsg = MessageFormat.format(LocaleManager.getString("admin.export.error.write"), e.getMessage());
            showMessage(errorMsg, true);
            return false;
        }
    }

    private void handleDeleteGame(Game game) {
        LOGGER.warning("Attempting to delete game: " + game.getName() + " (ID: " + game.getId() + ")");
        hideMessage();

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle(LocaleManager.getString("admin.gamelist.confirm.delete.title"));

        String headerText = MessageFormat.format(LocaleManager.getString("admin.gamelist.confirm.delete.header"), game.getName());
        confirmation.setHeaderText(headerText);
        confirmation.setContentText(LocaleManager.getString("admin.gamelist.confirm.delete.content"));

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            LOGGER.info("Deletion confirmed for game: " + game.getName());
            boolean success = adminService.deleteGame(game.getId());
            if (success) {
                showMessage(LocaleManager.getString("admin.gamelist.message.delete.success"), false);
                loadGameList();
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

            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource("/sk/vava/royalmate/view/add-game-view.fxml")),
                    LocaleManager.getBundle()
            );
            Parent editGameRoot = loader.load();

            AddGameController addGameController = loader.getController();
            if (addGameController != null) {

                addGameController.loadGameForEditing(game.getId());

                Scene scene = rootPane.getScene();
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

    private void navigateTo(ActionEvent event, String fxmlPath) {
        Node source = (Node) event.getSource();
        try {
            Scene scene = source.getScene();
            if (scene == null) {  return; }
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource(fxmlPath)), LocaleManager.getBundle());
            Parent nextRoot = loader.load();
            scene.setRoot(nextRoot);
            LOGGER.info("Successfully navigated to: " + fxmlPath);
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to load FXML: " + fxmlPath, e);
            showMessage(LocaleManager.getString("admin.gamelist.message.load.error"), true);
        } catch (ClassCastException e) {
            LOGGER.log(Level.SEVERE, "Failed to cast event source to Node.", e);
        }
    }

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