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
 *   slots: 10            # хотбар + основной инвентарь, 0..36
 *   closed: [OFFHAND, HELMET]   # закрытые части экипировки
 * </pre>
 * Игрок без записи — без ограничений (36 слотов, вся экипировка открыта).
 * Записи старого формата (uuid: число) игнорируются.
 */
public final class InventoryDataManager {

    private static final class Limits {
        int slots = InventoryComputer.MAX_SLOTS;
        final EnumSet<EquipmentPart> closed = EnumSet.noneOf(EquipmentPart.class);

        boolean isDefault() {
            return slots == InventoryComputer.MAX_SLOTS && closed.isEmpty();
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
        boolean legacyFound = false;
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
                legacyFound = true; // старый формат "uuid: число"
                continue;
            }
            Limits limits = new Limits();
            limits.slots = InventoryComputer.clamp(section.getInt("slots", InventoryComputer.MAX_SLOTS));
            for (String name : section.getStringList("closed")) {
                try {
                    limits.closed.add(EquipmentPart.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Неизвестная часть экипировки в players.yml: " + name);
                }
            }
            if (!limits.isDefault()) {
                values.put(uuid, limits);
            }
        }
        if (legacyFound) {
            plugin.getLogger().warning("players.yml содержит записи старого формата — они проигнорированы (лимиты сброшены).");
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Limits> e : values.entrySet()) {
            ConfigurationSection section = cfg.createSection(e.getKey().toString());
            section.set("slots", e.getValue().slots);
            List<String> closed = new ArrayList<>();
            for (EquipmentPart part : e.getValue().closed) {
                closed.add(part.name());
            }
            section.set("closed", closed);
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
     * Лимит слотов хранилища (хотбар + основной инвентарь). Не задан — максимум.
     */
    public int getSlots(UUID uuid) {
        Limits l = values.get(uuid);
        return l == null ? InventoryComputer.MAX_SLOTS : l.slots;
    }

    public int getSlots(Player player) {
        return getSlots(player.getUniqueId());
    }

    public boolean isPartOpen(UUID uuid, EquipmentPart part) {
        Limits l = values.get(uuid);
        return l == null || !l.closed.contains(part);
    }

    /**
     * @return копия набора закрытых частей экипировки
     */
    public Set<EquipmentPart> getClosedParts(UUID uuid) {
        EnumSet<EquipmentPart> copy = EnumSet.noneOf(EquipmentPart.class);
        Limits l = values.get(uuid);
        if (l != null) {
            copy.addAll(l.closed);
        }
        return copy;
    }

    public boolean hasCustomValue(UUID uuid) {
        return values.containsKey(uuid);
    }

    /**
     * Все открытые слоты игрока в нумерации PlayerInventory:
     * первые N слотов хранилища + открытые части экипировки.
     */
    public Set<Integer> getUnlockedSlots(UUID uuid) {
        Set<Integer> result = new HashSet<>(InventoryComputer.unlockedStorageSlots(getSlots(uuid)));
        for (EquipmentPart part : EquipmentPart.values()) {
            if (isPartOpen(uuid, part)) {
                result.add(part.getSlot());
            }
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
        if (open) {
            l.closed.removeAll(parts);
        } else {
            l.closed.addAll(parts);
        }
        cleanup(uuid, l);
        save();
    }

    /**
     * Сбрасывает всё: и слоты, и экипировку.
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