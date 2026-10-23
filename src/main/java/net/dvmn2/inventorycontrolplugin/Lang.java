package net.dvmn2.inventorycontrolplugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Player-visible messages in Russian and English, written in MiniMessage.
 * <p>
 * This is the only class with static mutable state: the configured language, which is volatile.
 * Dynamic values must be passed as {@link TagResolver}s, never concatenated into a template.
 */
public final class Lang {

    /** Message keys. Every key has both a Russian and an English variant. */
    public enum Key {
        ONLY_PLAYERS,
        PLAYER_NOT_FOUND,
        RELOADED,
        SET_SUCCESS,
        CELL_LOCKED,
        CELL_UNLOCKED,
        CRAFTING_CELL_LOCKED,
        CRAFTING_CELL_UNLOCKED,
        EQUIP_OPENED,
        EQUIP_CLOSED,
        UNKNOWN_PART,
        RESET_SUCCESS,
        GET_INFO,
        GET_EQUIPMENT_CLOSED,
        GET_EQUIPMENT_ALL_OPEN,
        GET_LOCKED_CELLS,
        GET_LOCKED_CELLS_NONE,
        GET_CRAFTING_CELLS_LOCKED,
        GET_CRAFTING_CELLS_NONE,
        GUI_TITLE,
        KICK_NO_MOD
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    private static volatile String language = "auto";

    static {
        put(Key.ONLY_PLAYERS,
                "<red>Эту команду могут использовать только игроки.",
                "<red>This command can only be used by players.");
        put(Key.PLAYER_NOT_FOUND,
                "<red>Не удалось найти игрока.",
                "<red>Player not found.");
        put(Key.RELOADED,
                "<green>Конфигурация перезагружена.",
                "<green>Configuration reloaded.");
        put(Key.SET_SUCCESS,
                "<green>Игроку <yellow><player></yellow> установлено слотов (хотбар + инвентарь): "
                        + "<yellow><amount> / <max></yellow>",
                "<green>Set <yellow><player></yellow>'s open slots (hotbar + inventory) to: "
                        + "<yellow><amount> / <max></yellow>");
        put(Key.CELL_LOCKED,
                "<green>Игроку <yellow><player></yellow> заблокирована ячейка <yellow><cell></yellow>.",
                "<green>Locked cell <yellow><cell></yellow> for <yellow><player></yellow>.");
        put(Key.CELL_UNLOCKED,
                "<green>Игроку <yellow><player></yellow> разблокирована ячейка <yellow><cell></yellow>.",
                "<green>Unlocked cell <yellow><cell></yellow> for <yellow><player></yellow>.");
        put(Key.CRAFTING_CELL_LOCKED,
                "<green>Игроку <yellow><player></yellow> заблокирована ячейка крафта <yellow><cell></yellow>.",
                "<green>Locked crafting cell <yellow><cell></yellow> for <yellow><player></yellow>.");
        put(Key.CRAFTING_CELL_UNLOCKED,
                "<green>Игроку <yellow><player></yellow> разблокирована ячейка крафта <yellow><cell></yellow>.",
                "<green>Unlocked crafting cell <yellow><cell></yellow> for <yellow><player></yellow>.");
        put(Key.EQUIP_OPENED,
                "<green>Игроку <yellow><player></yellow> открыто: <yellow><parts></yellow>",
                "<green>Opened for <yellow><player></yellow>: <yellow><parts></yellow>");
        put(Key.EQUIP_CLOSED,
                "<green>Игроку <yellow><player></yellow> закрыто: <yellow><parts></yellow>",
                "<green>Closed for <yellow><player></yellow>: <yellow><parts></yellow>");
        put(Key.UNKNOWN_PART,
                "<red>Неизвестная часть экипировки: <yellow><part></yellow>. Доступно: <yellow><available></yellow>",
                "<red>Unknown equipment part: <yellow><part></yellow>. Available: <yellow><available></yellow>");
        put(Key.RESET_SUCCESS,
                "<green>Игроку <yellow><player></yellow> сброшены все ограничения.",
                "<green>Reset all restrictions of <yellow><player></yellow>.");
        put(Key.GET_INFO,
                "<gray>У игрока <yellow><player></yellow> открыто слотов (хотбар + инвентарь): "
                        + "<yellow><open> / <max></yellow>",
                "<gray><yellow><player></yellow> has <yellow><open> / <max></yellow> slots open "
                        + "(hotbar + inventory)");
        put(Key.GET_EQUIPMENT_CLOSED,
                "<gray>Закрытая экипировка игрока <yellow><player></yellow>: <yellow><parts></yellow>",
                "<gray>Closed equipment of <yellow><player></yellow>: <yellow><parts></yellow>");
        put(Key.GET_EQUIPMENT_ALL_OPEN,
                "<gray>Вся экипировка игрока <yellow><player></yellow> открыта.",
                "<gray>All equipment of <yellow><player></yellow> is open.");
        put(Key.GET_LOCKED_CELLS,
                "<gray>Индивидуально заблокированные ячейки у <yellow><player></yellow>: <yellow><list></yellow>",
                "<gray>Individually locked cells of <yellow><player></yellow>: <yellow><list></yellow>");
        put(Key.GET_LOCKED_CELLS_NONE,
                "<gray>У <yellow><player></yellow> нет индивидуально заблокированных ячеек.",
                "<gray><yellow><player></yellow> has no individually locked cells.");
        put(Key.GET_CRAFTING_CELLS_LOCKED,
                "<gray>Заблокированные ячейки крафта у <yellow><player></yellow>: <yellow><list></yellow>",
                "<gray>Locked crafting cells of <yellow><player></yellow>: <yellow><list></yellow>");
        put(Key.GET_CRAFTING_CELLS_NONE,
                "<gray>У <yellow><player></yellow> все ячейки крафта открыты.",
                "<gray>All crafting cells of <yellow><player></yellow> are open.");
        put(Key.GUI_TITLE,
                "Ячейки: <player>",
                "Cells: <player>");
        put(Key.KICK_NO_MOD,
                "<red>Для игры на этом сервере нужен клиентский мод InventoryControl.",
                "<red>The InventoryControl client mod is required to play on this server.");
        verify();
    }

    private Lang() {
    }

    /**
     * Sets the configured language.
     *
     * @param value {@code ru}, {@code en} or {@code auto}; anything blank or {@code null} means {@code auto}
     */
    public static void setLanguage(String value) {
        language = value == null || value.isBlank() ? "auto" : value.toLowerCase(Locale.ROOT);
    }

    /**
     * Renders a message for the given recipient.
     *
     * @param key       message key
     * @param sender    recipient; decides the language when the configured language is {@code auto}
     * @param resolvers placeholder values
     */
    public static Component get(Key key, CommandSender sender, TagResolver... resolvers) {
        return MM.deserialize(table(sender).get(key), resolvers);
    }

    private static void put(Key key, String ru, String en) {
        RU.put(key, ru);
        EN.put(key, en);
    }

    private static void verify() {
        for (Key key : Key.values()) {
            if (!RU.containsKey(key) || !EN.containsKey(key)) {
                throw new IllegalStateException("Missing translation for " + key);
            }
        }
    }

    private static Map<Key, String> table(CommandSender sender) {
        return switch (language) {
            case "ru" -> RU;
            case "en" -> EN;
            default -> sender instanceof Player player
                    && "ru".equalsIgnoreCase(player.locale().getLanguage()) ? RU : EN;
        };
    }
}
