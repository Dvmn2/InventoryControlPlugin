package net.dvmn2.inventorycontrolplugin;

import java.util.List;

/**
 * Пять управляемых ячеек личного крафта 2x2 в собственном инвентаре игрока
 * (не верстак — у него другой тип инвентаря):
 * <pre>
 * id 1 | id 2      (TOP_LEFT | TOP_RIGHT)
 * id 3 | id 4      (BOTTOM_LEFT | BOTTOM_RIGHT)
 * id 5 — результат крафта
 * </pre>
 * {@code rawSlot} — индекс в верхнем инвентаре типа {@code CRAFTING}, как его
 * отдают {@code InventoryClickEvent#getRawSlot()} и {@code Inventory#getItem()}:
 * 0 — результат, 1-4 — сетка (тот же порядок, что и у {@code Slot#id} на клиенте).
 */
public enum CraftingCell {
    TOP_LEFT(1, 1),
    TOP_RIGHT(2, 2),
    BOTTOM_LEFT(3, 3),
    BOTTOM_RIGHT(4, 4),
    RESULT(5, 0);

    public static final List<Integer> ALL_IDS = List.of(1, 2, 3, 4, 5);

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

    public static CraftingCell byId(int id) {
        for (CraftingCell c : values()) {
            if (c.id == id) return c;
        }
        return null;
    }

    public static CraftingCell byRawSlot(int rawSlot) {
        for (CraftingCell c : values()) {
            if (c.rawSlot == rawSlot) return c;
        }
        return null;
    }
}