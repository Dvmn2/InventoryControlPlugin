package net.dvmn2.inventorycontrolplugin;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Простая система локализации плагина: русский (ru) и английский (en).
 * <p>
 * Язык задаётся в config.yml (settings.language):
 * - "ru"   — всегда русский;
 * - "en"   — всегда английский;
 * - "auto" (по умолчанию) — язык определяется индивидуально для каждого
 * отправителя: для игрока берётся его игровая локаль (Player#locale()),
 * для консоли/прочих — английский.
 * <p>
 * Simple RU/EN localization for the plugin.
 * Language is configured via config.yml (settings.language):
 * - "ru"   — always Russian;
 * - "en"   — always English;
 * - "auto" (default) — resolved per sender: for a player, use their
 * client locale (Player#locale()); for console/other senders,
 * fall back to English.
 */
public final class Lang {

    /**
     * Ключи всех локализуемых сообщений плагина. / Keys for all localizable plugin messages.
     */
    public enum Key {
        PLAYER_NOT_FOUND,
        SET_SUCCESS,
        GET_INFO,
        GET_EQUIPMENT_CLOSED,
        GET_EQUIPMENT_ALL_OPEN,
        EQUIP_OPENED,
        EQUIP_CLOSED,
        UNKNOWN_PART,
        RESET_SUCCESS,
        KICK_NO_MOD
    }

    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    static {
        RU.put(Key.PLAYER_NOT_FOUND, "Не удалось найти игрока.");
        RU.put(Key.SET_SUCCESS, "Игроку %s установлено слотов (хотбар + инвентарь): %d / %d");
        RU.put(Key.GET_INFO, "У игрока %s открыто слотов (хотбар + инвентарь): %d / %d");
        RU.put(Key.GET_EQUIPMENT_CLOSED, "Закрытая экипировка игрока %s: %s");
        RU.put(Key.GET_EQUIPMENT_ALL_OPEN, "Вся экипировка игрока %s открыта.");
        RU.put(Key.EQUIP_OPENED, "Игроку %s открыто: %s");
        RU.put(Key.EQUIP_CLOSED, "Игроку %s закрыто: %s");
        RU.put(Key.UNKNOWN_PART, "Неизвестная часть экипировки: %s. Доступно: %s");
        RU.put(Key.RESET_SUCCESS, "Игроку %s сброшены все ограничения (слоты и экипировка).");
        RU.put(Key.KICK_NO_MOD, "Для игры на этом сервере нужен клиентский мод InventoryControl.");

        EN.put(Key.PLAYER_NOT_FOUND, "Player not found.");
        EN.put(Key.SET_SUCCESS, "Set %s's open slots (hotbar + inventory) to: %d / %d");
        EN.put(Key.GET_INFO, "%s has %d / %d slots open (hotbar + inventory)");
        EN.put(Key.GET_EQUIPMENT_CLOSED, "%s's closed equipment: %s");
        EN.put(Key.GET_EQUIPMENT_ALL_OPEN, "All of %s's equipment is open.");
        EN.put(Key.EQUIP_OPENED, "Opened for %s: %s");
        EN.put(Key.EQUIP_CLOSED, "Closed for %s: %s");
        EN.put(Key.UNKNOWN_PART, "Unknown equipment part: %s. Available: %s");
        EN.put(Key.RESET_SUCCESS, "Reset all of %s's restrictions (slots and equipment).");
        EN.put(Key.KICK_NO_MOD, "The InventoryControl client mod is required to play on this server.");
    }

    /**
     * "ru", "en" или "auto" — значение из config.yml. / "ru", "en" or "auto" from config.yml.
     */
    private static volatile String configuredLanguage = "auto";

    private Lang() {
    }

    /**
     * Устанавливает язык плагина из конфига.
     * Setting the plugin language from the config.
     */
    public static void setLanguage(String language) {
        if (language == null || language.isBlank()) {
            configuredLanguage = "auto";
            return;
        }
        configuredLanguage = language.toLowerCase(Locale.ROOT);
    }

    /**
     * Возвращает локализованное сообщение для конкретного отправителя.
     * Поддерживает подстановку аргументов через {@link String#format}.
     * Returns the localized message for a specific sender, with
     * {@link String#format} argument substitution.
     */
    public static String get(Key key, CommandSender sender, Object... args) {
        Map<Key, String> table = resolveTable(sender);
        String pattern = table.getOrDefault(key, RU.get(key));
        return args.length == 0 ? pattern : String.format(pattern, args);
    }

    private static Map<Key, String> resolveTable(CommandSender sender) {
        return switch (configuredLanguage) {
            case "en" -> EN;
            case "ru" -> RU;
            default -> autoResolve(sender); // "auto" или некорректное значение
        };
    }

    private static Map<Key, String> autoResolve(CommandSender sender) {
        if (sender instanceof Player player) {
            // Player#locale() отдаёт java.util.Locale игрока, выставленную в настройках клиента.
            // Player#locale() returns the client-configured java.util.Locale.
            String langCode = player.locale().getLanguage();
            return "ru".equalsIgnoreCase(langCode) ? RU : EN;
        }
        // Консоль/RCON и т.п. — по умолчанию английский.
        // Console/RCON etc. — default to English.
        return EN;
    }
}