package net.dvmn2.inventorycontrolplugin.util;

import java.io.DataOutput;
import java.io.IOException;

/**
 * Primitive encoders of the Minecraft wire format used by the plugin-to-mod protocol.
 */
public final class Wire {

    private static final int VARINT_PAYLOAD_MASK = 0x7F;
    private static final int VARINT_CONTINUE_BIT = 0x80;
    private static final int VARINT_GROUP_BITS = 7;

    private Wire() {
    }

    /** Writes a Minecraft VarInt (7 bits per byte, least significant group first). */
    public static void writeVarInt(DataOutput out, int value) throws IOException {
        int remaining = value;
        while ((remaining & ~VARINT_PAYLOAD_MASK) != 0) {
            out.writeByte((remaining & VARINT_PAYLOAD_MASK) | VARINT_CONTINUE_BIT);
            remaining >>>= VARINT_GROUP_BITS;
        }
        out.writeByte(remaining);
    }
}
