package net.dvmn2.inventorycontrolplugin;

import java.util.Locale;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Immutable snapshot of {@code config.yml}.
 *
 * @param language                message language: {@code ru}, {@code en} or {@code auto}
 * @param requireClientMod        whether players without the client mod are kicked
 * @param modCheckTimeoutSeconds  time a joining player has to register the mod channel, seconds
 * @param enforceIntervalTicks    period of the full inventory re-check, ticks
 */
public record InventoryControlSettings(String language, boolean requireClientMod, int modCheckTimeoutSeconds,
                                       int enforceIntervalTicks) {

    private static final String ROOT = "settings.";

    private static final String DEFAULT_LANGUAGE = "auto";
    private static final boolean DEFAULT_REQUIRE_CLIENT_MOD = false;
    private static final int DEFAULT_MOD_CHECK_TIMEOUT_SECONDS = 10;
    private static final int MIN_MOD_CHECK_TIMEOUT_SECONDS = 1;
    private static final int MAX_MOD_CHECK_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_ENFORCE_INTERVAL_TICKS = 5;
    private static final int MIN_ENFORCE_INTERVAL_TICKS = 1;
    private static final int MAX_ENFORCE_INTERVAL_TICKS = 100;

    /**
     * Reads the settings, replacing every invalid value with its default and logging a warning.
     */
    public static InventoryControlSettings parse(ConfigurationSection config, Logger log) {
        return new InventoryControlSettings(
                parseLanguage(config, log),
                config.getBoolean(ROOT + "require-client-mod", DEFAULT_REQUIRE_CLIENT_MOD),
                readRange(config, log, "mod-check-timeout-seconds", DEFAULT_MOD_CHECK_TIMEOUT_SECONDS,
                        MIN_MOD_CHECK_TIMEOUT_SECONDS, MAX_MOD_CHECK_TIMEOUT_SECONDS),
                readRange(config, log, "enforce-interval-ticks", DEFAULT_ENFORCE_INTERVAL_TICKS,
                        MIN_ENFORCE_INTERVAL_TICKS, MAX_ENFORCE_INTERVAL_TICKS));
    }

    private static String parseLanguage(ConfigurationSection config, Logger log) {
        String value = config.getString(ROOT + "language", DEFAULT_LANGUAGE).toLowerCase(Locale.ROOT);
        if (!value.equals("ru") && !value.equals("en") && !value.equals("auto")) {
            log.warning("settings.language must be ru, en or auto, got " + value + "; using " + DEFAULT_LANGUAGE);
            return DEFAULT_LANGUAGE;
        }
        return value;
    }

    private static int readRange(ConfigurationSection config, Logger log, String key, int defaultValue,
                                 int min, int max) {
        int value = config.getInt(ROOT + key, defaultValue);
        if (value < min || value > max) {
            log.warning("settings." + key + " must be in " + min + ".." + max + ", got " + value
                    + "; using " + defaultValue);
            return defaultValue;
        }
        return value;
    }
}
