package net.dvmn2.inventorycontrolplugin.gui;

import net.dvmn2.inventorycontrolplugin.InventoryControlPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * GUI обзора ячеек ({@link SlotOverviewGui}) — read-only снаружи: предметы в него
 * положить/забрать нельзя, клик либо переключает ячейку, либо просто отменяется.
 */
public final class SlotOverviewGuiListener implements Listener {

    private final InventoryControlPlugin plugin;

    public SlotOverviewGuiListener(InventoryControlPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SlotOverviewHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return; // клик по инвентарю администратора снизу
        }
        SlotOverviewGui.handleClick(plugin, holder.getTargetUuid(), event.getRawSlot());
        SlotOverviewGui.render(holder.getInventory(), holder.getTargetUuid(), plugin.getDataManager());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SlotOverviewHolder) {
            event.setCancelled(true);
        }
    }
}