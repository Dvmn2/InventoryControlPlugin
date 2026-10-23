package net.dvmn2.inventorycontrolplugin;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import net.dvmn2.inventorycontrolplugin.model.EquipmentPart;
import net.dvmn2.inventorycontrolplugin.model.PlayerLimits;
import net.dvmn2.inventorycontrolplugin.model.SlotLayout;
import net.dvmn2.inventorycontrolplugin.storage.LimitsStore;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Owns the per-player restrictions and their persistence.
 * <p>
 * Must be used from the server main thread only. Changes mark the state dirty; a periodic task writes a
 * snapshot asynchronously, and {@link #shutdown()} writes the final state synchronously.
 * <p>
 * There is no separate "slot limit N": {@link #setSlots} is a bulk edit of the same locked-cell set that
 * {@link #setCellLocked} edits one cell at a time.
 */
public final class LimitsManager {

    private static final long TICKS_PER_SECOND = 20L;

    /** Period of the dirty-flag check, ticks. */
    private static final long SAVE_INTERVAL_TICKS = 30L * TICKS_PER_SECOND;

    private final JavaPlugin plugin;
    private final LimitsStore store;
    private final Map<UUID, PlayerLimits> values = new HashMap<>();

    private boolean dirty;
    private long snapshotVersion;
    private BukkitTask saveTask;

    public LimitsManager(JavaPlugin plugin, LimitsStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    // ---------- Lifecycle ----------

    /** Loads the stored restrictions. Call once before {@link #start()}. */
    public void load() {
        values.clear();
        values.putAll(store.load());
    }

    /** Starts the periodic save task. */
    public void start() {
        saveTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::saveIfDirty, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }

    /** Stops the save task and writes the final state synchronously. */
    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (dirty) {
            dirty = false;
            store.write(++snapshotVersion, Map.copyOf(values));
        }
    }

    /** Returns the number of players that have any restriction. */
    public int getRestrictedPlayerCount() {
        return values.size();
    }

    // ---------- Queries ----------

    /** Returns the locked PlayerInventory slots (0..40) of the player. The set is immutable. */
    public Set<Integer> getLockedCells(UUID uuid) {
        return limitsOf(uuid).lockedCells();
    }

    /** Returns the locked crafting cell ids (1..5) of the player. The set is immutable. */
    public Set<Integer> getLockedCraftingCells(UUID uuid) {
        return limitsOf(uuid).lockedCraftingCells();
    }

    /** Returns the number of currently open storage slots (hotbar + main inventory). */
    public int getOpenStorageSlots(UUID uuid) {
        Set<Integer> locked = getLockedCells(uuid);
        int open = 0;
        for (int slot : SlotLayout.storageOrder()) {
            if (!locked.contains(slot)) {
                open++;
            }
        }
        return open;
    }

    /** Returns the equipment parts that are currently closed. */
    public Set<EquipmentPart> getClosedParts(UUID uuid) {
        Set<Integer> locked = getLockedCells(uuid);
        Set<EquipmentPart> closed = EnumSet.noneOf(EquipmentPart.class);
        for (EquipmentPart part : EquipmentPart.values()) {
            if (locked.contains(part.getSlot())) {
                closed.add(part);
            }
        }
        return closed;
    }

    // ---------- Changes ----------

    /**
     * Opens the first {@code amount} storage slots (in {@link SlotLayout#storageOrder()} order) and locks the rest.
     * Armor, offhand and crafting are not touched.
     */
    public void setSlots(UUID uuid, int amount) {
        int open = SlotLayout.clamp(amount);
        update(uuid, limits -> {
            Set<Integer> cells = new HashSet<>(limits.lockedCells());
            List<Integer> order = SlotLayout.storageOrder();
            for (int i = 0; i < order.size(); i++) {
                if (i < open) {
                    cells.remove(order.get(i));
                } else {
                    cells.add(order.get(i));
                }
            }
            return limits.withLockedCells(cells);
        });
    }

    /** Opens or closes the given equipment parts. */
    public void setPartsOpen(UUID uuid, Collection<EquipmentPart> parts, boolean open) {
        update(uuid, limits -> {
            Set<Integer> cells = new HashSet<>(limits.lockedCells());
            for (EquipmentPart part : parts) {
                if (open) {
                    cells.remove(part.getSlot());
                } else {
                    cells.add(part.getSlot());
                }
            }
            return limits.withLockedCells(cells);
        });
    }

    /** Locks or unlocks one PlayerInventory slot (0..40). */
    public void setCellLocked(UUID uuid, int slot, boolean locked) {
        update(uuid, limits -> {
            Set<Integer> cells = new HashSet<>(limits.lockedCells());
            if (locked) {
                cells.add(slot);
            } else {
                cells.remove(slot);
            }
            return limits.withLockedCells(cells);
        });
    }

    /** Locks or unlocks one personal crafting cell (id 1..5). */
    public void setCraftingCellLocked(UUID uuid, int cellId, boolean locked) {
        update(uuid, limits -> {
            Set<Integer> cells = new HashSet<>(limits.lockedCraftingCells());
            if (locked) {
                cells.add(cellId);
            } else {
                cells.remove(cellId);
            }
            return limits.withLockedCraftingCells(cells);
        });
    }

    /** Removes every restriction of the player. */
    public void reset(UUID uuid) {
        update(uuid, limits -> PlayerLimits.EMPTY);
    }

    // ---------- Internals ----------

    private PlayerLimits limitsOf(UUID uuid) {
        return values.getOrDefault(uuid, PlayerLimits.EMPTY);
    }

    private void update(UUID uuid, UnaryOperator<PlayerLimits> change) {
        PlayerLimits updated = change.apply(limitsOf(uuid));
        if (updated.isDefault()) {
            values.remove(uuid);
        } else {
            values.put(uuid, updated);
        }
        dirty = true;
    }

    private void saveIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        long version = ++snapshotVersion;
        // PlayerLimits is immutable, so the copy is a consistent snapshot that is safe to hand to another thread.
        Map<UUID, PlayerLimits> snapshot = Map.copyOf(values);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!store.write(version, snapshot) && plugin.isEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> dirty = true);
            }
        });
    }
}
