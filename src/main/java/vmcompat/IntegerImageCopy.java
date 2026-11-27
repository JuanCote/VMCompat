package vmcompat;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Exact unsigned 16-bit copy for LookMasks when a driver rejects an FBO attachment. */
final class IntegerImageCopy {
    private static final long MAX_SOURCE_BYTES = 64L << 20;
    private static final int[] PACK = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH,
            GL12.GL_PACK_IMAGE_HEIGHT, GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SKIP_ROWS,
            GL12.GL_PACK_SKIP_IMAGES, GL11.GL_PACK_SWAP_BYTES};
    private static final int[] UNPACK = {GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH,
            GL12.GL_UNPACK_IMAGE_HEIGHT, GL11.GL_UNPACK_SKIP_PIXELS, GL11.GL_UNPACK_SKIP_ROWS,
            GL12.GL_UNPACK_SKIP_IMAGES, GL11.GL_UNPACK_SWAP_BYTES};
    private record Level(int width, int height, int layers, int format) {
        long bytes() {
            return 2L * width * height * layers;
        }
        boolean contains(int x, int y, int z, int w, int h, int d) {
            return x >= 0 && y >= 0 && z >= 0 && w > 0 && h > 0 && d > 0
                    && w <= width - x && h <= height - y && d <= layers - z;
        }
    }

    static boolean copy(int source, int sourceTarget, int sourceMip, int sx, int sy, int sz,
                        int destination, int destinationTarget, int destinationMip, int dx, int dy, int dz,
                        int width, int height, int layers) {
        if (!supported(sourceTarget) || !supported(destinationTarget)) return false;
        Level from = level(source, sourceTarget, sourceMip);
        Level to = level(destination, destinationTarget, destinationMip);
        if (from.format != GL30.GL_R16UI || to.format != GL30.GL_R16UI) return false;
        if (!from.contains(sx, sy, sz, width, height, layers) ||
            !to.contains(dx, dy, dz, width, height, layers))
            throw new IllegalArgumentException("R16UI copy exceeds the allocated mip or layer range");
        long bytes = from.bytes();
        if (bytes > MAX_SOURCE_BYTES)
            throw new IllegalStateException("R16UI copy source requires " + bytes + " bytes, limit " + MAX_SOURCE_BYTES);

        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] savedPack = read(PACK), savedUnpack = read(UNPACK);
        Packing.reserveAux(bytes);
        ByteBuffer pixels = null;
        try {
            pixels = MemoryUtil.memAlloc(Math.toIntExact(bytes));
            long address = MemoryUtil.memAddress(pixels);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            setTight(PACK);
            setTight(UNPACK);
            Textures.bound(source, () -> GL11.nglGetTexImage(sourceTarget, sourceMip,
                    GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT, address));
            checkGl("reading R16UI");
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, from.width);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, from.height);
            long offset = 2L * ((long) sz * from.width * from.height + (long) sy * from.width + sx);
            Textures.bound(destination, () -> {
                if (destinationTarget == GL30.GL_TEXTURE_2D_ARRAY)
                    GL12.nglTexSubImage3D(destinationTarget, destinationMip, dx, dy, dz,
                            width, height, layers, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT, address + offset);
                else
                    GL11.nglTexSubImage2D(destinationTarget, destinationMip, dx, dy,
                            width, height, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT, address + offset);
            });
            checkGl("uploading R16UI");
            Log.info("R16UI image copy used bounded fallback: " + width + "x" + height + "x" + layers);
            return true;
        } finally {
            restore(PACK, savedPack);
            restore(UNPACK, savedUnpack);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            if (pixels != null) MemoryUtil.memFree(pixels);
            Packing.releaseCpu(bytes);
        }
    }

    private static Level level(int texture, int target, int mip) {
        Textures.target(texture, target);
        return new Level(
                Textures.glGetTextureLevelParameteri(texture, mip, GL11.GL_TEXTURE_WIDTH),
                Textures.glGetTextureLevelParameteri(texture, mip, GL11.GL_TEXTURE_HEIGHT),
                target == GL30.GL_TEXTURE_2D_ARRAY
                        ? Textures.glGetTextureLevelParameteri(texture, mip, GL12.GL_TEXTURE_DEPTH) : 1,
                Textures.glGetTextureLevelParameteri(texture, mip, GL11.GL_TEXTURE_INTERNAL_FORMAT));
    }

    private static void checkGl(String operation) {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR)
            throw new IllegalStateException(operation + " failed with GL error 0x" + Integer.toHexString(error));
    }

    private static boolean supported(int target) {
        return target == GL11.GL_TEXTURE_2D || target == GL30.GL_TEXTURE_2D_ARRAY;
    }
    private static int[] read(int[] keys) {
        int[] result = new int[keys.length];
        for (int i = 0; i < keys.length; i++) result[i] = GL11.glGetInteger(keys[i]);
        return result;
    }
    private static void setTight(int[] keys) {
        for (int i = 0; i < keys.length; i++) GL11.glPixelStorei(keys[i], i == 0 ? 1 : 0);
    }
    private static void restore(int[] keys, int[] values) {
        for (int i = 0; i < keys.length; i++) GL11.glPixelStorei(keys[i], values[i]);
    }
    private IntegerImageCopy() {}
}
