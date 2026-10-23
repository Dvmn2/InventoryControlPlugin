package net.dvmn2.inventorycontrolplugin.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

/**
 * Equipment parts whose availability is controlled by {@code /inventorycontrol equip}.
 * Slots use the {@link org.bukkit.inventory.PlayerInventory} numbering.
 */
public enum EquipmentPart {
    HELMET("helmet", 39),
    CHESTPLATE("chestplate", 38),
    LEGGINGS("leggings", 37),
    BOOTS("boots", 36),
    OFFHAND("offhand", SlotLayout.OFFHAND_SLOT);

    public static final String SELECTOR_ARMOR = "armor";
    public static final String SELECTOR_ALL = "all";

    private static final List<EquipmentPart> ARMOR = List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);

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
     * Parses a command argument: one part name, {@code armor} (four armor parts) or {@code all}
     * (everything including the offhand).
     *
     * @return the selected parts, or an empty list if the argument is not recognised
     */
    public static List<EquipmentPart> parseSelector(String selector) {
        String key = selector.toLowerCase(Locale.ROOT);
        if (key.equals(SELECTOR_ARMOR)) {
            return ARMOR;
        }
        if (key.equals(SELECTOR_ALL)) {
            return List.of(values());
        }
        for (EquipmentPart part : values()) {
            if (part.id.equals(key)) {
                return List.of(part);
            }
        }
        return List.of();
    }

    /** Returns every valid selector, for suggestions and error messages. */
    public static List<String> selectorNames() {
        List<String> names = new ArrayList<>();
        for (EquipmentPart part : values()) {
            names.add(part.id);
        }
        names.add(SELECTOR_ARMOR);
        names.add(SELECTOR_ALL);
        return names;
    }

    /** Returns the part an item with the given equipment slot is worn in, or {@code null}. */
    public static @Nullable EquipmentPart fromEquipmentSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> HELMET;
            case CHEST -> CHESTPLATE;
            case LEGS -> LEGGINGS;
            case FEET -> BOOTS;
            default -> null;
        };
    }
}
