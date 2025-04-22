package sk.vava.royalmate.controller;

import javafx.animation.RotateTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.util.Duration;

import java.util.Random;

public class WheelController {

    @FXML
    private Pane wheelPane;

    @FXML
    private Label resultLabel;

    private final String[] prizes = {"x2", "x5", "Free Spin", "Lose", "x10", "x3"};
    private final int segmentCount = prizes.length;

    private double currentAngle = 0;

    @FXML
    public void initialize() {
        drawWheel();
    }

    @FXML
    private void drawWheel() {
        double centerX = 200;
        double centerY = 200;
        double radius = 150;
        double angleStep = 360.0 / segmentCount;

        for (int i = 0; i < segmentCount; i++) {
            Arc arc = new Arc(centerX, centerY, radius, radius, i * angleStep, angleStep);
            arc.setType(ArcType.ROUND);
            arc.setFill(Color.hsb(i * 360.0 / segmentCount, 0.8, 0.9));
            arc.setStroke(Color.BLACK);
            wheelPane.getChildren().add(arc);
        }
    }

    @FXML
    public void spinWheel() {
        Random random = new Random();
        int winnerIndex = random.nextInt(segmentCount);
        double anglePerSegment = 360.0 / segmentCount;

        double targetAngle = 360 * 5 + (360 - winnerIndex * anglePerSegment); // Spin 5 full rounds then stop at winner

        RotateTransition rotate = new RotateTransition(Duration.seconds(4), wheelPane);
        rotate.setFromAngle(currentAngle);
        rotate.setToAngle(currentAngle + targetAngle);
        rotate.setCycleCount(1);
        rotate.setOnFinished(e -> {
            currentAngle += targetAngle;
            currentAngle %= 360; // Keep angle clean
            resultLabel.setText("You won: " + prizes[winnerIndex]);
        });

        rotate.play();
    }
}
