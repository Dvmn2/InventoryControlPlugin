package net.dvmn2.inventorycontrolplugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import net.dvmn2.inventorycontrolplugin.model.CraftingCell;
import net.dvmn2.inventorycontrolplugin.model.LockedSlotsPacket;
import net.dvmn2.inventorycontrolplugin.model.SlotLayout;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps every online player's inventory consistent with their restrictions and tells the client mod
 * which slots are locked.
 * <p>
 * Locked slots are always empty. When an item shows up in one (pickup, shift-click, {@code /give}, a lifted
 * limit), {@link #enforce(Player)} moves it to an open storage slot, hotbar first and then the main inventory
 * bottom row up; whatever does not fit is dropped. Players in creative mode and players with
 * {@link Permissions#BYPASS} are never restricted.
 * <p>
 * Must be used from the server main thread only.
 */
public final class InventoryManager {

    private static final long TICKS_PER_SECOND = 20L;
    private static final long JOIN_ENFORCE_DELAY_TICKS = 2L;
    private static final long ENFORCE_START_DELAY_TICKS = 5L;

    /** Pickup delay of dropped leftovers, ticks; stops them from being picked up again at once. */
    private static final int EVICT_PICKUP_DELAY_TICKS = 40;

    private final JavaPlugin plugin;
    private final Supplier<InventoryControlSettings> settings;
    private final LimitsManager limits;

    /** Last packet sent to each modded player, to skip duplicates. */
    private final Map<UUID, LockedSlotsPacket> lastSent = new HashMap<>();

    private BukkitTask enforceTask;

    public InventoryManager(JavaPlugin plugin, Supplier<InventoryControlSettings> settings, LimitsManager limits) {
        this.plugin = plugin;
        this.settings = settings;
        this.limits = limits;
    }

    // ---------- Lifecycle ----------

    /** Registers the mod channel and starts the periodic check. */
    public void start() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, LockedSlotsPacket.CHANNEL);
        scheduleEnforceTask();
    }

    /** Stops the periodic check and releases the channel. */
    public void shutdown() {
        if (enforceTask != null) {
            enforceTask.cancel();
            enforceTask = null;
        }
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, LockedSlotsPacket.CHANNEL);
        lastSent.clear();
    }

    /** Applies new settings: restarts the periodic check with the configured interval. */
    public void onSettingsReloaded() {
        scheduleEnforceTask();
    }

    // ---------- Queries ----------

    /** Returns true if the slot is open for the player (bypass and creative open everything). */
    public boolean isSlotUnlocked(Player player, int slot) {
        return !lockedSlotsFor(player).contains(slot);
    }

    /** Returns true if the personal crafting cell is locked for the player. */
    public boolean isCraftingCellLocked(Player player, CraftingCell cell) {
        return lockedCraftingCellsFor(player).contains(cell.getId());
    }

    /** Returns true if the player registered the mod channel. */
    public boolean hasMod(Player player) {
        return player.getListeningPluginChannels().contains(LockedSlotsPacket.CHANNEL);
    }

    /** Returns true if at least part of the stack can be placed in an open storage slot. */
    public boolean hasRoomFor(Player player, ItemStack stack) {
        Set<Integer> locked = lockedSlotsFor(player);
        PlayerInventory inventory = player.getInventory();
        for (int slot : SlotLayout.storageOrder()) {
            if (locked.contains(slot)) {
                continue;
            }
            ItemStack current = inventory.getItem(slot);
            if (isEmpty(current)) {
                return true;
            }
            if (current.getAmount() < current.getMaxStackSize() && current.isSimilar(stack)) {
                return true;
            }
        }
        return false;
    }

    // ---------- Enforcement ----------

    /** Brings the player's inventory in line with their restrictions and syncs the client mod. */
    public void enforce(Player player) {
        Set<Integer> locked = lockedSlotsFor(player);
        PlayerInventory inventory = player.getInventory();
        evictLockedSlots(player, inventory, locked);
        evictLockedCraftingCells(player);
        syncClient(player, locked);
        ensureHeldSlotOpen(inventory, locked);
    }

    /**
     * Runs {@link #enforce(Player)} on the next tick. Used from events that fire before the change is applied.
     *
     * @param resyncClient whether to resend the inventory contents to the client afterwards
     */
    public void scheduleEnforce(Player player, boolean resyncClient) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            enforce(player);
            if (resyncClient) {
                player.updateInventory();
            }
        });
    }

    // ---------- Player events ----------

    /** Enforces the limits shortly after join and starts the mod check if it is enabled. */
    public void onJoin(Player player) {
        // Delay: the inventory is not fully loaded in the join tick.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                enforce(player);
            }
        }, JOIN_ENFORCE_DELAY_TICKS);
        scheduleModCheck(player);
    }

    /** Forgets per-player runtime state. */
    public void onQuit(UUID uuid) {
        lastSent.remove(uuid);
    }

    /** The mod registers its channel after join; send it the current state right away. */
    public void onModChannelRegistered(Player player) {
        lastSent.remove(player.getUniqueId());
        enforce(player);
    }

    // ---------- Internals ----------

    private void scheduleEnforceTask() {
        if (enforceTask != null) {
            enforceTask.cancel();
        }
        long interval = settings.get().enforceIntervalTicks();
        // enforce() is cheap: it does nothing when the locked slots are empty and the state is unchanged.
        enforceTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                enforce(player);
            }
        }, ENFORCE_START_DELAY_TICKS, interval);
    }

    private boolean isExempt(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.hasPermission(Permissions.BYPASS);
    }

    private Set<Integer> lockedSlotsFor(Player player) {
        return isExempt(player) ? Set.of() : limits.getLockedCells(player.getUniqueId());
    }

    private Set<Integer> lockedCraftingCellsFor(Player player) {
        return isExempt(player) ? Set.of() : limits.getLockedCraftingCells(player.getUniqueId());
    }

    private void evictLockedSlots(Player player, PlayerInventory inventory, Set<Integer> locked) {
        if (locked.isEmpty()) {
            return;
        }
        List<ItemStack> displaced = null;
        for (int slot : SlotLayout.allSlots()) {
            if (!locked.contains(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (isEmpty(item)) {
                continue;
            }
            if (displaced == null) {
                displaced = new ArrayList<>();
            }
            displaced.add(item.clone());
            inventory.setItem(slot, null);
        }
        if (displaced == null) {
            return;
        }

        List<Integer> targets = new ArrayList<>();
        for (int slot : SlotLayout.storageOrder()) {
            if (!locked.contains(slot)) {
                targets.add(slot);
            }
        }
        for (ItemStack stack : displaced) {
            ItemStack leftover = insert(inventory, targets, stack);
            if (leftover != null) {
                drop(player, leftover);
            }
        }
    }

    private void evictLockedCraftingCells(Player player) {
        Set<Integer> lockedCells = lockedCraftingCellsFor(player);
        if (lockedCells.isEmpty()) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getType() != InventoryType.CRAFTING) {
            return;
        }
        boolean changed = false;
        for (int raw = 0; raw < top.getSize(); raw++) {
            CraftingCell cell = CraftingCell.byRawSlot(raw);
            if (cell == null || !lockedCells.contains(cell.getId())) {
                continue;
            }
            ItemStack item = top.getItem(raw);
            if (isEmpty(item)) {
                continue;
            }
            // The result slot holds a preview, not a real item, so it is cleared without dropping.
            if (cell != CraftingCell.RESULT) {
                drop(player, item.clone());
            }
            top.setItem(raw, null);
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
    }

    private void drop(Player player, ItemStack stack) {
        player.getWorld().dropItemNaturally(player.getLocation(), stack,
                dropped -> dropped.setPickupDelay(EVICT_PICKUP_DELAY_TICKS));
    }

    /** Stacks onto similar items first, then fills empty slots; returns the remainder, or null if all fit. */
    private static @Nullable ItemStack insert(PlayerInventory inventory, List<Integer> targets, ItemStack stack) {
        ItemStack remaining = stack.clone();

        for (int slot : targets) {
            ItemStack current = inventory.getItem(slot);
            if (isEmpty(current) || !current.isSimilar(remaining)) {
                continue;
            }
            int space = current.getMaxStackSize() - current.getAmount();
            if (space <= 0) {
                continue;
            }
            int moved = Math.min(space, remaining.getAmount());
            ItemStack merged = current.clone();
            merged.setAmount(current.getAmount() + moved);
            inventory.setItem(slot, merged);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() <= 0) {
                return null;
            }
        }

        for (int slot : targets) {
            if (!isEmpty(inventory.getItem(slot))) {
                continue;
            }
            int put = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
            ItemStack placed = remaining.clone();
            placed.setAmount(put);
            inventory.setItem(slot, placed);
            remaining.setAmount(remaining.getAmount() - put);
            if (remaining.getAmount() <= 0) {
                return null;
            }
        }
        return remaining;
    }

    /** If the selected hotbar slot is locked, selects the first open one. */
    private static void ensureHeldSlotOpen(PlayerInventory inventory, Set<Integer> locked) {
        if (!locked.contains(inventory.getHeldItemSlot())) {
            return;
        }
        for (int slot = 0; slot < SlotLayout.HOTBAR_SIZE; slot++) {
            if (!locked.contains(slot)) {
                inventory.setHeldItemSlot(slot);
                return;
            }
        }
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private void syncClient(Player player, Set<Integer> locked) {
        if (!hasMod(player)) {
            return;
        }
        LockedSlotsPacket packet = LockedSlotsPacket.of(locked, lockedCraftingCellsFor(player));
        if (packet.equals(lastSent.get(player.getUniqueId()))) {
            return;
        }
        player.sendPluginMessage(plugin, LockedSlotsPacket.CHANNEL, packet.encode());
        lastSent.put(player.getUniqueId(), packet);
    }

    private void scheduleModCheck(Player player) {
        InventoryControlSettings current = settings.get();
        if (!current.requireClientMod()) {
            return;
        }
        long delay = current.modCheckTimeoutSeconds() * TICKS_PER_SECOND;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.hasPermission(Permissions.BYPASS) || hasMod(player)) {
                return;
            }
            player.kick(Lang.get(Lang.Key.KICK_NO_MOD, player));
        }, delay);
    }
}
