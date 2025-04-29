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
import javafx.scene.effect.DropShadow; // Import DropShadow
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import sk.vava.royalmate.model.Account;
import sk.vava.royalmate.model.Game;
import sk.vava.royalmate.model.GameAsset;
import sk.vava.royalmate.model.Gameplay;
import sk.vava.royalmate.service.GameService;
import sk.vava.royalmate.util.ImageUtil;
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

public class SlotGameController {

    private static final Logger LOGGER = Logger.getLogger(SlotGameController.class.getName());

    // --- FXML Injections ---
    @FXML private BorderPane rootPane;
    @FXML private Label gameTitleLabel;
    @FXML private VBox gameAreaVBox;
    @FXML private StackPane slotMachinePane;
    @FXML private Rectangle gameBackgroundRect;
    @FXML private VBox slotGridContainer; // VBox holding grid and controls
    @FXML private GridPane slotGrid;
    @FXML private HBox betControlsBox;
    @FXML private Label currentStakeLabel;
    @FXML private Label recentWinLossLabel;
    @FXML private Button decreaseStakeButton;
    @FXML private Button increaseStakeButton;
    @FXML private Button spinButton;
    @FXML private VBox leaderboardArea;
    @FXML private VBox leaderboardContent;
    @FXML private Button leaderboardButton;
    @FXML private VBox gameInfoPane;
    @FXML private Label descriptionLabel;
    @FXML private Label minStakeLabel;
    @FXML private Label maxStakeLabel;
    @FXML private Label volatilityLabel;

    // --- Constants & Services ---
    private static final int GRID_SIZE = 3;
    private static final Duration SYMBOL_FLASH_DURATION = Duration.millis(80);
    private static final Duration SPIN_ANIMATION_DURATION = Duration.seconds(2);
    private static final Duration LEADERBOARD_REFRESH_INTERVAL = Duration.seconds(30);
    private static final Duration BLINK_INTERVAL = Duration.millis(500);
    private static final int LEADERBOARD_LIMIT = 10;


    // Stakes filtered dynamically now
    private final List<BigDecimal> BASE_ALLOWED_STAKES = List.of(
            new BigDecimal("0.10"), new BigDecimal("0.20"), new BigDecimal("0.50"), new BigDecimal("1.00"),
            new BigDecimal("2.00"), new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("20.00"),
            new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("200.00"), new BigDecimal("500.00"),
            new BigDecimal("1000.00"), new BigDecimal("2000.00")
    );
    private List<BigDecimal> availableStakes = new ArrayList<>();
    private int currentStakeIndex = 0;

    // Payout multipliers based on symbol index (0=common -> 5=max win)
    private final Map<Integer, BigDecimal> PAYOUT_MULTIPLIERS = Map.of(
            0, new BigDecimal("1.5"), 1, new BigDecimal("2.0"), 2, new BigDecimal("2.5"), // Common
            3, new BigDecimal("8.0"), 4, new BigDecimal("15.0"),                         // Rare
            5, new BigDecimal("100.0")                                                    // Max Win
    );

    // --- State & Services ---
    private final GameService gameService;
    private Game currentGame;
    private List<GameAsset> symbolAssets; // Sorted by ID (rarity)
    private final ImageView[][] symbolImageViews = new ImageView[GRID_SIZE][GRID_SIZE];
    private final Rectangle[][] symbolBackgrounds = new Rectangle[GRID_SIZE][GRID_SIZE];
    private final List<Position> winningLineCoords = new ArrayList<>(); // Stores coords of currently highlighted cells
    private final Random random = new Random();
    private Timeline spinAnimationTimeline;
    private Timeline leaderboardRefreshTimeline;
    private Timeline blinkTimeline; // For blinking effect
    private boolean isSpinning = false;
    private Account currentUser;


    public SlotGameController() {
        this.gameService = new GameService();
    }

    // --- Initialization & Data Loading ---

    /** Called by navigation to pass game data */
    public void initData(Game game, List<GameAsset> symbols) {
        this.currentGame = Objects.requireNonNull(game, "Game cannot be null");
        this.symbolAssets = Objects.requireNonNull(symbols, "Symbols cannot be null");
        this.symbolAssets.sort(Comparator.comparingInt(GameAsset::getId));

        if (this.symbolAssets.isEmpty()) {
            handleInitializationError("Missing symbol assets."); return;
        }
        if(PAYOUT_MULTIPLIERS.size() < this.symbolAssets.size()){
            LOGGER.warning("Payout multiplier map size ("+PAYOUT_MULTIPLIERS.size()+") is less than number of symbols ("+this.symbolAssets.size()+"). Some symbols may have 0 payout.");
        }

        Platform.runLater(this::populateUI);
    }

    @FXML
    public void initialize() {
        currentUser = SessionManager.getCurrentAccount();
        if (currentUser == null) {
            LOGGER.severe("Slot game loaded without user session!"); return;
        }
        recentWinLossLabel.setText("");
        recentWinLossLabel.setEffect(null); // Clear effect initially
        createSlotGridCells();
        // Start leaderboard refresh polling *after* currentGame is set in initData/populateUI
    }

    /** Populates UI elements once game data is available */
    private void populateUI() {
        if (currentGame == null || symbolAssets == null) return;

        gameTitleLabel.setText(currentGame.getName());
        descriptionLabel.setText(currentGame.getDescription());
        try {
            // Set background rectangle color
            gameBackgroundRect.setFill(Color.web(currentGame.getBackgroundColor(), 0.8));
            // Dynamically resize background rectangle to fit grid+controls
            Platform.runLater(() -> { // Run later ensures layout pass has happened
                // Get bounds relative to the parent StackPane of the background rectangle
                Bounds gridContainerBounds = slotGridContainer.getBoundsInParent();
                // Add padding visually desired around the grid+controls
                double padding = 30.0;
                gameBackgroundRect.setWidth(gridContainerBounds.getWidth() + padding);
                gameBackgroundRect.setHeight(gridContainerBounds.getHeight() + padding);
                // Ensure it stays centered if StackPane alignment is CENTER
            });

        } catch (Exception e) {
            LOGGER.warning("Invalid background color hex: " + currentGame.getBackgroundColor() + ", using default.");
            gameBackgroundRect.setFill(Color.web("#222222", 0.8));
        }

        // Info Labels
        NumberFormat currencyFormatter = createCurrencyFormatter();
        minStakeLabel.setText(LocaleManager.getString("slot.label.minstake") + " " + currencyFormatter.format(currentGame.getMinStake()) + " €");
        maxStakeLabel.setText(LocaleManager.getString("slot.label.maxstake") + " " + currencyFormatter.format(currentGame.getMaxStake()) + " €");
        volatilityLabel.setText(LocaleManager.getString("slot.label.volatility") + " " + currentGame.getVolatility() + "/5");

        setupStakeControls();
        setInitialGridImages();
        startLeaderboardRefresh(); // Start refresh now

        LOGGER.info("Slot game UI populated for: " + currentGame.getName());
    }

    private void handleInitializationError(String message) {
        LOGGER.severe("Cannot initialize slot game: " + message);
        Platform.runLater(() -> {
            Alert error = new Alert(Alert.AlertType.ERROR, "Game cannot be loaded: " + message);
            error.showAndWait();
            if(spinButton != null) spinButton.setDisable(true);
            if(decreaseStakeButton != null) decreaseStakeButton.setDisable(true);
            if(increaseStakeButton != null) increaseStakeButton.setDisable(true);
        });
    }

    /** Creates the ImageView and Background Rectangle for each grid cell */
    private void createSlotGridCells() {
        slotGrid.getChildren().clear();
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                ImageView imageView = new ImageView();
                // Make symbols slightly larger
                imageView.setFitHeight(90);
                imageView.setFitWidth(90);
                imageView.setPreserveRatio(true);
                symbolImageViews[row][col] = imageView;

                // Make background rectangle match the symbol size + padding
                Rectangle background = new Rectangle(100, 100);
                background.setArcHeight(10);
                background.setArcWidth(10);
                background.getStyleClass().add("slot-symbol-background");
                symbolBackgrounds[row][col] = background;

                StackPane cellPane = new StackPane(background, imageView);
                cellPane.setAlignment(Pos.CENTER);
                slotGrid.add(cellPane, col, row);
            }
        }
    }

    /** Sets the initial (or placeholder) images in the grid */
    private void setInitialGridImages() {
        Image initialImage = null;
        if (!symbolAssets.isEmpty() && symbolAssets.get(0).getImageData() != null) {
            try {
                initialImage = ImageUtil.byteArrayToImage(symbolAssets.get(0).getImageData());
            } catch (IOException e) { LOGGER.warning("Failed to load initial symbol image."); }
        }

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                symbolImageViews[row][col].setImage(initialImage);
                resetCellHighlight(row, col);
            }
        }
    }

    /** Configures stake buttons and initial stake display */
    private void setupStakeControls() {
        availableStakes = BASE_ALLOWED_STAKES.stream()
                .filter(s -> s.compareTo(currentGame.getMinStake()) >= 0 && s.compareTo(currentGame.getMaxStake()) <= 0)
                .sorted()
                .collect(Collectors.toList());

        if (availableStakes.isEmpty()) {
            LOGGER.severe("No valid stakes available for game " + currentGame.getName() + " within min/max bounds.");
            spinButton.setDisable(true);
            increaseStakeButton.setDisable(true);
            decreaseStakeButton.setDisable(true);
            currentStakeLabel.setText("Stake: N/A");
            return;
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

    @FXML
    private void handleIncreaseStake(ActionEvent event) {
        if (currentStakeIndex < availableStakes.size() - 1) {
            currentStakeIndex++;
            updateStakeDisplay();
            updateSpinButtonState();
        }
    }

    @FXML
    private void handleDecreaseStake(ActionEvent event) {
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

    /** Checks balance and enables/disables spin button */
    private void updateSpinButtonState() {
        if (availableStakes.isEmpty()) {
            spinButton.setDisable(true);
            return;
        }
        BigDecimal currentStake = availableStakes.get(currentStakeIndex);
        Account acc = SessionManager.getCurrentAccount();
        boolean canAfford = acc != null && acc.getBalance() != null && acc.getBalance().compareTo(currentStake) >= 0;
        spinButton.setDisable(isSpinning || !canAfford);

        if(!canAfford && !isSpinning){
            showWinLossMessage(LocaleManager.getString("slot.error.insufficientfunds"), true, false); // No effect
        } else if(!isSpinning) {
            // Clear insufficient funds message if now affordable and not spinning,
            // but only if it's not currently showing a win/loss message from the last spin.
            String insufficientFundsMsg = LocaleManager.getString("slot.error.insufficientfunds");
            if(recentWinLossLabel.getText().equals(insufficientFundsMsg) && winningLineCoords.isEmpty()) {
                // Clear only if no highlights are active (meaning last spin wasn't a win)
                // and the message is the specific insufficient funds one.
                recentWinLossLabel.setText("");
                recentWinLossLabel.setEffect(null);
            }
        }
    }

    // --- Spin Logic ---

    @FXML
    private void handleSpin(ActionEvent event) {
        if (isSpinning || availableStakes.isEmpty()) return;

        BigDecimal currentStake = availableStakes.get(currentStakeIndex);
        Account acc = SessionManager.getCurrentAccount();

        if (acc == null || acc.getBalance() == null || acc.getBalance().compareTo(currentStake) < 0) {
            showWinLossMessage(LocaleManager.getString("slot.error.insufficientfunds"), true, false); // Show msg, no effect
            spinButton.setDisable(true);
            return;
        }

        isSpinning = true;
        spinButton.setDisable(true);
        increaseStakeButton.setDisable(true);
        decreaseStakeButton.setDisable(true);
        clearHighlightsAndWinLoss(); // Clear previous visual results *before* placing bet
        showWinLossMessage(LocaleManager.getString("slot.message.placingbet"), true, true); // Show placing bet as BLACK text, no effect

        // --- 1. Place Bet (Background Task) ---
        Task<Long> placeBetTask = new Task<>() {
            @Override protected Long call() throws Exception {
                return gameService.placeBet(currentUser.getId(), currentGame.getId(), currentStake);
            }
        };

        placeBetTask.setOnSucceeded(workerStateEvent -> {
            long gameplayId = placeBetTask.getValue();
            if (gameplayId < 0) {
                LOGGER.severe("Failed to place bet for game " + currentGame.getName());
                showWinLossMessage(LocaleManager.getString("slot.error.betfailed"), true, true); // Show error red, with effect
                finishSpin(false);
            } else {
                LOGGER.info("Bet placed, gameplayId: " + gameplayId);
                showWinLossMessage(LocaleManager.getString("slot.message.spinning"), true, true); // Show spinning as BLACK text, no effect
                startSpinAnimation(gameplayId, currentStake);
            }
        });

        placeBetTask.setOnFailed(workerStateEvent -> {
            LOGGER.log(Level.SEVERE, "Placing bet task failed.", placeBetTask.getException());
            showWinLossMessage(LocaleManager.getString("slot.error.betfailed"), true, true); // Show error red, with effect
            finishSpin(false);
        });

        new Thread(placeBetTask).start();
    }

    /** Starts the visual spinning animation */
    private void startSpinAnimation(long gameplayId, BigDecimal stakeAmount) {
        if (spinAnimationTimeline != null) {
            spinAnimationTimeline.stop();
        }

        final long startTime = System.currentTimeMillis();
        spinAnimationTimeline = new Timeline();
        spinAnimationTimeline.setCycleCount((int)(SPIN_ANIMATION_DURATION.toMillis() / SYMBOL_FLASH_DURATION.toMillis()));
        spinAnimationTimeline.getKeyFrames().add(new KeyFrame(SYMBOL_FLASH_DURATION, event -> {
            for (int r = 0; r < GRID_SIZE; r++) {
                for (int c = 0; c < GRID_SIZE; c++) {
                    displayRandomSymbol(r, c);
                }
            }
        }));

        spinAnimationTimeline.setOnFinished(event -> {
            LOGGER.fine("Animation finished. Generating final grid.");
            GameAsset[][] finalGrid = generateFinalGrid();
            displayFinalGrid(finalGrid);
            checkAndProcessWins(finalGrid, gameplayId, stakeAmount);
        });

        spinAnimationTimeline.play();
    }

    /** Displays a random symbol image at the given cell */
    private void displayRandomSymbol(int row, int col) {
        if (symbolAssets == null || symbolAssets.isEmpty()) return;
        GameAsset randomSymbol = symbolAssets.get(random.nextInt(symbolAssets.size()));
        try {
            symbolImageViews[row][col].setImage(ImageUtil.byteArrayToImage(randomSymbol.getImageData()));
        } catch (Exception e) { /* Ignore error during fast flashing */ }
    }

    /** Generates the final 3x3 grid based on weighted randomness including volatility */
    private GameAsset[][] generateFinalGrid() {
        GameAsset[][] grid = new GameAsset[GRID_SIZE][GRID_SIZE];
        List<GameAsset> weightedList = createWeightedSymbolList(); // Uses volatility

        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (weightedList.isEmpty()) {
                    grid[r][c] = symbolAssets.get(0); // Fallback
                } else {
                    grid[r][c] = weightedList.get(random.nextInt(weightedList.size()));
                }
            }
        }
        return grid;
    }

    /** Creates a list where rarer symbols appear fewer/more times based on volatility */
    private List<GameAsset> createWeightedSymbolList() {
        List<GameAsset> weightedList = new ArrayList<>();
        // Base weights - adjust these values to get the desired default distribution
        // Ensure this array has at least as many elements as symbolAssets
        int[] baseWeights = {10, 12, 15, 8, 5, 2}; // Example: Slightly more weight in the middle initially

        // Ensure baseWeights covers all symbols, provide default if needed
        int numSymbols = symbolAssets.size();
        if (baseWeights.length < numSymbols) {
            LOGGER.warning("Base weights array length (" + baseWeights.length + ") is less than number of symbols (" + numSymbols + "). Using default weight 1 for remaining symbols.");
            baseWeights = Arrays.copyOf(baseWeights, numSymbols);
            for (int i = baseWeights.length; i < numSymbols; i++) {
                baseWeights[i] = 1; // Default weight for symbols beyond the initial array
            }
        }

        // Volatility: 1 (Low) -> Favors middle
        // Volatility: 3 (Medium) -> Closer to baseWeights
        // Volatility: 5 (High) -> Favors ends (most common and rarest)
        int volatility = currentGame.getVolatility(); // 1-5
        // Map volatility to an adjustment factor (e.g., -2 to +2, or a multiplier)
        // Let's use a factor that scales the *change* applied based on distance from middle
        double volatilityFactor = (volatility - 3.0) * 0.5; // Range approx -1.0 to +1.0 (adjust multiplier 0.5 as needed)

        double middleIndex = (numSymbols - 1.0) / 2.0;

        for (int i = 0; i < numSymbols; i++) {
            int weight = baseWeights[i];
            // Calculate distance from the middle symbol index
            double distanceFromMiddle = Math.abs(i - middleIndex);

            // Calculate adjustment: Positive factor increases weight further from middle, decreases closer to middle.
            // Negative factor does the opposite.
            // We add 1.0 to avoid multiplying by zero if volatilityFactor is 0.
            // We use Math.pow to make the effect stronger further from the middle (e.g., power of 1.5 or 2)
            double adjustmentMultiplier = Math.pow(1.5, distanceFromMiddle * volatilityFactor); // Adjust base 1.5 or power as needed

            // Apply the adjustment, ensuring weight is at least 1
            weight = (int) Math.max(1.0, Math.round(weight * adjustmentMultiplier));

            LOGGER.finest("Symbol index: " + i + ", Base weight: " + baseWeights[i] + ", Volatility: " + volatility + ", Adj Multiplier: " + String.format("%.2f", adjustmentMultiplier) + ", Final weight: " + weight);

            // Add the symbol to the list 'weight' times
            for (int j = 0; j < weight; j++) {
                weightedList.add(symbolAssets.get(i));
            }
        }

        if (weightedList.isEmpty()) {
            LOGGER.severe("Weighted symbol list is empty! This should not happen if there are symbols. Adding fallback.");
            // Add one of each symbol as a fallback to prevent errors
            weightedList.addAll(symbolAssets);
        }

        Collections.shuffle(weightedList); // Shuffle ensures randomness within weights
        return weightedList;
    }


    /** Displays the final grid symbols */
    private void displayFinalGrid(GameAsset[][] finalGrid) {
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                try {
                    symbolImageViews[r][c].setImage(ImageUtil.byteArrayToImage(finalGrid[r][c].getImageData()));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to display final symbol at " + r + "," + c, e);
                    symbolImageViews[r][c].setImage(null);
                }
            }
        }
    }

    /** Checks for winning lines, calculates payout, highlights, records result */
    private void checkAndProcessWins(GameAsset[][] finalGrid, long gameplayId, BigDecimal stakeAmount) {
        BigDecimal totalWin = BigDecimal.ZERO;
        winningLineCoords.clear(); // Use the class member field

        // Define Lines (including diagonals)
        List<List<Position>> linesToCheck = List.of(
                // Horizontals
                List.of(new Position(0,0), new Position(0,1), new Position(0,2)),
                List.of(new Position(1,0), new Position(1,1), new Position(1,2)),
                List.of(new Position(2,0), new Position(2,1), new Position(2,2)),
                // Verticals
                List.of(new Position(0,0), new Position(1,0), new Position(2,0)),
                List.of(new Position(0,1), new Position(1,1), new Position(2,1)),
                List.of(new Position(0,2), new Position(1,2), new Position(2,2)),
                // Diagonals
                List.of(new Position(0,0), new Position(1,1), new Position(2,2)),
                List.of(new Position(0,2), new Position(1,1), new Position(2,0))
        );

        // Check Each Line
        for (List<Position> line : linesToCheck) {
            GameAsset firstSymbol = finalGrid[line.get(0).row][line.get(0).col];
            boolean isWinningLine = true;
            for (int i = 1; i < line.size(); i++) {
                if (finalGrid[line.get(i).row][line.get(i).col].getId() != firstSymbol.getId()) {
                    isWinningLine = false; break;
                }
            }
            if (isWinningLine) {
                int symbolIndex = findSymbolIndexById(firstSymbol.getId());
                if (symbolIndex >= 0 && PAYOUT_MULTIPLIERS.containsKey(symbolIndex)) {
                    BigDecimal multiplier = PAYOUT_MULTIPLIERS.get(symbolIndex);
                    BigDecimal lineWin = stakeAmount.multiply(multiplier).setScale(2, RoundingMode.DOWN);
                    totalWin = totalWin.add(lineWin);
                    // Add unique positions to the class member list
                    line.forEach(pos -> { if (!winningLineCoords.contains(pos)) winningLineCoords.add(pos); });
                    LOGGER.fine("Winning line! Symbol Index: " + symbolIndex + ", Multiplier: " + multiplier + ", Win: " + lineWin);
                } else { LOGGER.warning("Multiplier not found for winning symbol index: " + symbolIndex); }
            }
        }

        // Process Result
        final BigDecimal finalTotalWin = totalWin;
        boolean isWin = finalTotalWin.compareTo(BigDecimal.ZERO) > 0;

        // Display win/loss message PERSISTENTLY with EFFECT
        if (isWin) {
            String winMsg = MessageFormat.format(LocaleManager.getString("slot.message.won"), createCurrencyFormatter().format(finalTotalWin));
            showWinLossMessage(winMsg, false, true); // Green, stays until next spin starts, WITH effect
            startHighlightBlinking(); // Start blinking on win
        } else {
            showWinLossMessage(LocaleManager.getString("slot.message.lost"), true, true); // Red, stays until next spin starts, WITH effect
            stopHighlightBlinking(); // Ensure no blinking on loss
        }

        // Record result (background task)
        String outcomeString = gridToString(finalGrid);
        Task<Boolean> recordTask = new Task<>() {
            @Override protected Boolean call() throws Exception {
                return gameService.recordResult(gameplayId, outcomeString, finalTotalWin, currentUser.getId());
            }
        };
        recordTask.setOnSucceeded(e -> {
            boolean recorded = recordTask.getValue();
            if (!recorded) { LOGGER.severe("Failed to record game result for gameplayId: " + gameplayId); }
            else { LOGGER.info("Game result recorded successfully for gameplayId: " + gameplayId); }
            if (isWin && recorded) { loadRecentWinsLeaderboard(); } // Refresh leaderboard only if won and recorded
            finishSpin(true);
        });
        recordTask.setOnFailed(e -> {
            LOGGER.log(Level.SEVERE, "Recording result task failed.", recordTask.getException());
            // Show error message persistently with effect
            showWinLossMessage(LocaleManager.getString("slot.error.resultfailed"), true, true);
            finishSpin(true);
        });
        new Thread(recordTask).start();
    }

    /** Finds the index of a symbol in the sorted symbolAssets list */
    private int findSymbolIndexById(int symbolId) {
        for(int i=0; i < symbolAssets.size(); i++) {
            if(symbolAssets.get(i).getId() == symbolId) {
                return i;
            }
        }
        return -1;
    }

    /** Simple string representation of the grid for the outcome field */
    private String gridToString(GameAsset[][] grid) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                sb.append(grid[r][c] != null ? grid[r][c].getId() : "N");
                if (c < GRID_SIZE - 1) sb.append(",");
            }
            if (r < GRID_SIZE - 1) sb.append("|");
        }
        return sb.toString();
    }

    /** Starts blinking the winning cells */
    private void startHighlightBlinking() {
        if (winningLineCoords.isEmpty()) return;
        stopHighlightBlinking();

        blinkTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, e -> applyHighlightStyle(true)),
                new KeyFrame(BLINK_INTERVAL, e -> applyHighlightStyle(false)),
                new KeyFrame(BLINK_INTERVAL.multiply(2), e -> {}) // Loop handled by cycle count
        );
        blinkTimeline.setCycleCount(Animation.INDEFINITE);
        blinkTimeline.play();
        LOGGER.fine("Started highlight blinking.");
    }

    /** Stops the blinking animation AND resets the winning cells */
    private void stopHighlightBlinking() {
        if (blinkTimeline != null) {
            blinkTimeline.stop();
            blinkTimeline = null;
            LOGGER.fine("Stopped highlight blinking.");
            // --- FIX: Explicitly reset winning cells AFTER stopping ---
            // This ensures they revert to default even if stopped during the white phase
            resetWinningCellHighlights();
            // -----------------------------------------------------
        }
    }

    /** Resets only the winning cells back to default */
    private void resetWinningCellHighlights() {
        // Iterate through the stored winning coordinates
        winningLineCoords.forEach(pos -> {
            // Check bounds just in case
            if (pos.row >= 0 && pos.row < GRID_SIZE && pos.col >= 0 && pos.col < GRID_SIZE) {
                resetCellHighlight(pos.row, pos.col); // Call the reset helper
            }
        });
        winningLineCoords.clear(); // Clear the list AFTER resetting
    }

    /** Applies gold or white highlight style to winning cells */
    private void applyHighlightStyle(boolean gold) {
        String styleClass = gold ? "slot-symbol-background-win-gold" : "slot-symbol-background-win-white";
        winningLineCoords.forEach(pos -> {
            if (pos.row >= 0 && pos.row < GRID_SIZE && pos.col >= 0 && pos.col < GRID_SIZE) {
                symbolBackgrounds[pos.row][pos.col].getStyleClass().setAll(styleClass);
            }
        });
    }

    /** Clears highlights, stops blinking, and clears the win/loss label */
    private void clearHighlightsAndWinLoss() {
        stopHighlightBlinking(); // This now stops the timer AND resets the cells in winningLineCoords

        // --- FIX: Explicitly reset ALL cells just in case (belt-and-suspenders) ---
        // This handles cases where blinking might have stopped unexpectedly
        // or if we just want a full reset regardless of winningLineCoords state.
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                resetCellHighlight(r, c);
            }
        }
        // Clear the win/loss message and its effect
        Platform.runLater(() -> {
            recentWinLossLabel.setText("");
            recentWinLossLabel.setEffect(null);
        });
        // winningLineCoords should already be cleared by stopHighlightBlinking -> resetWinningCellHighlights
    }

    /** Resets a specific cell's highlight */
    private void resetCellHighlight(int row, int col) {
        symbolBackgrounds[row][col].getStyleClass().setAll("slot-symbol-background");
        symbolBackgrounds[row][col].setEffect(null);
    }

    /** Called after spin sequence completes */
    private void finishSpin(boolean spinExecutedSuccessfully) {
        isSpinning = false;
        increaseStakeButton.setDisable(currentStakeIndex >= availableStakes.size() - 1);
        decreaseStakeButton.setDisable(currentStakeIndex <= 0);
        updateSpinButtonState();
    }

    /** Displays win/loss message PERSISTENTLY with optional color and effect */
    private void showWinLossMessage(String message, boolean isError, boolean applyEffect) {
        Platform.runLater(() -> {
            recentWinLossLabel.setText(message);

            // Set color based on error status or if it's an intermediate message
            if (message.equals(LocaleManager.getString("slot.message.placingbet")) || message.equals(LocaleManager.getString("slot.message.spinning"))) {
                recentWinLossLabel.setTextFill(Color.BLACK); // Intermediate messages are black
            } else {
                recentWinLossLabel.setTextFill(isError ? Color.INDIANRED : Color.GREEN);
            }

            // Apply or clear effect
            if (applyEffect && !message.equals(LocaleManager.getString("slot.message.placingbet")) && !message.equals(LocaleManager.getString("slot.message.spinning"))) {
                Color shadowColor = isError ? Color.INDIANRED : Color.LIGHTGREEN;
                DropShadow ds = new DropShadow();
                ds.setRadius(1.0);
                ds.setOffsetX(0.0);
                ds.setOffsetY(0.0);
                ds.setColor(shadowColor);
                ds.setSpread(0.05); // Subtle spread
                recentWinLossLabel.setEffect(ds);
            } else {
                recentWinLossLabel.setEffect(null); // Remove effect for intermediate messages or no effect requested
            }
        });
    }

    // --- Leaderboard Logic ---
    private void startLeaderboardRefresh() {
        loadRecentWinsLeaderboard(); // Initial load
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
                    Label emptyLabel = new Label(LocaleManager.getString("slot.leaderboard.empty"));
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

    /** Creates a styled HBox card for a leaderboard entry */
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

    // --- Utils ---
    private NumberFormat createCurrencyFormatter() {
        NumberFormat currencyFormatter = NumberFormat.getNumberInstance(LocaleManager.getCurrentLocale());
        currencyFormatter.setMinimumFractionDigits(2);
        currencyFormatter.setMaximumFractionDigits(2);
        return currencyFormatter;
    }

    // --- Navigation Helpers ---
    private void navigateToLogin() {
        if (rootPane == null || rootPane.getScene() == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/sk/vava/royalmate/view/login-view.fxml")), LocaleManager.getBundle());
            Parent nextRoot = loader.load();
            rootPane.getScene().setRoot(nextRoot);
        } catch (IOException | NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Failed to navigate back to login screen.", e);
        }
    }

    // Helper class for grid positions
    private record Position(int row, int col) {}

    // --- Stop Timers on Exit ---
    public void cleanup() {
        if (spinAnimationTimeline != null) spinAnimationTimeline.stop();
        if (leaderboardRefreshTimeline != null) leaderboardRefreshTimeline.stop();
        if (blinkTimeline != null) blinkTimeline.stop();
        LOGGER.info("SlotGameController cleaned up timers.");
    }
}