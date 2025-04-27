package sk.vava.royalmate.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.service.AuthService;
// Make sure this import points to YOUR LocaleManager
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import java.io.IOException;
import java.util.Optional;
import java.util.ResourceBundle; // Keep for FXMLLoader type
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button signInButton;
    @FXML private Hyperlink registerLink;
    @FXML private Label errorLabel;
    @FXML private VBox formContainer;

    private AuthService authService;
    // private ResourceBundle bundle; // REMOVED - Use static LocaleManager

    public LoginController() {
        this.authService = new AuthService();
    }

    @FXML
    public void initialize() {
        // this.bundle = LocaleManager.getBundle(); // REMOVED - Not needed here
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        // FXML elements are automatically translated by FXMLLoader if bundle passed in navigateTo
    }

    @FXML
    private void handleSignIn(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.trim().isEmpty() || password.isEmpty()) {
            showError("login.error.emptyFields"); // Use key
            return;
        }

        signInButton.setDisable(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Optional<Account> accountOpt = authService.authenticate(username, password);

        signInButton.setDisable(false);

        if (accountOpt.isPresent()) {
            LOGGER.info("Login successful for: " + username);
            Account loggedInAccount = accountOpt.get();
            SessionManager.setCurrentAccount(loggedInAccount);
            navigateTo("/sk/vava/royalmate/view/main-menu-view.fxml");
        } else {
            LOGGER.warning("Login failed for: " + username);
            showError("login.error.invalidCredentials"); // Use key
        }
    }

    @FXML
    private void handleRegisterLink(ActionEvent event) {
        LOGGER.info("Navigating to Registration Screen.");
        navigateTo("/sk/vava/royalmate/view/register-view.fxml");
    }

    private void showError(String messageKey) {
        // Use static method from your LocaleManager
        errorLabel.setText(LocaleManager.getString(messageKey));
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    // Updated navigateTo to use static LocaleManager.getBundle()
    private void navigateTo(String fxmlPath) {
        try {
            // Get the bundle based on the current locale setting using static method
            ResourceBundle currentBundle = LocaleManager.getBundle();

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath), currentBundle); // Pass bundle
            if (loader.getLocation() == null) { throw new IOException("Cannot find FXML file: " + fxmlPath); }
            Parent nextRoot = loader.load();
            Scene scene = formContainer.getScene();
            if (scene == null) { LOGGER.severe("Could not get current scene."); return; }
            scene.setRoot(nextRoot);
            LOGGER.info("Navigated to: " + fxmlPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load FXML: " + fxmlPath, e);
            // Show error using the static getString method
            showError("error.load.page"); // Use key
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Critical error during navigation or bundle loading.", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, LocaleManager.getString("error.unexpected"), ButtonType.OK);
            alert.setTitle(LocaleManager.getString("error.dialog.title")); // Example: Add title key
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }
}