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
 *   slots: 10                        # хотбар + основной инвентарь, 0..36
 *   locked-cells: [36, 5]            # индивидуально заблокированные ячейки PlayerInventory (0-40:
 *                                    # 0-8 хотбар, 9-35 инвентарь, 36-39 броня, 40 оффхенд)
 *   locked-crafting-cells: [1, 5]    # заблокированные ячейки личного крафта 2x2 (id 1-5, см. CraftingCell)
 * </pre>
 * Игрок без записи — без ограничений. Записи без секции (старый формат) молча игнорируются.
 */
public final class InventoryDataManager {

    private static final class Limits {
        int slots = InventoryComputer.MAX_SLOTS;
        final Set<Integer> lockedCells = new HashSet<>();
        final Set<Integer> lockedCraftingCells = new HashSet<>();

        boolean isDefault() {
            return slots == InventoryComputer.MAX_SLOTS && lockedCells.isEmpty() && lockedCraftingCells.isEmpty();
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
            limits.slots = InventoryComputer.clamp(section.getInt("slots", InventoryComputer.MAX_SLOTS));
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
            section.set("slots", e.getValue().slots);
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

    public int getSlots(UUID uuid) {
        Limits l = values.get(uuid);
        return l == null ? InventoryComputer.MAX_SLOTS : l.slots;
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
     * @return копия набора индивидуально заблокированных ячеек (0-40)
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
     * Все открытые слоты игрока в нумерации PlayerInventory:
     * первые N слотов хранилища + вся броня/оффхенд, минус ручные блокировки.
     */
    public Set<Integer> getUnlockedSlots(UUID uuid) {
        Set<Integer> result = new HashSet<>(InventoryComputer.unlockedStorageSlots(getSlots(uuid)));
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

    public void setSlots(UUID uuid, int amount) {
        Limits l = values.computeIfAbsent(uuid, k -> new Limits());
        l.slots = InventoryComputer.clamp(amount);
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
     * Индивидуально блокирует/разблокирует конкретную ячейку PlayerInventory (0-40).
     * В отличие от {@link #setSlots}, никогда не "открывает" слот сверх лимита N —
     * только добавляет/снимает точечную блокировку.
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