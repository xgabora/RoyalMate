package sk.vava.royalmate.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import sk.vava.royalmate.data.DatabaseManager;
import sk.vava.royalmate.util.LocaleManager;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SplashController {

    private static final Logger LOGGER = Logger.getLogger(SplashController.class.getName());

    private static final String PRIMARY_BUTTON_COLOR = "#F1DA2C";
    private static final String SECONDARY_BUTTON_COLOR = "#FDEED2";
    private static final String TEXT_COLOR_DARK = "black";
    private static final String TEXT_COLOR_ACCENT = PRIMARY_BUTTON_COLOR;
    private static final Duration POLLING_INTERVAL = Duration.seconds(20);

    @FXML private StackPane rootPane;
    @FXML private ImageView backgroundImageView;
    @FXML private ImageView logoImageView;
    @FXML private VBox statusContainer;
    @FXML private Button enButton;
    @FXML private Button skButton;

    private Timeline connectionCheckTimeline;
    private final AtomicBoolean isCheckingConnection = new AtomicBoolean(false);
    private boolean currentlyConnected = false;

    @FXML
    public void initialize() {
        LOGGER.info("Splash screen initializing for locale: " + LocaleManager.getCurrentLocale().toLanguageTag());
        statusContainer.getChildren().clear();

        Label initialLabel = createStyledLabel(LocaleManager.getString("splash.connecting"), Color.WHITE, 16);
        statusContainer.getChildren().add(initialLabel);

        updateLocaleButtonStyles();

        checkConnection(true);
        setupPeriodicConnectionCheck();
    }

    @FXML
    private void handleSetLocaleEN(ActionEvent event) {
        setLocaleAndReload(LocaleManager.ENGLISH);
    }

    @FXML
    private void handleSetLocaleSK(ActionEvent event) {
        setLocaleAndReload(LocaleManager.SLOVAK);
    }

    private void setLocaleAndReload(Locale locale) {
        if (!locale.equals(LocaleManager.getCurrentLocale())) {
            LOGGER.info("Setting locale to: " + locale.toLanguageTag() + " and reloading view.");
            stopPolling();
            boolean localeSet = LocaleManager.setLocale(locale);
            if (localeSet) {
                reloadCurrentView();
            } else {
                LOGGER.severe("Failed to set locale to: " + locale.toLanguageTag());

            }
        }
    }

    private void reloadCurrentView() {
        try {

            Scene scene = rootPane.getScene();
            if (scene == null) {
                LOGGER.severe("Could not get scene for reloading.");
                return;
            }

            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource("/sk/vava/royalmate/view/splash-view.fxml")),
                    LocaleManager.getBundle()
            );

            Parent newRoot = loader.load();
            scene.setRoot(newRoot);
            LOGGER.info("Splash view reloaded successfully for locale: " + LocaleManager.getCurrentLocale().toLanguageTag());

        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to reload splash view FXML.", e);

        }
    }

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

            updateUIBasedOnConnection(false, isInitialCheck);
            isCheckingConnection.set(false);
        });
        new Thread(connectionTask) {{ setDaemon(true); start(); }};
    }

    private void updateUIBasedOnConnection(boolean connected, boolean isInitialCheck) {
        if (connected != currentlyConnected || isInitialCheck) {
            currentlyConnected = connected;
            Platform.runLater(() -> {
                statusContainer.getChildren().clear();
                if (connected) {
                    LOGGER.info("Updating UI to show connected state (buttons).");

                    Button loginButton = createStyledButton(LocaleManager.getString("button.login"), "primary");
                    Button createAccountButton = createStyledButton(LocaleManager.getString("button.register"), "secondary");

                    loginButton.setOnAction(e -> handleLogin());
                    createAccountButton.setOnAction(e -> handleCreateAccount());

                    statusContainer.getChildren().addAll(loginButton, createAccountButton);
                } else {
                    LOGGER.info("Updating UI to show disconnected state (error message).");

                    Label errorLabel = createStyledLabel(LocaleManager.getString("splash.required.internet"), Color.web(TEXT_COLOR_ACCENT), 18);
                    statusContainer.getChildren().add(errorLabel);
                }
            });
        } else {
            LOGGER.finer("Connection status unchanged. UI update skipped.");
        }
    }

    private Button createStyledButton(String text, String buttonType) {
        Button button = new Button(text);
        button.getStyleClass().add("splash-button");
        if ("primary".equals(buttonType)) {
            button.getStyleClass().add("button-primary");
        } else if ("secondary".equals(buttonType)) {
            button.getStyleClass().add("button-secondary");
        }
        return button;
    }

    private Label createStyledLabel(String text, Color textColor, double fontSize) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, fontSize));
        label.setTextFill(textColor);
        label.setAlignment(Pos.CENTER);
        return label;
    }

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

    private void navigateTo(String fxmlPath) {
        try {
            Scene scene = rootPane.getScene();
            if (scene == null) { LOGGER.severe("Could not get current scene to navigate."); return; }

            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource(fxmlPath)), LocaleManager.getBundle());
            Parent nextRoot = loader.load();
            scene.setRoot(nextRoot);
            LOGGER.info("Successfully navigated to: " + fxmlPath);
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to load or navigate to FXML: " + fxmlPath, e);

            Platform.runLater(() -> {
                statusContainer.getChildren().clear();
                Label errorLabel = createStyledLabel(LocaleManager.getString("splash.error.load.login"), Color.RED, 16);
                statusContainer.getChildren().add(errorLabel);
            });
        }
    }

    public void stopPolling() {
        if (connectionCheckTimeline != null) {
            connectionCheckTimeline.stop();
            LOGGER.info("Periodic connection check stopped.");
        }
    }

    private void updateLocaleButtonStyles() {
        Locale current = LocaleManager.getCurrentLocale();
        styleLocaleButton(enButton, LocaleManager.ENGLISH.equals(current));
        styleLocaleButton(skButton, LocaleManager.SLOVAK.equals(current));
    }

    private void styleLocaleButton(Button button, boolean isActive) {
        if (isActive) {
            button.setStyle("-fx-background-color: #F1DA2C; -fx-text-fill: black;");
            button.setDisable(true);
        } else {
            button.setStyle("");
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