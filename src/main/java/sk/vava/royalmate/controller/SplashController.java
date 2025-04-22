package sk.vava.royalmate.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader; // Need FXMLLoader for reload
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent; // Need Parent for reload
import javafx.scene.Scene; // Need Scene for reload
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane; // Import StackPane for root element access
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import sk.vava.royalmate.data.DatabaseManager;
import sk.vava.royalmate.util.LocaleManager; // Import LocaleManager

import java.io.IOException; // Need for reload
import java.util.Locale;
import java.util.Objects; // Need for reload null check
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SplashController {

    private static final Logger LOGGER = Logger.getLogger(SplashController.class.getName());
    // Colors remain useful for non-text elements
    private static final String PRIMARY_BUTTON_COLOR = "#F1DA2C";
    private static final String SECONDARY_BUTTON_COLOR = "#FDEED2";
    private static final String TEXT_COLOR_DARK = "black";
    private static final String TEXT_COLOR_ACCENT = PRIMARY_BUTTON_COLOR;
    private static final Duration POLLING_INTERVAL = Duration.seconds(15);

    @FXML private StackPane rootPane; // Inject the root pane
    @FXML private ImageView backgroundImageView;
    @FXML private ImageView logoImageView;
    @FXML private VBox statusContainer;
    @FXML private Button enButton; // Inject language buttons
    @FXML private Button skButton;

    private Timeline connectionCheckTimeline;
    private final AtomicBoolean isCheckingConnection = new AtomicBoolean(false);
    private boolean currentlyConnected = false;

    @FXML
    public void initialize() {
        LOGGER.info("Splash screen initializing for locale: " + LocaleManager.getCurrentLocale().toLanguageTag());
        statusContainer.getChildren().clear();
        // Use LocaleManager for initial text
        Label initialLabel = createStyledLabel(LocaleManager.getString("splash.connecting"), Color.WHITE, 16);
        statusContainer.getChildren().add(initialLabel);

        // Update button text based on current locale (they are static in FXML for now, but could be set here)
        // enButton.setText(LocaleManager.getString("button.en"));
        // skButton.setText(LocaleManager.getString("button.sk"));
        updateLocaleButtonStyles(); // Highlight current language button

        // Start connection check and polling
        checkConnection(true);
        setupPeriodicConnectionCheck();
    }

    // --- Locale Change Handlers ---
    @FXML
    private void handleSetLocaleEN(ActionEvent event) {
        setLocaleAndReload(LocaleManager.ENGLISH);
    }

    @FXML
    private void handleSetLocaleSK(ActionEvent event) {
        setLocaleAndReload(LocaleManager.SLOVAK);
    }

    /**
     * Sets the locale using LocaleManager and reloads the current view.
     */
    private void setLocaleAndReload(Locale locale) {
        if (!locale.equals(LocaleManager.getCurrentLocale())) {
            LOGGER.info("Setting locale to: " + locale.toLanguageTag() + " and reloading view.");
            stopPolling(); // Stop polling before reloading
            boolean localeSet = LocaleManager.setLocale(locale);
            if (localeSet) {
                reloadCurrentView();
            } else {
                LOGGER.severe("Failed to set locale to: " + locale.toLanguageTag());
                // Optionally show an error message to the user
            }
        }
    }

    /**
     * Reloads the splash-view.fxml into the current scene.
     */
    private void reloadCurrentView() {
        try {
            // Get the current scene from the root pane
            Scene scene = rootPane.getScene();
            if (scene == null) {
                LOGGER.severe("Could not get scene for reloading.");
                return;
            }

            // Load the FXML file again
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource("/sk/vava/royalmate/view/splash-view.fxml")),
                    LocaleManager.getBundle() // Pass the *current* bundle to FXMLLoader
            );

            Parent newRoot = loader.load();
            scene.setRoot(newRoot); // Replace the scene's root node
            LOGGER.info("Splash view reloaded successfully for locale: " + LocaleManager.getCurrentLocale().toLanguageTag());

        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to reload splash view FXML.", e);
            // Handle critical error - maybe show an alert
        }
    }
    // -----------------------------


    // --- Connection Check Logic (Use LocaleManager for text) ---
    private void checkConnection(boolean isInitialCheck) {
        if (!isCheckingConnection.compareAndSet(false, true)) { return; }
        if (!isInitialCheck) { LOGGER.info("Performing periodic connection check..."); }

        Task<Boolean> connectionTask = new Task<>() {
            @Override protected Boolean call() throws Exception { return DatabaseManager.testConnection(); }
        };
        connectionTask.setOnSucceeded(event -> {
            boolean connected = connectionTask.getValue();
            LOGGER.info("Connection check task finished. Result: " + (connected ? "Connected" : "Disconnected"));
            updateUIBasedOnConnection(connected, isInitialCheck);
            isCheckingConnection.set(false);
        });
        connectionTask.setOnFailed(event -> {
            Throwable exception = connectionTask.getException();
            LOGGER.log(Level.SEVERE, "Database connection task failed with exception.", exception);
            // Use LocaleManager for error text
            updateUIBasedOnConnection(false, isInitialCheck); // Treat failure as disconnected
            isCheckingConnection.set(false);
        });
        new Thread(connectionTask) {{ setDaemon(true); start(); }};
    }

    // --- Update UI (Use LocaleManager for text) ---
    private void updateUIBasedOnConnection(boolean connected, boolean isInitialCheck) {
        if (connected != currentlyConnected || isInitialCheck) {
            currentlyConnected = connected;
            Platform.runLater(() -> {
                statusContainer.getChildren().clear();
                if (connected) {
                    LOGGER.info("Updating UI to show connected state (buttons).");
                    // Use LocaleManager for button text keys
                    Button loginButton = createStyledButton(LocaleManager.getString("button.login"), "primary");
                    Button createAccountButton = createStyledButton(LocaleManager.getString("button.register"), "secondary");

                    loginButton.setOnAction(e -> handleLogin());
                    createAccountButton.setOnAction(e -> handleCreateAccount());

                    statusContainer.getChildren().addAll(loginButton, createAccountButton);
                } else {
                    LOGGER.info("Updating UI to show disconnected state (error message).");
                    // Use LocaleManager for error text key
                    Label errorLabel = createStyledLabel(LocaleManager.getString("splash.required.internet"), Color.web(TEXT_COLOR_ACCENT), 18);
                    statusContainer.getChildren().add(errorLabel);
                }
            });
        } else {
            LOGGER.finer("Connection status unchanged. UI update skipped.");
        }
    }

    // --- Button/Label Creation (Text now comes from LocaleManager via caller) ---
    private Button createStyledButton(String text, String buttonType) { // Text is already localized when passed in
        Button button = new Button(text);
        button.getStyleClass().add("splash-button");
        if ("primary".equals(buttonType)) {
            button.getStyleClass().add("button-primary");
        } else if ("secondary".equals(buttonType)) {
            button.getStyleClass().add("button-secondary");
        }
        return button;
    }

    private Label createStyledLabel(String text, Color textColor, double fontSize) { // Text is already localized
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, fontSize));
        label.setTextFill(textColor);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    // --- Navigation (Use LocaleManager for error messages) ---
    private void handleLogin() {
        LOGGER.info("Login button clicked - Navigating to Login Screen");
        stopPolling();
        navigateTo("/sk/vava/royalmate/view/login-view.fxml");
    }

    private void handleCreateAccount() {
        LOGGER.info("Create Account button clicked - Navigating to Register Screen");
        stopPolling();
        navigateTo("/sk/vava/royalmate/view/register-view.fxml");
    }

    // --- Navigation Helper (Use LocaleManager for error message) ---
    private void navigateTo(String fxmlPath) {
        try {
            Scene scene = rootPane.getScene(); // Get scene from root pane
            if (scene == null) { LOGGER.severe("Could not get current scene to navigate."); return; }

            // Pass the current bundle to the loader for the *next* screen
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource(fxmlPath)), LocaleManager.getBundle());
            Parent nextRoot = loader.load();
            scene.setRoot(nextRoot);
            LOGGER.info("Successfully navigated to: " + fxmlPath);
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to load or navigate to FXML: " + fxmlPath, e);
            // Show localized error on the current screen if possible
            Platform.runLater(() -> {
                statusContainer.getChildren().clear();
                Label errorLabel = createStyledLabel(LocaleManager.getString("splash.error.load.login"), Color.RED, 16); // Example key
                statusContainer.getChildren().add(errorLabel);
            });
        }
    }

    // --- Utility Methods ---
    public void stopPolling() {
        if (connectionCheckTimeline != null) {
            connectionCheckTimeline.stop();
            LOGGER.info("Periodic connection check stopped.");
        }
    }

    // Optional: Style the active language button
    private void updateLocaleButtonStyles() {
        Locale current = LocaleManager.getCurrentLocale();
        styleLocaleButton(enButton, LocaleManager.ENGLISH.equals(current));
        styleLocaleButton(skButton, LocaleManager.SLOVAK.equals(current));
    }

    private void styleLocaleButton(Button button, boolean isActive) {
        if (isActive) {
            button.setStyle("-fx-background-color: #F1DA2C; -fx-text-fill: black;"); // Example active style
            button.setDisable(true); // Disable clicking the active language
        } else {
            button.setStyle(""); // Reset to default CSS style
            button.setDisable(false);
        }
    }

    private void setupPeriodicConnectionCheck() {
        connectionCheckTimeline = new Timeline(new KeyFrame(POLLING_INTERVAL, event -> checkConnection(false)));
        connectionCheckTimeline.setCycleCount(Timeline.INDEFINITE);
        Timeline startPollingDelay = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            if(connectionCheckTimeline != null && connectionCheckTimeline.getStatus() != Timeline.Status.RUNNING){
                connectionCheckTimeline.play();
                LOGGER.info("Periodic connection check started (every " + POLLING_INTERVAL.toSeconds() + "s).");
            }
        }));
        startPollingDelay.play();
    }
}