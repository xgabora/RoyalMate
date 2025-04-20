package sk.vava.royalmate.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label; // Import Label
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.util.SessionManager; // Correct package used

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


public class MainMenuController {

    private static final Logger LOGGER = Logger.getLogger(MainMenuController.class.getName());

    @FXML private Button logoutButton;
    @FXML private Label welcomeLabel; // fx:id for the welcome label

    @FXML
    public void initialize() {
        // Set welcome message using logged-in user's data
        Account currentUser = SessionManager.getCurrentAccount();
        if (currentUser != null) {
            welcomeLabel.setText("Welcome, " + currentUser.getUsername() + "!");
            LOGGER.info("Main menu initialized for user: " + currentUser.getUsername());
        } else {
            // This shouldn't happen if navigation is correct, but handle defensively
            welcomeLabel.setText("Welcome!");
            LOGGER.warning("MainMenuController initialized but no user in session!");
            // Optionally navigate back to login immediately
            // navigateTo(logoutButton, "/sk/vava/royalmate/view/login-view.fxml");
        }
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        String username = "Unknown User";
        if (SessionManager.isLoggedIn()) {
            username = SessionManager.getCurrentAccount().getUsername();
        }
        LOGGER.info("User " + username + " logging out.");

        // Clear the session
        SessionManager.logout();

        // Navigate back to the Login screen
        navigateTo(event, "/sk/vava/royalmate/view/login-view.fxml");
    }


    // Navigation helper (Consider moving this to a NavigationUtil class later)
    private void navigateTo(ActionEvent event, String fxmlPath) {
        try {
            // Get the source node (the button that was clicked) to find the scene
            Node source = (Node) event.getSource();
            Scene scene = source.getScene();
            if (scene == null) {
                LOGGER.severe("Could not get current scene from event source.");
                // Optionally show an alert to the user
                return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            if (loader.getLocation() == null) {
                throw new IOException("Cannot find FXML file: " + fxmlPath);
            }
            Parent nextRoot = loader.load();
            scene.setRoot(nextRoot);
            LOGGER.info("Successfully navigated to: " + fxmlPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load FXML: " + fxmlPath, e);
            // TODO: Show an error message to the user (e.g., using an Alert)
        } catch (ClassCastException e) {
            LOGGER.log(Level.SEVERE, "Failed to cast event source to Node.", e);
        }
    }
}