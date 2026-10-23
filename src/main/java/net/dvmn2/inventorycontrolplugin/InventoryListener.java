package net.dvmn2.inventorycontrolplugin;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.dvmn2.inventorycontrolplugin.model.CraftingCell;
import net.dvmn2.inventorycontrolplugin.model.EquipmentPart;
import net.dvmn2.inventorycontrolplugin.model.LockedSlotsPacket;
import net.dvmn2.inventorycontrolplugin.model.SlotLayout;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Bukkit event handlers that stop players from using locked slots. All decisions are delegated to
 * {@link InventoryManager}; this class only filters events.
 * <p>
 * Items that reach a locked slot despite these checks (shift-click from a chest, hoppers, {@code /give}) are
 * moved out on the next tick by {@link InventoryManager#enforce(Player)}.
 */
public final class InventoryListener implements Listener {

    private final InventoryManager inventories;

    public InventoryListener(InventoryManager inventories) {
        this.inventories = inventories;
    }

    // ---------- Join, quit, mod channel, game mode ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        inventories.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inventories.onQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        if (LockedSlotsPacket.CHANNEL.equals(event.getChannel())) {
            inventories.onModChannelRegistered(event.getPlayer());
        }
    }

    /** Fires before the mode changes, so the re-check runs on the next tick. */
    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        inventories.scheduleEnforce(event.getPlayer(), true);
    }

    // ---------- Inventory windows ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean deny = false;

        // Direct click on a locked slot of the player's own inventory.
        if (event.getClickedInventory() == player.getInventory()
                && !inventories.isSlotUnlocked(player, event.getSlot())) {
            deny = true;
        }
        // Number keys swap with a hotbar slot.
        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0 && hotbarButton < SlotLayout.HOTBAR_SIZE
                && !inventories.isSlotUnlocked(player, hotbarButton)) {
            deny = true;
        }
        // The F key swaps with the offhand inside the inventory window.
        if (event.getClick() == ClickType.SWAP_OFFHAND
                && !inventories.isSlotUnlocked(player, SlotLayout.OFFHAND_SLOT)) {
            deny = true;
        }
        if (isLockedCraftingRawSlot(player, event.getView(), event.getRawSlot())) {
            deny = true;
        }

        if (deny) {
            event.setCancelled(true);
        }
        // Shift-clicks from another inventory can still land in a locked slot; normalise on the next tick.
        inventories.scheduleEnforce(player, deny);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryView view = event.getView();
        for (int rawSlot : event.getRawSlots()) {
            boolean lockedCrafting = isLockedCraftingRawSlot(player, view, rawSlot);
            boolean lockedStorage = view.getInventory(rawSlot) == player.getInventory()
                    && !inventories.isSlotUnlocked(player, view.convertSlot(rawSlot));
            if (lockedCrafting || lockedStorage) {
                event.setCancelled(true); // the dragged items return to the cursor
                inventories.scheduleEnforce(player, true);
                return;
            }
        }
        inventories.scheduleEnforce(player, false);
    }

    /** Creative inventory tabs send {@link InventoryCreativeEvent} instead of a click event. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreativeClick(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != player.getInventory()) {
            return;
        }
        if (!inventories.isSlotUnlocked(player, event.getSlot())) {
            event.setCancelled(true);
            inventories.scheduleEnforce(player, true);
        }
    }

    // ---------- Hands and hotbar ----------

    /** The F key outside the inventory window. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        boolean offhandLocked = !inventories.isSlotUnlocked(player, SlotLayout.OFFHAND_SLOT);
        boolean heldLocked = !inventories.isSlotUnlocked(player, player.getInventory().getHeldItemSlot());
        if (offhandLocked || heldLocked) {
            event.setCancelled(true);
        }
    }

    /** Selecting a locked hotbar slot with the wheel or number keys is not allowed. */
    @EventHandler(ignoreCancelled = true)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        if (!inventories.isSlotUnlocked(event.getPlayer(), event.getNewSlot())) {
            event.setCancelled(true);
        }
    }

    // ---------- Pickup ----------

    /**
     * A picked-up item may land in a locked slot; the manager moves it on the next tick. If there is no open
     * slot at all the pickup is cancelled, otherwise the item would be picked up and dropped in a loop.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!inventories.hasRoomFor(player, event.getItem().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        inventories.scheduleEnforce(player, false);
    }

    // ---------- Wearing armor outside the inventory window ----------

    /**
     * Right-clicking armor equips it without opening the inventory. Only the use of the item is denied; the
     * block interaction stays. The event fires once per hand and denying is idempotent, so the hand is not
     * filtered: filtering would let armor in the offhand be worn into a closed part.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentPart part = equipTarget(event.getItem());
        if (part != null && !inventories.isSlotUnlocked(event.getPlayer(), part.getSlot())) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /** A dispenser putting armor on a player. */
    @EventHandler(ignoreCancelled = true)
    public void onDispenseArmor(BlockDispenseArmorEvent event) {
        if (!(event.getTargetEntity() instanceof Player player)) {
            return;
        }
        EquipmentPart part = equipTarget(event.getItem());
        if (part != null && !inventories.isSlotUnlocked(player, part.getSlot())) {
            event.setCancelled(true);
        }
    }

    // ---------- Helpers ----------

    private boolean isLockedCraftingRawSlot(Player player, InventoryView view, int rawSlot) {
        if (view.getTopInventory().getType() != InventoryType.CRAFTING) {
            return false;
        }
        CraftingCell cell = CraftingCell.byRawSlot(rawSlot);
        return cell != null && inventories.isCraftingCellLocked(player, cell);
    }

    private static @Nullable EquipmentPart equipTarget(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        Equippable equippable = item.getData(DataComponentTypes.EQUIPPABLE);
        return equippable == null ? null : EquipmentPart.fromEquipmentSlot(equippable.slot());
    }
}
