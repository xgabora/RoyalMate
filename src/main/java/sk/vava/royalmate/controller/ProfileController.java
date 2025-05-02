package sk.vava.royalmate.controller;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.model.UserStatistics;
import sk.vava.royalmate.service.AuthService;
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProfileController {

    private static final Logger LOGGER = Logger.getLogger(ProfileController.class.getName());

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML private BorderPane rootPane;
    @FXML private NavbarController navbarComponentController;
    @FXML private ImageView profileIconBig;
    @FXML private Label usernameLabel;
    @FXML private Label memberSinceLabel;
    @FXML private ToggleButton balanceTabButton;
    @FXML private ToggleButton statsTabButton;
    @FXML private ToggleButton settingsTabButton;
    @FXML private StackPane tabContentPane;
    @FXML private VBox balanceContent;
    @FXML private VBox statsContent;
    @FXML private VBox settingsContent;

    @FXML private Label currentBalanceLabel;
    @FXML private TextField withdrawAmountField;
    @FXML private Button withdrawButton;
    @FXML private Label balanceMessageLabel;

    @FXML private Label totalSpinsValueLabel;
    @FXML private Label totalWageredValueLabel;
    @FXML private Label totalWinsValueLabel;
    @FXML private Label gamesPlayedValueLabel;

    @FXML private Button enButton;
    @FXML private Button skButton;
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField repeatPasswordField;
    @FXML private Button changePasswordButton;
    @FXML private Label passwordMessageLabel;

    private ToggleGroup tabGroup;
    private final AuthService authService;
    private Account currentUser;

    public ProfileController() {
        this.authService = new AuthService();
    }

    @FXML
    public void initialize() {
        currentUser = SessionManager.getCurrentAccount();
        if (currentUser == null) {
            LOGGER.severe("Profile screen loaded without user session. Redirecting to login.");
            Platform.runLater(this::navigateToLogin);
            return;
        }
        setupTabs();
        loadProfileHeader();
        loadBalanceTab();

        if (statsTabButton.isSelected()) {
            loadStatsTab();
        }
        loadSettingsTab();
        LOGGER.info("ProfileController initialized for user: " + currentUser.getUsername());
    }

    private void setupTabs() {
        tabGroup = new ToggleGroup();
        balanceTabButton.setToggleGroup(tabGroup);
        statsTabButton.setToggleGroup(tabGroup);
        settingsTabButton.setToggleGroup(tabGroup);

        tabGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                tabGroup.selectToggle(oldToggle != null ? oldToggle : balanceTabButton);
            } else {
                hideAllTabContent();
                clearMessages();
                if (newToggle == balanceTabButton) {
                    balanceContent.setVisible(true); balanceContent.setManaged(true);
                    loadBalanceTab();
                } else if (newToggle == statsTabButton) {
                    statsContent.setVisible(true); statsContent.setManaged(true);
                    loadStatsTab();
                } else if (newToggle == settingsTabButton) {
                    settingsContent.setVisible(true); settingsContent.setManaged(true);
                    loadSettingsTab();
                }
            }
        });
        balanceTabButton.setSelected(true);
    }

    private void hideAllTabContent() {
        balanceContent.setVisible(false);
        balanceContent.setManaged(false);
        statsContent.setVisible(false);
        statsContent.setManaged(false);
        settingsContent.setVisible(false);
        settingsContent.setManaged(false);
    }

    private void clearMessages() {
        balanceMessageLabel.setVisible(false);
        balanceMessageLabel.setManaged(false);
        passwordMessageLabel.setVisible(false);
        passwordMessageLabel.setManaged(false);
    }

    private void loadProfileHeader() {
        usernameLabel.setText(currentUser.getUsername());
        if (currentUser.getCreatedAt() != null) {

            String formattedDate = currentUser.getCreatedAt()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(DATE_FORMATTER);
            memberSinceLabel.setText(LocaleManager.getString("profile.membersince") + " " + formattedDate);
        } else {
            memberSinceLabel.setText(LocaleManager.getString("profile.membersince") + " N/A");
        }
    }

    private void loadBalanceTab() {

        Account freshAccount = SessionManager.getCurrentAccount();
        if(freshAccount == null) return;

        BigDecimal balance = freshAccount.getBalance() != null ? freshAccount.getBalance() : BigDecimal.ZERO;
        NumberFormat currencyFormatter = NumberFormat.getNumberInstance(LocaleManager.getCurrentLocale());

        currencyFormatter.setMinimumFractionDigits(2);
        currencyFormatter.setMaximumFractionDigits(2);

        currentBalanceLabel.setText(
                LocaleManager.getString("profile.label.currentbalance") + " " +
                        currencyFormatter.format(balance) + " €"
        );
        withdrawAmountField.clear();
    }

    private void loadStatsTab() {
        if (currentUser == null) return;
        LOGGER.fine("Loading statistics tab for user ID: " + currentUser.getId());

        totalSpinsValueLabel.setText("...");
        totalWageredValueLabel.setText("...");
        totalWinsValueLabel.setText("...");
        gamesPlayedValueLabel.setText("...");

        Optional<UserStatistics> statsOpt = authService.getUserStatistics(currentUser.getId());

        Platform.runLater(() -> {
            if (statsOpt.isPresent()) {
                UserStatistics stats = statsOpt.get();
                LOGGER.fine("Statistics data received: " + stats);

                totalSpinsValueLabel.setText(String.valueOf(stats.getTotalSpins()));

                NumberFormat currencyFormatter = NumberFormat.getNumberInstance(LocaleManager.getCurrentLocale());
                currencyFormatter.setMinimumFractionDigits(2);
                currencyFormatter.setMaximumFractionDigits(2);

                totalWageredValueLabel.setText(currencyFormatter.format(stats.getTotalWagered()) + " €");

                totalWinsValueLabel.setText(currencyFormatter.format(stats.getTotalWon()) + " €");

                gamesPlayedValueLabel.setText(String.valueOf(stats.getDistinctGamesPlayed()));

            } else {

                LOGGER.warning("Could not load statistics for user ID: " + currentUser.getId());
                String errorMsg = "Error";
                totalSpinsValueLabel.setText(errorMsg);
                totalWageredValueLabel.setText(errorMsg);
                totalWinsValueLabel.setText(errorMsg);
                gamesPlayedValueLabel.setText(errorMsg);
            }
        });
    }

    private void loadSettingsTab() {
        updateLocaleButtonStyles();

        oldPasswordField.clear();
        newPasswordField.clear();
        repeatPasswordField.clear();
    }

    @FXML
    private void handleWithdraw(ActionEvent event) {
        clearMessages();
        String amountText = withdrawAmountField.getText().replace(',', '.');
        BigDecimal amount;

        try {
            amount = new BigDecimal(amountText);
            if (amount.scale() > 2) {
                throw new NumberFormatException("Too many decimal places.");
            }
            boolean success = authService.withdrawFunds(currentUser.getId(), amount);
            if (success) {
                showBalanceMessage(LocaleManager.getString("profile.message.withdraw.success"), false);
                loadBalanceTab();

                if (navbarComponentController != null) {

                    LOGGER.info("Navbar refresh requested after withdrawal.");
                }
            } else {

                if(SessionManager.getCurrentAccount().getBalance().compareTo(amount) < 0){
                    showBalanceMessage(LocaleManager.getString("profile.message.withdraw.insufficient"), true);
                } else {
                    showBalanceMessage(LocaleManager.getString("profile.message.withdraw.error"), true);
                }
            }
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid withdrawal amount format: " + amountText);
            showBalanceMessage(LocaleManager.getString("profile.message.withdraw.invalidamount"), true);
        } catch (IllegalArgumentException | IllegalStateException e) {
            LOGGER.log(Level.WARNING, "Withdrawal failed: " + e.getMessage());
            showBalanceMessage(e.getMessage(), true);
        }
        withdrawAmountField.clear();
    }

    @FXML
    private void handleChangePassword(ActionEvent event) {
        clearMessages();
        String oldPass = oldPasswordField.getText();
        String newPass = newPasswordField.getText();
        String repeatPass = repeatPasswordField.getText();

        if (oldPass.isEmpty() || newPass.isEmpty() || repeatPass.isEmpty()) {
            showPasswordMessage(LocaleManager.getString("profile.message.password.empty"), true);
            return;
        }

        if (!newPass.equals(repeatPass)) {
            showPasswordMessage(LocaleManager.getString("profile.message.password.mismatch"), true);
            return;
        }

        try {
            boolean success = authService.changePassword(currentUser.getId(), oldPass, newPass);
            if (success) {
                showPasswordMessage(LocaleManager.getString("profile.message.password.success"), false);
                oldPasswordField.clear();
                newPasswordField.clear();
                repeatPasswordField.clear();
            } else {

                showPasswordMessage(LocaleManager.getString("profile.message.password.incorrectold"), true);
            }
        } catch (IllegalArgumentException e) {

            showPasswordMessage(e.getMessage(), true);
        } catch (Exception e) {

            LOGGER.log(Level.SEVERE, "Unexpected error changing password for user " + currentUser.getId(), e);
            showPasswordMessage(LocaleManager.getString("profile.message.password.error"), true);
        }
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
            LOGGER.info("Setting locale to: " + locale.toLanguageTag() + " and reloading profile view.");
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
                LOGGER.severe("Could not get scene for reloading profile view.");
                return;
            }
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource("/sk/vava/royalmate/view/profile-view.fxml")),
                    LocaleManager.getBundle()
            );
            Parent newRoot = loader.load();
            scene.setRoot(newRoot);
            LOGGER.info("Profile view reloaded successfully for locale: " + LocaleManager.getCurrentLocale().toLanguageTag());
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to reload profile view FXML.", e);
        }
    }

    private void showBalanceMessage(String message, boolean isError) {
        showMessage(balanceMessageLabel, message, isError);
    }
    private void showPasswordMessage(String message, boolean isError) {
        showMessage(passwordMessageLabel, message, isError);
    }

    private void showMessage(Label label, String message, boolean isError) {
        Platform.runLater(()-> {
            label.setText(message);
            label.setTextFill(isError ? Color.RED : Color.GREEN);
            label.setVisible(true);
            label.setManaged(true);
        });
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

    private void navigateToLogin() {

        if (rootPane == null || rootPane.getScene() == null) {
            LOGGER.severe("Cannot navigate to login, root pane or scene is null.");

            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource("/sk/vava/royalmate/view/login-view.fxml")),
                    LocaleManager.getBundle()
            );
            Parent nextRoot = loader.load();
            rootPane.getScene().setRoot(nextRoot);
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to navigate back to login screen.", e);
        }
    }
}