package net.dvmn2.inventorycontrolplugin.model;

import java.util.Collection;
import java.util.Set;

/**
 * Immutable set of restrictions of one player. A player without restrictions has no record at all.
 *
 * @param lockedCells         locked {@code PlayerInventory} slots, 0..40
 * @param lockedCraftingCells locked personal crafting cells, ids {@link CraftingCell#getId()}
 */
public record PlayerLimits(Set<Integer> lockedCells, Set<Integer> lockedCraftingCells) {

    public static final PlayerLimits EMPTY = new PlayerLimits(Set.of(), Set.of());

    public PlayerLimits {
        lockedCells = Set.copyOf(lockedCells);
        lockedCraftingCells = Set.copyOf(lockedCraftingCells);
    }

    /** Returns true if nothing is restricted. */
    public boolean isDefault() {
        return lockedCells.isEmpty() && lockedCraftingCells.isEmpty();
    }

    public PlayerLimits withLockedCells(Collection<Integer> cells) {
        return new PlayerLimits(Set.copyOf(cells), lockedCraftingCells);
    }

    public PlayerLimits withLockedCraftingCells(Collection<Integer> cells) {
        return new PlayerLimits(lockedCells, Set.copyOf(cells));
    }
}
