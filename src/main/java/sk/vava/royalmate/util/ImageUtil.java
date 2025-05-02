package sk.vava.royalmate.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ImageUtil {

    private static final Logger LOGGER = Logger.getLogger(ImageUtil.class.getName());

    public static byte[] imageToByteArray(Image image, String format) {
        if (image == null || format == null || format.isBlank()) {
            return null;
        }
        BufferedImage bImage = SwingFXUtils.fromFXImage(image, null);
        if (bImage == null) {
            LOGGER.warning("Failed to convert JavaFX Image to BufferedImage.");
            return null;
        }
        try (ByteArrayOutputStream s = new ByteArrayOutputStream()) {

            String validFormat = format.toLowerCase();
            if (!List.of("png", "jpg", "jpeg", "gif", "bmp").contains(validFormat)) {
                LOGGER.warning("Unsupported image format for saving: " + format + ". Defaulting to png.");
                validFormat = "png";
            }
            if ("jpg".equals(validFormat)) validFormat = "jpeg";

            ImageIO.write(bImage, validFormat, s);
            return s.toByteArray();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error converting image to byte array (format: " + format + ")", e);
            return null;
        }
    }

    public static Image byteArrayToImage(byte[] imageData) throws IOException {
        if (imageData == null || imageData.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageData)) {
            return new Image(bis);
        }
    }

    public static String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private ImageUtil() {}
}