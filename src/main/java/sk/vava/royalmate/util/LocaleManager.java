package sk.vava.royalmate.util;

import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LocaleManager {

    private static final Logger LOGGER = Logger.getLogger(LocaleManager.class.getName());
    private static final String BUNDLE_BASENAME = "bundles.messages";

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale SLOVAK = new Locale("sk");

    public static final List<Locale> SUPPORTED_LOCALES = List.of(ENGLISH, SLOVAK);

    private static Locale currentLocale = ENGLISH;
    private static ResourceBundle currentBundle;

    static {
        loadBundle(currentLocale);
    }

    private LocaleManager() {}

    private static boolean loadBundle(Locale locale) {
        try {

            currentBundle = ResourceBundle.getBundle(BUNDLE_BASENAME, locale);
            currentLocale = locale;
            LOGGER.info("ResourceBundle loaded successfully for locale: " + locale.toLanguageTag());
            return true;
        } catch (MissingResourceException e) {
            LOGGER.log(Level.SEVERE, "Missing resource bundle for locale: " + locale.toLanguageTag() + " - BaseName: " + BUNDLE_BASENAME, e);

            if (!locale.equals(ENGLISH)) {
                LOGGER.warning("Falling back to English locale due to missing bundle for " + locale.toLanguageTag());
                return loadBundle(ENGLISH);
            } else {

                LOGGER.severe("CRITICAL: Default English resource bundle is missing!");

                return false;
            }
        }
    }

    public static boolean setLocale(Locale locale) {
        if (SUPPORTED_LOCALES.contains(locale)) {
            if (!locale.equals(currentLocale)) {
                LOGGER.info("Setting locale to: " + locale.toLanguageTag());
                return loadBundle(locale);
            }
            return true;
        } else {
            LOGGER.warning("Attempted to set unsupported locale: " + locale.toLanguageTag());
            return false;
        }
    }

    public static Locale getCurrentLocale() {
        return currentLocale;
    }

    public static ResourceBundle getBundle() {

        if (currentBundle == null) {
            LOGGER.warning("Current bundle was null, attempting to load default.");
            loadBundle(currentLocale);
        }
        return currentBundle;
    }

    public static String getString(String key) {
        ResourceBundle bundle = getBundle();
        if (bundle == null) {
            LOGGER.severe("Cannot get string, resource bundle is null!");
            return "!" + key + "!";
        }
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            LOGGER.log(Level.WARNING, "Missing resource key '" + key + "' for locale: " + currentLocale.toLanguageTag());
            return "!" + key + "!";
        } catch (ClassCastException e){
            LOGGER.log(Level.SEVERE, "Resource key '" + key + "' is not a String for locale: " + currentLocale.toLanguageTag());
            return "!" + key + "!";
        }
    }
}