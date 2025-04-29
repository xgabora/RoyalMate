package sk.vava.royalmate.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.beans.value.ChangeListener; // Import ChangeListener
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor; // Import Cursor
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent; // Import MouseEvent for click handler
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox; // Import VBox for root container
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp; // Import Timestamp
// Removed NumberFormat import as it's no longer needed for balance
import java.time.Duration; // Import Duration
import java.time.Instant; // Import Instant
import java.util.Locale; // Import Locale for Locale.US
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;


public class NavbarController {

    private static final Logger LOGGER = Logger.getLogger(NavbarController.class.getName());
    private static final Duration WOF_COOLDOWN = Duration.ofHours(1); // Cooldown period

    // Existing FXML fields
    @FXML private VBox navbarRootContainer; // Renamed root container
    @FXML private HBox navbarContent; // The original content HBox
    @FXML private ImageView logoImageView;
    @FXML private Hyperlink gamesLink;
    @FXML private Hyperlink chatLink;
    @FXML private Hyperlink topWinsLink;
    @FXML private Label usernameLabel;
    @FXML private Label balanceLabel;
    @FXML private ImageView profileIconImageView;
    @FXML private Button logoutButton;
    @FXML private VBox userInfoVBox; // Inject the VBox containing username/balance


    // New FXML fields for WoF Alert
    @FXML private HBox wofAlertBar;
    @FXML private ImageView wofIcon;
    @FXML private Label wofAlertLabel;

    @FXML private Hyperlink serverLink; // Inject server link
    @FXML private Label serverSeparator; // Inject separator

    private ChangeListener<Account> sessionAccountListener;

    @FXML
    public void initialize() {
        // Initial load
        loadUserData(); // Load data based on current session state at init time

        // --- Add Listener for Session Changes ---
        sessionAccountListener = (observable, oldAccount, newAccount) -> {
            LOGGER.fine("SessionManager account changed. Old: " + (oldAccount != null ? oldAccount.getUsername() : "null") + ", New: " + (newAccount != null ? newAccount.getUsername() : "null") + ". Refreshing navbar UI.");
            // Run UI updates on the JavaFX Application Thread
            Platform.runLater(this::loadUserData); // Reload all user data display
            Platform.runLater(this::setupAdminFeatures); // Reload admin features too
        };
        SessionManager.currentAccountProperty().addListener(sessionAccountListener);
        // -----------------------------------------

        // Existing setup calls
        checkWofEligibilityAndUpdateUI();
        addNavigationClickHandlers();
        setupAdminFeatures(); // Initial setup for admin features

        LOGGER.info("Navbar initialized and listener added for session changes.");
    }

    // Optional: Method to clean up listener if navbar can be destroyed before app exit
    // Usually not needed for a main persistent navbar.
    public void cleanupListener() {
        if (sessionAccountListener != null) {
            SessionManager.currentAccountProperty().removeListener(sessionAccountListener);
            LOGGER.info("Navbar session listener removed.");
        }
    }

    /** Updated loadUserData to show balance as plain number + € */
    private void loadUserData() {
        Account currentUser = SessionManager.getCurrentAccount(); // Get current value from session
        if (currentUser != null) {
            usernameLabel.setText(currentUser.getUsername());

            // --- Plain Balance Formatting ---
            // 1. Get the balance, defaulting to zero if null
            BigDecimal balance = currentUser.getBalance() != null ? currentUser.getBalance() : BigDecimal.ZERO;

            // 2. Convert BigDecimal to plain string (may have variable decimal places)
            // Use toPlainString() to avoid potential scientific notation for very large/small numbers.
            String balanceString = balance.toPlainString();

            // 3. Combine the localized "Balance:" text, the plain number string, and the euro symbol
            String balanceText = LocaleManager.getString("navbar.balance") + " " + balanceString + " €";
            balanceLabel.setText(balanceText);
            // --- End of Plain Formatting ---

            setNavLinksEnabled(true);
            logoutButton.setVisible(true);
            setupAdminFeatures(); // Ensure admin features are correctly shown/hidden on refresh

        } else {
            // Handle logged out state
            usernameLabel.setText("Guest"); // Consider localizing "Guest"
            balanceLabel.setText(""); // Clear balance text
            setNavLinksEnabled(false);
            logoutButton.setVisible(false);
            setupAdminFeatures(); // Ensure admin features are hidden on logout
            // Don't log warning here, as listener handles expected logout state
        }
    }

    // --- Wheel of Fortune Logic ---

    private void checkWofEligibilityAndUpdateUI() {
        Account currentUser = SessionManager.getCurrentAccount();
        boolean eligible = false;

        if (currentUser != null) {
            Timestamp lastSpinTimestamp = currentUser.getLastWofSpinAt(); // Assumes getter exists via Lombok
            if (lastSpinTimestamp == null) {
                eligible = true; // Never spun before
                LOGGER.fine("User eligible for WoF spin (never spun).");
            } else {
                Instant lastSpinInstant = lastSpinTimestamp.toInstant();
                Instant now = Instant.now();
                Duration timeSinceLastSpin = Duration.between(lastSpinInstant, now);

                if (timeSinceLastSpin.compareTo(WOF_COOLDOWN) >= 0) {
                    eligible = true; // Cooldown passed
                    LOGGER.fine("User eligible for WoF spin (cooldown passed: " + timeSinceLastSpin + ").");
                } else {
                    eligible = false; // Still in cooldown
                    LOGGER.fine("User not eligible for WoF spin (cooldown remaining: " + WOF_COOLDOWN.minus(timeSinceLastSpin) + ").");
                }
            }
        } else {
            eligible = false; // Not logged in, not eligible
        }

        // Update UI based on eligibility
        final boolean finalEligible = eligible; // Need final variable for lambda
        javafx.application.Platform.runLater(() -> { // Ensure UI update on JavaFX thread
            wofAlertBar.setVisible(finalEligible);
            wofAlertBar.setManaged(finalEligible); // Only manage layout if visible
            // Update text in case locale changed, though %key in FXML handles this mostly
            // wofAlertLabel.setText(LocaleManager.getString("navbar.wof.alert"));
        });
    }

    @FXML
    private void handleWofAlertClick(MouseEvent event) {
        // Only navigate if the alert bar is actually visible (meaning eligible)
        if(wofAlertBar.isVisible()) {
            LOGGER.info("WoF alert bar clicked - Navigating to Wheel of Fortune (Placeholder)");
            navigateTo(event, "/sk/vava/royalmate/view/wheel-view.fxml"); // Navigate to the future wheel view
        }
    }

    // --- End WoF Logic ---

    private void setupAdminFeatures() {
        boolean isAdmin = SessionManager.isAdmin();
        // Show/hide server link and its separator based on admin status
        serverLink.setVisible(isAdmin);
        serverLink.setManaged(isAdmin);
        serverSeparator.setVisible(isAdmin);
        serverSeparator.setManaged(isAdmin);
    }

    private void addNavigationClickHandlers() {
        // Logo -> Main Menu
        logoImageView.setCursor(javafx.scene.Cursor.HAND);
        logoImageView.setOnMouseClicked(this::handleLogoClick);

        // User Info Area (Username/Balance) -> Profile
        Node userInfoArea = usernameLabel.getParent(); // Get the VBox container
        if (userInfoArea != null) {
            userInfoArea.setCursor(javafx.scene.Cursor.HAND);
            userInfoArea.setOnMouseClicked(this::handleProfileClick);
        } else {
            LOGGER.warning("Could not find parent VBox for username/balance labels to add click handler.");
            // Fallback: Add handlers to labels directly (less ideal for hit area)
            usernameLabel.setCursor(javafx.scene.Cursor.HAND);
            usernameLabel.setOnMouseClicked(this::handleProfileClick);
            balanceLabel.setCursor(javafx.scene.Cursor.HAND);
            balanceLabel.setOnMouseClicked(this::handleProfileClick);
        }

        // Profile Icon -> Profile
        profileIconImageView.setCursor(javafx.scene.Cursor.HAND);
        profileIconImageView.setOnMouseClicked(this::handleProfileClick);
    }

    // --- New Click Handlers ---
    private void handleLogoClick(MouseEvent event) {
        LOGGER.info("Logo clicked - Navigating to Main Menu.");
        navigateTo(event, "/sk/vava/royalmate/view/main-menu-view.fxml");
    }

    private void handleProfileClick(MouseEvent event) {
        LOGGER.info("User info/icon clicked - Navigating to Profile.");
        navigateTo(event, "/sk/vava/royalmate/view/profile-view.fxml");
    }

    private void setNavLinksEnabled(boolean enabled) {
        gamesLink.setDisable(!enabled);
        chatLink.setDisable(!enabled);
        topWinsLink.setDisable(!enabled);
    }

    @FXML
    private void handleGames(ActionEvent event) {
        LOGGER.info("Navigate to Games Search (from navbar)");
        // Navigate without any pre-selected filters
        navigateTo(event, "/sk/vava/royalmate/view/game-search-view.fxml");
    }

    @FXML
    private void handleChat(ActionEvent event) {
        LOGGER.info("Navigate to Chat");
        navigateTo(event, "/sk/vava/royalmate/view/chat-view.fxml"); // Correct path
    }

    @FXML
    private void handleTopWins(ActionEvent event) {
        LOGGER.info("Navigate to Leaderboards");
        navigateTo(event, "/sk/vava/royalmate/view/leaderboard-view.fxml"); // Navigate to leaderboard view
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        String username = SessionManager.isLoggedIn() ? SessionManager.getCurrentAccount().getUsername() : "Unknown";
        LOGGER.info("User " + username + " logging out from navbar.");
        SessionManager.logout();
        navigateTo(event, "/sk/vava/royalmate/view/splash-view.fxml");
    }

    @FXML
    private void handleServer(ActionEvent event) {
        LOGGER.info("Navigate to Server Settings");
        navigateTo(event, "/sk/vava/royalmate/view/admin-settings-view.fxml"); // Navigate to admin view
    }


    private void navigateTo(MouseEvent event, String fxmlPath) { // Overload for MouseEvent
        Node source = (Node) event.getSource();
        navigateTo(source, fxmlPath);
    }
    private void navigateTo(ActionEvent event, String fxmlPath) { // Overload for ActionEvent
        Node source = (Node) event.getSource();
        navigateTo(source, fxmlPath);
    }

    // Common navigation logic using Node source
    private void navigateTo(Node source, String fxmlPath) {
        try {
            Scene scene = source.getScene();
            if (scene == null) {
                scene = navbarRootContainer.getScene(); // Fallback to root container
                if (scene == null) {
                    LOGGER.severe("Could not get current scene from event source or root node.");
                    return;
                }
            }
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource(fxmlPath)), LocaleManager.getBundle());
            Parent nextRoot = loader.load();
            scene.setRoot(nextRoot);
            LOGGER.info("Successfully navigated to: " + fxmlPath);
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to load FXML: " + fxmlPath, e);
        } catch (ClassCastException e) {
            LOGGER.log(Level.SEVERE, "Failed to cast event source to Node.", e);
        }
    }

    public void refreshUserDataDisplay() {
        // Public method that can be called by other controllers (like ProfileController after withdrawal)
        LOGGER.info("Refreshing navbar user data display.");
        Platform.runLater(this::loadUserData); // Ensure UI update happens on FX thread
    }

}