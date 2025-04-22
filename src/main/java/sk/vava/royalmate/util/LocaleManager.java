package sk.vava.royalmate.util;

import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LocaleManager {

    private static final Logger LOGGER = Logger.getLogger(LocaleManager.class.getName());
    private static final String BUNDLE_BASENAME = "bundles.messages"; // Path relative to resources root

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale SLOVAK = new Locale("sk"); // Slovak locale

    public static final List<Locale> SUPPORTED_LOCALES = List.of(ENGLISH, SLOVAK);

    private static Locale currentLocale = ENGLISH; // Default to English
    private static ResourceBundle currentBundle;

    // Static initializer block to load the default bundle immediately when the class is loaded
    static {
        loadBundle(currentLocale); // Load default bundle (English)
    }

    // Private constructor to prevent instantiation
    private LocaleManager() {}

    /**
     * Loads the resource bundle for the given locale.
     *
     * @param locale The locale to load.
     * @return true if loading was successful, false otherwise.
     */
    private static boolean loadBundle(Locale locale) {
        try {
            // Ensure UTF-8 loading if needed, though usually handled by properties file encoding
            //ResourceBundle.Control control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES);
            // currentBundle = ResourceBundle.getBundle(BUNDLE_BASENAME, locale, control);

            currentBundle = ResourceBundle.getBundle(BUNDLE_BASENAME, locale);
            currentLocale = locale; // Update current locale only on successful load
            LOGGER.info("ResourceBundle loaded successfully for locale: " + locale.toLanguageTag());
            return true;
        } catch (MissingResourceException e) {
            LOGGER.log(Level.SEVERE, "Missing resource bundle for locale: " + locale.toLanguageTag() + " - BaseName: " + BUNDLE_BASENAME, e);
            // Keep the previous bundle loaded if the new one fails? Or fallback to default?
            // Let's fallback to English if the desired one fails (unless English itself failed)
            if (!locale.equals(ENGLISH)) {
                LOGGER.warning("Falling back to English locale due to missing bundle for " + locale.toLanguageTag());
                return loadBundle(ENGLISH); // Attempt to load English
            } else {
                // If even English fails, something is fundamentally wrong
                LOGGER.severe("CRITICAL: Default English resource bundle is missing!");
                // Maybe throw a runtime exception here?
                // throw new RuntimeException("Default resource bundle missing!", e);
                return false;
            }
        }
    }

    /**
     * Sets the application's current locale and reloads the resource bundle.
     *
     * @param locale The new locale to set. Must be one of the SUPPORTED_LOCALES.
     * @return true if the locale was set and bundle loaded, false otherwise.
     */
    public static boolean setLocale(Locale locale) {
        if (SUPPORTED_LOCALES.contains(locale)) {
            if (!locale.equals(currentLocale)) {
                LOGGER.info("Setting locale to: " + locale.toLanguageTag());
                return loadBundle(locale);
            }
            return true; // Locale already set
        } else {
            LOGGER.warning("Attempted to set unsupported locale: " + locale.toLanguageTag());
            return false;
        }
    }

    /**
     * Gets the currently active Locale.
     * @return The current Locale.
     */
    public static Locale getCurrentLocale() {
        return currentLocale;
    }

    /**
     * Gets the currently loaded ResourceBundle.
     * Useful if you need direct access (e.g., for MessageFormat).
     * @return The current ResourceBundle.
     */
    public static ResourceBundle getBundle() {
        // Ensure bundle is loaded if called before static initializer (shouldn't happen often)
        if (currentBundle == null) {
            LOGGER.warning("Current bundle was null, attempting to load default.");
            loadBundle(currentLocale);
        }
        return currentBundle;
    }

    /**
     * Gets the localized string for the given key.
     *
     * @param key The key from the properties file.
     * @return The localized string, or a placeholder/key if not found.
     */
    public static String getString(String key) {
        ResourceBundle bundle = getBundle(); // Ensure bundle is loaded
        if (bundle == null) {
            LOGGER.severe("Cannot get string, resource bundle is null!");
            return "!" + key + "!"; // Indicate bundle error
        }
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            LOGGER.log(Level.WARNING, "Missing resource key '" + key + "' for locale: " + currentLocale.toLanguageTag());
            return "!" + key + "!"; // Return key surrounded by ! to indicate it's missing
        } catch (ClassCastException e){
            LOGGER.log(Level.SEVERE, "Resource key '" + key + "' is not a String for locale: " + currentLocale.toLanguageTag());
            return "!" + key + "!";
        }
    }
}