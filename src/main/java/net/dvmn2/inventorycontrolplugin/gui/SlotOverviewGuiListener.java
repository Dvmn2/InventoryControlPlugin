package net.dvmn2.inventorycontrolplugin.gui;

import net.dvmn2.inventorycontrolplugin.InventoryManager;
import net.dvmn2.inventorycontrolplugin.LimitsManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Makes the overview window ({@link SlotOverviewGui}) read-only: no item can be taken or placed in either half of
 * the window. A click on a pane of the top inventory toggles that cell.
 * <p>
 * Must be registered before {@link net.dvmn2.inventorycontrolplugin.InventoryListener}, so that the
 * cancellation here is already visible to its {@code ignoreCancelled} handlers.
 */
public final class SlotOverviewGuiListener implements Listener {

    private final LimitsManager limits;
    private final InventoryManager inventories;

    public SlotOverviewGuiListener(LimitsManager limits, InventoryManager inventories) {
        this.limits = limits;
        this.inventories = inventories;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SlotOverviewHolder holder)) {
            return;
        }
        // Cancels clicks in both halves, shift-clicks from the lower inventory and number-key swaps.
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (!SlotOverviewGui.toggle(limits, holder.getTargetUuid(), event.getRawSlot())) {
            return;
        }
        Player target = Bukkit.getPlayer(holder.getTargetUuid());
        if (target != null) {
            inventories.enforce(target);
        }
        SlotOverviewGui.render(holder.getInventory(), holder.getTargetUuid(), limits);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SlotOverviewHolder) {
            event.setCancelled(true);
        }
    }
}
