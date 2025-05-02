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
import javafx.scene.effect.DropShadow;
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

    @FXML private BorderPane rootPane;
    @FXML private Label gameTitleLabel;
    @FXML private VBox gameAreaVBox;
    @FXML private StackPane slotMachinePane;
    @FXML private Rectangle gameBackgroundRect;
    @FXML private VBox slotGridContainer;
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

    private static final int GRID_SIZE = 3;
    private static final Duration SYMBOL_FLASH_DURATION = Duration.millis(80);
    private static final Duration SPIN_ANIMATION_DURATION = Duration.seconds(1.5);
    private static final Duration LEADERBOARD_REFRESH_INTERVAL = Duration.seconds(60);
    private static final Duration BLINK_INTERVAL = Duration.millis(500);
    private static final int LEADERBOARD_LIMIT = 10;

    private final List<BigDecimal> BASE_ALLOWED_STAKES = List.of(
            new BigDecimal("0.10"), new BigDecimal("0.20"), new BigDecimal("0.50"), new BigDecimal("1.00"),
            new BigDecimal("2.00"), new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("20.00"),
            new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("200.00"), new BigDecimal("500.00"),
            new BigDecimal("1000.00"), new BigDecimal("2000.00")
    );
    private List<BigDecimal> availableStakes = new ArrayList<>();
    private int currentStakeIndex = 0;

    private final Map<Integer, BigDecimal> PAYOUT_MULTIPLIERS = Map.of(
            0, new BigDecimal("1.5"), 1, new BigDecimal("2.0"), 2, new BigDecimal("2.5"),
            3, new BigDecimal("8.0"), 4, new BigDecimal("15.0"),
            5, new BigDecimal("100.0")
    );

    private final GameService gameService;
    private Game currentGame;
    private List<GameAsset> symbolAssets;
    private final ImageView[][] symbolImageViews = new ImageView[GRID_SIZE][GRID_SIZE];
    private final Rectangle[][] symbolBackgrounds = new Rectangle[GRID_SIZE][GRID_SIZE];
    private final List<Position> winningLineCoords = new ArrayList<>();
    private final Random random = new Random();
    private Timeline spinAnimationTimeline;
    private Timeline leaderboardRefreshTimeline;
    private Timeline blinkTimeline;
    private boolean isSpinning = false;
    private Account currentUser;

    public SlotGameController() {
        this.gameService = new GameService();
    }

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
        recentWinLossLabel.setEffect(null);
        createSlotGridCells();

    }

    private void populateUI() {
        if (currentGame == null || symbolAssets == null) return;

        gameTitleLabel.setText(currentGame.getName());
        descriptionLabel.setText(currentGame.getDescription());
        try {

            gameBackgroundRect.setFill(Color.web(currentGame.getBackgroundColor(), 0.8));

            Platform.runLater(() -> {

                Bounds gridContainerBounds = slotGridContainer.getBoundsInParent();

                double padding = 30.0;
                gameBackgroundRect.setWidth(gridContainerBounds.getWidth() + padding);
                gameBackgroundRect.setHeight(gridContainerBounds.getHeight() + padding);

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
        setInitialGridImages();
        startLeaderboardRefresh();

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

    private void createSlotGridCells() {
        slotGrid.getChildren().clear();
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                ImageView imageView = new ImageView();

                imageView.setFitHeight(90);
                imageView.setFitWidth(90);
                imageView.setPreserveRatio(true);
                symbolImageViews[row][col] = imageView;

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
            showWinLossMessage(LocaleManager.getString("slot.error.insufficientfunds"), true, false);
        } else if(!isSpinning) {

            String insufficientFundsMsg = LocaleManager.getString("slot.error.insufficientfunds");
            if(recentWinLossLabel.getText().equals(insufficientFundsMsg) && winningLineCoords.isEmpty()) {

                recentWinLossLabel.setText("");
                recentWinLossLabel.setEffect(null);
            }
        }
    }

    @FXML
    private void handleSpin(ActionEvent event) {
        if (isSpinning || availableStakes.isEmpty()) return;

        BigDecimal currentStake = availableStakes.get(currentStakeIndex);
        Account acc = SessionManager.getCurrentAccount();

        if (acc == null || acc.getBalance() == null || acc.getBalance().compareTo(currentStake) < 0) {
            showWinLossMessage(LocaleManager.getString("slot.error.insufficientfunds"), true, false);
            spinButton.setDisable(true);
            return;
        }

        isSpinning = true;
        spinButton.setDisable(true);
        increaseStakeButton.setDisable(true);
        decreaseStakeButton.setDisable(true);
        clearHighlightsAndWinLoss();
        showWinLossMessage(LocaleManager.getString("slot.message.placingbet"), true, true);

        Task<Long> placeBetTask = new Task<>() {
            @Override protected Long call() throws Exception {
                return gameService.placeBet(currentUser.getId(), currentGame.getId(), currentStake);
            }
        };

        placeBetTask.setOnSucceeded(workerStateEvent -> {
            long gameplayId = placeBetTask.getValue();
            if (gameplayId < 0) {
                LOGGER.severe("Failed to place bet for game " + currentGame.getName());
                showWinLossMessage(LocaleManager.getString("slot.error.betfailed"), true, true);
                finishSpin(false);
            } else {
                LOGGER.info("Bet placed, gameplayId: " + gameplayId);
                showWinLossMessage(LocaleManager.getString("slot.message.spinning"), true, true);
                startSpinAnimation(gameplayId, currentStake);
            }
        });

        placeBetTask.setOnFailed(workerStateEvent -> {
            LOGGER.log(Level.SEVERE, "Placing bet task failed.", placeBetTask.getException());
            showWinLossMessage(LocaleManager.getString("slot.error.betfailed"), true, true);
            finishSpin(false);
        });

        new Thread(placeBetTask).start();
    }

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

    private void displayRandomSymbol(int row, int col) {
        if (symbolAssets == null || symbolAssets.isEmpty()) return;
        GameAsset randomSymbol = symbolAssets.get(random.nextInt(symbolAssets.size()));
        try {
            symbolImageViews[row][col].setImage(ImageUtil.byteArrayToImage(randomSymbol.getImageData()));
        } catch (Exception e) {  }
    }

    private GameAsset[][] generateFinalGrid() {
        GameAsset[][] grid = new GameAsset[GRID_SIZE][GRID_SIZE];
        List<GameAsset> weightedList = createWeightedSymbolList();

        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (weightedList.isEmpty()) {
                    grid[r][c] = symbolAssets.get(0);
                } else {
                    grid[r][c] = weightedList.get(random.nextInt(weightedList.size()));
                }
            }
        }
        return grid;
    }

    private List<GameAsset> createWeightedSymbolList() {
        List<GameAsset> weightedList = new ArrayList<>();

        int[] baseWeights = {10, 12, 15, 8, 5, 2};

        int numSymbols = symbolAssets.size();
        if (baseWeights.length < numSymbols) {
            LOGGER.warning("Base weights array length (" + baseWeights.length + ") is less than number of symbols (" + numSymbols + "). Using default weight 1 for remaining symbols.");
            baseWeights = Arrays.copyOf(baseWeights, numSymbols);
            for (int i = baseWeights.length; i < numSymbols; i++) {
                baseWeights[i] = 1;
            }
        }

        int volatility = currentGame.getVolatility();

        double volatilityFactor = (volatility - 3.0) * 0.5;

        double middleIndex = (numSymbols - 1.0) / 2.0;

        for (int i = 0; i < numSymbols; i++) {
            int weight = baseWeights[i];

            double distanceFromMiddle = Math.abs(i - middleIndex);

            double adjustmentMultiplier = Math.pow(1.5, distanceFromMiddle * volatilityFactor);

            weight = (int) Math.max(1.0, Math.round(weight * adjustmentMultiplier));

            LOGGER.finest("Symbol index: " + i + ", Base weight: " + baseWeights[i] + ", Volatility: " + volatility + ", Adj Multiplier: " + String.format("%.2f", adjustmentMultiplier) + ", Final weight: " + weight);

            for (int j = 0; j < weight; j++) {
                weightedList.add(symbolAssets.get(i));
            }
        }

        if (weightedList.isEmpty()) {
            LOGGER.severe("Weighted symbol list is empty! This should not happen if there are symbols. Adding fallback.");

            weightedList.addAll(symbolAssets);
        }

        Collections.shuffle(weightedList);
        return weightedList;
    }

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

    private void checkAndProcessWins(GameAsset[][] finalGrid, long gameplayId, BigDecimal stakeAmount) {
        BigDecimal totalWin = BigDecimal.ZERO;
        winningLineCoords.clear();

        List<List<Position>> linesToCheck = List.of(

                List.of(new Position(0,0), new Position(0,1), new Position(0,2)),
                List.of(new Position(1,0), new Position(1,1), new Position(1,2)),
                List.of(new Position(2,0), new Position(2,1), new Position(2,2)),

                List.of(new Position(0,0), new Position(1,0), new Position(2,0)),
                List.of(new Position(0,1), new Position(1,1), new Position(2,1)),
                List.of(new Position(0,2), new Position(1,2), new Position(2,2)),

                List.of(new Position(0,0), new Position(1,1), new Position(2,2)),
                List.of(new Position(0,2), new Position(1,1), new Position(2,0))
        );

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

                    line.forEach(pos -> { if (!winningLineCoords.contains(pos)) winningLineCoords.add(pos); });
                    LOGGER.fine("Winning line! Symbol Index: " + symbolIndex + ", Multiplier: " + multiplier + ", Win: " + lineWin);
                } else { LOGGER.warning("Multiplier not found for winning symbol index: " + symbolIndex); }
            }
        }

        final BigDecimal finalTotalWin = totalWin;
        boolean isWin = finalTotalWin.compareTo(BigDecimal.ZERO) > 0;

        if (isWin) {
            String winMsg = MessageFormat.format(LocaleManager.getString("slot.message.won"), createCurrencyFormatter().format(finalTotalWin));
            showWinLossMessage(winMsg, false, true);
            startHighlightBlinking();
        } else {
            showWinLossMessage(LocaleManager.getString("slot.message.lost"), true, true);
            stopHighlightBlinking();
        }

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
            if (isWin && recorded) { loadRecentWinsLeaderboard(); }
            finishSpin(true);
        });
        recordTask.setOnFailed(e -> {
            LOGGER.log(Level.SEVERE, "Recording result task failed.", recordTask.getException());

            showWinLossMessage(LocaleManager.getString("slot.error.resultfailed"), true, true);
            finishSpin(true);
        });
        new Thread(recordTask).start();
    }

    private int findSymbolIndexById(int symbolId) {
        for(int i=0; i < symbolAssets.size(); i++) {
            if(symbolAssets.get(i).getId() == symbolId) {
                return i;
            }
        }
        return -1;
    }

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

    private void startHighlightBlinking() {
        if (winningLineCoords.isEmpty()) return;
        stopHighlightBlinking();

        blinkTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, e -> applyHighlightStyle(true)),
                new KeyFrame(BLINK_INTERVAL, e -> applyHighlightStyle(false)),
                new KeyFrame(BLINK_INTERVAL.multiply(2), e -> {})
        );
        blinkTimeline.setCycleCount(Animation.INDEFINITE);
        blinkTimeline.play();
        LOGGER.fine("Started highlight blinking.");
    }

    private void stopHighlightBlinking() {
        if (blinkTimeline != null) {
            blinkTimeline.stop();
            blinkTimeline = null;
            LOGGER.fine("Stopped highlight blinking.");

            resetWinningCellHighlights();

        }
    }

    private void resetWinningCellHighlights() {

        winningLineCoords.forEach(pos -> {

            if (pos.row >= 0 && pos.row < GRID_SIZE && pos.col >= 0 && pos.col < GRID_SIZE) {
                resetCellHighlight(pos.row, pos.col);
            }
        });
        winningLineCoords.clear();
    }

    private void applyHighlightStyle(boolean gold) {
        String styleClass = gold ? "slot-symbol-background-win-gold" : "slot-symbol-background-win-white";
        winningLineCoords.forEach(pos -> {
            if (pos.row >= 0 && pos.row < GRID_SIZE && pos.col >= 0 && pos.col < GRID_SIZE) {
                symbolBackgrounds[pos.row][pos.col].getStyleClass().setAll(styleClass);
            }
        });
    }

    private void clearHighlightsAndWinLoss() {
        stopHighlightBlinking();

        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                resetCellHighlight(r, c);
            }
        }

        Platform.runLater(() -> {
            recentWinLossLabel.setText("");
            recentWinLossLabel.setEffect(null);
        });

    }

    private void resetCellHighlight(int row, int col) {
        symbolBackgrounds[row][col].getStyleClass().setAll("slot-symbol-background");
        symbolBackgrounds[row][col].setEffect(null);
    }

    private void finishSpin(boolean spinExecutedSuccessfully) {
        isSpinning = false;
        increaseStakeButton.setDisable(currentStakeIndex >= availableStakes.size() - 1);
        decreaseStakeButton.setDisable(currentStakeIndex <= 0);
        updateSpinButtonState();
    }

    private void showWinLossMessage(String message, boolean isError, boolean applyEffect) {
        Platform.runLater(() -> {
            recentWinLossLabel.setText(message);

            if (message.equals(LocaleManager.getString("slot.message.placingbet")) || message.equals(LocaleManager.getString("slot.message.spinning"))) {
                recentWinLossLabel.setTextFill(Color.BLACK);
            } else {
                recentWinLossLabel.setTextFill(isError ? Color.INDIANRED : Color.GREEN);
            }

            if (applyEffect && !message.equals(LocaleManager.getString("slot.message.placingbet")) && !message.equals(LocaleManager.getString("slot.message.spinning"))) {
                Color shadowColor = isError ? Color.INDIANRED : Color.LIGHTGREEN;
                DropShadow ds = new DropShadow();
                ds.setRadius(1.0);
                ds.setOffsetX(0.0);
                ds.setOffsetY(0.0);
                ds.setColor(shadowColor);
                ds.setSpread(0.05);
                recentWinLossLabel.setEffect(ds);
            } else {
                recentWinLossLabel.setEffect(null);
            }
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

    private NumberFormat createCurrencyFormatter() {
        NumberFormat currencyFormatter = NumberFormat.getNumberInstance(LocaleManager.getCurrentLocale());
        currencyFormatter.setMinimumFractionDigits(2);
        currencyFormatter.setMaximumFractionDigits(2);
        return currencyFormatter;
    }

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

    private record Position(int row, int col) {}

    public void cleanup() {
        if (spinAnimationTimeline != null) spinAnimationTimeline.stop();
        if (leaderboardRefreshTimeline != null) leaderboardRefreshTimeline.stop();
        if (blinkTimeline != null) blinkTimeline.stop();
        LOGGER.info("SlotGameController cleaned up timers.");
    }
}