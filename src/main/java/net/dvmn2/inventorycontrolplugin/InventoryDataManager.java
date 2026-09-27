package net.dvmn2.inventorycontrolplugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Хранит лимиты игроков в plugins/InventoryControlPlugin/players.yml.
 * <p>
 * Формат:
 * <pre>
 * &lt;uuid&gt;:
 *   locked-cells: [36, 5]            # заблокированные ячейки PlayerInventory (0-40:
 *                                    # 0-8 хотбар, 9-35 инвентарь, 36-39 броня, 40 оффхенд).
 *                                    # /inventorycontrol set N — это массовая правка того же
 *                                    # списка: слоты хранилища с индексом >= N (в порядке
 *                                    # открытия, см. InventoryComputer#storageOrder) добавляются
 *                                    # сюда, с индексом < N — снимаются. Отдельного "лимита N"
 *                                    # больше не существует — это лишь способ быстро заполнить
 *                                    # locked-cells.
 *   locked-crafting-cells: [1, 5]    # заблокированные ячейки личного крафта 2x2 (id 1-5, см. CraftingCell)
 * </pre>
 * Игрок без записи — без ограничений. Записи без секции (старый формат) молча игнорируются.
 */
public final class InventoryDataManager {

    private static final class Limits {
        final Set<Integer> lockedCells = new HashSet<>();
        final Set<Integer> lockedCraftingCells = new HashSet<>();

        boolean isDefault() {
            return lockedCells.isEmpty() && lockedCraftingCells.isEmpty();
        }
    }

    private final InventoryControlPlugin plugin;
    private final File file;
    private final Map<UUID, Limits> values = new HashMap<>();

    public InventoryDataManager(InventoryControlPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public void load() {
        values.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Некорректный UUID в players.yml: " + key);
                continue;
            }
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Limits limits = new Limits();
            for (int slot : section.getIntegerList("locked-cells")) {
                if (slot >= 0 && slot <= InventoryComputer.LAST_SLOT) {
                    limits.lockedCells.add(slot);
                } else {
                    plugin.getLogger().warning("players.yml: некорректная ячейка " + slot + " у " + key);
                }
            }
            for (int cellId : section.getIntegerList("locked-crafting-cells")) {
                if (CraftingCell.byId(cellId) != null) {
                    limits.lockedCraftingCells.add(cellId);
                } else {
                    plugin.getLogger().warning("players.yml: некорректная ячейка крафта " + cellId + " у " + key);
                }
            }
            if (!limits.isDefault()) {
                values.put(uuid, limits);
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Limits> e : values.entrySet()) {
            ConfigurationSection section = cfg.createSection(e.getKey().toString());
            section.set("locked-cells", new ArrayList<>(e.getValue().lockedCells));
            section.set("locked-crafting-cells", new ArrayList<>(e.getValue().lockedCraftingCells));
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить players.yml", ex);
        }
    }

    // ---------- чтение ----------

    /**
     * Количество сейчас открытых слотов хранилища (хотбар + основной инвентарь).
     * Отдельного лимита N не хранится — это просто подсчёт по факту, нужен только
     * для отображения в {@code /inventorycontrol get}. Если внутри диапазона вперемешку
     * есть точечные блокировки и открытые ячейки, число всё равно честное — это именно
     * "сколько сейчас открыто", а не позиция какой-то границы.
     */
    public int getSlots(UUID uuid) {
        Limits l = values.get(uuid);
        if (l == null) {
            return InventoryComputer.MAX_SLOTS;
        }
        int open = 0;
        for (int slot : InventoryComputer.storageOrder()) {
            if (!l.lockedCells.contains(slot)) {
                open++;
            }
        }
        return open;
    }

    public int getSlots(Player player) {
        return getSlots(player.getUniqueId());
    }

    public boolean isPartOpen(UUID uuid, EquipmentPart part) {
        return !isCellLocked(uuid, part.getSlot());
    }

    public boolean isCellLocked(UUID uuid, int slot) {
        Limits l = values.get(uuid);
        return l != null && l.lockedCells.contains(slot);
    }

    /**
     * @return копия набора заблокированных ячеек (0-40)
     */
    public Set<Integer> getLockedCells(UUID uuid) {
        Limits l = values.get(uuid);
        return l == null ? Set.of() : Set.copyOf(l.lockedCells);
    }

    public Set<EquipmentPart> getClosedParts(UUID uuid) {
        EnumSet<EquipmentPart> closed = EnumSet.noneOf(EquipmentPart.class);
        for (EquipmentPart part : EquipmentPart.values()) {
            if (isCellLocked(uuid, part.getSlot())) {
                closed.add(part);
            }
        }
        return closed;
    }

    public boolean isCraftingCellLocked(UUID uuid, int cellId) {
        Limits l = values.get(uuid);
        return l != null && l.lockedCraftingCells.contains(cellId);
    }

    /**
     * @return копия набора заблокированных ячеек крафта (id 1-5)
     */
    public Set<Integer> getLockedCraftingCells(UUID uuid) {
        Limits l = values.get(uuid);
        return l == null ? Set.of() : Set.copyOf(l.lockedCraftingCells);
    }

    public boolean hasCustomValue(UUID uuid) {
        return values.containsKey(uuid);
    }

    /**
     * Все открытые слоты игрока в нумерации PlayerInventory: все слоты хранилища
     * (0..35) и вся броня/оффхенд, минус заблокированные ячейки. Это единственный
     * источник истины об "открытости" — им пользуются и {@code InventoryEnforcer},
     * и {@code SlotOverviewGui}, поэтому они больше не могут разойтись между собой
     * (раньше был отдельный "лимит N", из-за чего GUI мог показывать красным ячейку,
     * которую клик помечал как открытую, и наоборот).
     */
    public Set<Integer> getUnlockedSlots(UUID uuid) {
        Set<Integer> result = new HashSet<>(InventoryComputer.storageOrder());
        for (EquipmentPart part : EquipmentPart.values()) {
            result.add(part.getSlot());
        }
        Limits l = values.get(uuid);
        if (l != null) {
            result.removeAll(l.lockedCells);
        }
        return result;
    }

    // ---------- запись ----------

    /**
     * Массово выставляет открытыми первые {@code amount} слотов хранилища (хотбар +
     * основной инвентарь, в порядке открытия — см. {@link InventoryComputer#storageOrder()}),
     * а все слоты хранилища после них — блокирует. Экипировку (броню/оффхенд) и личный
     * крафт не трогает.
     * <p>
     * Технически это просто массовая правка того же набора ячеек, что и
     * {@link #setCellLocked} — отдельного "лимита N" больше не существует, поэтому эта
     * команда и {@code SlotOverviewGui} всегда согласованы между собой.
     */
    public void setSlots(UUID uuid, int amount) {
        int clamped = InventoryComputer.clamp(amount);
        Limits l = values.computeIfAbsent(uuid, k -> new Limits());
        List<Integer> order = InventoryComputer.storageOrder();
        for (int i = 0; i < order.size(); i++) {
            int slot = order.get(i);
            if (i < clamped) {
                l.lockedCells.remove(slot);
            } else {
                l.lockedCells.add(slot);
            }
        }
        cleanup(uuid, l);
        save();
    }

    public void setPartsOpen(UUID uuid, Collection<EquipmentPart> parts, boolean open) {
        Limits l = values.computeIfAbsent(uuid, k -> new Limits());
        for (EquipmentPart part : parts) {
            if (open) {
                l.lockedCells.remove(part.getSlot());
            } else {
                l.lockedCells.add(part.getSlot());
            }
        }
        cleanup(uuid, l);
        save();
    }

    /**
     * Блокирует/разблокирует одну конкретную ячейку PlayerInventory (0-40) точечно,
     * не трогая остальные ячейки. Массовая версия для всего диапазона хранилища —
     * {@link #setSlots}.
     */
    public void setCellLocked(UUID uuid, int slot, boolean locked) {
        Limits l = values.computeIfAbsent(uuid, k -> new Limits());
        if (locked) {
            l.lockedCells.add(slot);
        } else {
            l.lockedCells.remove(slot);
        }
        cleanup(uuid, l);
        save();
    }

    public void setCraftingCellLocked(UUID uuid, int cellId, boolean locked) {
        Limits l = values.computeIfAbsent(uuid, k -> new Limits());
        if (locked) {
            l.lockedCraftingCells.add(cellId);
        } else {
            l.lockedCraftingCells.remove(cellId);
        }
        cleanup(uuid, l);
        save();
    }

    /**
     * Сбрасывает всё: слоты, экипировку, ручные блокировки и крафт.
     */
    public void reset(UUID uuid) {
        values.remove(uuid);
        save();
    }

    private void cleanup(UUID uuid, Limits l) {
        if (l.isDefault()) {
            values.remove(uuid);
        }
    }
}