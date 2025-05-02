package sk.vava.royalmate.controller;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
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

public class RouletteGameController {

    private static final Logger LOGGER = Logger.getLogger(RouletteGameController.class.getName());

    @FXML private BorderPane rootPane;
    @FXML private Label gameTitleLabel;
    @FXML private VBox gameAreaVBox;
    @FXML private Rectangle gameBackgroundRect;
    @FXML private ImageView rouletteWheelImageView;
    @FXML private StackPane resultDisplayPane;
    @FXML private Circle resultCircle;
    @FXML private Label resultNumberLabel;
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
    @FXML private Label volatilityLabel;

    @FXML private ToggleButton redBetButton;
    @FXML private ToggleButton blackBetButton;
    @FXML private ToggleButton greenBetButton;
    @FXML private ToggleButton range1BetButton;
    @FXML private ToggleButton range2BetButton;
    @FXML private ToggleButton range3BetButton;
    @FXML private ToggleButton range4BetButton;

    private static final Duration FLASH_DURATION = Duration.millis(125);
    private static final Duration FLASH_TOTAL_DURATION = Duration.seconds(1.5);
    private static final Duration LEADERBOARD_REFRESH_INTERVAL = Duration.seconds(60);

    private static final BigDecimal COLOR_PAYOUT_MULTIPLIER = new BigDecimal("2.00");
    private static final BigDecimal RANGE_PAYOUT_MULTIPLIER = new BigDecimal("4.00");
    private static final BigDecimal GREEN_PAYOUT_MULTIPLIER = new BigDecimal("36.00");

    private final List<BigDecimal> BASE_ALLOWED_STAKES = List.of(
            new BigDecimal("0.10"), new BigDecimal("0.20"), new BigDecimal("0.50"), new BigDecimal("1.00"),
            new BigDecimal("2.00"), new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("20.00"),
            new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("200.00"), new BigDecimal("500.00"),
            new BigDecimal("1000.00"), new BigDecimal("2000.00")
    );
    private List<BigDecimal> availableStakes = new ArrayList<>();
    private int currentStakeIndex = 0;

    private static final Color COLOR_RED = Color.web("#F55231");
    private static final Color COLOR_BLACK = Color.web("#434343");
    private static final Color COLOR_GREEN = Color.web("#67A832");
    private static final Color COLOR_NUMBER = Color.web("#FFFFFF");

    private final GameService gameService;
    private Game currentGame;
    private final Random random = new Random();
    private Timeline flashAnimationTimeline;
    private Timeline leaderboardRefreshTimeline;
    private boolean isSpinning = false;
    private Account currentUser;
    private Set<BetType> selectedBetTypes = new HashSet<>();

    private enum BetType { RED, BLACK, GREEN, RANGE_1_9, RANGE_10_18, RANGE_19_27, RANGE_28_36 }

    public RouletteGameController() {
        this.gameService = new GameService();
    }

    public void initData(Game game) {
        this.currentGame = Objects.requireNonNull(game, "Game cannot be null");
        Platform.runLater(this::populateUI);
    }

    @FXML
    public void initialize() {
        currentUser = SessionManager.getCurrentAccount();
        if (currentUser == null) {
            LOGGER.severe("Roulette game loaded without user session!"); return;
        }
        recentWinLossLabel.setText("");
        resultDisplayPane.setVisible(false);

        setupBetToggles();

    }

    private void populateUI() {
        if (currentGame == null) return;

        gameTitleLabel.setText(currentGame.getName());
        descriptionLabel.setText(currentGame.getDescription());
        try {
            gameBackgroundRect.setFill(Color.web(currentGame.getBackgroundColor(), 0.8));

            Platform.runLater(() -> {
                Bounds contentBounds = gameAreaVBox.getBoundsInParent();
                double padding = 30.0;
                gameBackgroundRect.setWidth(contentBounds.getWidth() + padding);
                gameBackgroundRect.setHeight(contentBounds.getHeight() + padding);
            });
        } catch (Exception e) {
            LOGGER.warning("Invalid background color hex: " + currentGame.getBackgroundColor() + ", using default.");
            gameBackgroundRect.setFill(Color.web("#222222", 0.8));
        }

        NumberFormat currencyFormatter = createCurrencyFormatter();
        minStakeLabel.setText(LocaleManager.getString("slot.label.minstake") + " " + currencyFormatter.format(currentGame.getMinStake()) + " €");
        maxStakeLabel.setText(LocaleManager.getString("slot.label.maxstake") + " " + currencyFormatter.format(currentGame.getMaxStake()) + " €");
        volatilityLabel.setText(LocaleManager.getString("slot.label.volatility") + " " + currentGame.getVolatility() + "/5");

        setupStakeControls();
        startLeaderboardRefresh();

        LOGGER.info("Roulette game UI populated for: " + currentGame.getName());
    }

    private void setupBetToggles() {
        redBetButton.setOnAction(e -> handleBetToggle(BetType.RED, redBetButton.isSelected()));
        blackBetButton.setOnAction(e -> handleBetToggle(BetType.BLACK, blackBetButton.isSelected()));
        greenBetButton.setOnAction(e -> handleBetToggle(BetType.GREEN, greenBetButton.isSelected()));
        range1BetButton.setOnAction(e -> handleBetToggle(BetType.RANGE_1_9, range1BetButton.isSelected()));
        range2BetButton.setOnAction(e -> handleBetToggle(BetType.RANGE_10_18, range2BetButton.isSelected()));
        range3BetButton.setOnAction(e -> handleBetToggle(BetType.RANGE_19_27, range3BetButton.isSelected()));
        range4BetButton.setOnAction(e -> handleBetToggle(BetType.RANGE_28_36, range4BetButton.isSelected()));

        redBetButton.setSelected(true);
        handleBetToggle(BetType.RED, true);
    }

    private void handleBetToggle(BetType type, boolean isSelected) {
        if (isSelected) {
            if (type == BetType.GREEN) {
                selectedBetTypes.clear();
                selectedBetTypes.add(BetType.GREEN);
                deselectOtherButtons(greenBetButton);
            } else {
                selectedBetTypes.remove(BetType.GREEN);
                greenBetButton.setSelected(false);
                selectedBetTypes.add(type);
                if(type == BetType.RED) { blackBetButton.setSelected(false); selectedBetTypes.remove(BetType.BLACK); }
                if(type == BetType.BLACK) { redBetButton.setSelected(false); selectedBetTypes.remove(BetType.RED); }

            }
        } else {
            selectedBetTypes.remove(type);
        }
        LOGGER.fine("Selected bets updated: " + selectedBetTypes);
    }

    private void deselectOtherButtons(ToggleButton keepSelected) {
        if (redBetButton != keepSelected) redBetButton.setSelected(false);
        if (blackBetButton != keepSelected) blackBetButton.setSelected(false);
        if (greenBetButton != keepSelected) greenBetButton.setSelected(false);
        if (range1BetButton != keepSelected) range1BetButton.setSelected(false);
        if (range2BetButton != keepSelected) range2BetButton.setSelected(false);
        if (range3BetButton != keepSelected) range3BetButton.setSelected(false);
        if (range4BetButton != keepSelected) range4BetButton.setSelected(false);
    }

    private void setupStakeControls() {

        availableStakes = BASE_ALLOWED_STAKES.stream()
                .filter(s -> s.compareTo(currentGame.getMinStake()) >= 0 && s.compareTo(currentGame.getMaxStake()) <= 0)
                .sorted()
                .collect(Collectors.toList());

        if (availableStakes.isEmpty()) {
            LOGGER.severe("No valid stakes available for game " + currentGame.getName() + " within min/max bounds.");
            actionButton.setDisable(true); increaseStakeButton.setDisable(true); decreaseStakeButton.setDisable(true);
            currentStakeLabel.setText("Stake: N/A"); return;
        }

        currentStakeIndex = 0;

        for(int i=0; i < availableStakes.size(); i++){
            if(availableStakes.get(i).compareTo(currentGame.getMinStake()) >= 0){
                currentStakeIndex = i;
                break;
            }
        }
        updateStakeDisplay();
        updateSpinButtonState();
    }

    @FXML private void handleIncreaseStake(ActionEvent event) {
        if (currentStakeIndex < availableStakes.size() - 1) {
            currentStakeIndex++;
            updateStakeDisplay();
            updateSpinButtonState();
        }
    }
    @FXML private void handleDecreaseStake(ActionEvent event) {
        if (currentStakeIndex > 0) {
            currentStakeIndex--;
            updateStakeDisplay();
            updateSpinButtonState();
        }
    }

    private void updateStakeDisplay() {
        if (!availableStakes.isEmpty()) {
            BigDecimal currentStake = availableStakes.get(currentStakeIndex);

            currentStakeLabel.setText(LocaleManager.getString("slot.label.currentstake") + " " + createCurrencyFormatter().format(currentStake) + " €");
        }
        decreaseStakeButton.setDisable(currentStakeIndex <= 0);
        increaseStakeButton.setDisable(currentStakeIndex >= availableStakes.size() - 1);
    }

    private void updateSpinButtonState() {
        if (availableStakes.isEmpty()) { actionButton.setDisable(true); return; }
        BigDecimal currentStake = availableStakes.get(currentStakeIndex);
        Account acc = SessionManager.getCurrentAccount();
        boolean canAfford = acc != null && acc.getBalance() != null && acc.getBalance().compareTo(currentStake) >= 0;
        actionButton.setDisable(isSpinning || !canAfford);

        String insufficientFundsMsg = LocaleManager.getString("slot.error.insufficientfunds");
        if (!canAfford && !isSpinning) {
            showWinLossMessage(insufficientFundsMsg, true, false);
        } else if (!isSpinning && recentWinLossLabel.getText().equals(insufficientFundsMsg)) {

            recentWinLossLabel.setText("");
            recentWinLossLabel.setEffect(null);
        }
    }

    @FXML
    private void handleSpin(ActionEvent event) {
        if (isSpinning || availableStakes.isEmpty()) return;

        if (selectedBetTypes.isEmpty()) {
            showWinLossMessage(LocaleManager.getString("roulette.error.nobet"), true, false);
            return;
        }
        if (selectedBetTypes.contains(BetType.GREEN) && selectedBetTypes.size() > 1) {
            showWinLossMessage(LocaleManager.getString("roulette.error.invalidbet"), true, false);
            return;
        }

        BigDecimal currentStake = availableStakes.get(currentStakeIndex);
        Account acc = SessionManager.getCurrentAccount();

        if (acc == null || acc.getBalance() == null || acc.getBalance().compareTo(currentStake) < 0) {
            showWinLossMessage(LocaleManager.getString("slot.error.insufficientfunds"), true, false);
            actionButton.setDisable(true); return;
        }

        isSpinning = true;
        actionButton.setDisable(true); increaseStakeButton.setDisable(true); decreaseStakeButton.setDisable(true);
        disableBetToggles(true);
        recentWinLossLabel.setText(""); recentWinLossLabel.setEffect(null);
        resultDisplayPane.setVisible(false);

        showWinLossMessage(LocaleManager.getString("slot.message.placingbet"), false, false);

        Task<Long> placeBetTask = new Task<>() {
            @Override protected Long call() throws Exception {
                return gameService.placeBet(currentUser.getId(), currentGame.getId(), currentStake);
            }
        };
        placeBetTask.setOnSucceeded(workerStateEvent -> {
            long gameplayId = placeBetTask.getValue();
            if (gameplayId < 0) { finishSpin(false, "slot.error.betfailed"); }
            else {

                showWinLossMessage(LocaleManager.getString("slot.message.spinning"), false, false);
                startSpinAnimation(gameplayId, currentStake);
            }
        });
        placeBetTask.setOnFailed(workerStateEvent -> {
            LOGGER.log(Level.SEVERE, "Placing bet task failed.", placeBetTask.getException());
            finishSpin(false, "slot.error.betfailed");
        });
        new Thread(placeBetTask).start();
    }

    private void startSpinAnimation(long gameplayId, BigDecimal stakeAmount) {
        if (flashAnimationTimeline != null) { flashAnimationTimeline.stop(); }

        flashAnimationTimeline = new Timeline();
        int cycles = (int)(FLASH_TOTAL_DURATION.toMillis() / FLASH_DURATION.toMillis());
        flashAnimationTimeline.setCycleCount(cycles);

        flashAnimationTimeline.getKeyFrames().add(new KeyFrame(FLASH_DURATION, event -> {
            int randomNum = random.nextInt(37);
            Color randomColor = getNumberColor(randomNum);
            updateResultDisplay(randomNum, randomColor, true);
        }));

        flashAnimationTimeline.setOnFinished(event -> {
            LOGGER.fine("Animation finished. Generating final result.");
            int winningNumber = generateWinningNumber();
            Color winningColor = getNumberColor(winningNumber);
            String colorName = getColorName(winningColor);
            updateResultDisplay(winningNumber, winningColor, false);
            processResult(winningNumber, colorName, gameplayId, stakeAmount);
        });

        flashAnimationTimeline.play();
    }

    private int generateWinningNumber() {
        int volatility = currentGame.getVolatility();
        double zeroChance = 0.01 + (volatility - 1) * 0.01;
        if (random.nextDouble() < zeroChance) { return 0; }
        else { return random.nextInt(36) + 1; }
    }

    private Color getNumberColor(int number) {
        if (number == 0) return COLOR_GREEN;
        if ((number >= 1 && number <= 10) || (number >= 19 && number <= 28)) {
            return (number % 2 != 0) ? COLOR_RED : COLOR_BLACK;
        } else {
            return (number % 2 != 0) ? COLOR_BLACK : COLOR_RED;
        }
    }

    private String getColorName(Color color) {
        if (color.equals(COLOR_RED)) return "Red";
        if (color.equals(COLOR_BLACK)) return "Black";
        if (color.equals(COLOR_GREEN)) return "Green";
        return "Unknown";
    }

    private boolean isInRange(int number, BetType rangeType) {
        return switch (rangeType) {
            case RANGE_1_9 -> number >= 1 && number <= 9;
            case RANGE_10_18 -> number >= 10 && number <= 18;
            case RANGE_19_27 -> number >= 19 && number <= 27;
            case RANGE_28_36 -> number >= 28 && number <= 36;
            default -> false;
        };
    }

    private void updateResultDisplay(int number, Color color, boolean isFlashing) {
        resultDisplayPane.setVisible(true);
        resultCircle.setFill(color);
        resultNumberLabel.setText(String.valueOf(number));
        resultNumberLabel.setTextFill(number == 0 ? Color.WHITE : COLOR_NUMBER);
        resultDisplayPane.setOpacity(isFlashing ? (0.6 + random.nextDouble() * 0.4) : 1.0);
    }

    private void processResult(int winningNumber, String winningColorName, long gameplayId, BigDecimal stakeAmount) {
        BigDecimal totalPayout = BigDecimal.ZERO;
        boolean betWon = false;
        BigDecimal calculatedMultiplier = BigDecimal.ZERO;

        long selectedRangeCount = selectedBetTypes.stream()
                .filter(bt -> bt == BetType.RANGE_1_9 || bt == BetType.RANGE_10_18 ||
                        bt == BetType.RANGE_19_27 || bt == BetType.RANGE_28_36)
                .count();

        if (selectedBetTypes.contains(BetType.GREEN)) {
            if (winningNumber == 0 && selectedBetTypes.size() == 1) {
                calculatedMultiplier = GREEN_PAYOUT_MULTIPLIER;
                betWon = true;
                LOGGER.fine("Winning bet type: GREEN");
            }

        }

        else if (!selectedBetTypes.isEmpty()) {
            boolean colorBetPlaced = selectedBetTypes.contains(BetType.RED) || selectedBetTypes.contains(BetType.BLACK);
            boolean rangeBetPlaced = selectedRangeCount > 0;

            boolean colorConditionMet = !colorBetPlaced ||
                    (selectedBetTypes.contains(BetType.RED) && winningColorName.equals("Red")) ||
                    (selectedBetTypes.contains(BetType.BLACK) && winningColorName.equals("Black"));

            boolean rangeConditionMet = !rangeBetPlaced ||
                    (selectedBetTypes.contains(BetType.RANGE_1_9) && isInRange(winningNumber, BetType.RANGE_1_9)) ||
                    (selectedBetTypes.contains(BetType.RANGE_10_18) && isInRange(winningNumber, BetType.RANGE_10_18)) ||
                    (selectedBetTypes.contains(BetType.RANGE_19_27) && isInRange(winningNumber, BetType.RANGE_19_27)) ||
                    (selectedBetTypes.contains(BetType.RANGE_28_36) && isInRange(winningNumber, BetType.RANGE_28_36));

            if (colorConditionMet && rangeConditionMet) {

                if (colorBetPlaced && rangeBetPlaced) {

                    betWon = true;
                    calculatedMultiplier = switch ((int) selectedRangeCount) {
                        case 1 -> new BigDecimal("8.00");
                        case 2 -> new BigDecimal("4.00");
                        case 3 -> new BigDecimal("3.00");
                        case 4 -> new BigDecimal("2.00");
                        default -> BigDecimal.ZERO;
                    };
                    LOGGER.fine("Winning combined Color + Range. Ranges selected: " + selectedRangeCount + ", Multiplier: " + calculatedMultiplier);

                } else if (colorBetPlaced && !rangeBetPlaced) {

                    betWon = true;
                    calculatedMultiplier = COLOR_PAYOUT_MULTIPLIER;
                    LOGGER.fine("Winning bet type: Color Only. Multiplier: " + calculatedMultiplier);

                } else if (!colorBetPlaced && rangeBetPlaced) {

                    betWon = true;
                    calculatedMultiplier = switch ((int) selectedRangeCount) {
                        case 1 -> new BigDecimal("4.00");
                        case 2 -> new BigDecimal("2.00");
                        case 3 -> new BigDecimal("1.50");
                        case 4 -> new BigDecimal("1.00");
                        default -> BigDecimal.ZERO;
                    };
                    LOGGER.fine("Winning bet type: Range Only. Ranges selected: " + selectedRangeCount + ", Multiplier: " + calculatedMultiplier);
                }

            }

        }

        totalPayout = stakeAmount.multiply(calculatedMultiplier).setScale(2, RoundingMode.DOWN);

        boolean isProfitableWin = betWon && calculatedMultiplier.compareTo(BigDecimal.ONE) > 0;

        String formattedResultMessage;
        if (isProfitableWin) {
            formattedResultMessage = MessageFormat.format(
                    LocaleManager.getString("roulette.message.won"),
                    createCurrencyFormatter().format(totalPayout)
            );
            showWinLossMessage(formattedResultMessage, false, true);
        } else {
            formattedResultMessage = LocaleManager.getString("roulette.message.lost");
            showWinLossMessage(formattedResultMessage, true, true);
        }

        String outcomeString = winningNumber + " (" + winningColorName + ")";
        final BigDecimal finalTotalPayout = totalPayout;
        Task<Boolean> recordTask = new Task<>() {
            @Override protected Boolean call() throws Exception {

                return gameService.recordResult(gameplayId, outcomeString, finalTotalPayout, currentUser.getId());
            }
        };
        boolean finalIsProfitableWin = isProfitableWin;
        recordTask.setOnSucceeded(e -> {
            boolean recorded = recordTask.getValue();
            if (!recorded) { LOGGER.severe("Failed to record game result for gameplayId: " + gameplayId); }
            else { LOGGER.info("Game result recorded successfully for gameplayId: " + gameplayId); }

            if (finalIsProfitableWin && recorded) { loadRecentWinsLeaderboard(); }
            finishSpin(true, null);
        });
        recordTask.setOnFailed(e -> {
            LOGGER.log(Level.SEVERE, "Recording result task failed.", recordTask.getException());
            showWinLossMessage(LocaleManager.getString("slot.error.resultfailed"), true, true);
            finishSpin(true, null);
        });
        new Thread(recordTask).start();
    }

    private void finishSpin(boolean betPlacedSuccessfully, String messageKeyIfFailed) {
        isSpinning = false;
        if (betPlacedSuccessfully) {
            increaseStakeButton.setDisable(currentStakeIndex >= availableStakes.size() - 1);
            decreaseStakeButton.setDisable(currentStakeIndex <= 0);
            disableBetToggles(false);
            updateSpinButtonState();
        } else if (messageKeyIfFailed != null){
            showWinLossMessage(LocaleManager.getString(messageKeyIfFailed), true, true);
            increaseStakeButton.setDisable(currentStakeIndex >= availableStakes.size() - 1);
            decreaseStakeButton.setDisable(currentStakeIndex <= 0);
            disableBetToggles(false);
            actionButton.setDisable(true);
        } else {
            increaseStakeButton.setDisable(currentStakeIndex >= availableStakes.size() - 1);
            decreaseStakeButton.setDisable(currentStakeIndex <= 0);
            disableBetToggles(false);
            actionButton.setDisable(true);
        }
    }

    private void disableBetToggles(boolean disable) {
        redBetButton.setDisable(disable);
        blackBetButton.setDisable(disable);
        greenBetButton.setDisable(disable);
        range1BetButton.setDisable(disable);
        range2BetButton.setDisable(disable);
        range3BetButton.setDisable(disable);
        range4BetButton.setDisable(disable);
    }

    private void showWinLossMessage(String message, boolean isError, boolean applyEffect) {
        Platform.runLater(() -> {
            recentWinLossLabel.setText(message);

            Color textColor;
            if (message.equals(LocaleManager.getString("slot.message.placingbet")) || message.equals(LocaleManager.getString("slot.message.spinning"))) {
                textColor = Color.BLACK;
            } else {
                textColor = isError ? Color.INDIANRED : Color.GREEN;
            }
            recentWinLossLabel.setTextFill(textColor);

            if (applyEffect && textColor != Color.BLACK) {
                Color shadowColor = isError ? Color.INDIANRED : Color.LIGHTGREEN;
                DropShadow ds = new DropShadow(1.0, 0.0, 0.0, shadowColor);
                ds.setSpread(0.05);
                recentWinLossLabel.setEffect(ds);
            } else {
                recentWinLossLabel.setEffect(null);
            }
        });
    }

    private void showWinLossMessage(String message, boolean isError) {
        showWinLossMessage(message, isError, false);
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
            @Override protected List<Gameplay> call() throws Exception {
                return gameService.getRecentGameWins(currentGame.getId());
            }
        };
        loadTask.setOnSucceeded(e -> {
            List<Gameplay> wins = loadTask.getValue();
            Platform.runLater(() -> {
                leaderboardContent.getChildren().clear();
                if(wins == null || wins.isEmpty()) {
                    Label emptyLabel = new Label(LocaleManager.getString("roulette.leaderboard.empty"));
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
        HBox card = new HBox();
        card.getStyleClass().add("leaderboard-card");
        ImageView icon = new ImageView();
        try {
            Image profileImg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/profile_icon_white.png")));
            icon.setImage(profileImg);
        } catch (Exception e) { LOGGER.warning("Failed to load profile_icon_white.png for leaderboard"); }
        icon.setFitHeight(20); icon.setFitWidth(20);
        Label usernameLabel = new Label(win.getUsername() != null ? win.getUsername() : "Unknown");
        usernameLabel.getStyleClass().add("leaderboard-username");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label amountLabel = new Label(createCurrencyFormatter().format(win.getPayoutAmount()) + " €");
        amountLabel.getStyleClass().add("leaderboard-amount");
        card.getChildren().addAll(icon, usernameLabel, spacer, amountLabel);
        return card;
    }

    @FXML
    private void handleLeaderboardButton(ActionEvent event) {
        LOGGER.info("Navigate to Leaderboards");
        navigateTo(event, "/sk/vava/royalmate/view/leaderboard-view.fxml");
    }

    private NumberFormat createCurrencyFormatter() {
        NumberFormat currencyFormatter = NumberFormat.getNumberInstance(LocaleManager.getCurrentLocale());
        currencyFormatter.setMinimumFractionDigits(2);
        currencyFormatter.setMaximumFractionDigits(2);
        return currencyFormatter;
    }

    private void navigateTo(ActionEvent event, String fxmlPath) {
        Node source = (Node) event.getSource();
        try {
            Scene scene = source.getScene();
            if (scene == null) {
                scene = rootPane.getScene();
                if (scene == null) { LOGGER.severe("Could not get current scene."); return; }
            }
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource(fxmlPath)), LocaleManager.getBundle());
            Parent nextRoot = loader.load();
            scene.setRoot(nextRoot);
            LOGGER.info("Successfully navigated to: " + fxmlPath);
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to load FXML: " + fxmlPath, e);
        } catch (ClassCastException e) {
            LOGGER.log(Level.SEVERE, "Failed to cast event source to Node.", e);
        }
    }

    public void cleanup() {
        if (flashAnimationTimeline != null) flashAnimationTimeline.stop();
        if (leaderboardRefreshTimeline != null) leaderboardRefreshTimeline.stop();
        LOGGER.info("RouletteGameController cleaned up timers.");
    }
}