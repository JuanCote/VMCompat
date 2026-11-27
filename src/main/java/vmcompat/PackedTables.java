package vmcompat;

import java.util.Arrays;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL31;

/** Bounded table rows in the prefix of a program's existing packed buffer. */
final class PackedTables {
    static final boolean ENABLED = Boolean.parseBoolean(Options.value("packingTableCache", "true"));
    static final int SLOTS = 8;
    private final Row[] rows = new Row[SLOTS];
    private final long[] signature;
    private int hash;
    private long clock;
    private final int tableBytes;

    private static final class Row {
        final long[] signature;
        int hash;
        long lastUse;
        Row(int length) { signature = new long[length]; }
    }

    static PackedTables create(int samplers) {
        if (!ENABLED || samplers > 32) return null;
        int bytes = samplers * 256 + 16;
        long deviceBytes = (long) GL11.glGetInteger(GL31.GL_MAX_TEXTURE_BUFFER_SIZE) * 16;
        return deviceBytes >= Packing.CACHE_MAX + (long) SLOTS * bytes ? new PackedTables(samplers, bytes) : null;
    }

    private PackedTables(int samplers, int bytes) {
        signature = new long[samplers * 3];
        tableBytes = bytes;
    }

    int extraBytes() { return SLOTS * tableBytes; }
    int offset(int index) { return (index + 1) * tableBytes; }

    boolean capture(List<Packing.Slot> slots, Samplers.State bindings, PackedSampling parameters) {
        int at = 0;
        for (Packing.Slot slot : slots) {
            int texture = slot.active ? bindings.texture(slot.unit, 0) : 0;
            int sampler = texture == 0 ? 0 : bindings.sampler[slot.unit];
            long version = Textures.version(texture);
            if (texture != 0 && version == 0) return false;
            signature[at++] = ((long) texture << 32) | Integer.toUnsignedLong(sampler);
            signature[at++] = version;
            signature[at++] = texture == 0 ? 0 : parameters.revision(texture, sampler);
        }
        hash = Arrays.hashCode(signature);
        return true;
    }

    int find() {
        for (int i = 0; i < rows.length; i++) {
            Row row = rows[i];
            if (row != null && row.lastUse != 0 && hash == row.hash && Arrays.equals(signature, row.signature)) {
                row.lastUse = ++clock;
                return i;
            }
        }
        return -1;
    }

    int replace() {
        int oldest = 0;
        for (int i = 0; i < rows.length; i++) {
            Row row = rows[i];
            if (row == null || row.lastUse == 0) return i;
            if (row.lastUse < rows[oldest].lastUse) oldest = i;
        }
        rows[oldest].lastUse = 0;
        Counters.add(Counters.Key.PACK_TABLE_EVICTIONS, 1);
        return oldest;
    }

    void publish(int index) {
        Row row = rows[index];
        if (row == null) rows[index] = row = new Row(signature.length);
        System.arraycopy(signature, 0, row.signature, 0, signature.length);
        row.hash = hash;
        row.lastUse = ++clock;
    }

    void invalidate() {
        for (Row row : rows) if (row != null) row.lastUse = 0;
    }
}
