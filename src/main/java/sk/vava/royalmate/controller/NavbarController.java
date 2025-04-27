package sk.vava.royalmate.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
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


    @FXML
    public void initialize() {
        loadUserData();
        checkWofEligibilityAndUpdateUI();
        addNavigationClickHandlers(); // Add click handlers
        LOGGER.info("Navbar initialized for locale: " + LocaleManager.getCurrentLocale().toLanguageTag());

        // Make alert bar clickable
        wofAlertBar.setCursor(Cursor.HAND);
    }

    private void loadUserData() {
        Account currentUser = SessionManager.getCurrentAccount();
        if (currentUser != null) {
            usernameLabel.setText(currentUser.getUsername());

            // --- Simplified Balance Formatting ---
            // 1. Get the balance, defaulting to zero if null
            BigDecimal balance = currentUser.getBalance() != null ? currentUser.getBalance() : BigDecimal.ZERO;

            // 2. Format the BigDecimal to 2 decimal places using String.format
            //    Use Locale.US to ensure '.' is used as the decimal separator, regardless of system locale.
            String formattedBalance = String.format(Locale.US, "%.2f", balance);

            // 3. Combine the localized "Balance:" text with the formatted amount and the euro symbol
            String balanceText = LocaleManager.getString("navbar.balance") + " " + formattedBalance + " €";
            balanceLabel.setText(balanceText);
            // --- End of Simplified Formatting ---

            setNavLinksEnabled(true);
            logoutButton.setVisible(true);
        } else {
            usernameLabel.setText("Guest");
            balanceLabel.setText(""); // Keep balance empty for guests
            setNavLinksEnabled(false);
            logoutButton.setVisible(false);
            LOGGER.warning("Navbar loaded but no user session found!");
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

    @FXML private void handleGames(ActionEvent event) { LOGGER.info("Navigate to Games (Placeholder)"); }
    @FXML private void handleChat(ActionEvent event) { LOGGER.info("Navigate to Chat (Placeholder)"); }
    @FXML private void handleTopWins(ActionEvent event) { LOGGER.info("Navigate to Top Wins (Placeholder)"); }

    @FXML
    private void handleLogout(ActionEvent event) {
        String username = SessionManager.isLoggedIn() ? SessionManager.getCurrentAccount().getUsername() : "Unknown";
        LOGGER.info("User " + username + " logging out from navbar.");
        SessionManager.logout();
        navigateTo(event, "/sk/vava/royalmate/view/splash-view.fxml");
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