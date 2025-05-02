package sk.vava.royalmate.controller;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.model.Game;
import sk.vava.royalmate.model.Gameplay;
import sk.vava.royalmate.service.GameService;
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CoinflipGameController {

    private static final Logger LOGGER = Logger.getLogger(CoinflipGameController.class.getName());

    @FXML private BorderPane rootPane;
    @FXML private Label gameTitleLabel;
    @FXML private VBox gameAreaVBox;
    @FXML private Rectangle gameBackgroundRect;
    @FXML private StackPane coinPane;
    @FXML private Circle coinCircle;
    @FXML private ToggleButton headsBetButton;
    @FXML private ToggleButton tailsBetButton;
    @FXML private HBox betControlsBox;
    @FXML private Label currentStakeLabel;
    @FXML private Label recentWinLossLabel;
    @FXML private Button decreaseStakeButton;
    @FXML private Button increaseStakeButton;
    @FXML private Button actionButton;
    @FXML private VBox leaderboardArea;
    @FXML private VBox leaderboardContent;
    @FXML private Button leaderboardButton;
    @FXML private VBox gameInfoPane;
    @FXML private Label descriptionLabel;
    @FXML private Label minStakeLabel;
    @FXML private Label maxStakeLabel;

    private static final Duration FLIP_HALF_DURATION = Duration.millis(100);
    private static final int NUM_FLIP_ANIMATIONS = 8;
    private static final Duration LEADERBOARD_REFRESH_INTERVAL = Duration.seconds(60);
    private static final Duration BLINK_INTERVAL = Duration.millis(750);
    private static final BigDecimal WIN_MULTIPLIER = new BigDecimal("2.00");
    private final List<BigDecimal> BASE_ALLOWED_STAKES = List.of(
            new BigDecimal("0.10"), new BigDecimal("0.20"), new BigDecimal("0.50"), new BigDecimal("1.00"),
            new BigDecimal("2.00"), new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("20.00"),
            new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("200.00"), new BigDecimal("500.00"),
            new BigDecimal("1000.00"), new BigDecimal("2000.00")
    );
    private List<BigDecimal> availableStakes = new ArrayList<>();
    private int currentStakeIndex = 0;

    private static final Color HEADS_COLOR_DISPLAY = Color.web("#F1DA2C");
    private static final Color TAILS_COLOR_DISPLAY = Color.web("#CCCCCC");
    private static final Color WIN_COLOR = HEADS_COLOR_DISPLAY;
    private static final Color LOSS_COLOR = TAILS_COLOR_DISPLAY;

    private final DropShadow WIN_GLOW_EFFECT = new DropShadow(20, WIN_COLOR);

    private final GameService gameService;
    private Game currentGame;
    private final Random random = new Random();
    private Animation flipAnimation;
    private Timeline leaderboardRefreshTimeline;
    private Timeline blinkTimeline;
    private boolean isFlipping = false;

    private Account currentUser;
    private ToggleGroup betToggleGroup;
    private BetType selectedBet = BetType.HEADS;
    private FlipResult currentVisualResult = FlipResult.HEADS;

    private enum BetType { HEADS, TAILS }
    private enum FlipResult { HEADS, TAILS }

    public CoinflipGameController() {
        this.gameService = new GameService();
    }

    public void initData(Game game) {
        this.currentGame = Objects.requireNonNull(game, "Game cannot be null");
        Platform.runLater(() -> {
            if (rootPane != null && rootPane.getScene() != null) {
                rootPane.getScene().setUserData(this);
            }
            populateUI();
        });
    }

    @FXML
    public void initialize() {
        currentUser = SessionManager.getCurrentAccount();
        if (currentUser == null) {
            LOGGER.severe("Coinflip game loaded without user session!");

            if (actionButton != null) actionButton.setDisable(true);
            if (decreaseStakeButton != null) decreaseStakeButton.setDisable(true);
            if (increaseStakeButton != null) increaseStakeButton.setDisable(true);
            if (headsBetButton != null) headsBetButton.setDisable(true);
            if (tailsBetButton != null) tailsBetButton.setDisable(true);
            return;
        }
        recentWinLossLabel.setText("");

        setupBetToggles();
        setCoinAppearance(FlipResult.HEADS);
        stopHighlightBlinking();
    }

    private void populateUI() {
        if (currentGame == null) return;

        gameTitleLabel.setText(currentGame.getName());
        descriptionLabel.setText(currentGame.getDescription());
        try {
            gameBackgroundRect.setFill(Color.web(currentGame.getBackgroundColor(), 0.8));
            Platform.runLater(() -> {
                if (gameAreaVBox != null && gameAreaVBox.getBoundsInParent() != null) {
                    Bounds contentBounds = gameAreaVBox.getBoundsInParent();
                    double padding = 30.0;
                    gameBackgroundRect.setWidth(contentBounds.getWidth() + padding);
                    gameBackgroundRect.setHeight(contentBounds.getHeight() + padding);
                } else {
                    LOGGER.warning("Could not get bounds for gameAreaVBox to resize background.");
                }
            });
        } catch (Exception e) {
            LOGGER.warning("Invalid background color hex: " + currentGame.getBackgroundColor() + ", using default.");
            gameBackgroundRect.setFill(Color.web("#222222", 0.8));
        }

        NumberFormat currencyFormatter = createCurrencyFormatter();
        minStakeLabel.setText(LocaleManager.getString("slot.label.minstake") + " " + currencyFormatter.format(currentGame.getMinStake()) + " €");
        maxStakeLabel.setText(LocaleManager.getString("slot.label.maxstake") + " " + currencyFormatter.format(currentGame.getMaxStake()) + " €");

        setupStakeControls();
        startLeaderboardRefresh();

        LOGGER.info("Coinflip game UI populated for: " + currentGame.getName());
    }

    private void setupBetToggles() {
        betToggleGroup = new ToggleGroup();
        headsBetButton.setToggleGroup(betToggleGroup);
        tailsBetButton.setToggleGroup(betToggleGroup);

        betToggleGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (isFlipping) return;
            if (newToggle == headsBetButton) { selectedBet = BetType.HEADS; }
            else if (newToggle == tailsBetButton) { selectedBet = BetType.TAILS; }
            else {
                if(oldToggle != null) { Platform.runLater(() -> betToggleGroup.selectToggle(oldToggle)); }
                else { headsBetButton.setSelected(true); selectedBet = BetType.HEADS; }
            }
            LOGGER.fine("Selected bet: " + selectedBet);
        });
        headsBetButton.setSelected(true);
        selectedBet = BetType.HEADS;
    }

    private void setupStakeControls() {
        availableStakes = BASE_ALLOWED_STAKES.stream()
                .filter(s -> s.compareTo(currentGame.getMinStake()) >= 0 && s.compareTo(currentGame.getMaxStake()) <= 0)
                .sorted().collect(Collectors.toList());
        if (availableStakes.isEmpty()) {
            LOGGER.severe("No valid stakes available for game " + currentGame.getName());
            actionButton.setDisable(true); increaseStakeButton.setDisable(true); decreaseStakeButton.setDisable(true);
            currentStakeLabel.setText("Stake: N/A"); return;
        }
        currentStakeIndex = 0;
        for(int i=0; i < availableStakes.size(); i++){ if(availableStakes.get(i).compareTo(currentGame.getMinStake()) >= 0){ currentStakeIndex = i; break; } }
        updateStakeDisplay();
        updateActionButtonState();
    }

    @FXML private void handleIncreaseStake(ActionEvent event) {
        if (!isFlipping && currentStakeIndex < availableStakes.size() - 1) {
            currentStakeIndex++;
            updateStakeDisplay();
            updateActionButtonState();
        }
    }
    @FXML private void handleDecreaseStake(ActionEvent event) {
        if (!isFlipping && currentStakeIndex > 0) {
            currentStakeIndex--;
            updateStakeDisplay();
            updateActionButtonState();
        }
    }

    private void updateStakeDisplay() {
        if (!availableStakes.isEmpty()) {
            BigDecimal stake = availableStakes.get(currentStakeIndex);
            currentStakeLabel.setText(LocaleManager.getString("slot.label.currentstake") + " " + createCurrencyFormatter().format(stake) + " €");
        }
        decreaseStakeButton.setDisable(isFlipping || currentStakeIndex <= 0);
        increaseStakeButton.setDisable(isFlipping || currentStakeIndex >= availableStakes.size() - 1);
    }

    private void updateActionButtonState() {

        actionButton.setText(LocaleManager.getString("coinflip.button.flip"));
        actionButton.setOnAction(this::handleFlip);

        if (availableStakes.isEmpty()) { actionButton.setDisable(true); return; }
        BigDecimal currentStake = availableStakes.get(currentStakeIndex);
        Account acc = SessionManager.getCurrentAccount();
        boolean canAfford = acc != null && acc.getBalance() != null && acc.getBalance().compareTo(currentStake) >= 0;
        actionButton.setDisable(isFlipping || !canAfford);

        String insufficientFundsMsg = LocaleManager.getString("slot.error.insufficientfunds");
        if (!canAfford && !isFlipping) {
            showWinLossMessage(insufficientFundsMsg, true, false);
        } else if (!isFlipping && recentWinLossLabel.getText().equals(insufficientFundsMsg)) {

            if (coinCircle.getEffect() == null) {
                recentWinLossLabel.setText(""); recentWinLossLabel.setEffect(null);
            }
        }
    }

    @FXML
    private void handleFlip(ActionEvent event) {

        if (isFlipping || availableStakes.isEmpty()) {
            LOGGER.warning("Flip rejected: isFlipping=" + isFlipping);
            return;
        }
        if (selectedBet == null) { showWinLossMessage(LocaleManager.getString("coinflip.message.selectbet"), true, false); return; }

        BigDecimal currentStake = availableStakes.get(currentStakeIndex);
        Account acc = SessionManager.getCurrentAccount();
        if (acc == null || acc.getBalance() == null || acc.getBalance().compareTo(currentStake) < 0) {
            showWinLossMessage(LocaleManager.getString("slot.error.insufficientfunds"), true, false);
            actionButton.setDisable(true); return;
        }

        isFlipping = true;
        actionButton.setDisable(true); increaseStakeButton.setDisable(true); decreaseStakeButton.setDisable(true);
        disableBetToggles(true);
        recentWinLossLabel.setText(""); recentWinLossLabel.setEffect(null);
        stopHighlightBlinking();

        showWinLossMessage(LocaleManager.getString("slot.message.placingbet"), false, false);

        Task<Long> placeBetTask = new Task<>() {
            @Override protected Long call() throws Exception { return gameService.placeBet(currentUser.getId(), currentGame.getId(), currentStake); }
        };
        placeBetTask.setOnSucceeded(workerStateEvent -> {
            long gameplayId = placeBetTask.getValue();
            if (gameplayId < 0) { finishFlip(false, "slot.error.betfailed"); }
            else { showWinLossMessage(LocaleManager.getString("slot.message.spinning"), false, false); startFlipAnimation(gameplayId, currentStake); }
        });
        placeBetTask.setOnFailed(workerStateEvent -> {
            LOGGER.log(Level.SEVERE, "Placing bet task failed.", placeBetTask.getException());
            finishFlip(false, "slot.error.betfailed");
        });
        new Thread(placeBetTask).start();
    }

    private void startFlipAnimation(long gameplayId, BigDecimal stakeAmount) {
        if (flipAnimation != null) { flipAnimation.stop(); }

        FlipResult finalResult = (random.nextBoolean()) ? FlipResult.HEADS : FlipResult.TAILS;
        LOGGER.info("Final coin flip result determined: " + finalResult);

        SequentialTransition sequence = new SequentialTransition();

        currentVisualResult = (finalResult == FlipResult.HEADS) ? FlipResult.TAILS : FlipResult.HEADS;
        setCoinAppearance(currentVisualResult);

        for (int i = 0; i < NUM_FLIP_ANIMATIONS; i++) {

            FlipResult targetSide = (currentVisualResult == FlipResult.HEADS) ? FlipResult.TAILS : FlipResult.HEADS;
            sequence.getChildren().add(createHalfFlip(targetSide));
            currentVisualResult = targetSide;
        }

        sequence.setOnFinished(event -> {
            LOGGER.fine("Flip animation sequence finished.");
            processResult(finalResult, gameplayId, stakeAmount);
        });

        flipAnimation = sequence;
        LOGGER.info("Starting coin flip animation sequence...");
        flipAnimation.play();
    }

    private Animation createHalfFlip(FlipResult nextSide) {
        ScaleTransition scaleDown = new ScaleTransition(FLIP_HALF_DURATION.divide(2), coinCircle);
        scaleDown.setFromX(1.0); scaleDown.setToX(0.0); scaleDown.setInterpolator(Interpolator.EASE_IN);
        PauseTransition changeFace = new PauseTransition(Duration.millis(1));

        changeFace.setOnFinished(e -> setCoinAppearance(nextSide));
        ScaleTransition scaleUp = new ScaleTransition(FLIP_HALF_DURATION.divide(2), coinCircle);
        scaleUp.setFromX(0.0); scaleUp.setToX(1.0); scaleUp.setInterpolator(Interpolator.EASE_OUT);
        return new SequentialTransition(scaleDown, changeFace, scaleUp);
    }

    private void setCoinAppearance(FlipResult side) {
        coinCircle.setFill(side == FlipResult.HEADS ? HEADS_COLOR_DISPLAY : TAILS_COLOR_DISPLAY);
        coinCircle.setEffect(null);
    }

    private void setCoinWinLossAppearance(boolean win) {

        coinCircle.setFill(win ? WIN_COLOR : LOSS_COLOR);

        coinCircle.setEffect(win ? WIN_GLOW_EFFECT : null);
    }

    private void processResult(FlipResult result, long gameplayId, BigDecimal stakeAmount) {
        BigDecimal payoutAmount = BigDecimal.ZERO;
        boolean win = (selectedBet == BetType.HEADS && result == FlipResult.HEADS) ||
                (selectedBet == BetType.TAILS && result == FlipResult.TAILS);
        String resultString = (result == FlipResult.HEADS) ? LocaleManager.getString("coinflip.bet.heads") : LocaleManager.getString("coinflip.bet.tails");

        setCoinWinLossAppearance(win);

        if (win) {
            payoutAmount = stakeAmount.multiply(WIN_MULTIPLIER).setScale(2, RoundingMode.DOWN);
            String winMsg = MessageFormat.format(LocaleManager.getString("coinflip.message.won"),
                    createCurrencyFormatter().format(payoutAmount), resultString);
            showWinLossMessage(winMsg, false, true);
            startHighlightBlinking();
        } else {
            String lossMsg = MessageFormat.format(LocaleManager.getString("coinflip.message.lost"), resultString);
            showWinLossMessage(lossMsg, true, true);
            stopHighlightBlinking();
        }

        final BigDecimal finalPayout = payoutAmount;
        Task<Boolean> recordTask = new Task<>() {
            @Override protected Boolean call() throws Exception { return gameService.recordResult(gameplayId, result.name(), finalPayout, currentUser.getId()); }
        };
        boolean finalIsWin = win;
        recordTask.setOnSucceeded(e -> {
            boolean recorded = recordTask.getValue();
            if (!recorded) { LOGGER.severe("Failed to record coinflip result for gameplayId: " + gameplayId); }
            else { LOGGER.info("Coinflip result recorded successfully for gameplayId: " + gameplayId); }
            if (finalIsWin && recorded) { loadRecentWinsLeaderboard(); }

            finishFlip(true, null);
        });
        recordTask.setOnFailed(e -> {
            LOGGER.log(Level.SEVERE, "Recording coinflip result task failed.", recordTask.getException());
            showWinLossMessage(LocaleManager.getString("slot.error.resultfailed"), true, true);

            finishFlip(true, null);
        });
        new Thread(recordTask).start();
    }

    private void startHighlightBlinking() {
        stopHighlightBlinking();

        blinkTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, e -> {
                    coinCircle.setFill(WIN_COLOR);
                    coinCircle.setEffect(WIN_GLOW_EFFECT);
                }),
                new KeyFrame(BLINK_INTERVAL, e -> {
                    coinCircle.setFill(WIN_COLOR);
                    coinCircle.setEffect(null);
                }),
                new KeyFrame(BLINK_INTERVAL.multiply(2))
        );
        blinkTimeline.setCycleCount(Animation.INDEFINITE);
        blinkTimeline.play();
        LOGGER.fine("Started coin highlight blinking (glow toggle).");
    }

    private void stopHighlightBlinking() {
        if (blinkTimeline != null) {
            blinkTimeline.stop();
            blinkTimeline = null;
            LOGGER.fine("Stopped coin highlight blinking.");
        }

        if (!isFlipping) {
            coinCircle.setEffect(null);
        }
    }

    private void finishFlip(boolean betPlacedSuccessfully, String messageKeyIfFailed) {
        isFlipping = false;

        Platform.runLater(() -> {
            if (messageKeyIfFailed != null) {
                showWinLossMessage(LocaleManager.getString(messageKeyIfFailed), true, true);

                actionButton.setDisable(true);
                disableBetToggles(false);
                updateStakeDisplay();
            } else {

                updateActionButtonState();
                disableBetToggles(false);
                updateStakeDisplay();
            }
        });
    }

    private void disableBetToggles(boolean disable) {
        headsBetButton.setDisable(disable);
        tailsBetButton.setDisable(disable);
    }

    private void showWinLossMessage(String message, boolean isError, boolean applyEffect) {
        Platform.runLater(() -> {
            recentWinLossLabel.setText(message);
            Color textColor;
            if (message.equals(LocaleManager.getString("slot.message.placingbet")) || message.equals(LocaleManager.getString("slot.message.spinning"))) {
                textColor = Color.BLACK;
            } else { textColor = isError ? Color.INDIANRED : Color.GREEN; }
            recentWinLossLabel.setTextFill(textColor);
            if (applyEffect && textColor != Color.BLACK) {
                Color shadowColor = isError ? Color.INDIANRED : Color.LIGHTGREEN;
                DropShadow ds = new DropShadow(1.0, 0.0, 0.0, shadowColor); ds.setSpread(0.05);
                recentWinLossLabel.setEffect(ds);
            } else { recentWinLossLabel.setEffect(null); }
        });
    }

    private void startLeaderboardRefresh() {
        loadRecentWinsLeaderboard();
        if (leaderboardRefreshTimeline != null) leaderboardRefreshTimeline.stop();
        leaderboardRefreshTimeline = new Timeline(new KeyFrame(LEADERBOARD_REFRESH_INTERVAL, e -> loadRecentWinsLeaderboard()));
        leaderboardRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        leaderboardRefreshTimeline.play();
        LOGGER.info("Leaderboard refresh started.");
    }
    private void loadRecentWinsLeaderboard() {
        if(currentGame == null) return;
        Task<List<Gameplay>> loadTask = new Task<>() {
            @Override protected List<Gameplay> call() throws Exception { return gameService.getRecentGameWins(currentGame.getId()); }
        };
        loadTask.setOnSucceeded(e -> {
            List<Gameplay> wins = loadTask.getValue();
            Platform.runLater(() -> {
                leaderboardContent.getChildren().clear();
                if(wins == null || wins.isEmpty()) {
                    Label emptyLabel = new Label(LocaleManager.getString("coinflip.leaderboard.empty"));
                    emptyLabel.getStyleClass().add("leaderboard-entry-label");
                    leaderboardContent.getChildren().add(emptyLabel);
                } else {
                    wins.forEach(win -> leaderboardContent.getChildren().add(createLeaderboardCard(win)));
                }
            });
        });
        loadTask.setOnFailed(e -> LOGGER.log(Level.SEVERE, "Failed to load recent wins leaderboard.", loadTask.getException()));
        new Thread(loadTask).start();
    }
    private Node createLeaderboardCard(Gameplay win) {
        HBox card = new HBox(); card.getStyleClass().add("leaderboard-card");
        ImageView icon = new ImageView();
        try { icon.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/profile_icon_white.png")))); }
        catch (Exception e) { LOGGER.warning("Failed to load profile_icon_white.png for leaderboard"); }
        icon.setFitHeight(20); icon.setFitWidth(20);
        Label usernameLabel = new Label(win.getUsername() != null ? win.getUsername() : "Unknown"); usernameLabel.getStyleClass().add("leaderboard-username");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label amountLabel = new Label(createCurrencyFormatter().format(win.getPayoutAmount()) + " €"); amountLabel.getStyleClass().add("leaderboard-amount");
        card.getChildren().addAll(icon, usernameLabel, spacer, amountLabel); return card;
    }
    @FXML
    private void handleLeaderboardButton(ActionEvent event) {
        LOGGER.info("Navigate to Leaderboards");
        navigateTo(event, "/sk/vava/royalmate/view/leaderboard-view.fxml");
    }

    private NumberFormat createCurrencyFormatter() {
        NumberFormat currencyFormatter = NumberFormat.getNumberInstance(LocaleManager.getCurrentLocale());
        currencyFormatter.setMinimumFractionDigits(2); currencyFormatter.setMaximumFractionDigits(2);
        return currencyFormatter;
    }

    private void navigateTo(ActionEvent event, String fxmlPath) {
        Node source = (Node) event.getSource();
        try {
            cleanup();
            Scene scene = source.getScene();
            if (scene == null) { scene = rootPane.getScene(); if (scene == null) { LOGGER.severe("Could not get current scene."); return; } }
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource(fxmlPath)), LocaleManager.getBundle());
            Parent nextRoot = loader.load();

            Object oldController = scene.getUserData();
            if(oldController instanceof SlotGameController oldSlot) oldSlot.cleanup();
            else if(oldController instanceof RouletteGameController oldRoulette) oldRoulette.cleanup();
            else if(oldController instanceof CoinflipGameController oldCoinflip) oldCoinflip.cleanup();
            scene.setRoot(nextRoot);
            scene.setUserData(null);
            LOGGER.info("Successfully navigated to: " + fxmlPath);
        } catch (IOException | NullPointerException e) { LOGGER.log(Level.SEVERE, "Failed to load FXML: " + fxmlPath, e);
        } catch (ClassCastException e) { LOGGER.log(Level.SEVERE, "Failed to cast event source to Node.", e); }
    }

    public void cleanup() {
        if (flipAnimation != null) flipAnimation.stop();
        if (leaderboardRefreshTimeline != null) leaderboardRefreshTimeline.stop();
        stopHighlightBlinking();
        LOGGER.info("CoinflipGameController cleaned up timers.");
    }
}