package sk.vava.royalmate.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
// No longer need Cursor import here if handled by CSS
// import javafx.scene.Cursor;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import sk.vava.royalmate.data.DatabaseManager;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;


public class SplashController {

    // Constants remain the same...
    private static final Logger LOGGER = Logger.getLogger(SplashController.class.getName());
    private static final String PRIMARY_BUTTON_COLOR = "#F1DA2C"; // Still useful maybe? Or rely on CSS var
    private static final String SECONDARY_BUTTON_COLOR = "#FDEED2"; // Still useful maybe?
    private static final String TEXT_COLOR_DARK = "black"; // Still useful maybe?
    private static final String TEXT_COLOR_ACCENT = PRIMARY_BUTTON_COLOR;
    private static final Duration POLLING_INTERVAL = Duration.seconds(10);

    @FXML private ImageView backgroundImageView;
    @FXML private ImageView logoImageView; // Ensure this fx:id exists if needed, otherwise styleClass is enough
    @FXML private VBox statusContainer;

    private Timeline connectionCheckTimeline;
    private final AtomicBoolean isCheckingConnection = new AtomicBoolean(false);
    private boolean currentlyConnected = false;

    // initialize, checkConnection, setupPeriodicConnectionCheck methods remain the same...
    @FXML
    public void initialize() {
        LOGGER.info("Splash screen initializing.");
        statusContainer.getChildren().clear(); // Ensure container is empty initially
        Label initialLabel = createStyledLabel("Connecting to server...", Color.WHITE, 16);
        statusContainer.getChildren().add(initialLabel);

        checkConnection(true); // true indicates this is the initial check
        setupPeriodicConnectionCheck();
    }

    private void checkConnection(boolean isInitialCheck) {
        if (!isCheckingConnection.compareAndSet(false, true)) {
            LOGGER.finer("Connection check already in progress. Skipping.");
            return;
        }
        if (!isInitialCheck) { LOGGER.info("Performing periodic connection check..."); }

        Task<Boolean> connectionTask = new Task<>() { /* ... task logic ... */
            @Override protected Boolean call() throws Exception { return DatabaseManager.testConnection(); }
        };
        connectionTask.setOnSucceeded(event -> { /* ... update UI ... */
            boolean connected = connectionTask.getValue();
            LOGGER.info("Connection check task finished. Result: " + (connected ? "Connected" : "Disconnected"));
            updateUIBasedOnConnection(connected, isInitialCheck);
            isCheckingConnection.set(false);
        });
        connectionTask.setOnFailed(event -> { /* ... update UI on error ... */
            Throwable exception = connectionTask.getException();
            LOGGER.log(Level.SEVERE, "Database connection task failed with exception.", exception);
            updateUIBasedOnConnection(false, isInitialCheck);
            isCheckingConnection.set(false);
        });

        Thread connectionThread = new Thread(connectionTask);
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    // --- Modified updateUIBasedOnConnection ---
    private void updateUIBasedOnConnection(boolean connected, boolean isInitialCheck) {
        if (connected != currentlyConnected || isInitialCheck) {
            currentlyConnected = connected;
            Platform.runLater(() -> {
                statusContainer.getChildren().clear();
                if (connected) {
                    LOGGER.info("Updating UI to show connected state (buttons with CSS styling).");
                    // Pass type ("primary" or "secondary") to createStyledButton
                    Button loginButton = createStyledButton("LOGIN", "primary");
                    Button createAccountButton = createStyledButton("CREATE ACCOUNT", "secondary");

                    loginButton.setOnAction(e -> handleLogin());
                    createAccountButton.setOnAction(e -> handleCreateAccount());

                    statusContainer.getChildren().addAll(loginButton, createAccountButton);
                } else {
                    LOGGER.info("Updating UI to show disconnected state (error message).");
                    Label errorLabel = createStyledLabel("Internet connection is required to play.", Color.web(TEXT_COLOR_ACCENT), 18);
                    statusContainer.getChildren().add(errorLabel);
                }
            });
        } else {
            LOGGER.finer("Connection status unchanged (" + currentlyConnected + "). UI update skipped.");
        }
    }


    private void setupPeriodicConnectionCheck() {
        connectionCheckTimeline = new Timeline( /* ... timeline logic ... */
                new KeyFrame(POLLING_INTERVAL, event -> checkConnection(false))
        );
        connectionCheckTimeline.setCycleCount(Timeline.INDEFINITE);
        Timeline startPollingDelay = new Timeline(new KeyFrame(Duration.seconds(2), e -> { /* ... start logic ... */
            if(connectionCheckTimeline.getStatus() != Timeline.Status.RUNNING){
                connectionCheckTimeline.play();
                LOGGER.info("Periodic connection check started (every " + POLLING_INTERVAL.toSeconds() + "s).");
            }
        }));
        startPollingDelay.play();
    }


    // --- Modified createStyledButton Method ---
    // Now takes buttonType ("primary" or "secondary") instead of colors
    private Button createStyledButton(String text, String buttonType) {
        Button button = new Button(text);


        // Add the common style class and the specific type class
        button.getStyleClass().add("splash-button");
        if ("primary".equals(buttonType)) {
            button.getStyleClass().add("button-primary");
        } else if ("secondary".equals(buttonType)) {
            button.getStyleClass().add("button-secondary");
        }
        return button;
    }
    // --- End of Modified Method ---


    private Label createStyledLabel(String text, Color textColor, double fontSize) {
        // This method remains the same, as it only uses inline styles
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, fontSize));
        label.setTextFill(textColor);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    // handleLogin, handleCreateAccount, stopPolling methods remain the same...
    // --- Updated Action Handlers ---
    private void handleLogin() {
        LOGGER.info("Login button clicked - Navigating to Login Screen");
        stopPolling(); // Stop checking connection
        navigateTo("/sk/vava/royalmate/view/login-view.fxml");
    }

    private void handleCreateAccount() {
        LOGGER.info("Create Account button clicked - Navigating to Register Screen");
        stopPolling(); // Stop checking connection
        navigateTo("/sk/vava/royalmate/view/register-view.fxml");
    }

    /**
     * Helper method for navigating between FXML views.
     * Assumes the target FXML exists and is loadable.
     * @param fxmlPath The absolute path to the FXML file from the classpath root.
     */
    private void navigateTo(String fxmlPath) {
        try {
            // Use ClassLoader getResource to ensure it works within JARs too
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            if (loader.getLocation() == null) {
                throw new IOException("Cannot find FXML file at path: " + fxmlPath);
            }
            Parent nextRoot = loader.load();
            Scene scene = statusContainer.getScene(); // Get scene from any node in the current scene
            if (scene == null) {
                LOGGER.severe("Could not get current scene to navigate.");
                // Handle error - maybe show alert
                return;
            }
            scene.setRoot(nextRoot); // Change the root node of the current scene
            LOGGER.info("Successfully navigated to: " + fxmlPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load or navigate to FXML: " + fxmlPath, e);
            // TODO: Show an error message to the user (e.g., using an Alert)
            // Alert alert = new Alert(Alert.AlertType.ERROR, "Error loading page: " + e.getMessage());
            // alert.showAndWait();
        } catch (IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Error navigating, stage might be null: " + fxmlPath, e);
        }
    }

    // ... (stopPolling method remains the same) ...
    public void stopPolling() { /* ... stop logic ... */
        if (connectionCheckTimeline != null) { connectionCheckTimeline.stop(); LOGGER.info("Periodic connection check stopped."); }
    }

}