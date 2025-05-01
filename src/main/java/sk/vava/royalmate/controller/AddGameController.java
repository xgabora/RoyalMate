package sk.vava.royalmate.controller;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
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
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import sk.vava.royalmate.model.*; // Import models
import sk.vava.royalmate.service.AdminService;
import sk.vava.royalmate.util.ImageUtil; // Import ImageUtil helper
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import javafx.stage.Window; // Import Window
import javafx.util.StringConverter;
import org.w3c.dom.Document; // Import DOM Document
import org.w3c.dom.Element; // Import DOM Element
import org.w3c.dom.NodeList; // Import NodeList
import org.xml.sax.SAXException; // Import SAXException
import javax.xml.XMLConstants; // Import XMLConstants
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;


import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.MessageFormat; // For title formatting
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AddGameController {

    private static final Logger LOGGER = Logger.getLogger(AddGameController.class.getName());
    private static final int MAX_SYMBOLS = 6;

    // --- FXML Fields ---
    @FXML private BorderPane rootPane;
    @FXML private Label titleLabel;
    @FXML private TextField gameNameField;
    @FXML private HBox gameTypeBox;
    @FXML private ToggleButton slotToggleButton;
    @FXML private ToggleButton rouletteToggleButton;
    @FXML private ToggleButton coinflipToggleButton;
    @FXML private TextArea descriptionArea;
    @FXML private VBox coverUploadBox;
    @FXML private ImageView coverImageView;
    @FXML private Label coverPromptLabel;
    @FXML private Label coverHintLabel;
    @FXML private Label coverErrorLabel;
    @FXML private ComboBox<BigDecimal> minWagerComboBox;
    @FXML private ComboBox<BigDecimal> maxWagerComboBox;
    @FXML private ComboBox<Integer> volatilityComboBox;
    @FXML private ComboBox<NamedColor> bgColorComboBox;
    @FXML private GridPane symbolGrid;
    @FXML private Label symbolPromptLabel;
    @FXML private Label symbolErrorLabel;
    @FXML private Button actionButton; // Unified button
    @FXML private Button importButton; // <-- Inject import button
    @FXML private Label generalMessageLabel;

    // --- Services and Helpers ---
    private final AdminService adminService;
    private final FileChooser imageFileChooser;
    private FileChooser xmlImportFileChooser; // <-- File chooser for XML
    private ToggleGroup gameTypeGroup;

    // --- State ---
    private Game gameToEdit = null; // If null, ADD mode; otherwise EDIT mode
    private final ObjectProperty<ImageDataHolder> coverImageData = new SimpleObjectProperty<>(null);
    private final Map<Integer, ObjectProperty<ImageDataHolder>> symbolImageDataMap = new HashMap<>(); // Index -> Property
    // No need to store existingSymbolAssets map anymore, we fetch assets when needed

    // --- Data ---
    private final List<BigDecimal> minWagers = List.of(new BigDecimal("0.10"), new BigDecimal("0.50"), new BigDecimal("1.00"), new BigDecimal("2.00"));
    private final List<BigDecimal> maxWagers = List.of(new BigDecimal("500.00"), new BigDecimal("1000.00"), new BigDecimal("2000.00"));
    private final List<Integer> volatilities = List.of(1, 2, 3, 4, 5);
    private final List<NamedColor> backgroundColors = List.of(
            new NamedColor("Blue", "#2980B9"), new NamedColor("Red", "#C0392B"),
            new NamedColor("Dark Green", "#16A085"), new NamedColor("Light Green", "#2ECC71"),
            new NamedColor("Orange", "#F39C12"), new NamedColor("Yellow", "#F1C40F"),
            new NamedColor("Purple", "#8E44AD"), new NamedColor("Dark Grey", "#34495E"),
            new NamedColor("Light Grey", "#95A5A6"), new NamedColor("White", "#FFFFFF")
    );

    // Helper record for ComboBox color display
    private record NamedColor(String name, String hexValue) {
        @Override public String toString() { return name; }
        public String getHexValue() { return hexValue; }
    }
    // Helper record to store image data and file info temporarily
    private record ImageDataHolder(byte[] data, String format, String name) {}

    // --- Constructor ---
    public AddGameController() {
        adminService = new AdminService();
        imageFileChooser = new FileChooser();
        xmlImportFileChooser = new FileChooser(); // Initialize XML chooser
        setupFileChooser();
        setupXmlImportFileChooser(); // Configure XML chooser
    }

    // --- Initialization ---
    @FXML
    public void initialize() {
        setupGameTypeToggle();
        setupComboBoxes();
        createSymbolUploadBoxes();
        clearMessages();
        prepareForAddMode(); // Default to ADD mode initially

        if (!SessionManager.isAdmin()) {
            LOGGER.severe("Non-admin loaded Add/Edit Game Screen!");
            rootPane.setDisable(true);
            // Platform.runLater(this::navigateToAdminSettings); // Option to navigate back
        }
    }

    /** Prepares the screen for adding a new game (default state) */
    private void prepareForAddMode() {
        this.gameToEdit = null;
        titleLabel.setText(LocaleManager.getString("admin.title.addgame"));
        actionButton.setText(LocaleManager.getString("admin.button.addnewgame"));
        actionButton.setOnAction(this::handleSaveGameAction);
        clearForm();
        LOGGER.info("Add/Edit Game Controller initialized in ADD mode.");
    }

    /**
     * Public method called by GameListController to populate the form for editing.
     * @param gameId The ID of the game to load.
     */
    public void loadGameForEditing(int gameId) {
        LOGGER.info("Loading game data for editing, ID: " + gameId);
        Optional<Game> gameOpt = adminService.getGameDetails(gameId);

        if (gameOpt.isPresent()) {
            this.gameToEdit = gameOpt.get();

            // --- Populate Core Fields ---
            titleLabel.setText(MessageFormat.format(LocaleManager.getString("admin.title.editgame"), gameToEdit.getName()));
            gameNameField.setText(gameToEdit.getName());
            descriptionArea.setText(gameToEdit.getDescription());

            switch (gameToEdit.getGameType()) {
                case SLOT -> gameTypeGroup.selectToggle(slotToggleButton);
                case ROULETTE -> gameTypeGroup.selectToggle(rouletteToggleButton);
                case COINFLIP -> gameTypeGroup.selectToggle(coinflipToggleButton);
                default -> gameTypeGroup.selectToggle(null); // Should not happen
            }
            boolean isSlot = gameToEdit.getGameType() == GameType.SLOT;
            symbolGrid.setDisable(!isSlot);
            symbolGrid.setOpacity(isSlot ? 1.0 : 0.5);
            symbolPromptLabel.setText(LocaleManager.getString("admin.addgame.label.symbols")); // Reset prompt

            minWagerComboBox.setValue(gameToEdit.getMinStake());
            maxWagerComboBox.setValue(gameToEdit.getMaxStake());
            volatilityComboBox.setValue(gameToEdit.getVolatility());
            bgColorComboBox.setValue(findNamedColorByHex(gameToEdit.getBackgroundColor()));

            // --- Load Assets ---
            loadAndDisplayExistingAssets(gameId);

            // --- Update Button ---
            actionButton.setText(LocaleManager.getString("admin.button.updategame"));
            actionButton.setOnAction(this::handleSaveGameAction);

            LOGGER.info("Game data loaded for editing: " + gameToEdit.getName());

        } else {
            LOGGER.severe("Could not find game data for ID: " + gameId + " to edit.");
            showGeneralMessage(LocaleManager.getString("admin.addgame.message.loaderror"), true);
            rootPane.setDisable(true);
        }
    }

    /** Fetches and displays existing assets for the game being edited */
    private void loadAndDisplayExistingAssets(int gameId) {
        List<GameAsset> assets = adminService.getGameAssets(gameId);
        clearSymbolUploads();

        // Load Cover
        assets.stream()
                .filter(a -> a.getAssetType() == AssetType.COVER)
                .findFirst()
                .ifPresent(cover -> {
                    try {
                        Image img = ImageUtil.byteArrayToImage(cover.getImageData());
                        coverImageView.setImage(img);
                        coverImageData.set(new ImageDataHolder(cover.getImageData(), ImageUtil.getFileExtension(cover.getAssetName()), cover.getAssetName()));
                        coverPromptLabel.setText(cover.getAssetName());
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to load existing cover image for game ID " + gameId, e);
                        showError(coverErrorLabel,"Error loading cover");
                    }
                });
        if (coverImageData.get() == null) {
            coverPromptLabel.setText(LocaleManager.getString("admin.addgame.label.uploadcover"));
        }

        // Load Symbols
        List<GameAsset> symbols = assets.stream()
                .filter(a -> a.getAssetType() == AssetType.SYMBOL)
                // Optional: Sort by ID or Name if needed for consistent order
                // .sorted(Comparator.comparing(GameAsset::getId))
                .collect(Collectors.toList());
        for (int i = 0; i < Math.min(symbols.size(), MAX_SYMBOLS); i++) {
            GameAsset symbol = symbols.get(i);
            ObjectProperty<ImageDataHolder> prop = symbolImageDataMap.get(i);
            if (prop != null) {
                try {
                    prop.set(new ImageDataHolder(symbol.getImageData(), ImageUtil.getFileExtension(symbol.getAssetName()), symbol.getAssetName()));
                    // Payout multiplier is no longer displayed/edited here
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to load existing symbol image " + i + " for game ID " + gameId, e);
                }
            }
        }
    }

    // Helper to find NamedColor by Hex value for ComboBox selection
    private NamedColor findNamedColorByHex(String hexValue) {
        if (hexValue == null) return backgroundColors.get(0);
        return backgroundColors.stream()
                .filter(nc -> hexValue.equalsIgnoreCase(nc.getHexValue()))
                .findFirst()
                .orElse(backgroundColors.get(0));
    }

    // --- Setup Methods ---
    private void setupFileChooser() {
        imageFileChooser.setTitle(LocaleManager.getString("admin.message.banner.select")); // Reuse key
        imageFileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("PNG Files", "*.png"),
                new FileChooser.ExtensionFilter("JPEG Files", "*.jpg", "*.jpeg")
        );
    }

    private void setupGameTypeToggle() {
        gameTypeGroup = new ToggleGroup();
        slotToggleButton.setToggleGroup(gameTypeGroup);
        rouletteToggleButton.setToggleGroup(gameTypeGroup);
        coinflipToggleButton.setToggleGroup(gameTypeGroup);
        slotToggleButton.setSelected(true);

        gameTypeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSlot = newVal == slotToggleButton;
            symbolGrid.setDisable(!isSlot);
            symbolGrid.setOpacity(isSlot ? 1.0 : 0.5);
            String promptKey = isSlot ? "admin.addgame.label.symbols" : "admin.addgame.label.symbols.hint"; // <-- Use new key
            symbolPromptLabel.setText(LocaleManager.getString(promptKey)); // <-- Use variable
            if (!isSlot) {
                clearSymbolUploads();
            }
        });
        boolean isSlot = gameTypeGroup.getSelectedToggle() == slotToggleButton;
        symbolGrid.setDisable(!isSlot);
        symbolGrid.setOpacity(isSlot ? 1.0 : 0.5);
        String initialPromptKey = isSlot ? "admin.addgame.label.symbols" : "admin.addgame.label.symbols.hint"; // <-- Use new key
        symbolPromptLabel.setText(LocaleManager.getString(initialPromptKey)); // <-- Use variable
    }

    private void setupComboBoxes() {
        minWagerComboBox.setItems(FXCollections.observableArrayList(minWagers));
        maxWagerComboBox.setItems(FXCollections.observableArrayList(maxWagers));
        minWagerComboBox.getSelectionModel().selectFirst();
        maxWagerComboBox.getSelectionModel().selectFirst();

        volatilityComboBox.setItems(FXCollections.observableArrayList(volatilities));
        volatilityComboBox.getSelectionModel().select(Integer.valueOf(3));

        bgColorComboBox.setItems(FXCollections.observableArrayList(backgroundColors));
        bgColorComboBox.setConverter(new StringConverter<NamedColor>() {
            @Override public String toString(NamedColor obj) { return obj == null ? null : obj.name(); }
            @Override public NamedColor fromString(String s) { return null; }
        });
        bgColorComboBox.getSelectionModel().selectFirst();
    }

    private void createSymbolUploadBoxes() {
        symbolGrid.getChildren().clear();
        symbolImageDataMap.clear();

        for (int i = 0; i < MAX_SYMBOLS; i++) {
            final int index = i;
            VBox uploadBox = new VBox(5);
            uploadBox.setAlignment(Pos.CENTER);
            uploadBox.getStyleClass().add("symbol-upload-box");
            uploadBox.setPrefSize(80, 100);
            uploadBox.setCursor(Cursor.HAND);

            ImageView imageView = new ImageView();
            imageView.setFitHeight(60);
            imageView.setFitWidth(60);
            imageView.setPreserveRatio(true);

            // --- Set label based on index ---
            String labelKey;
            Object[] labelArgs;
            if (i < 3) { // First 3 are regular
                labelKey = "admin.addgame.symbol.regular";
                labelArgs = new Object[]{i + 1};
            } else if (i < 5) { // Next 2 are rare
                labelKey = "admin.addgame.symbol.rare";
                labelArgs = new Object[]{i - 3 + 1}; // Number them 1, 2 within rare category
            } else {
                labelArgs = null; // Last one is max win
                labelKey = "admin.addgame.symbol.maxwin";
            }
            String labelText = (labelArgs != null)
                    ? MessageFormat.format(LocaleManager.getString(labelKey), labelArgs)
                    : LocaleManager.getString(labelKey);
            Label promptLabel = new Label(labelText);
            promptLabel.getStyleClass().add("upload-hint-label");
            // --------------------------------

            uploadBox.getChildren().addAll(imageView, promptLabel);

            ObjectProperty<ImageDataHolder> imageProp = new SimpleObjectProperty<>(null);
            symbolImageDataMap.put(index, imageProp);

            uploadBox.setOnMouseClicked(event -> handleUploadSymbolImage(index, imageView, promptLabel));

            imageProp.addListener((obs, oldHolder, newHolder) -> {
                // Keep original prompt text logic based on index, but show filename when loaded
                String defaultText = (labelArgs != null)
                        ? MessageFormat.format(LocaleManager.getString(labelKey), labelArgs)
                        : LocaleManager.getString(labelKey);
                if (newHolder != null) {
                    try {
                        imageView.setImage(ImageUtil.byteArrayToImage(newHolder.data()));
                        promptLabel.setText(newHolder.name()); // Show filename on load
                        promptLabel.setTextAlignment(TextAlignment.CENTER);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to display symbol image " + index, e);
                        imageView.setImage(null);
                        promptLabel.setText("Error");
                    }
                } else {
                    imageView.setImage(null);
                    promptLabel.setText(defaultText); // Reset to default text when cleared
                }
            });

            int row = i / (symbolGrid.getColumnConstraints().size());
            int col = i % (symbolGrid.getColumnConstraints().size());
            symbolGrid.add(uploadBox, col, row);
        }
    }

    // --- Image Upload Handlers ---
    @FXML
    void handleUploadCoverImage(javafx.scene.input.MouseEvent event) {
        clearMessages();
        File selectedFile = imageFileChooser.showOpenDialog(rootPane.getScene().getWindow());
        if (selectedFile != null) {
            try {
                Image image = new Image(selectedFile.toURI().toString(), 250, 150, true, true);
                String format = ImageUtil.getFileExtension(selectedFile.getName());
                byte[] imageData = ImageUtil.imageToByteArray(image, format);

                if (imageData != null) {
                    coverImageData.set(new ImageDataHolder(imageData, format, selectedFile.getName()));
                    coverImageView.setImage(image);
                    coverPromptLabel.setText(selectedFile.getName()); // Show new filename
                    coverErrorLabel.setVisible(false);
                    coverErrorLabel.setManaged(false);
                } else {
                    coverImageData.set(null);
                    showError(coverErrorLabel, "Failed to convert image.");
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error processing cover image file: " + selectedFile.getPath(), e);
                coverImageData.set(null);
                showError(coverErrorLabel, "Error reading image file.");
            }
        }
    }

    private void handleUploadSymbolImage(int index, ImageView preview, Label prompt) {
        clearMessages();
        File selectedFile = imageFileChooser.showOpenDialog(rootPane.getScene().getWindow());
        if (selectedFile != null) {
            try {
                Image image = new Image(selectedFile.toURI().toString(), 60, 60, true, true);
                String format = ImageUtil.getFileExtension(selectedFile.getName());
                byte[] imageData = ImageUtil.imageToByteArray(image, format);

                if (imageData != null) {
                    symbolImageDataMap.get(index).set(new ImageDataHolder(imageData, format, selectedFile.getName()));
                    symbolErrorLabel.setVisible(false); symbolErrorLabel.setManaged(false);
                } else {
                    symbolImageDataMap.get(index).set(null);
                    showError(symbolErrorLabel, "Failed to convert symbol " + (index+1));
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error processing symbol image file: " + selectedFile.getPath(), e);
                symbolImageDataMap.get(index).set(null);
                showError(symbolErrorLabel, "Error reading symbol " + (index+1));
            }
        }
    }

    private void clearSymbolUploads() {
        symbolImageDataMap.values().forEach(prop -> prop.set(null));
        // Listeners handle clearing UI
    }

    // --- Save/Update Action ---
    @FXML
    void handleSaveGameAction(ActionEvent event) {
        clearMessages();
        if (!validateInput()) {
            return;
        }

        Game gameData = buildGameFromForm();
        Optional<GameAsset> newCoverAssetOpt = buildCoverAssetFromForm();
        List<GameAsset> symbolAssets = buildSymbolAssetsFromForm();

        actionButton.setDisable(true);
        boolean success = false;
        try {
            if (gameToEdit == null) { // ADD Mode
                if (newCoverAssetOpt.isEmpty()) {
                    showError(coverErrorLabel, LocaleManager.getString("admin.addgame.error.nocover"));
                    actionButton.setDisable(false); // Re-enable on validation fail
                    return; // Stop
                }
                success = adminService.createGame(gameData, newCoverAssetOpt.get(), symbolAssets);
                if (success) {
                    // Navigate back on success
                    LOGGER.info("Game added successfully, navigating to game list.");
                    navigateToGameList(); // <--- ADDED NAVIGATION
                    // clearForm(); // No longer needed as we are navigating away
                } else {
                    showGeneralMessage(LocaleManager.getString("admin.addgame.message.error"), true);
                    actionButton.setDisable(false); // Re-enable on failure
                }
            } else { // EDIT Mode
                gameData.setId(gameToEdit.getId());
                success = adminService.updateGame(gameData, newCoverAssetOpt, symbolAssets);
                if (success) {
                    // Navigate back on success
                    LOGGER.info("Game updated successfully, navigating to game list.");
                    navigateToGameList(); // <--- ADDED NAVIGATION
                    // showGeneralMessage(LocaleManager.getString("admin.addgame.message.updatesuccess"), false); // Message not seen if we navigate immediately
                } else {
                    showGeneralMessage(LocaleManager.getString("admin.addgame.message.updateerror"), true);
                    actionButton.setDisable(false); // Re-enable on failure
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error saving/updating game", e);
            showGeneralMessage(LocaleManager.getString("admin.addgame.message.error.exception"), true);
            actionButton.setDisable(false); // Re-enable on exception
        }
    }

    // --- NEW XML Import Logic ---

    /** Configures the FileChooser for XML import */
    private void setupXmlImportFileChooser() {
        xmlImportFileChooser.setTitle(LocaleManager.getString("admin.import.title"));
        xmlImportFileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XML Files (*.xml)", "*.xml")
        );
    }

    /** Handler for the "Import from XML" button */
    @FXML
    void handleImportXml(ActionEvent event) {
        clearMessages();
        LOGGER.info("Import from XML button clicked.");

        Window window = rootPane.getScene().getWindow();
        File selectedFile = xmlImportFileChooser.showOpenDialog(window);

        if (selectedFile != null) {
            LOGGER.info("Attempting to import game data from: " + selectedFile.getAbsolutePath());
            parseAndLoadGameFromXml(selectedFile);
        } else {
            LOGGER.info("XML Import cancelled by user.");
            showGeneralMessage(LocaleManager.getString("admin.import.error.select"), true);
        }
    }

    /** Parses the selected XML file and populates the form */
    private void parseAndLoadGameFromXml(File xmlFile) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();

            // --- ADJUSTED SECURITY SETTINGS ---
            // Keep secure processing enabled
            dbFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            // Keep disallow doctype decl
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            // Remove or comment out the problematic feature:
            // dbFactory.setFeature("http://xml.apache.org/xml/features/nonvalidating/load-external-dtd", false); // <-- REMOVED/COMMENTED
            // Keep these disabled
            dbFactory.setXIncludeAware(false);
            dbFactory.setExpandEntityReferences(false);
            // ------------------------------------

            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList gameNodes = doc.getElementsByTagName("game");
            if (gameNodes.getLength() > 0) {
                Element gameElement = (Element) gameNodes.item(0);
                populateFormFromXmlElement(gameElement);
                // Switch to ADD mode visually
                this.gameToEdit = null;
                titleLabel.setText(LocaleManager.getString("admin.title.addgame"));
                actionButton.setText(LocaleManager.getString("admin.button.addnewgame"));
                actionButton.setOnAction(this::handleSaveGameAction);
                showGeneralMessage(LocaleManager.getString("admin.import.success"), false);
                LOGGER.info("Successfully loaded game data from XML into form.");
            } else {
                LOGGER.warning("No <game> elements found in the selected XML file.");
                showGeneralMessage(LocaleManager.getString("admin.import.error.nodata"), true);
            }

        } catch (ParserConfigurationException | SAXException e) { // Catch SAXException too
            LOGGER.log(Level.SEVERE, "Error parsing XML file: " + xmlFile.getName(), e);
            showGeneralMessage(MessageFormat.format(LocaleManager.getString("admin.import.error.parse"), e.getMessage()), true);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading XML file: " + xmlFile.getName(), e);
            showGeneralMessage(MessageFormat.format(LocaleManager.getString("admin.import.error.read"), e.getMessage()), true);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing data from XML element for file: " + xmlFile.getName(), e);
            showGeneralMessage(e.getMessage(), true);
        }
    }

    /** Populates form fields based on data from an XML <game> Element */
    private void populateFormFromXmlElement(Element gameElement) throws Exception {
        // --- Extract and Set Data ---
        // Attributes
        String gameName = gameElement.getAttribute("name");
        String gameTypeStr = gameElement.getAttribute("type");

        if (gameName.isEmpty() || gameTypeStr.isEmpty()) {
            throw new Exception(LocaleManager.getString("admin.import.error.missing"));
        }
        gameNameField.setText(gameName);

        // Set Game Type Toggle
        try {
            GameType type = GameType.valueOf(gameTypeStr.toUpperCase());
            switch (type) {
                case SLOT -> gameTypeGroup.selectToggle(slotToggleButton);
                case ROULETTE -> gameTypeGroup.selectToggle(rouletteToggleButton);
                case COINFLIP -> gameTypeGroup.selectToggle(coinflipToggleButton);
            }
        } catch (IllegalArgumentException e) {
            throw new Exception(MessageFormat.format(LocaleManager.getString("admin.import.error.invalidtype"), gameTypeStr));
        }

        // Child Elements
        descriptionArea.setText(getElementTextContent(gameElement, "description"));

        try {
            minWagerComboBox.setValue(new BigDecimal(getElementTextContent(gameElement, "minStake")));
            maxWagerComboBox.setValue(new BigDecimal(getElementTextContent(gameElement, "maxStake")));
            volatilityComboBox.setValue(Integer.parseInt(getElementTextContent(gameElement, "volatility")));
        } catch (NumberFormatException | NullPointerException e) {
            throw new Exception(MessageFormat.format(LocaleManager.getString("admin.import.error.invalidnumber"),
                    "stakes/volatility", e.getMessage()));
        }

        String bgColorHex = getElementTextContent(gameElement, "backgroundColor");
        bgColorComboBox.setValue(findNamedColorByHex(bgColorHex));

        // Clear existing assets - Import only sets definition data
        coverImageData.set(null);
        coverImageView.setImage(null);
        coverPromptLabel.setText(LocaleManager.getString("admin.addgame.label.uploadcover"));
        clearSymbolUploads();
        showGeneralMessage(LocaleManager.getString("admin.import.assetnote"), false); // Inform user
    }

    /** Helper to get text content of a child element, returns empty string if not found */
    private String getElementTextContent(Element parent, String childTagName) {
        NodeList nl = parent.getElementsByTagName(childTagName);
        if (nl != null && nl.getLength() > 0) {
            return nl.item(0).getTextContent();
        }
        return ""; // Return empty instead of null
    }

    // --- END XML Import Logic ---

    /** Builds the core Game object from form inputs */
    private Game buildGameFromForm() {
        return Game.builder()
                .name(gameNameField.getText().trim())
                .description(descriptionArea.getText().trim())
                .gameType(getSelectedGameType())
                .minStake(minWagerComboBox.getValue())
                .maxStake(maxWagerComboBox.getValue())
                .volatility(volatilityComboBox.getValue())
                .backgroundColor(bgColorComboBox.getValue().getHexValue())
                .isActive(true)
                .build();
    }

    /** Builds the cover GameAsset Optional based on uploaded data */
    private Optional<GameAsset> buildCoverAssetFromForm() {
        ImageDataHolder coverHolder = coverImageData.get();
        if (coverHolder != null) {
            boolean isNew = gameToEdit == null || !Objects.equals(getExistingAssetName(AssetType.COVER), coverHolder.name());
            if (isNew) {
                LOGGER.fine("Building new cover asset data.");
                return Optional.of(GameAsset.builder()
                        .assetName("cover_" + gameNameField.getText().trim().replaceAll("\\s+", "_"))
                        .imageData(coverHolder.data())
                        .assetType(AssetType.COVER)
                        .build());
            } else {
                LOGGER.fine("Cover image unchanged, not including in update.");
            }
        }
        return Optional.empty();
    }

    /** Builds the list of symbol GameAssets based on uploaded data */
    private List<GameAsset> buildSymbolAssetsFromForm() {
        List<GameAsset> symbols = new ArrayList<>();
        if (getSelectedGameType() == GameType.SLOT) {
            for (int i = 0; i < MAX_SYMBOLS; i++) {
                ImageDataHolder symbolHolder = symbolImageDataMap.get(i).get();
                if (symbolHolder != null) {
                    BigDecimal payoutMultiplier = BigDecimal.ONE; // Placeholder!

                    symbols.add(GameAsset.builder()
                            .assetName("symbol_" + (i + 1)) // Simple name
                            .imageData(symbolHolder.data())
                            .assetType(AssetType.SYMBOL)
                            .symbolPayoutMultiplier(payoutMultiplier)
                            .build());
                }
            }
        }
        return symbols;
    }


    // --- Validation ---
    private boolean validateInput() {
        boolean valid = true;
        clearMessages();

        if (gameNameField.getText().trim().isEmpty()) {
            showGeneralMessage(LocaleManager.getString("admin.addgame.error.noname"), true); valid = false;
        }
        if (gameTypeGroup.getSelectedToggle() == null) {
            showGeneralMessage("Please select a game type.", true); valid = false; // Add locale key
        }
        BigDecimal minW = minWagerComboBox.getValue();
        BigDecimal maxW = maxWagerComboBox.getValue();
        if (minW == null || maxW == null || minW.compareTo(maxW) >= 0) {
            showGeneralMessage(LocaleManager.getString("admin.addgame.error.stakes"), true); valid = false;
        }
        if (volatilityComboBox.getValue() == null) {
            showGeneralMessage("Please select volatility.", true); valid = false; // Add locale key
        }
        if (bgColorComboBox.getValue() == null) {
            showGeneralMessage("Please select background color.", true); valid = false; // Add locale key
        }

        // Cover Image: Mandatory if adding OR if editing and no cover existed before.
        if (coverImageData.get() == null) {
            boolean existedBefore = false;
            if (gameToEdit != null) {
                // Re-fetch assets to check if cover existed at the start of edit session
                List<GameAsset> assets = adminService.getGameAssets(gameToEdit.getId());
                existedBefore = assets.stream().anyMatch(a -> a.getAssetType() == AssetType.COVER);
            }
            if (!existedBefore) { // If adding OR editing and none existed -> Error
                showError(coverErrorLabel, LocaleManager.getString("admin.addgame.error.nocover"));
                valid = false;
            }
        }

        if (getSelectedGameType() == GameType.SLOT) {
            boolean anySymbolPresent = symbolImageDataMap.values().stream().anyMatch(prop -> prop.get() != null);
            if (!anySymbolPresent) {
                showError(symbolErrorLabel, LocaleManager.getString("admin.addgame.error.nosymbols"));
                valid = false;
            }
        }

        return valid;
    }


    // --- Getters / Helpers ---
    private GameType getSelectedGameType() {
        Toggle selected = gameTypeGroup.getSelectedToggle();
        if (selected == slotToggleButton) return GameType.SLOT;
        if (selected == rouletteToggleButton) return GameType.ROULETTE;
        if (selected == coinflipToggleButton) return GameType.COINFLIP;
        return GameType.SLOT; // Fallback
    }

    private String getExistingAssetName(AssetType type) {
        if (gameToEdit == null) return null;
        // Fetch assets specifically for this check if needed, or rely on initially loaded data
        List<GameAsset> assets = adminService.getGameAssets(gameToEdit.getId());
        if (type == AssetType.COVER) {
            return assets.stream()
                    .filter(a -> a.getAssetType() == AssetType.COVER)
                    .map(GameAsset::getAssetName)
                    .findFirst().orElse(null);
        }
        // Add for symbols if more complex comparison needed
        return null;
    }

    // --- Form/Message Clearing ---
    private void clearForm() {
        gameNameField.clear();
        descriptionArea.clear();
        slotToggleButton.setSelected(true);
        minWagerComboBox.getSelectionModel().selectFirst();
        maxWagerComboBox.getSelectionModel().selectFirst();
        volatilityComboBox.getSelectionModel().select(Integer.valueOf(3));
        bgColorComboBox.getSelectionModel().selectFirst();
        coverImageData.set(null);
        coverImageView.setImage(null);
        coverPromptLabel.setText(LocaleManager.getString("admin.addgame.label.uploadcover")); // Reset prompt
        clearSymbolUploads();
        clearMessages();
        gameToEdit = null; // Ensure we are back in ADD mode
        actionButton.setText(LocaleManager.getString("admin.button.addnewgame"));
        actionButton.setOnAction(this::handleSaveGameAction);
    }

    private void clearMessages() {
        coverErrorLabel.setVisible(false); coverErrorLabel.setManaged(false);
        symbolErrorLabel.setVisible(false); symbolErrorLabel.setManaged(false);
        generalMessageLabel.setVisible(false); generalMessageLabel.setManaged(false);
    }

    private void showError(Label label, String message) {
        showMessage(label, message, true);
    }
    private void showGeneralMessage(String message, boolean isError) {
        showMessage(generalMessageLabel, message, isError);
    }

    private void showMessage(Label label, String message, boolean isError) {
        Platform.runLater(()-> {
            label.setText(message);
            label.setTextFill(isError ? Color.RED : Color.GREEN);
            label.setVisible(true);
            label.setManaged(true);
        });
    }

    // --- Navigation ---
    private void navigateToAdminSettings() {
        // Simplified navigation back
        if (rootPane == null || rootPane.getScene() == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/sk/vava/royalmate/view/admin-settings-view.fxml")), LocaleManager.getBundle());
            Parent nextRoot = loader.load();
            rootPane.getScene().setRoot(nextRoot);
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to navigate back to admin settings.", e);
        }
    }
    // Optional: Navigate back to game list after successful save/update
    private void navigateToGameList() {
        LOGGER.info("Navigating back to Game List.");
        if (rootPane == null || rootPane.getScene() == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/sk/vava/royalmate/view/game-list-view.fxml")), LocaleManager.getBundle());
            Parent nextRoot = loader.load();
            rootPane.getScene().setRoot(nextRoot);
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to navigate back to game list.", e);
        }
    }
}