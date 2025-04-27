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

    /**
     * Converts a JavaFX Image object to a byte array.
     *
     * @param image The JavaFX Image.
     * @param format The desired image format ("png", "jpg", etc.).
     * @return Byte array, or null on error.
     */
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
            // Ensure format is valid for ImageIO
            String validFormat = format.toLowerCase();
            if (!List.of("png", "jpg", "jpeg", "gif", "bmp").contains(validFormat)) {
                LOGGER.warning("Unsupported image format for saving: " + format + ". Defaulting to png.");
                validFormat = "png"; // Default to PNG if format is weird
            }
            if ("jpg".equals(validFormat)) validFormat = "jpeg"; // ImageIO often prefers "jpeg"

            ImageIO.write(bImage, validFormat, s);
            return s.toByteArray();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error converting image to byte array (format: " + format + ")", e);
            return null;
        }
    }

    /**
     * Converts a byte array back to a JavaFX Image.
     *
     * @param imageData The image byte array.
     * @return JavaFX Image, or null on error.
     * @throws IOException if the byte array is invalid image data.
     */
    public static Image byteArrayToImage(byte[] imageData) throws IOException {
        if (imageData == null || imageData.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageData)) {
            return new Image(bis);
        }
    }

    /**
     * Extracts the file extension (lowercase) from a filename.
     * @param filename The full filename (e.g., "image.PNG").
     * @return The lowercase extension without the dot (e.g., "png"), or an empty string if no extension.
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return ""; // No extension found
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    // Private constructor
    private ImageUtil() {}
}