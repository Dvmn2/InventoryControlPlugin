package net.dvmn2.inventorycontrolplugin.storage;

import net.dvmn2.inventorycontrolplugin.model.CraftingCell;
import net.dvmn2.inventorycontrolplugin.model.PlayerLimits;
import net.dvmn2.inventorycontrolplugin.model.SlotLayout;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads and writes {@code players.yml}.
 * <p>
 * {@link #load()} is called on the main thread at startup. {@link #write} may be called from any thread;
 * calls are serialised and a snapshot older than the last written one is dropped.
 * <pre>
 * version: 1
 * players:
 *   &lt;uuid&gt;:
 *     locked-cells: [36, 5]         # PlayerInventory slots 0..40
 *     locked-crafting-cells: [1, 5] # crafting cell ids 1..5
 * </pre>
 * A file without {@code version} (the pre-2.0 flat format) is renamed to {@code players.yml.bak} and ignored.
 */
public final class LimitsStore {

    private static final int FORMAT_VERSION = 1;

    /**
     * Upper bound of list entries read per player; real data never exceeds 41.
     */
    private static final int MAX_LIST_ENTRIES = 256;

    private final Path file;
    private final Logger log;

    private long lastWrittenVersion;

    public LimitsStore(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /**
     * Loads all player records; invalid entries are skipped with a warning.
     */
    public Map<UUID, PlayerLimits> load() {
        Map<UUID, PlayerLimits> result = new HashMap<>();
        if (!Files.exists(file)) {
            return result;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        if (!yaml.isInt("version")) {
            backUpUnsupportedFile();
            return result;
        }
        if (yaml.getInt("version") > FORMAT_VERSION) {
            log.warning(file.getFileName() + " has a newer format version " + yaml.getInt("version")
                    + "; unknown fields are ignored");
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return result;
        }
        for (String key : players.getKeys(false)) {
            readPlayer(players, key, result);
        }
        return result;
    }

    /**
     * Writes a snapshot atomically.
     *
     * @param version  monotonically increasing snapshot number
     * @param snapshot immutable state to write
     * @return false if the write failed; true if it succeeded or a newer snapshot was already written
     */
    public synchronized boolean write(long version, Map<UUID, PlayerLimits> snapshot) {
        if (version <= lastWrittenVersion) {
            return true;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", FORMAT_VERSION);
        Map<UUID, PlayerLimits> sorted = new TreeMap<>(snapshot);
        for (Map.Entry<UUID, PlayerLimits> entry : sorted.entrySet()) {
            String base = "players." + entry.getKey();
            yaml.set(base + ".locked-cells", new ArrayList<>(new TreeSet<>(entry.getValue().lockedCells())));
            yaml.set(base + ".locked-crafting-cells",
                    new ArrayList<>(new TreeSet<>(entry.getValue().lockedCraftingCells())));
        }
        try {
            writeAtomically(file, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
            lastWrittenVersion = version;
            return true;
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Could not save " + file.getFileName(), ex);
            return false;
        }
    }

    private void readPlayer(ConfigurationSection players, String key, Map<UUID, PlayerLimits> result) {
        UUID uuid;
        try {
            uuid = UUID.fromString(key);
        } catch (IllegalArgumentException ex) {
            log.warning(file.getFileName() + ": invalid UUID " + key);
            return;
        }
        ConfigurationSection section = players.getConfigurationSection(key);
        if (section == null) {
            log.warning(file.getFileName() + ": entry " + key + " is not a section");
            return;
        }
        List<Integer> cells = new ArrayList<>();
        for (int slot : limited(section.getIntegerList("locked-cells"))) {
            if (slot >= 0 && slot <= SlotLayout.LAST_SLOT) {
                cells.add(slot);
            } else {
                log.warning(file.getFileName() + ": invalid cell " + slot + " of " + key);
            }
        }
        List<Integer> craftingCells = new ArrayList<>();
        for (int id : limited(section.getIntegerList("locked-crafting-cells"))) {
            if (CraftingCell.byId(id) != null) {
                craftingCells.add(id);
            } else {
                log.warning(file.getFileName() + ": invalid crafting cell " + id + " of " + key);
            }
        }
        PlayerLimits limits = new PlayerLimits(
                Set.copyOf(cells),
                Set.copyOf(craftingCells)
        );
        if (!limits.isDefault()) {
            result.put(uuid, limits);
        }
    }

    private static List<Integer> limited(List<Integer> values) {
        return values.size() > MAX_LIST_ENTRIES ? values.subList(0, MAX_LIST_ENTRIES) : values;
    }

    private void backUpUnsupportedFile() {
        Path backup = file.resolveSibling(file.getFileName() + ".bak");
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            log.warning(file.getFileName() + " has no format version (pre-2.0 file); moved to "
                    + backup.getFileName() + " and ignored");
        } catch (IOException ex) {
            log.log(Level.WARNING, file.getFileName() + " has no format version and could not be backed up", ex);
        }
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
