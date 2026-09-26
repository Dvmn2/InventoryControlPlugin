package net.dvmn2.inventorycontrolplugin;

import java.util.*;

/**
 * Вычисляет, какие слоты {@link org.bukkit.inventory.PlayerInventory} открыты
 * при заданном количестве {@code n} (хотбар + основной инвентарь, всего 0..36).
 * <p>
 * Порядок открытия:
 * <pre>
 *  1..9   -> хотбар слева направо (слоты 0..8)
 *  10..18 -> нижний ряд основного инвентаря, слева направо (слоты 27..35)
 *  19..27 -> средний ряд (слоты 18..26)
 *  28..36 -> верхний ряд (слоты 9..17)
 * </pre>
 * Броня и оффхенд сюда НЕ входят — ими управляет {@link EquipmentPart}.
 * <p>
 * Индексы как в PlayerInventory: 0..8 хотбар, 9..35 основной инвентарь,
 * 36..39 броня (36 — ботинки, 39 — шлем), 40 — оффхенд.
 */
public final class InventoryComputer {

    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_SIZE = 27;

    /**
     * Максимум значения N (хотбар + основной инвентарь).
     */
    public static final int MAX_SLOTS = HOTBAR_SIZE + MAIN_SIZE; // 36

    public static final int OFFHAND_SLOT = 40;
    public static final int LAST_SLOT = OFFHAND_SLOT;

    /**
     * Порядок, в котором открываются слоты хранилища (и в котором в них
     * раскладываются вытесненные предметы).
     */
    private static final List<Integer> STORAGE_ORDER;
    private static final Set<Integer> ALL_SLOTS;

    static {
        List<Integer> order = new ArrayList<>(MAX_SLOTS);
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            order.add(i);
        }
        // основной инвентарь: строки снизу вверх, внутри строки слева направо
        for (int rowStart = HOTBAR_SIZE + MAIN_SIZE - 9; rowStart >= HOTBAR_SIZE; rowStart -= 9) {
            for (int i = 0; i < 9; i++) {
                order.add(rowStart + i);
            }
        }
        STORAGE_ORDER = Collections.unmodifiableList(order);

        Set<Integer> all = new LinkedHashSet<>();
        for (int i = 0; i <= LAST_SLOT; i++) {
            all.add(i);
        }
        ALL_SLOTS = Collections.unmodifiableSet(all);
    }

    private InventoryComputer() {
    }

    public static int clamp(int n) {
        if (n < 0) return 0;
        if (n > MAX_SLOTS) return MAX_SLOTS;
        return n;
    }

    /**
     * Все слоты хранилища (0..35) в порядке открытия.
     */
    public static List<Integer> storageOrder() {
        return STORAGE_ORDER;
    }

    /**
     * @return первые {@code n} слотов хранилища в порядке открытия
     */
    public static List<Integer> unlockedStorageSlots(int n) {
        return STORAGE_ORDER.subList(0, clamp(n));
    }

    /**
     * Все управляемые слоты 0..40 (хранилище + броня + оффхенд).
     */
    public static Set<Integer> allSlots() {
        return ALL_SLOTS;
    }
}