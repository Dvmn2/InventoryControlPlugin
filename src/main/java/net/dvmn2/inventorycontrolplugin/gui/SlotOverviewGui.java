package net.dvmn2.inventorycontrolplugin.gui;

import net.dvmn2.inventorycontrolplugin.InventoryControlPlugin;
import net.dvmn2.inventorycontrolplugin.InventoryDataManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Set;
import java.util.UUID;

/**
 * Интерактивный обзор ячеек инвентаря игрока в виде двойного сундука (54 слота).
 * <p>
 * Раскладка (см. {@link #buildLayout()}):
 * <pre>
 * 0 ботинки | 1 поножи | 2 нагрудник | 3 шлем | 4 - | 5 крафт1 | 6 крафт2 | 7 - | 8 крафт5(результат)
 * 9 оффхенд | 10-13 -             | 14 крафт3 | 15 крафт4 | 16-17 -
 * 18-26: верхняя строка инвентаря (PlayerInventory 9-17)
 * 27-35: центральная строка (18-26)
 * 36-44: нижняя строка (27-35)
 * 45-53: хотбар (0-8)
 * </pre>
 * Серое стекло — декоративный placeholder (клик игнорируется). Красное стекло —
 * заблокированная ячейка, пусто — открытая. Клик по не-placeholder ячейке
 * переключает её блокировку и сразу применяется через {@code InventoryEnforcer#enforce}.
 */
public final class SlotOverviewGui {

    private static final int SIZE = 54;
    private static final int PLACEHOLDER = -1;
    private static final int CRAFT_BASE = 100; // значения 101..105 — id ячейки крафта (1..5)

    private static final int[] LAYOUT = buildLayout();

    private SlotOverviewGui() {
    }

    private static int[] buildLayout() {
        int[] layout = new int[SIZE];

        layout[0] = 36; // ботинки
        layout[1] = 37; // поножи
        layout[2] = 38; // нагрудник
        layout[3] = 39; // шлем
        layout[4] = PLACEHOLDER;
        layout[5] = CRAFT_BASE + 1;
        layout[6] = CRAFT_BASE + 2;
        layout[7] = PLACEHOLDER;
        layout[8] = CRAFT_BASE + 5;

        layout[9] = 40; // оффхенд
        layout[10] = PLACEHOLDER;
        layout[11] = PLACEHOLDER;
        layout[12] = PLACEHOLDER;
        layout[13] = PLACEHOLDER;
        layout[14] = CRAFT_BASE + 3;
        layout[15] = CRAFT_BASE + 4;
        layout[16] = PLACEHOLDER;
        layout[17] = PLACEHOLDER;

        fillStorageRow(layout, 18, 9);
        fillStorageRow(layout, 27, 18);
        fillStorageRow(layout, 36, 27);
        fillStorageRow(layout, 45, 0);

        return layout;
    }

    private static void fillStorageRow(int[] layout, int rawStart, int slotStart) {
        for (int i = 0; i < 9; i++) {
            layout[rawStart + i] = slotStart + i;
        }
    }

    public static void open(InventoryControlPlugin plugin, Player admin, Player target) {
        SlotOverviewHolder holder = new SlotOverviewHolder(target.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text("Ячейки: " + target.getName()));
        holder.setInventory(inv);
        render(inv, target.getUniqueId(), plugin.getDataManager());
        admin.openInventory(inv);
    }

    public static void render(Inventory inv, UUID targetUuid, InventoryDataManager dm) {
        Set<Integer> unlocked = dm.getUnlockedSlots(targetUuid);
        Set<Integer> lockedCraft = dm.getLockedCraftingCells(targetUuid);
        for (int raw = 0; raw < LAYOUT.length; raw++) {
            int v = LAYOUT[raw];
            if (v == PLACEHOLDER) {
                inv.setItem(raw, pane(Material.GRAY_STAINED_GLASS_PANE));
            } else if (v >= CRAFT_BASE) {
                int cellId = v - CRAFT_BASE;
                inv.setItem(raw, lockedCraft.contains(cellId) ? pane(Material.RED_STAINED_GLASS_PANE) : null);
            } else {
                inv.setItem(raw, unlocked.contains(v) ? null : pane(Material.RED_STAINED_GLASS_PANE));
            }
        }
    }

    /**
     * Обрабатывает клик по ячейке GUI: переключает блокировку и применяет её к игроку.
     * Для placeholder-ячеек и неизвестного rawSlot ничего не делает.
     */
    public static void handleClick(InventoryControlPlugin plugin, UUID targetUuid, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= LAYOUT.length) {
            return;
        }
        int v = LAYOUT[rawSlot];
        if (v == PLACEHOLDER) {
            return;
        }
        InventoryDataManager dm = plugin.getDataManager();
        if (v >= CRAFT_BASE) {
            int cellId = v - CRAFT_BASE;
            boolean locked = dm.getLockedCraftingCells(targetUuid).contains(cellId);
            dm.setCraftingCellLocked(targetUuid, cellId, !locked);
        } else {
            boolean locked = dm.getLockedCells(targetUuid).contains(v);
            dm.setCellLocked(targetUuid, v, !locked);
        }
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null) {
            plugin.getEnforcer().enforce(target);
        }
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }
}