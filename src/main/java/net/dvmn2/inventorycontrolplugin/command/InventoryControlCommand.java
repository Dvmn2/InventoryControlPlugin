package net.dvmn2.inventorycontrolplugin.command;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.dvmn2.inventorycontrolplugin.InventoryControlPlugin;
import net.dvmn2.inventorycontrolplugin.InventoryManager;
import net.dvmn2.inventorycontrolplugin.Lang;
import net.dvmn2.inventorycontrolplugin.LimitsManager;
import net.dvmn2.inventorycontrolplugin.Permissions;
import net.dvmn2.inventorycontrolplugin.gui.SlotOverviewGui;
import net.dvmn2.inventorycontrolplugin.model.CraftingCell;
import net.dvmn2.inventorycontrolplugin.model.EquipmentPart;
import net.dvmn2.inventorycontrolplugin.model.SlotLayout;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * The {@code /inventorycontrol} command.
 * <pre>
 * /inventorycontrol reload
 * /inventorycontrol set &lt;players&gt; &lt;0..36&gt;                  open the first N storage slots, lock the rest
 * /inventorycontrol cell &lt;players&gt; &lt;0..35&gt; lock|unlock      lock one hotbar / main inventory slot
 * /inventorycontrol equip &lt;players&gt; &lt;part&gt; open|closed     helmet, chestplate, leggings, boots, offhand,
 *                                                       armor or all
 * /inventorycontrol crafting &lt;players&gt; &lt;1..5&gt; lock|unlock   one cell of the personal 2x2 crafting grid
 * /inventorycontrol gui &lt;player&gt;                         visual overview, click a pane to toggle a cell
 * /inventorycontrol get &lt;players&gt;                        show current restrictions
 * /inventorycontrol reset &lt;players&gt;                      remove every restriction
 * </pre>
 * Everything except {@code reload} requires {@link Permissions#MANAGE}; {@code reload} requires
 * {@link Permissions#RELOAD}.
 */
public final class InventoryControlCommand {

    private static final String ARG_PLAYERS = "players";
    private static final String ARG_PLAYER = "player";
    private static final String ARG_AMOUNT = "amount";
    private static final String ARG_SLOT = "slot";
    private static final String ARG_PART = "part";
    private static final String ARG_CELL = "cell";

    private static final String LITERAL_LOCK = "lock";
    private static final String LITERAL_UNLOCK = "unlock";
    private static final String LITERAL_OPEN = "open";
    private static final String LITERAL_CLOSED = "closed";

    private final InventoryControlPlugin plugin;
    private final LimitsManager limits;
    private final InventoryManager inventories;

    public InventoryControlCommand(InventoryControlPlugin plugin, LimitsManager limits,
                                   InventoryManager inventories) {
        this.plugin = plugin;
        this.limits = limits;
        this.inventories = inventories;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("inventorycontrol")
                .requires(src -> src.getSender().hasPermission(Permissions.MANAGE)
                        || src.getSender().hasPermission(Permissions.RELOAD))
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission(Permissions.RELOAD))
                        .executes(this::reload))
                .then(Commands.literal("set")
                        .requires(InventoryControlCommand::canManage)
                        .then(Commands.argument(ARG_PLAYERS, ArgumentTypes.players())
                                .then(Commands.argument(ARG_AMOUNT,
                                                IntegerArgumentType.integer(0, SlotLayout.MAX_SLOTS))
                                        .executes(this::set))))
                .then(Commands.literal("cell")
                        .requires(InventoryControlCommand::canManage)
                        .then(Commands.argument(ARG_PLAYERS, ArgumentTypes.players())
                                .then(Commands.argument(ARG_SLOT,
                                                IntegerArgumentType.integer(0, SlotLayout.MAX_SLOTS - 1))
                                        .then(Commands.literal(LITERAL_LOCK)
                                                .executes(ctx -> cell(ctx, true)))
                                        .then(Commands.literal(LITERAL_UNLOCK)
                                                .executes(ctx -> cell(ctx, false))))))
                .then(Commands.literal("equip")
                        .requires(InventoryControlCommand::canManage)
                        .then(Commands.argument(ARG_PLAYERS, ArgumentTypes.players())
                                .then(Commands.argument(ARG_PART, StringArgumentType.word())
                                        .suggests(this::suggestParts)
                                        .then(Commands.literal(LITERAL_OPEN)
                                                .executes(ctx -> equip(ctx, true)))
                                        .then(Commands.literal(LITERAL_CLOSED)
                                                .executes(ctx -> equip(ctx, false))))))
                .then(Commands.literal("crafting")
                        .requires(InventoryControlCommand::canManage)
                        .then(Commands.argument(ARG_PLAYERS, ArgumentTypes.players())
                                .then(Commands.argument(ARG_CELL,
                                                IntegerArgumentType.integer(CraftingCell.MIN_ID, CraftingCell.MAX_ID))
                                        .then(Commands.literal(LITERAL_LOCK)
                                                .executes(ctx -> crafting(ctx, true)))
                                        .then(Commands.literal(LITERAL_UNLOCK)
                                                .executes(ctx -> crafting(ctx, false))))))
                .then(Commands.literal("gui")
                        .requires(InventoryControlCommand::canManage)
                        .then(Commands.argument(ARG_PLAYER, ArgumentTypes.player())
                                .executes(this::gui)))
                .then(Commands.literal("get")
                        .requires(InventoryControlCommand::canManage)
                        .then(Commands.argument(ARG_PLAYERS, ArgumentTypes.players())
                                .executes(this::get)))
                .then(Commands.literal("reset")
                        .requires(InventoryControlCommand::canManage)
                        .then(Commands.argument(ARG_PLAYERS, ArgumentTypes.players())
                                .executes(this::reset)))
                .build();
    }

    // ---------- Executors ----------

    private int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        plugin.reloadAll();
        sender.sendMessage(Lang.get(Lang.Key.RELOADED, sender));
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = resolveTargets(ctx, ARG_PLAYERS);
        int amount = IntegerArgumentType.getInteger(ctx, ARG_AMOUNT);
        for (Player target : targets) {
            limits.setSlots(target.getUniqueId(), amount);
            inventories.enforce(target);
            sender.sendMessage(Lang.get(Lang.Key.SET_SUCCESS, sender,
                    player(target),
                    number("amount", amount),
                    number("max", SlotLayout.MAX_SLOTS)));
        }
        return targets.size();
    }

    private int cell(CommandContext<CommandSourceStack> ctx, boolean lock) {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = resolveTargets(ctx, ARG_PLAYERS);
        int slot = IntegerArgumentType.getInteger(ctx, ARG_SLOT);
        for (Player target : targets) {
            limits.setCellLocked(target.getUniqueId(), slot, lock);
            inventories.enforce(target);
            sender.sendMessage(Lang.get(lock ? Lang.Key.CELL_LOCKED : Lang.Key.CELL_UNLOCKED, sender,
                    player(target), number("cell", slot)));
        }
        return targets.size();
    }

    private int equip(CommandContext<CommandSourceStack> ctx, boolean open) {
        CommandSender sender = ctx.getSource().getSender();
        String selector = StringArgumentType.getString(ctx, ARG_PART);
        List<EquipmentPart> parts = EquipmentPart.parseSelector(selector);
        if (parts.isEmpty()) {
            sender.sendMessage(Lang.get(Lang.Key.UNKNOWN_PART, sender,
                    Placeholder.unparsed("part", selector),
                    Placeholder.unparsed("available", String.join(", ", EquipmentPart.selectorNames()))));
            return 0;
        }
        List<Player> targets = resolveTargets(ctx, ARG_PLAYERS);
        String names = parts.stream().map(EquipmentPart::getId).collect(Collectors.joining(", "));
        for (Player target : targets) {
            limits.setPartsOpen(target.getUniqueId(), parts, open);
            inventories.enforce(target);
            sender.sendMessage(Lang.get(open ? Lang.Key.EQUIP_OPENED : Lang.Key.EQUIP_CLOSED, sender,
                    player(target), Placeholder.unparsed("parts", names)));
        }
        return targets.size();
    }

    private int crafting(CommandContext<CommandSourceStack> ctx, boolean lock) {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = resolveTargets(ctx, ARG_PLAYERS);
        int cellId = IntegerArgumentType.getInteger(ctx, ARG_CELL);
        for (Player target : targets) {
            limits.setCraftingCellLocked(target.getUniqueId(), cellId, lock);
            inventories.enforce(target);
            sender.sendMessage(Lang.get(lock ? Lang.Key.CRAFTING_CELL_LOCKED : Lang.Key.CRAFTING_CELL_UNLOCKED,
                    sender, player(target), number("cell", cellId)));
        }
        return targets.size();
    }

    private int gui(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Entity executor = ctx.getSource().getExecutor();
        if (!(executor instanceof Player admin)) {
            sender.sendMessage(Lang.get(Lang.Key.ONLY_PLAYERS, sender));
            return 0;
        }
        List<Player> targets = resolveTargets(ctx, ARG_PLAYER);
        if (targets.isEmpty()) {
            return 0;
        }
        SlotOverviewGui.open(limits, admin, targets.get(0));
        return Command.SINGLE_SUCCESS;
    }

    private int get(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = resolveTargets(ctx, ARG_PLAYERS);
        for (Player target : targets) {
            sendRestrictions(sender, target);
        }
        return targets.size();
    }

    private int reset(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = resolveTargets(ctx, ARG_PLAYERS);
        for (Player target : targets) {
            limits.reset(target.getUniqueId());
            inventories.enforce(target);
            sender.sendMessage(Lang.get(Lang.Key.RESET_SUCCESS, sender, player(target)));
        }
        return targets.size();
    }

    // ---------- Helpers ----------

    private void sendRestrictions(CommandSender sender, Player target) {
        UUID uuid = target.getUniqueId();
        TagResolver name = player(target);

        sender.sendMessage(Lang.get(Lang.Key.GET_INFO, sender, name,
                number("open", limits.getOpenStorageSlots(uuid)),
                number("max", SlotLayout.MAX_SLOTS)));

        Set<EquipmentPart> closedParts = limits.getClosedParts(uuid);
        if (closedParts.isEmpty()) {
            sender.sendMessage(Lang.get(Lang.Key.GET_EQUIPMENT_ALL_OPEN, sender, name));
        } else {
            String names = closedParts.stream().map(EquipmentPart::getId).collect(Collectors.joining(", "));
            sender.sendMessage(Lang.get(Lang.Key.GET_EQUIPMENT_CLOSED, sender, name,
                    Placeholder.unparsed("parts", names)));
        }

        // Equipment is reported above; here only the hotbar / main inventory cells.
        List<Integer> lockedStorage = limits.getLockedCells(uuid).stream()
                .filter(slot -> slot < SlotLayout.MAX_SLOTS)
                .sorted()
                .toList();
        if (lockedStorage.isEmpty()) {
            sender.sendMessage(Lang.get(Lang.Key.GET_LOCKED_CELLS_NONE, sender, name));
        } else {
            sender.sendMessage(Lang.get(Lang.Key.GET_LOCKED_CELLS, sender, name,
                    Placeholder.unparsed("list", join(lockedStorage))));
        }

        List<Integer> lockedCrafting = limits.getLockedCraftingCells(uuid).stream().sorted().toList();
        if (lockedCrafting.isEmpty()) {
            sender.sendMessage(Lang.get(Lang.Key.GET_CRAFTING_CELLS_NONE, sender, name));
        } else {
            sender.sendMessage(Lang.get(Lang.Key.GET_CRAFTING_CELLS_LOCKED, sender, name,
                    Placeholder.unparsed("list", join(lockedCrafting))));
        }
    }

    /** Resolves a player selector; sends the "not found" message and returns an empty list on failure. */
    private static List<Player> resolveTargets(CommandContext<CommandSourceStack> ctx, String argument) {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> players;
        try {
            players = ctx.getArgument(argument, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        } catch (CommandSyntaxException ex) {
            players = List.of();
        }
        if (players.isEmpty()) {
            sender.sendMessage(Lang.get(Lang.Key.PLAYER_NOT_FOUND, sender));
        }
        return players;
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

    private static boolean canManage(CommandSourceStack source) {
        return source.getSender().hasPermission(Permissions.MANAGE);
    }

    private static TagResolver player(Player target) {
        return Placeholder.unparsed("player", target.getName());
    }

    private static TagResolver number(String name, int value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    private static String join(List<Integer> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
