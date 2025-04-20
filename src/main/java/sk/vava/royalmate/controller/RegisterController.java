package sk.vava.royalmate.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox; // Or the root pane type
import javafx.scene.paint.Color;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.service.AuthService;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RegisterController {

    private static final Logger LOGGER = Logger.getLogger(RegisterController.class.getName());

    @FXML private TextField emailField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField repeatPasswordField;
    @FXML private Button createAccountButton;
    @FXML private Hyperlink loginLink;
    @FXML private Label passwordErrorLabel;
    @FXML private Label messageLabel; // For general errors or success
    @FXML private VBox formContainer; // To get the scene

    private AuthService authService;

    public RegisterController() {
        this.authService = new AuthService();
    }

    @FXML
    public void initialize() {
        // Hide error labels initially
        passwordErrorLabel.setVisible(false);
        passwordErrorLabel.setManaged(false);
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);

        // Optional: Add listener to clear password mismatch error when user types
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> clearPasswordError());
        repeatPasswordField.textProperty().addListener((obs, oldVal, newVal) -> clearPasswordError());
    }


    @FXML
    private void handleCreateAccount(ActionEvent event) {
        clearMessages(); // Clear previous messages

        String email = emailField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String repeatPassword = repeatPasswordField.getText();

        // --- Input Validation ---
        if (email.isEmpty() || username.isEmpty() || password.isEmpty() || repeatPassword.isEmpty()) {
            showMessage("All fields are required.", true);
            return;
        }

        if (!password.equals(repeatPassword)) {
            showPasswordError("Passwords do not match.");
            return;
        }
        // Add more validation from AuthService or here (e.g., email format already checked in service)

        // Disable button during processing
        createAccountButton.setDisable(true);

        try {
            // Call the registration service method
            Optional<Account> newAccountOpt = authService.register(email, username, password);

            if (newAccountOpt.isPresent()) {
                LOGGER.info("Registration successful for: " + username);
                showMessage("Account created successfully! You can now log in.", false);
                // Optionally clear fields or navigate to login after a delay
                navigateTo("/sk/vava/royalmate/view/login-view.fxml");
                // Or show success message and let user click login link
                createAccountButton.setDisable(true);

            } else {
                // Registration failed (likely username/email exists based on service logic)
                LOGGER.warning("Registration failed for: " + username + " or " + email + " (likely duplicate)");
                showMessage("Username or email already exists.", true);
                createAccountButton.setDisable(false); // Re-enable on failure
            }

        } catch (IllegalArgumentException e) {
            // Handle validation errors from the service (e.g., invalid email format)
            LOGGER.log(Level.WARNING, "Registration validation error: " + e.getMessage());
            showMessage(e.getMessage(), true); // Show specific validation message
            createAccountButton.setDisable(false); // Re-enable
        } catch (RuntimeException e) {
            // Handle unexpected database errors during save
            LOGGER.log(Level.SEVERE, "Registration failed due to runtime exception.", e);
            showMessage("An unexpected error occurred. Please try again later.", true);
            createAccountButton.setDisable(false); // Re-enable
        }
        // Make sure button is re-enabled in failure cases handled within the try block as well
    }


    @FXML
    private void handleLoginLink(ActionEvent event) {
        LOGGER.info("Navigating to Login Screen.");
        navigateTo("/sk/vava/royalmate/view/login-view.fxml");
    }


    private void showPasswordError(String message) {
        passwordErrorLabel.setText(message);
        passwordErrorLabel.setVisible(true);
        passwordErrorLabel.setManaged(true);
    }

    private void clearPasswordError() {
        passwordErrorLabel.setVisible(false);
        passwordErrorLabel.setManaged(false);
    }

    private void showMessage(String message, boolean isError) {
        messageLabel.setText(message);
        if (isError) {
            messageLabel.setTextFill(Color.RED); // Or use a CSS class .error-label
        } else {
            messageLabel.setTextFill(Color.GREEN); // Or use a CSS class .success-label
        }
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void clearMessages() {
        passwordErrorLabel.setVisible(false);
        passwordErrorLabel.setManaged(false);
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);
    }

    // Navigation helper (same as in LoginController)
    private void navigateTo(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            if (loader.getLocation() == null) { throw new IOException("Cannot find FXML file: " + fxmlPath); }
            Parent nextRoot = loader.load();
            Scene scene = formContainer.getScene(); // Get scene from any node
            if (scene == null) { LOGGER.severe("Could not get current scene."); return; }
            scene.setRoot(nextRoot);
            LOGGER.info("Navigated to: " + fxmlPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load FXML: " + fxmlPath, e);
            showMessage("Error loading the next page.", true);
        }
    }
}