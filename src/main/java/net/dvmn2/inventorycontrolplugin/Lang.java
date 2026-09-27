package net.dvmn2.inventorycontrolplugin;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class Lang {

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
        KICK_NO_MOD,
        CELL_LOCKED,
        CELL_UNLOCKED,
        CRAFTING_CELL_LOCKED,
        CRAFTING_CELL_UNLOCKED,
        GET_LOCKED_CELLS,
        GET_LOCKED_CELLS_NONE,
        GET_CRAFTING_CELLS_LOCKED,
        GET_CRAFTING_CELLS_NONE,
        GUI_ONLY_PLAYER
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
        RU.put(Key.RESET_SUCCESS, "Игроку %s сброшены все ограничения.");
        RU.put(Key.KICK_NO_MOD, "Для игры на этом сервере нужен клиентский мод InventoryControl.");
        RU.put(Key.CELL_LOCKED, "Игроку %s заблокирована ячейка %d.");
        RU.put(Key.CELL_UNLOCKED, "Игроку %s разблокирована ячейка %d.");
        RU.put(Key.CRAFTING_CELL_LOCKED, "Игроку %s заблокирована ячейка крафта %d.");
        RU.put(Key.CRAFTING_CELL_UNLOCKED, "Игроку %s разблокирована ячейка крафта %d.");
        RU.put(Key.GET_LOCKED_CELLS, "Индивидуально заблокированные ячейки у %s: %s");
        RU.put(Key.GET_LOCKED_CELLS_NONE, "У %s нет индивидуально заблокированных ячеек.");
        RU.put(Key.GET_CRAFTING_CELLS_LOCKED, "Заблокированные ячейки крафта у %s: %s");
        RU.put(Key.GET_CRAFTING_CELLS_NONE, "У %s все ячейки крафта открыты.");
        RU.put(Key.GUI_ONLY_PLAYER, "Эту команду может выполнить только игрок.");

        EN.put(Key.PLAYER_NOT_FOUND, "Player not found.");
        EN.put(Key.SET_SUCCESS, "Set %s's open slots (hotbar + inventory) to: %d / %d");
        EN.put(Key.GET_INFO, "%s has %d / %d slots open (hotbar + inventory)");
        EN.put(Key.GET_EQUIPMENT_CLOSED, "%s's closed equipment: %s");
        EN.put(Key.GET_EQUIPMENT_ALL_OPEN, "All of %s's equipment is open.");
        EN.put(Key.EQUIP_OPENED, "Opened for %s: %s");
        EN.put(Key.EQUIP_CLOSED, "Closed for %s: %s");
        EN.put(Key.UNKNOWN_PART, "Unknown equipment part: %s. Available: %s");
        EN.put(Key.RESET_SUCCESS, "Reset all of %s's restrictions.");
        EN.put(Key.KICK_NO_MOD, "The InventoryControl client mod is required to play on this server.");
        EN.put(Key.CELL_LOCKED, "Locked cell %2$d for %1$s.");
        EN.put(Key.CELL_UNLOCKED, "Unlocked cell %2$d for %1$s.");
        EN.put(Key.CRAFTING_CELL_LOCKED, "Locked crafting cell %2$d for %1$s.");
        EN.put(Key.CRAFTING_CELL_UNLOCKED, "Unlocked crafting cell %2$d for %1$s.");
        EN.put(Key.GET_LOCKED_CELLS, "%s's individually locked cells: %s");
        EN.put(Key.GET_LOCKED_CELLS_NONE, "%s has no individually locked cells.");
        EN.put(Key.GET_CRAFTING_CELLS_LOCKED, "%s's locked crafting cells: %s");
        EN.put(Key.GET_CRAFTING_CELLS_NONE, "All of %s's crafting cells are open.");
        EN.put(Key.GUI_ONLY_PLAYER, "This command can only be run by a player.");
    }

    private static volatile String configuredLanguage = "auto";

    private Lang() {
    }

    public static void setLanguage(String language) {
        if (language == null || language.isBlank()) {
            configuredLanguage = "auto";
            return;
        }
        configuredLanguage = language.toLowerCase(Locale.ROOT);
    }

    public static String get(Key key, CommandSender sender, Object... args) {
        Map<Key, String> table = resolveTable(sender);
        String pattern = table.getOrDefault(key, RU.get(key));
        return args.length == 0 ? pattern : String.format(pattern, args);
    }

    private static Map<Key, String> resolveTable(CommandSender sender) {
        return switch (configuredLanguage) {
            case "en" -> EN;
            case "ru" -> RU;
            default -> autoResolve(sender);
        };
    }

    private static Map<Key, String> autoResolve(CommandSender sender) {
        if (sender instanceof Player player) {
            String langCode = player.locale().getLanguage();
            return "ru".equalsIgnoreCase(langCode) ? RU : EN;
        }
        return EN;
    }
}