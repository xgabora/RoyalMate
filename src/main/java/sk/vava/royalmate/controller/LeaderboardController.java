package sk.vava.royalmate.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import sk.vava.royalmate.model.GameType;
import sk.vava.royalmate.model.Gameplay;
import sk.vava.royalmate.service.LeaderboardService;
import sk.vava.royalmate.util.ImageUtil;
import sk.vava.royalmate.util.LocaleManager;
import sk.vava.royalmate.util.SessionManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LeaderboardController {

    private static final Logger LOGGER = Logger.getLogger(LeaderboardController.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML private BorderPane rootPane;
    @FXML private ToggleButton payoutSortButton;
    @FXML private ToggleButton multiplierSortButton;
    @FXML private VBox slotsColumnContent;
    @FXML private VBox rouletteColumnContent;
    @FXML private VBox coinflipColumnContent;

    private final LeaderboardService leaderboardService;
    private ToggleGroup sortToggleGroup;
    private boolean sortByPayout = true;

    private final ExecutorService executorService = Executors.newFixedThreadPool(3, runnable -> {
        Thread t = Executors.defaultThreadFactory().newThread(runnable);
        t.setDaemon(true);
        return t;
    });

    public LeaderboardController() {
        this.leaderboardService = new LeaderboardService();
    }

    @FXML
    public void initialize() {
        if (SessionManager.getCurrentAccount() == null) {
            LOGGER.severe("Leaderboard screen loaded without user session!");
            return;
        }
        setupSortToggle();
        loadLeaderboards();
        LOGGER.info("LeaderboardController initialized.");
    }

    private void setupSortToggle() {
        sortToggleGroup = new ToggleGroup();
        payoutSortButton.setToggleGroup(sortToggleGroup);
        multiplierSortButton.setToggleGroup(sortToggleGroup);
        payoutSortButton.setSelected(true);

        sortToggleGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                sortToggleGroup.selectToggle(oldToggle != null ? oldToggle : payoutSortButton);
            } else {
                boolean newSortByPayout = (newToggle == payoutSortButton);
                if (newSortByPayout != sortByPayout) {
                    sortByPayout = newSortByPayout;
                    LOGGER.info("Leaderboard sort changed to: " + (sortByPayout ? "Payout" : "Multiplier"));
                    loadLeaderboards();
                }
            }
        });
    }

    private void loadLeaderboards() {
        LOGGER.info("Loading leaderboards, sorting by " + (sortByPayout ? "Payout" : "Multiplier"));
        submitLoadTask(GameType.SLOT, slotsColumnContent);
        submitLoadTask(GameType.ROULETTE, rouletteColumnContent);
        submitLoadTask(GameType.COINFLIP, coinflipColumnContent);
    }

    private void submitLoadTask(GameType gameType, VBox columnVBox) {
        Task<List<Gameplay>> loadTask = new Task<>() {
            @Override
            protected List<Gameplay> call() throws Exception {
                LOGGER.fine("Background task started for " + gameType + ", sorting by " + (sortByPayout ? "Payout" : "Multiplier"));
                if (sortByPayout) {
                    return leaderboardService.getTopPayouts(gameType);
                } else {
                    return leaderboardService.getTopMultipliers(gameType);
                }
            }
        };

        loadTask.setOnSucceeded(event -> {
            List<Gameplay> topPlays = loadTask.getValue();
            LOGGER.fine("Background task succeeded for " + gameType + ". Found " + (topPlays != null ? topPlays.size() : "null") + " plays.");
            Platform.runLater(() -> populateColumnUI(columnVBox, topPlays));
        });

        loadTask.setOnFailed(event -> {
            Throwable error = loadTask.getException();
            LOGGER.log(Level.SEVERE, "Background task failed for " + gameType, error);
            Platform.runLater(() -> {
                columnVBox.getChildren().clear();
                Label errorLabel = new Label("Error loading data.");
                errorLabel.getStyleClass().add("error-label");
                columnVBox.getChildren().add(errorLabel);
            });
        });

        executorService.submit(loadTask);
    }

    private void populateColumnUI(VBox columnVBox, List<Gameplay> topPlays) {
        columnVBox.getChildren().clear();
        if (topPlays == null || topPlays.isEmpty()) {
            Label noDataLabel = new Label(LocaleManager.getString("leaderboard.nodata"));
            noDataLabel.getStyleClass().add("leaderboard-nodata-label");
            columnVBox.getChildren().add(noDataLabel);
            LOGGER.info("No data found or returned for column: " + columnVBox.getId());
        } else {
            LOGGER.fine("Populating UI for column " + columnVBox.getId() + " with " + topPlays.size() + " entries.");
            for (Gameplay play : topPlays) {
                try {
                    Node card = createLeaderboardCardNode(play, sortByPayout);
                    columnVBox.getChildren().add(card);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error creating leaderboard card for play ID: " + play.getId(), e);
                    Label errorCard = new Label("Error displaying entry");
                    errorCard.getStyleClass().add("error-label");
                    columnVBox.getChildren().add(errorCard);
                }
            }
        }
    }

    private Node createLeaderboardCardNode(Gameplay play, boolean showPayout) {
        HBox card = new HBox(10);
        card.getStyleClass().add("leaderboard-card-new");
        card.setAlignment(Pos.CENTER_LEFT);

        ImageView coverImageView = new ImageView();
        coverImageView.setFitHeight(45);
        coverImageView.setFitWidth(70);
        coverImageView.setPreserveRatio(false);
        coverImageView.getStyleClass().add("leaderboard-cover-image");

        if (play.getCoverImageData() != null) {
            try {
                coverImageView.setImage(ImageUtil.byteArrayToImage(play.getCoverImageData()));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load cover image for leaderboard entry, game: " + play.getGameName(), e);
                coverImageView.setImage(null);
            }
        } else {
            coverImageView.setImage(null);

        }

        VBox playerDateBox = new VBox(1);
        playerDateBox.setAlignment(Pos.CENTER_LEFT);
        Label playerLabel = new Label(play.getUsername() != null ? play.getUsername() : "Unknown");
        playerLabel.getStyleClass().add("leaderboard-player-name-small");
        String formattedDate = "N/A";
        if (play.getTimestamp() != null) {
            formattedDate = play.getTimestamp().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(DATE_FORMATTER);
        }
        Label dateLabel = new Label(formattedDate);
        dateLabel.getStyleClass().add("leaderboard-date");
        playerDateBox.getChildren().addAll(playerLabel, dateLabel);

        HBox.setHgrow(playerDateBox, Priority.SOMETIMES);

        String metricText;
        NumberFormat formatter = createCurrencyFormatter();
        if (showPayout) {
            metricText = formatter.format(play.getPayoutAmount()) + " €";
        } else {
            formatter.setMaximumFractionDigits(2);
            BigDecimal multiplier = play.getMultiplier() != null ? play.getMultiplier() : BigDecimal.ZERO;
            metricText = formatter.format(multiplier) + LocaleManager.getString("leaderboard.suffix.multiplier");
        }
        Label metricLabel = new Label(metricText);
        metricLabel.getStyleClass().add("leaderboard-metric-small");

        HBox metricContainer = new HBox(metricLabel);
        metricContainer.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(metricContainer, Priority.ALWAYS);

        card.getChildren().addAll(coverImageView, playerDateBox, metricContainer);
        return card;
    }

    private NumberFormat createCurrencyFormatter() {
        NumberFormat currencyFormatter = NumberFormat.getNumberInstance(LocaleManager.getCurrentLocale());
        currencyFormatter.setMinimumFractionDigits(2);
        currencyFormatter.setMaximumFractionDigits(2);
        return currencyFormatter;
    }

    public void shutdownExecutor() {
        LOGGER.info("Shutting down Leaderboard executor service.");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
                    LOGGER.severe("Leaderboard executor service did not terminate.");
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}