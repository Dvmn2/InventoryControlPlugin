package net.dvmn2.inventorycontrolplugin.model;

import org.jetbrains.annotations.Nullable;

/**
 * The five lockable cells of the personal 2x2 crafting grid in the player's own inventory
 * (not a crafting table, which has a different inventory type):
 * <pre>
 * id 1 | id 2      (TOP_LEFT | TOP_RIGHT)
 * id 3 | id 4      (BOTTOM_LEFT | BOTTOM_RIGHT)
 * id 5 - crafting result
 * </pre>
 * The raw slot is the index in the top inventory of type {@code CRAFTING}, as reported by
 * {@code InventoryClickEvent#getRawSlot()}: 0 is the result, 1..4 the grid. The ids are part of the
 * plugin-to-mod protocol.
 */
public enum CraftingCell {
    TOP_LEFT(1, 1),
    TOP_RIGHT(2, 2),
    BOTTOM_LEFT(3, 3),
    BOTTOM_RIGHT(4, 4),
    RESULT(5, 0);

    public static final int MIN_ID = 1;
    public static final int MAX_ID = 5;

    private final int id;
    private final int rawSlot;

    CraftingCell(int id, int rawSlot) {
        this.id = id;
        this.rawSlot = rawSlot;
    }

    public int getId() {
        return id;
    }

    public int getRawSlot() {
        return rawSlot;
    }

    /** Returns the cell with the given id, or {@code null} if there is none. */
    public static @Nullable CraftingCell byId(int id) {
        for (CraftingCell cell : values()) {
            if (cell.id == id) {
                return cell;
            }
        }
        return null;
    }

    /** Returns the cell at the given raw slot of a {@code CRAFTING} inventory, or {@code null}. */
    public static @Nullable CraftingCell byRawSlot(int rawSlot) {
        for (CraftingCell cell : values()) {
            if (cell.rawSlot == rawSlot) {
                return cell;
            }
        }
        return null;
    }
}
