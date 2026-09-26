package net.dvmn2.inventorycontrolplugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dvmn2.inventorycontrolplugin.commands.InventoryCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

public final class InventoryControlPlugin extends JavaPlugin {

    /**
     * Игроки с этим правом не подпадают ни под какие ограничения и не кикаются за отсутствие мода.
     * По умолчанию его нет даже у операторов — выдавать явно (LuckPerms и т.п.).
     */
    public static final String BYPASS_PERMISSION = "inventorycontrol.bypass";

    private InventoryDataManager dataManager;
    private InventoryEnforcer enforcer;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();
        Lang.setLanguage(getConfig().getString("settings.language", "auto"));

        if (getServer().getPluginManager().getPermission(BYPASS_PERMISSION) == null) {
            getServer().getPluginManager().addPermission(new Permission(
                    BYPASS_PERMISSION,
                    "Не подпадает под ограничения слотов и проверку клиентского мода",
                    PermissionDefault.FALSE));
        }

        this.dataManager = new InventoryDataManager(this);
        this.dataManager.load();

        this.enforcer = new InventoryEnforcer(this);
        getServer().getPluginManager().registerEvents(enforcer, this);

        // Канал для клиентского Fabric-мода (визуальная блокировка слотов на клиенте).
        getServer().getMessenger().registerOutgoingPluginChannel(this, InventoryEnforcer.LOCKED_SLOTS_CHANNEL);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        new InventoryCommand(this).build().build(),
                        "Управление количеством доступных слотов инвентаря игрока"
                ));

        // Периодическая подстраховка (хопперы, /give и прочее в обход событий).
        // enforce() дешёвый: если в закрытых слотах пусто и список слотов не менялся — ничего не делает.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                enforcer.enforce(p);
            }
        }, 5L, 5L);

        getLogger().info("InventoryControlPlugin включён. Команда: /inventorycontrol set|get|reset|equip <игрок> ... (слотов: 0-"
                + InventoryComputer.MAX_SLOTS + ")");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.save();
        }
    }

    public InventoryDataManager getDataManager() {
        return dataManager;
    }

    public InventoryEnforcer getEnforcer() {
        return enforcer;
    }
}