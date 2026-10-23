package net.dvmn2.inventorycontrolplugin.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Slot numbering of {@link org.bukkit.inventory.PlayerInventory} and the order in which storage slots open.
 * <p>
 * Indices: 0..8 hotbar, 9..35 main inventory, 36..39 armor (36 boots, 39 helmet), 40 offhand.
 * With {@code n} open storage slots (0..36) the slots open in this order:
 * <pre>
 *  1..9   hotbar, left to right (slots 0..8)
 *  10..18 bottom main row, left to right (slots 27..35)
 *  19..27 middle main row (slots 18..26)
 *  28..36 top main row (slots 9..17)
 * </pre>
 * Armor and offhand are not part of the storage; they are controlled through {@link EquipmentPart}.
 */
public final class SlotLayout {

    public static final int HOTBAR_SIZE = 9;
    public static final int ROW_SIZE = 9;
    public static final int MAIN_SIZE = 27;

    /** Number of storage slots (hotbar + main inventory). */
    public static final int MAX_SLOTS = HOTBAR_SIZE + MAIN_SIZE;

    public static final int OFFHAND_SLOT = 40;
    public static final int LAST_SLOT = OFFHAND_SLOT;

    /** Storage slots in opening order. Also the order in which displaced items are placed. */
    private static final List<Integer> STORAGE_ORDER = buildStorageOrder();

    /** Every managed slot, 0..40. */
    private static final List<Integer> ALL_SLOTS = buildAllSlots();

    private SlotLayout() {
    }

    /** Clamps a storage slot count into {@code 0..MAX_SLOTS}. */
    public static int clamp(int amount) {
        return Math.max(0, Math.min(MAX_SLOTS, amount));
    }

    /** Returns the 36 storage slots in opening order. */
    public static List<Integer> storageOrder() {
        return STORAGE_ORDER;
    }

    /** Returns every managed slot, 0..40. */
    public static List<Integer> allSlots() {
        return ALL_SLOTS;
    }

    private static List<Integer> buildStorageOrder() {
        List<Integer> order = new ArrayList<>(MAX_SLOTS);
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            order.add(slot);
        }
        // Main inventory rows open from the bottom row up, each row left to right.
        for (int rowStart = MAX_SLOTS - ROW_SIZE; rowStart >= HOTBAR_SIZE; rowStart -= ROW_SIZE) {
            for (int column = 0; column < ROW_SIZE; column++) {
                order.add(rowStart + column);
            }
        }
        return Collections.unmodifiableList(order);
    }

    private static List<Integer> buildAllSlots() {
        List<Integer> all = new ArrayList<>(LAST_SLOT + 1);
        for (int slot = 0; slot <= LAST_SLOT; slot++) {
            all.add(slot);
        }
        return Collections.unmodifiableList(all);
    }
}
