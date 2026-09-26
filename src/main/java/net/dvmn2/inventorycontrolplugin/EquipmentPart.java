package net.dvmn2.inventorycontrolplugin;

import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Части экипировки, открытостью которых управляет команда
 * {@code /inventorycontrol equip}. Слоты — в нумерации PlayerInventory.
 */
public enum EquipmentPart {
    HELMET("helmet", 39),
    CHESTPLATE("chestplate", 38),
    LEGGINGS("leggings", 37),
    BOOTS("boots", 36),
    OFFHAND("offhand", InventoryComputer.OFFHAND_SLOT);

    public static final List<EquipmentPart> ARMOR = List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);

    private final String id;
    private final int slot;

    EquipmentPart(String id, int slot) {
        this.id = id;
        this.slot = slot;
    }

    public String getId() {
        return id;
    }

    public int getSlot() {
        return slot;
    }

    /**
     * Разбирает аргумент команды: имя одной части, {@code armor} (4 части брони)
     * или {@code all} (всё, включая оффхенд).
     *
     * @return список частей или {@code null}, если аргумент не распознан
     */
    public static List<EquipmentPart> parseSelector(String selector) {
        String key = selector.toLowerCase(Locale.ROOT);
        if (key.equals("armor")) {
            return ARMOR;
        }
        if (key.equals("all")) {
            return List.of(values());
        }
        for (EquipmentPart part : values()) {
            if (part.id.equals(key)) {
                return List.of(part);
            }
        }
        return null;
    }

    /**
     * Допустимые значения аргумента команды (для подсказок и сообщений об ошибке).
     */
    public static List<String> selectorNames() {
        List<String> names = new ArrayList<>();
        for (EquipmentPart part : values()) {
            names.add(part.id);
        }
        names.add("armor");
        names.add("all");
        return names;
    }

    /**
     * @return часть экипировки, в которую надевается предмет для данного слота, либо {@code null}
     */
    public static EquipmentPart fromEquipmentSlot(EquipmentSlot slot) {
        if (slot == null) {
            return null;
        }
        return switch (slot) {
            case HEAD -> HELMET;
            case CHEST -> CHESTPLATE;
            case LEGS -> LEGGINGS;
            case FEET -> BOOTS;
            default -> null;
        };
    }
}