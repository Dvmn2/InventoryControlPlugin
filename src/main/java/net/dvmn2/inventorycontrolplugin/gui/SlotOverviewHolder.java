package net.dvmn2.inventorycontrolplugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Маркер-держатель GUI-обзора заблокированных ячеек игрока (см. {@link SlotOverviewGui}).
 */
public final class SlotOverviewHolder implements InventoryHolder {

    private final UUID targetUuid;
    private Inventory inventory;

    public SlotOverviewHolder(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}