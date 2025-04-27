package sk.vava.royalmate.controller;

// Removed: import javafx.event.ActionEvent;
import javafx.fxml.FXML;
// Removed: import javafx.fxml.FXMLLoader;
// Removed: import javafx.scene.Node;
// Removed: import javafx.scene.Parent;
// Removed: import javafx.scene.Scene;
// Removed: import javafx.scene.control.Button;
import javafx.scene.control.Label;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.util.LocaleManager; // Keep LocaleManager if needed here
import sk.vava.royalmate.util.SessionManager;

// Removed: import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainMenuController {

    private static final Logger LOGGER = Logger.getLogger(MainMenuController.class.getName());

    // Removed: @FXML private Button logoutButton;
    @FXML private Label welcomeLabel;

    // Optional: Inject the controller of the included navbar
    // This allows the MainMenuController to interact with the NavbarController if needed
    @FXML private NavbarController navbarComponentController; // Name must be <fx:id>Controller

    @FXML
    public void initialize() {
        // Set welcome message using logged-in user's data
        Account currentUser = SessionManager.getCurrentAccount();
        if (currentUser != null) {
            // You could potentially use LocaleManager here too if the welcome text needed parameters
            welcomeLabel.setText("Welcome, " + currentUser.getUsername() + "!");
            LOGGER.info("Main menu initialized for user: " + currentUser.getUsername());

            // Example: Accessing the included controller (if needed)
            if (navbarComponentController != null) {
                LOGGER.fine("NavbarController injected successfully.");
                // You could call public methods on navbarComponentController here
            } else {
                LOGGER.warning("NavbarController was not injected. Check FXML fx:id and controller class name.");
            }

        } else {
            welcomeLabel.setText("Welcome!");
            LOGGER.warning("MainMenuController initialized but no user in session!");
            // If no user, ideally redirect back to login from here?
            // navigateToLogin(); // You'd need a navigation method here
        }
    }
}