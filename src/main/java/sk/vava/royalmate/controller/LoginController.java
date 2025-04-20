package sk.vava.royalmate.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox; // Or the root pane type
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.service.AuthService;
import sk.vava.royalmate.util.SessionManager; // Assuming SessionManager exists

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button signInButton;
    @FXML private Hyperlink registerLink;
    @FXML private Label errorLabel;
    @FXML private VBox formContainer; // Get a reference to the container to get the scene

    private AuthService authService;

    public LoginController() {
        // Initialize services - consider dependency injection later for testability
        this.authService = new AuthService();
    }

    @FXML
    public void initialize() {
        errorLabel.setVisible(false); // Hide error label initially
        errorLabel.setManaged(false); // Don't reserve space when hidden
    }

    @FXML
    private void handleSignIn(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // Basic client-side validation
        if (username.trim().isEmpty() || password.isEmpty()) {
            showError("Username and password cannot be empty.");
            return;
        }

        // Disable button during processing? (Optional)
        signInButton.setDisable(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // Simulate backend call (replace with actual service call)
        // Use Task for real backend calls to avoid freezing UI
        Optional<Account> accountOpt = authService.authenticate(username, password);

        signInButton.setDisable(false); // Re-enable button

        if (accountOpt.isPresent()) {
            LOGGER.info("Login successful for: " + username);
            Account loggedInAccount = accountOpt.get();
            // Store user session (Simple static approach for now)
            SessionManager.setCurrentAccount(loggedInAccount);

            navigateTo("/sk/vava/royalmate/view/main-menu-view.fxml");

        } else {
            LOGGER.warning("Login failed for: " + username);
            showError("Invalid username or password.");
        }
    }

    @FXML
    private void handleRegisterLink(ActionEvent event) {
        LOGGER.info("Navigating to Registration Screen.");
        navigateTo("/sk/vava/royalmate/view/register-view.fxml");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true); // Reserve space when visible
    }


    // Navigation helper (copied from SplashController, could be moved to a utility class)
    private void navigateTo(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            if (loader.getLocation() == null) { throw new IOException("Cannot find FXML file: " + fxmlPath); }
            Parent nextRoot = loader.load();
            // Get scene from any node within the current controller's view
            Scene scene = formContainer.getScene();
            if (scene == null) { LOGGER.severe("Could not get current scene."); return; }
            scene.setRoot(nextRoot);
            LOGGER.info("Navigated to: " + fxmlPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load FXML: " + fxmlPath, e);
            showError("Error loading the next page."); // Show error on login screen
        }
    }
}