package net.dvmn2.inventorycontrolplugin.model;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;

import net.dvmn2.inventorycontrolplugin.util.Wire;

/**
 * S2C {@code dvmn2:ic_locked}: the slots and crafting cells the client must draw as locked.
 * The packet always carries the full state and replaces the previous one on the client.
 * <pre>
 * VarInt  protocol       (must equal {@link #PROTOCOL})
 * VarInt  slotCount      (0..{@link #MAX_SLOTS})
 * VarInt  slot           x slotCount; PlayerInventory index, 0..40
 * VarInt  cellCount      (0..{@link #MAX_CRAFTING_CELLS})
 * VarInt  cell           x cellCount; crafting cell id, 1..5
 * </pre>
 * The lists are sorted ascending so that equal states compare equal.
 *
 * @param slots         locked PlayerInventory slots
 * @param craftingCells locked personal crafting cell ids
 */
public record LockedSlotsPacket(List<Integer> slots, List<Integer> craftingCells) {

    public static final String CHANNEL = "dvmn2:ic_locked";

    /** Wire format version; increase on any change of the format. */
    public static final int PROTOCOL = 1;

    public static final int MAX_SLOTS = SlotLayout.LAST_SLOT + 1;
    public static final int MAX_CRAFTING_CELLS = CraftingCell.MAX_ID;

    public LockedSlotsPacket {
        slots = List.copyOf(slots);
        craftingCells = List.copyOf(craftingCells);
    }

    /** Builds a packet from unordered collections. */
    public static LockedSlotsPacket of(Collection<Integer> slots, Collection<Integer> craftingCells) {
        return new LockedSlotsPacket(slots.stream().sorted().toList(), craftingCells.stream().sorted().toList());
    }

    /** Serialises the packet into the wire format described above. */
    public byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            Wire.writeVarInt(out, PROTOCOL);
            Wire.writeVarInt(out, slots.size());
            for (int slot : slots) {
                Wire.writeVarInt(out, slot);
            }
            Wire.writeVarInt(out, craftingCells.size());
            for (int cell : craftingCells) {
                Wire.writeVarInt(out, cell);
            }
        } catch (IOException ex) {
            // Writing into a byte array cannot fail.
            throw new UncheckedIOException(ex);
        }
        return bytes.toByteArray();
    }
}
