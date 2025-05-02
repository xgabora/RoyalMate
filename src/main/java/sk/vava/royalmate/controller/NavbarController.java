package sk.vava.royalmate.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NavbarController {

    private static final Logger LOGGER = Logger.getLogger(NavbarController.class.getName());
    private static final Duration WOF_COOLDOWN = Duration.ofHours(1);

    @FXML private VBox navbarRootContainer;
    @FXML private HBox navbarContent;
    @FXML private ImageView logoImageView;
    @FXML private Hyperlink gamesLink;
    @FXML private Hyperlink chatLink;
    @FXML private Hyperlink topWinsLink;
    @FXML private Label usernameLabel;
    @FXML private Label balanceLabel;
    @FXML private ImageView profileIconImageView;
    @FXML private Button logoutButton;
    @FXML private VBox userInfoVBox;

    @FXML private HBox wofAlertBar;
    @FXML private ImageView wofIcon;
    @FXML private Label wofAlertLabel;

    @FXML private Hyperlink serverLink;
    @FXML private Label serverSeparator;

    private ChangeListener<Account> sessionAccountListener;

    @FXML
    public void initialize() {

        loadUserData();

        sessionAccountListener = (observable, oldAccount, newAccount) -> {
            LOGGER.fine("SessionManager account changed. Old: " + (oldAccount != null ? oldAccount.getUsername() : "null") + ", New: " + (newAccount != null ? newAccount.getUsername() : "null") + ". Refreshing navbar UI.");

            Platform.runLater(this::loadUserData);
            Platform.runLater(this::setupAdminFeatures);
        };
        SessionManager.currentAccountProperty().addListener(sessionAccountListener);

        checkWofEligibilityAndUpdateUI();
        addNavigationClickHandlers();
        setupAdminFeatures();

        LOGGER.info("Navbar initialized and listener added for session changes.");
    }

    public void cleanupListener() {
        if (sessionAccountListener != null) {
            SessionManager.currentAccountProperty().removeListener(sessionAccountListener);
            LOGGER.info("Navbar session listener removed.");
        }
    }

    private void loadUserData() {
        Account currentUser = SessionManager.getCurrentAccount();
        if (currentUser != null) {
            usernameLabel.setText(currentUser.getUsername());

            BigDecimal balance = currentUser.getBalance() != null ? currentUser.getBalance() : BigDecimal.ZERO;

            String balanceString = balance.toPlainString();

            String balanceText = LocaleManager.getString("navbar.balance") + " " + balanceString + " €";
            balanceLabel.setText(balanceText);

            setNavLinksEnabled(true);
            logoutButton.setVisible(true);
            setupAdminFeatures();

        } else {

            usernameLabel.setText("Guest");
            balanceLabel.setText("");
            setNavLinksEnabled(false);
            logoutButton.setVisible(false);
            setupAdminFeatures();

        }
    }

    private void checkWofEligibilityAndUpdateUI() {
        Account currentUser = SessionManager.getCurrentAccount();
        boolean eligible = false;

        if (currentUser != null) {
            Timestamp lastSpinTimestamp = currentUser.getLastWofSpinAt();
            if (lastSpinTimestamp == null) {
                eligible = true;
                LOGGER.fine("User eligible for WoF spin (never spun).");
            } else {
                Instant lastSpinInstant = lastSpinTimestamp.toInstant();
                Instant now = Instant.now();
                Duration timeSinceLastSpin = Duration.between(lastSpinInstant, now);

                if (timeSinceLastSpin.compareTo(WOF_COOLDOWN) >= 0) {
                    eligible = true;
                    LOGGER.fine("User eligible for WoF spin (cooldown passed: " + timeSinceLastSpin + ").");
                } else {
                    eligible = false;
                    LOGGER.fine("User not eligible for WoF spin (cooldown remaining: " + WOF_COOLDOWN.minus(timeSinceLastSpin) + ").");
                }
            }
        } else {
            eligible = false;
        }

        final boolean finalEligible = eligible;
        javafx.application.Platform.runLater(() -> {
            wofAlertBar.setVisible(finalEligible);
            wofAlertBar.setManaged(finalEligible);

        });
    }

    @FXML
    private void handleWofAlertClick(MouseEvent event) {

        if(wofAlertBar.isVisible()) {
            LOGGER.info("WoF alert bar clicked - Navigating to Wheel of Fortune (Placeholder)");
            navigateTo(event, "/sk/vava/royalmate/view/wheel-view.fxml");
        }
    }

    private void setupAdminFeatures() {
        boolean isAdmin = SessionManager.isAdmin();

        serverLink.setVisible(isAdmin);
        serverLink.setManaged(isAdmin);
        serverSeparator.setVisible(isAdmin);
        serverSeparator.setManaged(isAdmin);
    }

    private void addNavigationClickHandlers() {

        logoImageView.setCursor(javafx.scene.Cursor.HAND);
        logoImageView.setOnMouseClicked(this::handleLogoClick);

        Node userInfoArea = usernameLabel.getParent();
        if (userInfoArea != null) {
            userInfoArea.setCursor(javafx.scene.Cursor.HAND);
            userInfoArea.setOnMouseClicked(this::handleProfileClick);
        } else {
            LOGGER.warning("Could not find parent VBox for username/balance labels to add click handler.");

            usernameLabel.setCursor(javafx.scene.Cursor.HAND);
            usernameLabel.setOnMouseClicked(this::handleProfileClick);
            balanceLabel.setCursor(javafx.scene.Cursor.HAND);
            balanceLabel.setOnMouseClicked(this::handleProfileClick);
        }

        profileIconImageView.setCursor(javafx.scene.Cursor.HAND);
        profileIconImageView.setOnMouseClicked(this::handleProfileClick);
    }

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

        navigateTo(event, "/sk/vava/royalmate/view/game-search-view.fxml");
    }

    @FXML
    private void handleChat(ActionEvent event) {
        LOGGER.info("Navigate to Chat");
        navigateTo(event, "/sk/vava/royalmate/view/chat-view.fxml");
    }

    @FXML
    private void handleTopWins(ActionEvent event) {
        LOGGER.info("Navigate to Leaderboards");
        navigateTo(event, "/sk/vava/royalmate/view/leaderboard-view.fxml");
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
        navigateTo(event, "/sk/vava/royalmate/view/admin-settings-view.fxml");
    }

    private void navigateTo(MouseEvent event, String fxmlPath) {
        Node source = (Node) event.getSource();
        navigateTo(source, fxmlPath);
    }
    private void navigateTo(ActionEvent event, String fxmlPath) {
        Node source = (Node) event.getSource();
        navigateTo(source, fxmlPath);
    }

    private void navigateTo(Node source, String fxmlPath) {
        try {
            Scene scene = source.getScene();
            if (scene == null) {
                scene = navbarRootContainer.getScene();
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

        LOGGER.info("Refreshing navbar user data display.");
        Platform.runLater(this::loadUserData);
    }

}