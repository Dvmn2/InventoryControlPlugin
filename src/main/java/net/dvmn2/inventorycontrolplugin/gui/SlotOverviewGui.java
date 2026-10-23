package net.dvmn2.inventorycontrolplugin.gui;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import net.dvmn2.inventorycontrolplugin.Lang;
import net.dvmn2.inventorycontrolplugin.LimitsManager;
import net.dvmn2.inventorycontrolplugin.model.CraftingCell;
import net.dvmn2.inventorycontrolplugin.model.EquipmentPart;
import net.dvmn2.inventorycontrolplugin.model.SlotLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Interactive overview of a player's cells as a double chest (54 slots). Red glass is a locked cell, green
 * glass an open one, gray glass a decoration. Clicking a red or green pane toggles that cell.
 * <pre>
 *  0 boots | 1 leggings | 2 chestplate | 3 helmet | 4 - | 5 craft 1 | 6 craft 2 | 7 - | 8 craft 5 (result)
 *  9 offhand | 10..13 - | 14 craft 3 | 15 craft 4 | 16..17 -
 * 18..26 top main row (PlayerInventory 9..17)
 * 27..35 middle main row (18..26)
 * 36..44 bottom main row (27..35)
 * 45..53 hotbar (0..8)
 * </pre>
 * Main thread only.
 */
public final class SlotOverviewGui {

    private static final int SIZE = 54;
    private static final int PLACEHOLDER = -1;

    /** Layout values from this base up are crafting cells: value - base is the cell id. */
    private static final int CRAFT_BASE = 100;

    private static final int[] LAYOUT = buildLayout();

    private SlotOverviewGui() {
    }

    /** Opens the overview of {@code target} for {@code admin}. */
    public static void open(LimitsManager limits, Player admin, Player target) {
        SlotOverviewHolder holder = new SlotOverviewHolder(target.getUniqueId());
        Component title = Lang.get(Lang.Key.GUI_TITLE, admin, Placeholder.unparsed("player", target.getName()));
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);
        render(inventory, target.getUniqueId(), limits);
        admin.openInventory(inventory);
    }

    /** Redraws every pane of an overview window from the current limits. */
    public static void render(Inventory inventory, UUID targetUuid, LimitsManager limits) {
        Set<Integer> lockedCells = limits.getLockedCells(targetUuid);
        Set<Integer> lockedCraft = limits.getLockedCraftingCells(targetUuid);
        for (int raw = 0; raw < LAYOUT.length; raw++) {
            int value = LAYOUT[raw];
            Material material;
            if (value == PLACEHOLDER) {
                material = Material.GRAY_STAINED_GLASS_PANE;
            } else {
                boolean locked = value >= CRAFT_BASE
                        ? lockedCraft.contains(value - CRAFT_BASE)
                        : lockedCells.contains(value);
                material = locked ? Material.RED_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE;
            }
            inventory.setItem(raw, pane(material));
        }
    }

    /**
     * Toggles the cell under the given raw slot of the overview window.
     *
     * @return true if a cell was toggled, false for decorations and slots outside the window
     */
    public static boolean toggle(LimitsManager limits, UUID targetUuid, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= LAYOUT.length || LAYOUT[rawSlot] == PLACEHOLDER) {
            return false;
        }
        int value = LAYOUT[rawSlot];
        if (value >= CRAFT_BASE) {
            int cellId = value - CRAFT_BASE;
            boolean locked = limits.getLockedCraftingCells(targetUuid).contains(cellId);
            limits.setCraftingCellLocked(targetUuid, cellId, !locked);
        } else {
            boolean locked = limits.getLockedCells(targetUuid).contains(value);
            limits.setCellLocked(targetUuid, value, !locked);
        }
        return true;
    }

    /** Closes every open overview window; called on shutdown so no window outlives its listener. */
    public static void closeAll(Server server) {
        for (Player player : server.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SlotOverviewHolder) {
                player.closeInventory();
            }
        }
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static int[] buildLayout() {
        int[] layout = new int[SIZE];
        Arrays.fill(layout, PLACEHOLDER);

        layout[0] = EquipmentPart.BOOTS.getSlot();
        layout[1] = EquipmentPart.LEGGINGS.getSlot();
        layout[2] = EquipmentPart.CHESTPLATE.getSlot();
        layout[3] = EquipmentPart.HELMET.getSlot();
        layout[5] = CRAFT_BASE + CraftingCell.TOP_LEFT.getId();
        layout[6] = CRAFT_BASE + CraftingCell.TOP_RIGHT.getId();
        layout[8] = CRAFT_BASE + CraftingCell.RESULT.getId();

        layout[9] = EquipmentPart.OFFHAND.getSlot();
        layout[14] = CRAFT_BASE + CraftingCell.BOTTOM_LEFT.getId();
        layout[15] = CRAFT_BASE + CraftingCell.BOTTOM_RIGHT.getId();

        fillStorageRow(layout, 18, SlotLayout.HOTBAR_SIZE);
        fillStorageRow(layout, 27, SlotLayout.HOTBAR_SIZE + SlotLayout.ROW_SIZE);
        fillStorageRow(layout, 36, SlotLayout.HOTBAR_SIZE + 2 * SlotLayout.ROW_SIZE);
        fillStorageRow(layout, 45, 0);
        return layout;
    }

    private static void fillStorageRow(int[] layout, int rawStart, int slotStart) {
        for (int i = 0; i < SlotLayout.ROW_SIZE; i++) {
            layout[rawStart + i] = slotStart + i;
        }
    }
}
