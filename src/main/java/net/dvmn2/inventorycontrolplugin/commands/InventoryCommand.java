package net.dvmn2.inventorycontrolplugin.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.dvmn2.inventorycontrolplugin.EquipmentPart;
import net.dvmn2.inventorycontrolplugin.InventoryComputer;
import net.dvmn2.inventorycontrolplugin.InventoryControlPlugin;
import net.dvmn2.inventorycontrolplugin.Lang;
import net.dvmn2.inventorycontrolplugin.gui.SlotOverviewGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /inventorycontrol set <игрок> <0..36>                     — граница по хвосту хранилища
 * /inventorycontrol cell <игрок> <0..35> lock|unlock         — точечная блокировка ячейки хотбара/инвентаря
 * /inventorycontrol equip <игрок> <часть> open|closed        — броня и оффхенд
 * /inventorycontrol crafting <игрок> <1..5> lock|unlock      — ячейка личного крафта 2x2 (см. CraftingCell)
 * /inventorycontrol gui <игрок>                              — визуальный обзор заблокированных ячеек
 * /inventorycontrol get <игрок>                              — текущие ограничения
 * /inventorycontrol reset <игрок>                            — снять все ограничения
 * <p>
 * Требует право inventorycontrol.admin.
 */
public final class InventoryCommand {

    private final InventoryControlPlugin plugin;

    public InventoryCommand(InventoryControlPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("inventorycontrol")
                .requires(src -> src.getSender().hasPermission("inventorycontrol.admin"))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, InventoryComputer.MAX_SLOTS))
                                        .executes(this::executeSet))))
                .then(Commands.literal("cell")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0, InventoryComputer.MAX_SLOTS - 1))
                                        .then(Commands.literal("lock").executes(ctx -> executeCell(ctx, true)))
                                        .then(Commands.literal("unlock").executes(ctx -> executeCell(ctx, false))))))
                .then(Commands.literal("equip")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("part", StringArgumentType.word())
                                        .suggests(this::suggestParts)
                                        .then(Commands.literal("open")
                                                .executes(ctx -> executeEquip(ctx, true)))
                                        .then(Commands.literal("closed")
                                                .executes(ctx -> executeEquip(ctx, false))))))
                .then(Commands.literal("crafting")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("cell", IntegerArgumentType.integer(1, 5))
                                        .then(Commands.literal("lock").executes(ctx -> executeCraftingCell(ctx, true)))
                                        .then(Commands.literal("unlock").executes(ctx -> executeCraftingCell(ctx, false))))))
                .then(Commands.literal("gui")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(this::executeGui)))
                .then(Commands.literal("get")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(this::executeGet)))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(this::executeReset)));
    }

    private CompletableFuture<Suggestions> suggestParts(CommandContext<CommandSourceStack> ctx,
                                                        SuggestionsBuilder builder) {
        String typed = builder.getRemainingLowerCase();
        for (String name : EquipmentPart.selectorNames()) {
            if (name.startsWith(typed)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private List<Player> resolvePlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
        List<Player> players = resolver.resolve(ctx.getSource());
        if (players.isEmpty()) {
            send(ctx, NamedTextColor.RED, Lang.Key.PLAYER_NOT_FOUND);
        }
        return players;
    }

    private void send(CommandContext<CommandSourceStack> ctx, NamedTextColor color, Lang.Key key, Object... args) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(Component.text(Lang.get(key, sender, args), color));
    }

    private static String join(Set<Integer> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private int executeSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        List<Player> players = resolvePlayers(ctx);
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        for (Player target : players) {
            plugin.getDataManager().setSlots(target.getUniqueId(), amount);
            plugin.getEnforcer().enforce(target);
            send(ctx, NamedTextColor.GREEN, Lang.Key.SET_SUCCESS, target.getName(), amount, InventoryComputer.MAX_SLOTS);
        }
        return players.size();
    }

    private int executeCell(CommandContext<CommandSourceStack> ctx, boolean lock) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        List<Player> players = resolvePlayers(ctx);
        for (Player target : players) {
            plugin.getDataManager().setCellLocked(target.getUniqueId(), slot, lock);
            plugin.getEnforcer().enforce(target);
            send(ctx, NamedTextColor.GREEN, lock ? Lang.Key.CELL_LOCKED : Lang.Key.CELL_UNLOCKED, target.getName(), slot);
        }
        return players.size();
    }

    private int executeEquip(CommandContext<CommandSourceStack> ctx, boolean open) throws CommandSyntaxException {
        String selector = StringArgumentType.getString(ctx, "part");
        List<EquipmentPart> parts = EquipmentPart.parseSelector(selector);
        if (parts == null) {
            send(ctx, NamedTextColor.RED, Lang.Key.UNKNOWN_PART, selector,
                    String.join(", ", EquipmentPart.selectorNames()));
            return 0;
        }
        List<Player> players = resolvePlayers(ctx);
        String names = parts.stream().map(EquipmentPart::getId).collect(Collectors.joining(", "));
        for (Player target : players) {
            plugin.getDataManager().setPartsOpen(target.getUniqueId(), parts, open);
            plugin.getEnforcer().enforce(target);
            send(ctx, NamedTextColor.GREEN, open ? Lang.Key.EQUIP_OPENED : Lang.Key.EQUIP_CLOSED,
                    target.getName(), names);
        }
        return players.size();
    }

    private int executeCraftingCell(CommandContext<CommandSourceStack> ctx, boolean lock) throws CommandSyntaxException {
        int cellId = IntegerArgumentType.getInteger(ctx, "cell");
        List<Player> players = resolvePlayers(ctx);
        for (Player target : players) {
            plugin.getDataManager().setCraftingCellLocked(target.getUniqueId(), cellId, lock);
            plugin.getEnforcer().enforce(target);
            send(ctx, NamedTextColor.GREEN, lock ? Lang.Key.CRAFTING_CELL_LOCKED : Lang.Key.CRAFTING_CELL_UNLOCKED,
                    target.getName(), cellId);
        }
        return players.size();
    }

    private int executeGui(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!(ctx.getSource().getSender() instanceof Player admin)) {
            send(ctx, NamedTextColor.RED, Lang.Key.GUI_ONLY_PLAYER);
            return 0;
        }
        List<Player> players = resolvePlayers(ctx);
        if (players.isEmpty()) {
            return 0;
        }
        SlotOverviewGui.open(plugin, admin, players.get(0));
        return 1;
    }

    private int executeGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        List<Player> players = resolvePlayers(ctx);
        for (Player target : players) {
            var uuid = target.getUniqueId();
            int amount = plugin.getDataManager().getSlots(target);
            send(ctx, NamedTextColor.AQUA, Lang.Key.GET_INFO, target.getName(), amount, InventoryComputer.MAX_SLOTS);

            Set<EquipmentPart> closed = plugin.getDataManager().getClosedParts(uuid);
            if (closed.isEmpty()) {
                send(ctx, NamedTextColor.AQUA, Lang.Key.GET_EQUIPMENT_ALL_OPEN, target.getName());
            } else {
                String names = closed.stream().map(p -> p.getId().toLowerCase(Locale.ROOT))
                        .collect(Collectors.joining(", "));
                send(ctx, NamedTextColor.AQUA, Lang.Key.GET_EQUIPMENT_CLOSED, target.getName(), names);
            }

            // отдельно от брони/оффхенда — точечные блокировки хотбара/инвентаря (слоты < MAX_SLOTS)
            Set<Integer> cellLocked = new TreeSet<>();
            for (int slot : plugin.getDataManager().getLockedCells(uuid)) {
                if (slot < InventoryComputer.MAX_SLOTS) {
                    cellLocked.add(slot);
                }
            }
            if (cellLocked.isEmpty()) {
                send(ctx, NamedTextColor.AQUA, Lang.Key.GET_LOCKED_CELLS_NONE, target.getName());
            } else {
                send(ctx, NamedTextColor.AQUA, Lang.Key.GET_LOCKED_CELLS, target.getName(), join(cellLocked));
            }

            Set<Integer> craftLocked = new TreeSet<>(plugin.getDataManager().getLockedCraftingCells(uuid));
            if (craftLocked.isEmpty()) {
                send(ctx, NamedTextColor.AQUA, Lang.Key.GET_CRAFTING_CELLS_NONE, target.getName());
            } else {
                send(ctx, NamedTextColor.AQUA, Lang.Key.GET_CRAFTING_CELLS_LOCKED, target.getName(), join(craftLocked));
            }
        }
        return players.size();
    }

    private int executeReset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        List<Player> players = resolvePlayers(ctx);
        for (Player target : players) {
            plugin.getDataManager().reset(target.getUniqueId());
            plugin.getEnforcer().enforce(target);
            send(ctx, NamedTextColor.GREEN, Lang.Key.RESET_SUCCESS, target.getName());
        }
        return players.size();
    }
}