package sk.vava.royalmate.controller;

import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView; // Import ImageView
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.service.WheelService;
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WheelController {

    private static final Logger LOGGER = Logger.getLogger(WheelController.class.getName());
    // Prizes remain the same, even if not visually represented by segments anymore
    private static final List<BigDecimal> PRIZES = List.of(
            new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("1.00"),
            new BigDecimal("2.00"), new BigDecimal("2.00"), new BigDecimal("5.00"),
            new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("50.00")
    );
    // Number of segments is still relevant for prize selection if prize list is tied to it
    private static final int NUM_SEGMENTS = PRIZES.size();
    private static final Duration SPIN_DURATION = Duration.seconds(3);

    @FXML private StackPane rootPane;
    @FXML private StackPane wheelContainer;
    @FXML private ImageView wheelImageView; // Target for rotation
    @FXML private Button actionButton;
    @FXML private Label resultLabel;

    private final WheelService wheelService;
    private final Random random = new Random();
    private boolean isSpinning = false;
    private boolean spinCompleted = false;

    public WheelController() {
        this.wheelService = new WheelService();
    }

    @FXML
    public void initialize() {
        resultLabel.setVisible(false);
        resultLabel.setManaged(false);
        spinCompleted = false;

        // No need to draw segments anymore
        // drawWheelSegments();

        Account currentUser = SessionManager.getCurrentAccount();
        if (currentUser == null) {
            LOGGER.severe("WheelController loaded without logged-in user!");
            actionButton.setDisable(true);
            showResult(LocaleManager.getString("common.error.notloggedin"), true); // Provide fallback text
            return;
        }

        updateButtonState(currentUser);
    }

    /**
     * Updates the main action button's text and disabled state based on eligibility.
     */
    private void updateButtonState(Account currentUser) {
        if (spinCompleted) {
            actionButton.setText(LocaleManager.getString("button.backtocasino"));
            actionButton.setOnAction(this::handleBackToCasino);
            actionButton.setDisable(false);
        } else if (wheelService.isEligibleToSpin(currentUser)) {
            actionButton.setText(LocaleManager.getString("wof.button.spin"));
            actionButton.setOnAction(this::handleSpin);
            actionButton.setDisable(false);
            resultLabel.setVisible(false);
            resultLabel.setManaged(false);
        } else {
            actionButton.setText(LocaleManager.getString("wof.button.spin"));
            actionButton.setOnAction(this::handleSpin);
            actionButton.setDisable(true);
            showResult(LocaleManager.getString("wof.comeback"), false);
        }
    }


    @FXML
    private void handleSpin(ActionEvent event) {
        Account currentUser = SessionManager.getCurrentAccount();
        if (isSpinning || currentUser == null || !wheelService.isEligibleToSpin(currentUser) || spinCompleted) {
            LOGGER.warning("Spin attempt rejected. State: isSpinning=" + isSpinning + ", spinCompleted=" + spinCompleted + ", eligible=" + (currentUser != null && wheelService.isEligibleToSpin(currentUser)));
            return;
        }

        isSpinning = true;
        actionButton.setDisable(true);
        resultLabel.setVisible(false);
        resultLabel.setManaged(false);

        // --- Select Prize (independent of visual segments now) ---
        BigDecimal prize = PRIZES.get(random.nextInt(PRIZES.size()));
        LOGGER.info("User " + currentUser.getUsername() + " spinning for... Prize selected: " + prize + " €");

        // --- Animation ---
        // Rotate the ImageView containing the wheel texture
        RotateTransition rt = new RotateTransition(SPIN_DURATION, wheelImageView);
        double currentAngle = wheelImageView.getRotate();
        // More rotation for visual effect
        double randomExtraAngle = 360 + random.nextDouble() * 360 * 3;
        double targetAngle = currentAngle + 360 * 6 + randomExtraAngle; // Spin more times

        rt.setByAngle(targetAngle - currentAngle);
        rt.setCycleCount(1);
        rt.setInterpolator(Interpolator.EASE_OUT);

        rt.setOnFinished(e -> {
            LOGGER.info("Spin animation finished.");
            // Perform backend updates in background task
            performBackendSpinUpdate(currentUser, prize);
        });

        LOGGER.info("Starting spin animation...");
        rt.play();
    }

    /**
     * Executes the database updates in a background thread.
     */
    private void performBackendSpinUpdate(Account currentUser, BigDecimal prize) {
        Task<Boolean> spinTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return wheelService.performSpin(currentUser.getId(), prize);
            }
        };

        spinTask.setOnSucceeded(workerStateEvent -> {
            boolean success = spinTask.getValue();
            if (success) {
                String resultMsg = LocaleManager.getString("wof.result")
                        .replace("XX", prize.toPlainString());
                showResult(resultMsg, false);
                spinCompleted = true;
                updateButtonState(SessionManager.getCurrentAccount()); // Update button to "BACK"
                LOGGER.info("Spin successful (backend update) for user: " + currentUser.getUsername() + ", prize: " + prize);
            } else {
                showResult(LocaleManager.getString("wof.error.spin"), true);
                // Keep button disabled until navigation
                LOGGER.severe("Spin failed during backend update for user: " + currentUser.getUsername());
            }
            isSpinning = false;
        });

        spinTask.setOnFailed(workerStateEvent -> {
            Throwable error = spinTask.getException();
            LOGGER.log(Level.SEVERE, "Background spin task failed with exception for user: " + currentUser.getUsername(), error);
            showResult(LocaleManager.getString("wof.error.spin"), true);
            isSpinning = false;
            // Keep button disabled until navigation
        });

        new Thread(spinTask).start();
    }


    /**
     * Handles the action when the button says "BACK TO CASINO".
     */
    private void handleBackToCasino(ActionEvent event) {
        LOGGER.info("Navigating back to Main Menu.");
        navigateTo(event, "/sk/vava/royalmate/view/main-menu-view.fxml");
    }


    private void showResult(String message, boolean isError) {
        Platform.runLater(()-> {
            resultLabel.setText(message);
            resultLabel.setTextFill(isError ? Color.RED : Color.LIGHTGREEN);
            resultLabel.setVisible(true);
            resultLabel.setManaged(true);
        });
    }


    // --- Navigation Helper ---
    private void navigateTo(ActionEvent event, String fxmlPath) {
        Node source = (Node) event.getSource();
        try {
            Scene scene = source.getScene();
            if (scene == null) {
                scene = rootPane.getScene();
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
            showResult("Error loading next screen.", true);
        } catch (ClassCastException e) {
            LOGGER.log(Level.SEVERE, "Failed to cast event source to Node.", e);
        }
    }

    // --- Helper method for fallback text in case bundle key is missing ---
    // (Could be moved to LocaleManager)
    public static String getString(String key, String fallback) {
        String text = LocaleManager.getString(key);
        return text.startsWith("!") && text.endsWith("!") ? fallback : text;
    }
}