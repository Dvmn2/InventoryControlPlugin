package net.dvmn2.inventorycontrolplugin;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Следит, чтобы у игрока были доступны только разрешённые слоты.
 * <p>
 * Закрытые слоты ВСЕГДА пустые — никаких предметов-заглушек. Как только в
 * закрытом слоте оказывается предмет (подбор, шифт-клик из сундука, /give,
 * снятие лимита и т.д.), {@link #enforce(Player)} забирает его оттуда,
 * пытается разложить по открытым слотам хранилища (хотбар → основной
 * инвентарь снизу вверх), а то, что не влезло, выбрасывает на землю.
 * <p>
 * Отдельно от слотов может быть заблокирован личный крафт 2x2 (сетка в
 * собственном инвентаре игрока, не верстак) — см. {@link #isCraftingLocked(Player)}.
 * <p>
 * В креативном режиме (и с правом bypass) ограничения не действуют вообще —
 * ни на слоты, ни на крафт.
 * <p>
 * Клиентский мод получает список закрытых слотов и флаг крафта и рисует их
 * как недоступные; сервер при этом остаётся единственным источником истины.
 */
public final class InventoryEnforcer implements Listener {

    /**
     * Канал plugin-message для клиентского Fabric-мода. Формат:
     * VarInt count, затем count × VarInt slot (нумерация PlayerInventory),
     * затем 1 байт — заблокирован ли личный крафт 2x2.
     */
    public static final String LOCKED_SLOTS_CHANNEL = "dvmn2:locked_slots";

    /**
     * Снимок того, что последний раз отправили конкретному игроку — чтобы не слать пакет зря.
     */
    private record SentState(Set<Integer> locked, Set<Integer> craftingLockedCells) {
    }

    private final InventoryControlPlugin plugin;

    private final Map<UUID, SentState> lastSentLocked = new HashMap<>();

    public InventoryEnforcer(InventoryControlPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Основная логика
    // ------------------------------------------------------------------

    /**
     * Множество открытых слотов игрока. Игрок с правом bypass или в креативе — без ограничений.
     */
    public Set<Integer> unlockedFor(Player player) {
        if (player.hasPermission(InventoryControlPlugin.BYPASS_PERMISSION)
                || player.getGameMode() == GameMode.CREATIVE) {
            return InventoryComputer.allSlots();
        }
        return plugin.getDataManager().getUnlockedSlots(player.getUniqueId());
    }

    public Set<Integer> lockedCraftingCellsFor(Player player) {
        if (player.hasPermission(InventoryControlPlugin.BYPASS_PERMISSION)
                || player.getGameMode() == GameMode.CREATIVE) {
            return Set.of();
        }
        return plugin.getDataManager().getLockedCraftingCells(player.getUniqueId());
    }

    /**
     * Приводит инвентарь игрока в соответствие с его лимитами.
     */
    public void enforce(Player player) {
        Set<Integer> unlocked = unlockedFor(player);
        PlayerInventory inv = player.getInventory();

        evictLockedSlots(player, inv, unlocked);
        enforceCraftingLock(player);
        syncLockedSlots(player, unlocked);
        ensureHeldSlotOpen(inv, unlocked);
    }

    private void evictLockedSlots(Player player, PlayerInventory inv, Set<Integer> unlocked) {
        List<ItemStack> displaced = null;
        for (int slot : InventoryComputer.allSlots()) {
            if (unlocked.contains(slot)) {
                continue;
            }
            ItemStack item = inv.getItem(slot);
            if (isEmpty(item)) {
                continue;
            }
            if (displaced == null) {
                displaced = new ArrayList<>();
            }
            displaced.add(item.clone());
            inv.setItem(slot, null);
        }
        if (displaced == null) {
            return;
        }

        // Раскладываем только в хранилище (хотбар/основной инвентарь), в порядке открытия.
        List<Integer> targets = new ArrayList<>();
        for (int slot : InventoryComputer.storageOrder()) {
            if (unlocked.contains(slot)) {
                targets.add(slot);
            }
        }

        for (ItemStack stack : displaced) {
            ItemStack leftover = insert(inv, targets, stack);
            if (leftover != null) {
                // Небольшая задержка подбора — чтобы выброшенное не "прилипло" обратно мгновенно.
                player.getWorld().dropItemNaturally(player.getLocation(), leftover,
                        dropped -> dropped.setPickupDelay(40));
            }
        }
    }

    private void enforceCraftingLock(Player player) {
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
            if (raw != 0) { // 0 — результат, его не выбрасываем, просто чистим
                player.getWorld().dropItemNaturally(player.getLocation(), item.clone(),
                        dropped -> dropped.setPickupDelay(40));
            }
            top.setItem(raw, null);
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
    }

    /**
     * Кладёт стак в указанные слоты: сначала доукладывает в такие же стаки,
     * потом занимает пустые.
     *
     * @return остаток, который не влез, либо {@code null}, если влезло всё
     */
    private static ItemStack insert(PlayerInventory inv, List<Integer> targets, ItemStack stack) {
        ItemStack remaining = stack.clone();

        for (int slot : targets) {
            ItemStack current = inv.getItem(slot);
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
            inv.setItem(slot, merged);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() <= 0) {
                return null;
            }
        }

        for (int slot : targets) {
            if (!isEmpty(inv.getItem(slot))) {
                continue;
            }
            int put = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
            ItemStack placed = remaining.clone();
            placed.setAmount(put);
            inv.setItem(slot, placed);
            remaining.setAmount(remaining.getAmount() - put);
            if (remaining.getAmount() <= 0) {
                return null;
            }
        }
        return remaining;
    }

    /**
     * Есть ли у игрока куда положить хотя бы часть этого стака (только открытые слоты хранилища).
     */
    private boolean hasRoomFor(Player player, ItemStack stack) {
        Set<Integer> unlocked = unlockedFor(player);
        PlayerInventory inv = player.getInventory();
        for (int slot : InventoryComputer.storageOrder()) {
            if (!unlocked.contains(slot)) {
                continue;
            }
            ItemStack current = inv.getItem(slot);
            if (isEmpty(current)) {
                return true;
            }
            if (current.getAmount() < current.getMaxStackSize() && current.isSimilar(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Если выбранный слот хотбара закрыт — переключаемся на первый открытый.
     * (Если открытых слотов хотбара нет, ничего не делаем: слот всё равно пустой.)
     */
    private void ensureHeldSlotOpen(PlayerInventory inv, Set<Integer> unlocked) {
        if (unlocked.contains(inv.getHeldItemSlot())) {
            return;
        }
        for (int i = 0; i < InventoryComputer.HOTBAR_SIZE; i++) {
            if (unlocked.contains(i)) {
                inv.setHeldItemSlot(i);
                return;
            }
        }
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    // ------------------------------------------------------------------
    // Синхронизация с клиентским модом
    // ------------------------------------------------------------------

    private static boolean hasMod(Player player) {
        return player.getListeningPluginChannels().contains(LOCKED_SLOTS_CHANNEL);
    }

    /**
     * Шлёт клиентскому моду список закрытых слотов и флаг крафта — только если
     * что-то изменилось с прошлой отправки и мод зарегистрировал канал.
     */
    private void syncLockedSlots(Player player, Set<Integer> unlocked) {
        if (!hasMod(player)) {
            return;
        }

        Set<Integer> locked = new TreeSet<>();
        for (int slot : InventoryComputer.allSlots()) {
            if (!unlocked.contains(slot)) {
                locked.add(slot);
            }
        }
        Set<Integer> craftingLockedCells = new TreeSet<>(lockedCraftingCellsFor(player));
        SentState state = new SentState(locked, craftingLockedCells);
        if (state.equals(lastSentLocked.get(player.getUniqueId()))) {
            return;
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            writeVarInt(out, locked.size());
            for (int slot : locked) {
                writeVarInt(out, slot);
            }
            writeVarInt(out, craftingLockedCells.size());
            for (int cellId : craftingLockedCells) {
                writeVarInt(out, cellId);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сформировать пакет locked_slots: " + ex.getMessage());
            return;
        }

        try {
            player.sendPluginMessage(plugin, LOCKED_SLOTS_CHANNEL, bytes.toByteArray());
            lastSentLocked.put(player.getUniqueId(), state);
        } catch (IllegalArgumentException ex) {
            // Канал не зарегистрирован как исходящий — см. onEnable().
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private void scheduleEnforce(Player player, boolean resyncClient) {
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

    // ------------------------------------------------------------------
    // Вход / выход / проверка мода / смена режима игры
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // небольшая задержка, чтобы инвентарь успел полностью загрузиться
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                enforce(player);
            }
        }, 2L);
        scheduleModCheck(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastSentLocked.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Клиент с модом регистрирует наш канал уже после входа — в этот момент
     * шлём ему актуальный список закрытых слотов.
     */
    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        if (!LOCKED_SLOTS_CHANNEL.equals(event.getChannel())) {
            return;
        }
        Player player = event.getPlayer();
        lastSentLocked.remove(player.getUniqueId());
        enforce(player);
    }

    /**
     * Смена режима игры (например, /gamemode или переключение через меню) не
     * ждёт периодической подстраховки — сразу пересчитываем и досылаем клиенту.
     * Событие прилетает до фактической смены режима, поэтому enforce() зовём
     * через scheduleEnforce (на следующем тике), а не прямо в хендлере.
     */
    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        scheduleEnforce(event.getPlayer(), true);
    }

    private void scheduleModCheck(Player player) {
        if (!plugin.getConfig().getBoolean("settings.require-client-mod", true)) {
            return;
        }
        long seconds = Math.max(1L, plugin.getConfig().getLong("settings.mod-check-timeout-seconds", 10L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()
                    || player.hasPermission(InventoryControlPlugin.BYPASS_PERMISSION)
                    || hasMod(player)) {
                return;
            }
            player.kick(Component.text(Lang.get(Lang.Key.KICK_NO_MOD, player), NamedTextColor.RED));
        }, seconds * 20L);
    }

    // ------------------------------------------------------------------
    // Инвентарь: клики, drag, креатив
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Set<Integer> unlocked = unlockedFor(player);
        boolean deny = false;

        // Прямой клик по закрытому слоту в собственном инвентаре игрока
        if (event.getClickedInventory() == player.getInventory() && !unlocked.contains(event.getSlot())) {
            deny = true;
        }
        // Обмен с хотбаром через цифровые клавиши 1-9
        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0 && hotbarButton < InventoryComputer.HOTBAR_SIZE && !unlocked.contains(hotbarButton)) {
            deny = true;
        }
        // Обмен с оффхендом клавишей F прямо в окне инвентаря
        if (event.getClick() == ClickType.SWAP_OFFHAND && !unlocked.contains(InventoryComputer.OFFHAND_SLOT)) {
            deny = true;
        }

        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING
                && event.getRawSlot() >= 0 && event.getRawSlot() <= 4) {
            CraftingCell cell = CraftingCell.byRawSlot(event.getRawSlot());
            if (cell != null && lockedCraftingCellsFor(player).contains(cell.getId())) {
                deny = true;
            }
        }

        if (deny) {
            event.setCancelled(true);
        }

        // Шифт-клики из чужого инвентаря и т.п. могут положить предмет в закрытый слот
        // в обход проверок выше — донормализуем на следующем тике.
        scheduleEnforce(player, deny);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Set<Integer> unlocked = unlockedFor(player);
        PlayerInventory playerInv = player.getInventory();
// СТАЛО:
        Set<Integer> lockedCraftCells = lockedCraftingCellsFor(player);
        boolean craftingTop = event.getView().getTopInventory().getType() == InventoryType.CRAFTING;

        for (int rawSlot : event.getRawSlots()) {
            if (craftingTop && rawSlot >= 0 && rawSlot <= 4) {
                CraftingCell cell = CraftingCell.byRawSlot(rawSlot);
                if (cell != null && lockedCraftCells.contains(cell.getId())) {
                    event.setCancelled(true);
                    scheduleEnforce(player, true);
                    return;
                }
            }
            if (event.getView().getInventory(rawSlot) != playerInv) {
                continue; // слот принадлежит не инвентарю игрока
            }
            int slot = event.getView().convertSlot(rawSlot);
            if (!unlocked.contains(slot)) {
                event.setCancelled(true); // предметы вернутся на курсор
                scheduleEnforce(player, true);
                return;
            }
        }
        scheduleEnforce(player, false);
    }

    /**
     * Креатив: предметы из вкладок идут через InventoryCreativeEvent, а не через ClickEvent.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreativeClick(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != player.getInventory()) {
            return;
        }
        if (!unlockedFor(player).contains(event.getSlot())) {
            event.setCancelled(true);
            scheduleEnforce(player, true);
        }
    }

    // ------------------------------------------------------------------
    // Руки и хотбар
    // ------------------------------------------------------------------

    /**
     * Клавиша F вне инвентаря.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Set<Integer> unlocked = unlockedFor(event.getPlayer());
        boolean offhandLocked = !unlocked.contains(InventoryComputer.OFFHAND_SLOT);
        boolean heldSlotLocked = !unlocked.contains(event.getPlayer().getInventory().getHeldItemSlot());
        if (offhandLocked || heldSlotLocked) {
            event.setCancelled(true);
        }
    }

    /**
     * Запрещаем выбирать закрытый слот хотбара (колесо / цифры).
     */
    @EventHandler(ignoreCancelled = true)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        if (!unlockedFor(event.getPlayer()).contains(event.getNewSlot())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Подбор предметов
    // ------------------------------------------------------------------

    /**
     * Сам предмет в закрытый слот мы не "предотвращаем" — его потом переложит
     * {@link #enforce(Player)}. Но если открытых слотов для него вообще нет,
     * подбор отменяем: иначе предмет бесконечно подбирался бы и выбрасывался обратно.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!hasRoomFor(player, event.getItem().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        scheduleEnforce(player, false);
    }

    // ------------------------------------------------------------------
    // Надевание брони в обход окна инвентаря
    // ------------------------------------------------------------------

    private static EquipmentPart equipTarget(ItemStack item) {
        if (isEmpty(item)) {
            return null;
        }
        Equippable equippable = item.getData(DataComponentTypes.EQUIPPABLE);
        return equippable == null ? null : EquipmentPart.fromEquipmentSlot(equippable.slot());
    }

    /**
     * ПКМ предметом-бронёй: без этого можно надеть закрытую часть, не открывая инвентарь.
     * Запрещаем только использование предмета, взаимодействие с блоком не трогаем.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentPart part = equipTarget(event.getItem());
        if (part != null && !unlockedFor(event.getPlayer()).contains(part.getSlot())) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /**
     * Раздатчик, надевающий броню на игрока.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDispenseArmor(BlockDispenseArmorEvent event) {
        if (!(event.getTargetEntity() instanceof Player player)) {
            return;
        }
        EquipmentPart part = equipTarget(event.getItem());
        if (part != null && !unlockedFor(player).contains(part.getSlot())) {
            event.setCancelled(true);
        }
    }
}