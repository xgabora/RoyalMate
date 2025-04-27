package sk.vava.royalmate.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils; // Needed for Image -> BufferedImage
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.model.ChatMessage; // Placeholder model
import sk.vava.royalmate.model.HomepageBanner; // Placeholder model
import sk.vava.royalmate.service.AdminService;
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import javax.imageio.ImageIO; // Needed for BufferedImage -> byte[]
import java.awt.image.BufferedImage; // Needed for conversion
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminSettingsController {

    private static final Logger LOGGER = Logger.getLogger(AdminSettingsController.class.getName());

    @FXML
    private BorderPane rootPane; // <-- Must be BorderPane and have @FXML
    @FXML private ImageView bannerImageView;
    @FXML private Button uploadBannerButton;
    @FXML private Label bannerMessageLabel;
    @FXML private TextArea pinnedMessageArea;
    @FXML private Button updateChatButton;
    @FXML private Label chatMessageLabel;
    @FXML private ComboBox<Account> playerComboBox;
    @FXML private TextField amountField;
    @FXML private Button addFundsButton;
    @FXML private Button subtractFundsButton;
    @FXML private Button makeAdminButton;
    @FXML private Button removeAccountButton;
    @FXML private Label playerMessageLabel;
    @FXML private Button gameListButton;
    @FXML private Button addGameButton;

    private AdminService adminService;
    private FileChooser imageFileChooser;

    public AdminSettingsController() {
        adminService = new AdminService();
    }

    @FXML
    public void initialize() {
        if (!SessionManager.isAdmin()) {
            LOGGER.severe("Non-admin attempting to access Admin Settings. Redirecting.");
            // Redirect or block access - ideally handled before loading this view
            return;
        }
        configurePlayerComboBox();
        loadInitialData();
        setupFileChooser();
        LOGGER.info("Admin Settings initialized.");
    }

    private void configurePlayerComboBox() {
        playerComboBox.setConverter(new StringConverter<Account>() {
            @Override
            public String toString(Account account) {
                return account == null ? null : account.getUsername() + (account.isAdmin() ? " (Admin)" : "");
            }

            @Override
            public Account fromString(String string) {
                // Not needed for selection-only ComboBox
                return null;
            }
        });
    }

    private void loadInitialData() {
        clearAllMessages();
        loadPlayerList();
        loadBanner();
        loadPinnedMessage();
    }

    private void loadPlayerList() {
        List<Account> players = adminService.getAllPlayers();
        playerComboBox.setItems(FXCollections.observableArrayList(players));
    }

    private void loadBanner() {
        Optional<HomepageBanner> bannerOpt = adminService.getMainBanner(); // Placeholder call
        if (bannerOpt.isPresent() && bannerOpt.get().getImageData() != null) {
            byte[] imgBytes = bannerOpt.get().getImageData();
            try (ByteArrayInputStream bis = new ByteArrayInputStream(imgBytes)) {
                Image image = new Image(bis);
                bannerImageView.setImage(image);
                showBannerMessage(LocaleManager.getString("admin.message.banner.select"), false); // Show default prompt
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error loading banner image from DB data.", e);
                bannerImageView.setImage(null); // Clear image on error
                showBannerMessage(LocaleManager.getString("admin.message.banner.error"), true);
            }
        } else {
            bannerImageView.setImage(null); // Clear if no banner
            showBannerMessage(LocaleManager.getString("admin.message.banner.nodata"), false);
        }
    }

    private void loadPinnedMessage() {
        Optional<ChatMessage> pinnedOpt = adminService.getPinnedMessage(); // Placeholder call
        pinnedMessageArea.setText(pinnedOpt.map(ChatMessage::getMessageText).orElse(""));
    }

    private void setupFileChooser() {
        imageFileChooser = new FileChooser();
        imageFileChooser.setTitle(LocaleManager.getString("admin.message.banner.select"));
        imageFileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
    }

    // --- Event Handlers ---

    @FXML
    void handleUploadBanner(ActionEvent event) {
        clearBannerMessage();
        File selectedFile = imageFileChooser.showOpenDialog(rootPane.getScene().getWindow());

        if (selectedFile != null) {
            try {
                Image image = new Image(selectedFile.toURI().toString());
                // Convert JavaFX Image to byte[] for DB
                byte[] imageData = imageToByteArray(image, selectedFile.getName().toLowerCase().endsWith("png") ? "png" : "jpg");

                if (imageData != null) {
                    // Use a default name or allow admin to set one? Using default for now.
                    boolean success = adminService.updateMainBanner("main_banner", imageData); // Placeholder call
                    if (success) {
                        bannerImageView.setImage(image); // Update UI immediately
                        showBannerMessage(LocaleManager.getString("admin.message.banner.success"), false);
                    } else {
                        showBannerMessage(LocaleManager.getString("admin.message.banner.error"), true);
                    }
                } else {
                    showBannerMessage("Failed to convert image data.", true);
                }

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error processing selected image file: " + selectedFile.getPath(), e);
                showBannerMessage("Error reading or processing image file.", true);
            }
        } else {
            LOGGER.fine("Banner upload cancelled by user.");
        }
    }

    @FXML
    void handleUpdatePinnedMessage(ActionEvent event) {
        clearChatMessage();
        String messageText = pinnedMessageArea.getText();
        boolean success = adminService.updatePinnedMessage(messageText); // Placeholder call
        if(success) {
            showChatMessage(LocaleManager.getString("admin.message.chat.success"), false);
        } else {
            showChatMessage(LocaleManager.getString("admin.message.chat.error"), true);
        }
    }

    @FXML
    void handleAddFunds(ActionEvent event) {
        executePlayerAction((account, amount) -> adminService.addFundsToPlayer(account.getId(), amount),
                LocaleManager.getString("admin.message.player.addsuccess"),
                LocaleManager.getString("admin.message.player.adderror"), true);
    }

    @FXML
    void handleSubtractFunds(ActionEvent event) {
        executePlayerAction((account, amount) -> adminService.subtractFundsFromPlayer(account.getId(), amount),
                LocaleManager.getString("admin.message.player.subsuccess"),
                LocaleManager.getString("admin.message.player.suberror"), true);
    }

    @FXML
    void handleMakeAdmin(ActionEvent event) {
        executePlayerAction((account, amount) -> adminService.setPlayerAdminStatus(account.getId(), !account.isAdmin()), // Toggle status
                LocaleManager.getString("admin.message.player.adminsetsuccess"),
                LocaleManager.getString("admin.message.player.adminseterror"), false); // Amount not needed
    }

    @FXML
    void handleRemoveAccount(ActionEvent event) {
        Account selectedAccount = playerComboBox.getValue();
        if (selectedAccount == null) {
            showPlayerMessage(LocaleManager.getString("admin.message.player.select"), true);
            return;
        }

        // Confirmation Dialog
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Account Removal");
        confirmation.setHeaderText("Remove Account: " + selectedAccount.getUsername());
        confirmation.setContentText("Are you sure you want to permanently remove this account? This cannot be undone.");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Proceed with deletion
            executePlayerAction((account, amount) -> adminService.deletePlayerAccount(account.getId()),
                    LocaleManager.getString("admin.message.player.removesuccess"),
                    LocaleManager.getString("admin.message.player.removeerror"), false); // Amount not needed
            loadPlayerList(); // Refresh list after deletion
        } else {
            LOGGER.info("Account removal cancelled by admin for user: " + selectedAccount.getUsername());
        }
    }

    // --- Helper for Player Actions ---
    private void executePlayerAction(PlayerAction action, String successMsg, String errorMsg, boolean requiresAmount) {
        clearPlayerMessage();
        Account selectedAccount = playerComboBox.getValue();
        BigDecimal amount = null;

        if (selectedAccount == null) {
            showPlayerMessage(LocaleManager.getString("admin.message.player.select"), true);
            return;
        }

        if (requiresAmount) {
            try {
                String amountText = amountField.getText().replace(',', '.').trim();
                if (amountText.isEmpty()) throw new NumberFormatException("Amount is empty");
                amount = new BigDecimal(amountText);
                if (amount.scale() > 2) throw new NumberFormatException("Too many decimal places");
                if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Amount must be positive");
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Invalid amount for player action: " + amountField.getText() + " - " + e.getMessage());
                showPlayerMessage(LocaleManager.getString("admin.message.player.amountinvalid"), true);
                return;
            }
        }

        try {
            boolean success = action.execute(selectedAccount, amount);
            if (success) {
                showPlayerMessage(successMsg, false);
                amountField.clear(); // Clear amount field on success
                // Optionally refresh specific player data if needed, or full list on delete
                if (action.toString().contains("deletePlayerAccount")) { // Hacky check - better ways exist
                    loadPlayerList();
                } else {
                    // Optionally refresh the selected player details or the whole list
                }

            } else {
                showPlayerMessage(errorMsg, true);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing player action for " + selectedAccount.getUsername(), e);
            showPlayerMessage(errorMsg + " (Exception)", true);
        }
    }

    // Functional interface for player actions
    @FunctionalInterface
    interface PlayerAction {
        boolean execute(Account account, BigDecimal amount) throws Exception;
    }

    // --- Placeholder Game Button Handlers ---
// ... inside AdminSettingsController ...

    @FXML void handleGameList(ActionEvent event) {
        LOGGER.info("Open Game List clicked");
        navigateTo(event, "/sk/vava/royalmate/view/game-list-view.fxml");
    }
    @FXML void handleAddGame(ActionEvent event) {
        LOGGER.info("Add New Game clicked");
        navigateTo(event, "/sk/vava/royalmate/view/add-game-view.fxml");
    }

    // --- Navigation Helper ---
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
            showMessage(playerMessageLabel, "Error loading page.", true); // Show error on current screen
        } catch (ClassCastException e) {
            LOGGER.log(Level.SEVERE, "Failed to cast event source to Node.", e);
        }
    }


    // --- Message Display Helpers ---
    private void clearAllMessages() {
        clearBannerMessage();
        clearChatMessage();
        clearPlayerMessage();
    }
    private void clearBannerMessage() { bannerMessageLabel.setVisible(false); bannerMessageLabel.setManaged(false); }
    private void clearChatMessage() { chatMessageLabel.setVisible(false); chatMessageLabel.setManaged(false); }
    private void clearPlayerMessage() { playerMessageLabel.setVisible(false); playerMessageLabel.setManaged(false); }

    private void showBannerMessage(String msg, boolean isError) { showMessage(bannerMessageLabel, msg, isError); }
    private void showChatMessage(String msg, boolean isError) { showMessage(chatMessageLabel, msg, isError); }
    private void showPlayerMessage(String msg, boolean isError) { showMessage(playerMessageLabel, msg, isError); }

    private void showMessage(Label label, String message, boolean isError) {
        Platform.runLater(()-> {
            label.setText(message);
            label.setTextFill(isError ? Color.RED : Color.GREEN);
            label.setVisible(true);
            label.setManaged(true);
        });
    }

    // --- Image Conversion Helper ---
    private byte[] imageToByteArray(Image image, String format) throws IOException {
        BufferedImage bImage = SwingFXUtils.fromFXImage(image, null);
        if (bImage == null) return null;
        try (ByteArrayOutputStream s = new ByteArrayOutputStream()) {
            ImageIO.write(bImage, format, s); // format should be "png" or "jpg"
            return s.toByteArray();
        }
    }
}