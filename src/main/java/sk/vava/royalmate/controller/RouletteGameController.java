package sk.vava.royalmate.controller;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds; // Keep for background resize logic
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
import javafx.scene.control.ToggleGroup; // Still used indirectly by logic, keep import
import javafx.scene.effect.DropShadow; // Keep for effects
import javafx.scene.image.Image; // Keep for icons etc.
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font; // Keep for FXML reference if needed
import javafx.scene.text.FontWeight; // Keep for FXML reference if needed
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

    // --- FXML Injections ---
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

    // Bet Toggle Buttons
    @FXML private ToggleButton redBetButton;
    @FXML private ToggleButton blackBetButton;
    @FXML private ToggleButton greenBetButton;
    @FXML private ToggleButton range1BetButton; // 1-9
    @FXML private ToggleButton range2BetButton; // 10-18
    @FXML private ToggleButton range3BetButton; // 19-27
    @FXML private ToggleButton range4BetButton; // 28-36

    // --- Constants & Services ---
    private static final Duration FLASH_DURATION = Duration.millis(125);
    private static final Duration FLASH_TOTAL_DURATION = Duration.seconds(2);
    private static final Duration LEADERBOARD_REFRESH_INTERVAL = Duration.seconds(30);

    // Payout Multipliers
    private static final BigDecimal COLOR_PAYOUT_MULTIPLIER = new BigDecimal("2.00");
    private static final BigDecimal RANGE_PAYOUT_MULTIPLIER = new BigDecimal("4.00");
    private static final BigDecimal GREEN_PAYOUT_MULTIPLIER = new BigDecimal("36.00");

    // Base Stakes
    private final List<BigDecimal> BASE_ALLOWED_STAKES = List.of(
            new BigDecimal("0.10"), new BigDecimal("0.20"), new BigDecimal("0.50"), new BigDecimal("1.00"),
            new BigDecimal("2.00"), new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("20.00"),
            new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("200.00"), new BigDecimal("500.00"),
            new BigDecimal("1000.00"), new BigDecimal("2000.00") // Extended list
    );
    private List<BigDecimal> availableStakes = new ArrayList<>();
    private int currentStakeIndex = 0;

    // Roulette Numbers and Colors
    private static final Color COLOR_RED = Color.web("#F55231");
    private static final Color COLOR_BLACK = Color.web("#434343");
    private static final Color COLOR_GREEN = Color.web("#67A832");
    private static final Color COLOR_NUMBER = Color.web("#FFFFFF");

    // --- State & Services ---
    private final GameService gameService;
    private Game currentGame;
    private final Random random = new Random();
    private Timeline flashAnimationTimeline;
    private Timeline leaderboardRefreshTimeline;
    private boolean isSpinning = false;
    private Account currentUser;
    private Set<BetType> selectedBetTypes = new HashSet<>();

    // Enum for Bet Types
   private enum BetType { RED, BLACK, GREEN, RANGE_1_9, RANGE_10_18, RANGE_19_27, RANGE_28_36 }

    public RouletteGameController() {
        this.gameService = new GameService();
    }

    // --- Initialization & Data Loading ---

    /** Called by navigation to pass game data */
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

    /** Populates UI elements once game data is available */
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

    /** Configures bet toggle buttons and their listeners */
    private void setupBetToggles() {
        redBetButton.setOnAction(e -> handleBetToggle(BetType.RED, redBetButton.isSelected()));
        blackBetButton.setOnAction(e -> handleBetToggle(BetType.BLACK, blackBetButton.isSelected()));
        greenBetButton.setOnAction(e -> handleBetToggle(BetType.GREEN, greenBetButton.isSelected()));
        range1BetButton.setOnAction(e -> handleBetToggle(BetType.RANGE_1_9, range1BetButton.isSelected()));
        range2BetButton.setOnAction(e -> handleBetToggle(BetType.RANGE_10_18, range2BetButton.isSelected()));
        range3BetButton.setOnAction(e -> handleBetToggle(BetType.RANGE_19_27, range3BetButton.isSelected()));
        range4BetButton.setOnAction(e -> handleBetToggle(BetType.RANGE_28_36, range4BetButton.isSelected()));

        // Set default bet (Red)
        redBetButton.setSelected(true);
        handleBetToggle(BetType.RED, true);
    }

    /** Handles bet toggle selection logic */
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
                // Allow multiple ranges along with a color
            }
        } else {
            selectedBetTypes.remove(type);
        }
        LOGGER.fine("Selected bets updated: " + selectedBetTypes);
    }

    /** Helper to deselect all bet buttons except the one passed */
    private void deselectOtherButtons(ToggleButton keepSelected) {
        if (redBetButton != keepSelected) redBetButton.setSelected(false);
        if (blackBetButton != keepSelected) blackBetButton.setSelected(false);
        if (greenBetButton != keepSelected) greenBetButton.setSelected(false);
        if (range1BetButton != keepSelected) range1BetButton.setSelected(false);
        if (range2BetButton != keepSelected) range2BetButton.setSelected(false);
        if (range3BetButton != keepSelected) range3BetButton.setSelected(false);
        if (range4BetButton != keepSelected) range4BetButton.setSelected(false);
    }

    /** Configures stake buttons and initial stake display */
    private void setupStakeControls() {
        // Filter the extended BASE list based on game min/max
        availableStakes = BASE_ALLOWED_STAKES.stream()
                .filter(s -> s.compareTo(currentGame.getMinStake()) >= 0 && s.compareTo(currentGame.getMaxStake()) <= 0)
                .sorted()
                .collect(Collectors.toList());

        if (availableStakes.isEmpty()) {
            LOGGER.severe("No valid stakes available for game " + currentGame.getName() + " within min/max bounds.");
            actionButton.setDisable(true); increaseStakeButton.setDisable(true); decreaseStakeButton.setDisable(true);
            currentStakeLabel.setText("Stake: N/A"); return;
        }
        // Set initial stake index to the lowest available valid stake
        currentStakeIndex = 0;
        // Find the first stake >= minStake (already filtered, so index 0 is fine if list not empty)
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
            updateSpinButtonState(); // Check if spin button should be enabled/disabled
        }
    }
    @FXML private void handleDecreaseStake(ActionEvent event) {
        if (currentStakeIndex > 0) {
            currentStakeIndex--;
            updateStakeDisplay();
            updateSpinButtonState(); // Check if spin button should be enabled/disabled
        }
    }

    private void updateStakeDisplay() {
        if (!availableStakes.isEmpty()) {
            BigDecimal currentStake = availableStakes.get(currentStakeIndex);
            // Reusing slot key for consistency, update if needed
            currentStakeLabel.setText(LocaleManager.getString("slot.label.currentstake") + " " + createCurrencyFormatter().format(currentStake) + " €");
        }
        decreaseStakeButton.setDisable(currentStakeIndex <= 0);
        increaseStakeButton.setDisable(currentStakeIndex >= availableStakes.size() - 1);
    }

    /** Checks balance and enables/disables spin button */
    private void updateSpinButtonState() {
        if (availableStakes.isEmpty()) { actionButton.setDisable(true); return; }
        BigDecimal currentStake = availableStakes.get(currentStakeIndex);
        Account acc = SessionManager.getCurrentAccount();
        boolean canAfford = acc != null && acc.getBalance() != null && acc.getBalance().compareTo(currentStake) >= 0;
        actionButton.setDisable(isSpinning || !canAfford);

        String insufficientFundsMsg = LocaleManager.getString("slot.error.insufficientfunds");
        if (!canAfford && !isSpinning) {
            showWinLossMessage(insufficientFundsMsg, true, false); // Show error, no effect
        } else if (!isSpinning && recentWinLossLabel.getText().equals(insufficientFundsMsg)) {
            // Clear insufficient funds message ONLY if it was the last message shown
            recentWinLossLabel.setText("");
            recentWinLossLabel.setEffect(null);
        }
    }

    // --- Spin Logic ---

    @FXML
    private void handleSpin(ActionEvent event) {
        if (isSpinning || availableStakes.isEmpty()) return;

        // Validate Bets
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

        showWinLossMessage(LocaleManager.getString("slot.message.placingbet"), false, false); // Black text, no effect

        // Place Bet (Background Task)
        Task<Long> placeBetTask = new Task<>() {
            @Override protected Long call() throws Exception {
                return gameService.placeBet(currentUser.getId(), currentGame.getId(), currentStake);
            }
        };
        placeBetTask.setOnSucceeded(workerStateEvent -> {
            long gameplayId = placeBetTask.getValue();
            if (gameplayId < 0) { finishSpin(false, "slot.error.betfailed"); }
            else {
                showWinLossMessage(LocaleManager.getString("slot.message.spinning"), false, false); // Black text, no effect
                startSpinAnimation(gameplayId, currentStake);
            }
        });
        placeBetTask.setOnFailed(workerStateEvent -> {
            LOGGER.log(Level.SEVERE, "Placing bet task failed.", placeBetTask.getException());
            finishSpin(false, "slot.error.betfailed");
        });
        new Thread(placeBetTask).start();
    }

    /** Starts the flashing result animation */
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

    /** Generates the final winning number, considering volatility for 0 */
    private int generateWinningNumber() {
        int volatility = currentGame.getVolatility();
        double zeroChance = 0.01 + (volatility - 1) * 0.01;
        if (random.nextDouble() < zeroChance) { return 0; }
        else { return random.nextInt(36) + 1; }
    }

    /** Determines the color of a roulette number */
    private Color getNumberColor(int number) {
        if (number == 0) return COLOR_GREEN;
        if ((number >= 1 && number <= 10) || (number >= 19 && number <= 28)) {
            return (number % 2 != 0) ? COLOR_RED : COLOR_BLACK;
        } else {
            return (number % 2 != 0) ? COLOR_BLACK : COLOR_RED;
        }
    }

    /** Gets a simple string name for the color */
    private String getColorName(Color color) {
        if (color.equals(COLOR_RED)) return "Red";
        if (color.equals(COLOR_BLACK)) return "Black";
        if (color.equals(COLOR_GREEN)) return "Green";
        return "Unknown";
    }

    /** Checks if a number falls within a specific range BetType */
    private boolean isInRange(int number, BetType rangeType) {
        return switch (rangeType) {
            case RANGE_1_9 -> number >= 1 && number <= 9;
            case RANGE_10_18 -> number >= 10 && number <= 18;
            case RANGE_19_27 -> number >= 19 && number <= 27;
            case RANGE_28_36 -> number >= 28 && number <= 36;
            default -> false;
        };
    }

    /** Updates the visual display of the result circle and number */
    private void updateResultDisplay(int number, Color color, boolean isFlashing) {
        resultDisplayPane.setVisible(true);
        resultCircle.setFill(color);
        resultNumberLabel.setText(String.valueOf(number));
        resultNumberLabel.setTextFill(number == 0 ? Color.WHITE : COLOR_NUMBER);
        resultDisplayPane.setOpacity(isFlashing ? (0.6 + random.nextDouble() * 0.4) : 1.0);
    }

    /** Calculates payout based on combined bets (specific rules), shows message, records result */
    private void processResult(int winningNumber, String winningColorName, long gameplayId, BigDecimal stakeAmount) {
        BigDecimal totalPayout = BigDecimal.ZERO; // Start with zero payout
        boolean betWon = false;
        BigDecimal calculatedMultiplier = BigDecimal.ZERO; // Start multiplier at 0 (no win yet)

        // --- Count selected ranges ---
        long selectedRangeCount = selectedBetTypes.stream()
                .filter(bt -> bt == BetType.RANGE_1_9 || bt == BetType.RANGE_10_18 ||
                        bt == BetType.RANGE_19_27 || bt == BetType.RANGE_28_36)
                .count();

        // --- Logic for GREEN Bet ---
        if (selectedBetTypes.contains(BetType.GREEN)) {
            if (winningNumber == 0 && selectedBetTypes.size() == 1) { // Must be ONLY green bet
                calculatedMultiplier = GREEN_PAYOUT_MULTIPLIER; // x36
                betWon = true;
                LOGGER.fine("Winning bet type: GREEN");
            }
            // If Green selected with others, or number isn't 0, it's a loss.
        }
        // --- Logic for RED/BLACK and RANGE Bets ---
        else if (!selectedBetTypes.isEmpty()) { // Process only if non-green bets were selected
            boolean colorBetPlaced = selectedBetTypes.contains(BetType.RED) || selectedBetTypes.contains(BetType.BLACK);
            boolean rangeBetPlaced = selectedRangeCount > 0;

            // Check if the winning number satisfies the PLACED color bet (if any)
            boolean colorConditionMet = !colorBetPlaced ||
                    (selectedBetTypes.contains(BetType.RED) && winningColorName.equals("Red")) ||
                    (selectedBetTypes.contains(BetType.BLACK) && winningColorName.equals("Black"));

            // Check if the winning number satisfies AT LEAST ONE of the PLACED range bets (if any)
            boolean rangeConditionMet = !rangeBetPlaced ||
                    (selectedBetTypes.contains(BetType.RANGE_1_9) && isInRange(winningNumber, BetType.RANGE_1_9)) ||
                    (selectedBetTypes.contains(BetType.RANGE_10_18) && isInRange(winningNumber, BetType.RANGE_10_18)) ||
                    (selectedBetTypes.contains(BetType.RANGE_19_27) && isInRange(winningNumber, BetType.RANGE_19_27)) ||
                    (selectedBetTypes.contains(BetType.RANGE_28_36) && isInRange(winningNumber, BetType.RANGE_28_36));

            // Determine Win and Multiplier
            if (colorConditionMet && rangeConditionMet) {
                // Win only if BOTH conditions met for the bets actually placed.

                if (colorBetPlaced && rangeBetPlaced) {
                    // Both Color AND Range(s) were placed and matched
                    betWon = true;
                    calculatedMultiplier = switch ((int) selectedRangeCount) {
                        case 1 -> new BigDecimal("8.00"); // COLOR + 1 RANGE = x8
                        case 2 -> new BigDecimal("4.00"); // COLOR + 2 RANGES = x4
                        case 3 -> new BigDecimal("3.00"); // COLOR + 3 RANGES = x3
                        case 4 -> new BigDecimal("2.00"); // COLOR + 4 RANGES = x2 (same as just color)
                        default -> BigDecimal.ZERO;
                    };
                    LOGGER.fine("Winning combined Color + Range. Ranges selected: " + selectedRangeCount + ", Multiplier: " + calculatedMultiplier);

                } else if (colorBetPlaced && !rangeBetPlaced) {
                    // Only Color bet placed and matched
                    betWon = true;
                    calculatedMultiplier = COLOR_PAYOUT_MULTIPLIER; // x2
                    LOGGER.fine("Winning bet type: Color Only. Multiplier: " + calculatedMultiplier);

                } else if (!colorBetPlaced && rangeBetPlaced) {
                    // Only Range bet(s) placed and matched
                    betWon = true;
                    calculatedMultiplier = switch ((int) selectedRangeCount) {
                        case 1 -> new BigDecimal("4.00");  // 1 RANGE ONLY = x4
                        case 2 -> new BigDecimal("2.00");  // 2 RANGES ONLY = x2
                        case 3 -> new BigDecimal("1.50");  // 3 RANGES ONLY = x1.5
                        case 4 -> new BigDecimal("1.00");  // 4 RANGES ONLY = x1 (stake back)
                        default -> BigDecimal.ZERO;
                    };
                    LOGGER.fine("Winning bet type: Range Only. Ranges selected: " + selectedRangeCount + ", Multiplier: " + calculatedMultiplier);
                }
            }
        }

        // Calculate final payout (Stake * Multiplier = Total Return)
        // If multiplier is 1.0, payout = stake. If 0, payout = 0.
        totalPayout = stakeAmount.multiply(calculatedMultiplier).setScale(2, RoundingMode.DOWN);

        // Determine if it was actually profitable win (multiplier > 1)
        boolean isProfitableWin = betWon && calculatedMultiplier.compareTo(BigDecimal.ONE) > 0;


        // Display Win/Loss Message - UPDATED FORMAT
        String formattedResultMessage;
        if (isProfitableWin) { // Show win message only if payout > stake
            formattedResultMessage = MessageFormat.format(
                    LocaleManager.getString("roulette.message.won"),
                    createCurrencyFormatter().format(totalPayout) // Show total amount returned
            );
            showWinLossMessage(formattedResultMessage, false, true); // Green with effect
        } else { // Loss or stake back (multiplier <= 1)
            formattedResultMessage = LocaleManager.getString("roulette.message.lost");
            showWinLossMessage(formattedResultMessage, true, true); // Red with effect (even for stake back)
        }

        // Record result (background task)
        String outcomeString = winningNumber + " (" + winningColorName + ")";
        final BigDecimal finalTotalPayout = totalPayout; // Record the total return amount
        Task<Boolean> recordTask = new Task<>() {
            @Override protected Boolean call() throws Exception {
                // Pass the calculated total return to the service
                return gameService.recordResult(gameplayId, outcomeString, finalTotalPayout, currentUser.getId());
            }
        };
        boolean finalIsProfitableWin = isProfitableWin; // Final copy for lambda
        recordTask.setOnSucceeded(e -> {
            boolean recorded = recordTask.getValue();
            if (!recorded) { LOGGER.severe("Failed to record game result for gameplayId: " + gameplayId); }
            else { LOGGER.info("Game result recorded successfully for gameplayId: " + gameplayId); }
            // Refresh leaderboard only on profitable wins that were recorded
            if (finalIsProfitableWin && recorded) { loadRecentWinsLeaderboard(); }
            finishSpin(true, null); // Re-enable controls after result recorded
        });
        recordTask.setOnFailed(e -> {
            LOGGER.log(Level.SEVERE, "Recording result task failed.", recordTask.getException());
            showWinLossMessage(LocaleManager.getString("slot.error.resultfailed"), true, true); // Generic error with effect
            finishSpin(true, null); // Still finish spin, but log error
        });
        new Thread(recordTask).start();
    }


    /** Called after spin sequence completes or fails */
    private void finishSpin(boolean betPlacedSuccessfully, String messageKeyIfFailed) {
        isSpinning = false;
        if (betPlacedSuccessfully) {
            increaseStakeButton.setDisable(currentStakeIndex >= availableStakes.size() - 1);
            decreaseStakeButton.setDisable(currentStakeIndex <= 0);
            disableBetToggles(false);
            updateSpinButtonState();
        } else if (messageKeyIfFailed != null){
            showWinLossMessage(LocaleManager.getString(messageKeyIfFailed), true, true); // Show error with effect
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

    /** Enables/disables all bet toggle buttons */
    private void disableBetToggles(boolean disable) {
        redBetButton.setDisable(disable);
        blackBetButton.setDisable(disable);
        greenBetButton.setDisable(disable);
        range1BetButton.setDisable(disable);
        range2BetButton.setDisable(disable);
        range3BetButton.setDisable(disable);
        range4BetButton.setDisable(disable);
    }

    /** Displays win/loss message PERSISTENTLY with optional effect */
    private void showWinLossMessage(String message, boolean isError, boolean applyEffect) {
        Platform.runLater(() -> {
            recentWinLossLabel.setText(message);

            // Determine color: Intermediate messages (Placing Bet, Spinning) are Black
            Color textColor;
            if (message.equals(LocaleManager.getString("slot.message.placingbet")) || message.equals(LocaleManager.getString("slot.message.spinning"))) {
                textColor = Color.BLACK;
            } else {
                textColor = isError ? Color.INDIANRED : Color.GREEN;
            }
            recentWinLossLabel.setTextFill(textColor);

            // Apply effect only for final win/loss/error messages, not intermediate ones
            if (applyEffect && textColor != Color.BLACK) {
                Color shadowColor = isError ? Color.INDIANRED : Color.LIGHTGREEN;
                DropShadow ds = new DropShadow(1.0, 0.0, 0.0, shadowColor); // Simplified constructor
                ds.setSpread(0.05);
                recentWinLossLabel.setEffect(ds);
            } else {
                recentWinLossLabel.setEffect(null);
            }
        });
    }
    /** Displays win/loss message PERSISTENTLY without effect */
    private void showWinLossMessage(String message, boolean isError) {
        showWinLossMessage(message, isError, false); // Call overload with effect=false
    }


    // --- Leaderboard Logic ---
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
        navigateTo(event, "/sk/vava/royalmate/view/leaderboard-view.fxml"); // Navigate to leaderboard view
    }

    // --- Utils & Navigation ---
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

    // --- Cleanup ---
    public void cleanup() {
        if (flashAnimationTimeline != null) flashAnimationTimeline.stop();
        if (leaderboardRefreshTimeline != null) leaderboardRefreshTimeline.stop();
        LOGGER.info("RouletteGameController cleaned up timers.");
    }
}