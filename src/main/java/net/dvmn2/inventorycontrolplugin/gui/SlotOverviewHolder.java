package net.dvmn2.inventorycontrolplugin.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker holder of the slot overview window ({@link SlotOverviewGui}). Holds the player being edited.
 * Used on the main thread only.
 */
public final class SlotOverviewHolder implements InventoryHolder {

    private final UUID targetUuid;
    private Inventory inventory;

    public SlotOverviewHolder(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
