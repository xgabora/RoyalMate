package sk.vava.royalmate.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RoyalMate extends Application {

    private static final Logger LOGGER = Logger.getLogger(RoyalMate.class.getName());

    @Override
    public void start(Stage stage) {
        try {
            InputStream iconStream = getClass().getResourceAsStream("/images/royalmate_logo.png");
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            } else {
                LOGGER.warning("Application icon resource not found: /images/royalmate_logo.png");
            }
            FXMLLoader fxmlLoader = new FXMLLoader(Objects.requireNonNull(
                    RoyalMate.class.getResource("/sk/vava/royalmate/view/splash-view.fxml")
            ));

            Parent root = fxmlLoader.load();
            Scene scene = new Scene(root, 1080, 768);
            stage.setTitle("RoyalMate");
            stage.setScene(scene);
            stage.show();
            LOGGER.info("Splash screen loaded successfully.");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load FXML.", e);
        } catch (NullPointerException e) {
            LOGGER.log(Level.SEVERE, "FXML file or resource not found. Check paths.", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An unexpected error occurred during startup.", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}