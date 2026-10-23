package net.dvmn2.inventorycontrolplugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dvmn2.inventorycontrolplugin.command.InventoryControlCommand;
import net.dvmn2.inventorycontrolplugin.gui.SlotOverviewGui;
import net.dvmn2.inventorycontrolplugin.gui.SlotOverviewGuiListener;
import net.dvmn2.inventorycontrolplugin.storage.LimitsStore;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Composition root: builds the components, registers them and shuts them down.
 */
public final class InventoryControlPlugin extends JavaPlugin {

    private static final String DATA_FILE = "players.yml";

    private volatile InventoryControlSettings settings;
    private LimitsManager limits;
    private InventoryManager inventories;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        limits = new LimitsManager(this, new LimitsStore(getDataFolder().toPath().resolve(DATA_FILE), getLogger()));
        limits.load();
        inventories = new InventoryManager(this, () -> settings, limits);

        // The window listener goes first so that its cancellations are visible to InventoryListener.
        getServer().getPluginManager().registerEvents(new SlotOverviewGuiListener(limits, inventories), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(inventories), this);

        InventoryControlCommand command = new InventoryControlCommand(this, limits, inventories);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "Control which inventory slots a player can use"));

        limits.start();
        inventories.start();

        getLogger().info("Enabled. Players with restrictions: " + limits.getRestrictedPlayerCount() + ".");
    }

    @Override
    public void onDisable() {
        SlotOverviewGui.closeAll(getServer());
        if (inventories != null) {
            inventories.shutdown();
        }
        if (limits != null) {
            limits.shutdown();
        }
    }

    /** Re-reads config.yml and applies it without losing runtime state. */
    public void reloadAll() {
        reloadConfig();
        reloadSettings();
        inventories.onSettingsReloaded();
    }

    private void reloadSettings() {
        settings = InventoryControlSettings.parse(getConfig(), getLogger());
        Lang.setLanguage(settings.language());
    }
}
