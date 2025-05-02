package sk.vava.royalmate.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.service.AuthService;

import sk.vava.royalmate.util.LocaleManager;

import java.io.IOException;
import java.util.Optional;
import java.util.ResourceBundle;
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
    @FXML private Label messageLabel;
    @FXML private VBox formContainer;

    private AuthService authService;

    public RegisterController() {
        this.authService = new AuthService();
    }

    @FXML
    public void initialize() {

        passwordErrorLabel.setVisible(false);
        passwordErrorLabel.setManaged(false);
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);

        passwordField.textProperty().addListener((obs, oldVal, newVal) -> clearPasswordError());
        repeatPasswordField.textProperty().addListener((obs, oldVal, newVal) -> clearPasswordError());
    }

    @FXML
    private void handleCreateAccount(ActionEvent event) {
        clearMessages();

        String email = emailField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String repeatPassword = repeatPasswordField.getText();

        if (email.isEmpty() || username.isEmpty() || password.isEmpty() || repeatPassword.isEmpty()) {
            showMessage("register.error.allFieldsRequired", true);
            return;
        }

        if (!password.equals(repeatPassword)) {
            showPasswordError("register.error.passwordMismatch");
            return;
        }

        createAccountButton.setDisable(true);

        try {
            Optional<Account> newAccountOpt = authService.register(email, username, password);

            if (newAccountOpt.isPresent()) {
                LOGGER.info("Registration successful for: " + username);
                showMessage("register.success.accountCreated", false);
                createAccountButton.setDisable(true);

            } else {
                LOGGER.warning("Registration failed for: " + username + " or " + email + " (likely duplicate)");
                showMessage("register.error.duplicateUserOrEmail", true);
                createAccountButton.setDisable(false);
            }

        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Registration validation error: " + e.getMessage());

            showMessageDirect(e.getMessage(), true);
            createAccountButton.setDisable(false);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Registration failed due to runtime exception.", e);
            showMessage("error.unexpected", true);
            createAccountButton.setDisable(false);
        }
    }

    @FXML
    private void handleLoginLink(ActionEvent event) {
        LOGGER.info("Navigating to Login Screen.");
        navigateTo("/sk/vava/royalmate/view/login-view.fxml");
    }

    private void showPasswordError(String messageKey) {

        passwordErrorLabel.setText(LocaleManager.getString(messageKey));
        passwordErrorLabel.setVisible(true);
        passwordErrorLabel.setManaged(true);
    }

    private void clearPasswordError() {
        passwordErrorLabel.setVisible(false);
        passwordErrorLabel.setManaged(false);
    }

    private void showMessage(String messageKey, boolean isError) {

        messageLabel.setText(LocaleManager.getString(messageKey));
        configureLabelStyle(messageLabel, isError);
    }

    private void showMessageDirect(String message, boolean isError) {
        messageLabel.setText(message);
        configureLabelStyle(messageLabel, isError);
    }

    private void configureLabelStyle(Label label, boolean isError) {

        label.setTextFill(isError ? Color.RED : Color.GREEN);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void clearMessages() {
        passwordErrorLabel.setVisible(false);
        passwordErrorLabel.setManaged(false);
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);
    }

    private void navigateTo(String fxmlPath) {
        try {
            ResourceBundle currentBundle = LocaleManager.getBundle();

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath), currentBundle);
            if (loader.getLocation() == null) { throw new IOException("Cannot find FXML file: " + fxmlPath); }
            Parent nextRoot = loader.load();
            Scene scene = formContainer.getScene();
            if (scene == null) { LOGGER.severe("Could not get current scene."); return; }
            scene.setRoot(nextRoot);
            LOGGER.info("Navigated to: " + fxmlPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load FXML: " + fxmlPath, e);
            showMessage("error.load.page", true);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Critical error during navigation or bundle loading.", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, LocaleManager.getString("error.unexpected"), ButtonType.OK);
            alert.setTitle(LocaleManager.getString("error.dialog.title"));
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }
}