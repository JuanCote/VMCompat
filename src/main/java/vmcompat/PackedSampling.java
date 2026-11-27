package vmcompat;

import java.util.HashMap;
import java.util.Map;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL33;

/** Sampling metadata for the packed sampler. Image bytes have their own version. */
final class PackedSampling {
    static final boolean ENABLED = Boolean.parseBoolean(Options.value("packingParameterTracking", "true"));
    private static final StateStore<PackedSampling> STATES = new StateStore<>(PackedSampling::new);
    private static final int[] NAMES = {
        GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_TEXTURE_WRAP_S, GL11.GL_TEXTURE_WRAP_T
    };
    private final Map<Integer, Parameters> textures = new HashMap<>();
    private final Map<Integer, Parameters> samplers = new HashMap<>();
    private long revision;

    private static final class Parameters {
        final int[] values = new int[NAMES.length];
        long revision;
    }

    static PackedSampling current(ContextResources.Group group) {
        return ENABLED && group.trusted && !group.everShared ? STATES.get() : null;
    }

    private static PackedSampling current() {
        return ENABLED ? current(ContextResources.group()) : null;
    }

    static void textureParameter(int texture, int target, int name, double value) {
        int index = index(name);
        if (ENABLED && target == GL11.GL_TEXTURE_2D && texture != 0 && index >= 0) {
            PackedSampling state = current();
            if (state != null && valid(index, value)) {
                Opaque.textureParameters(texture);
                state.update(state.textures, texture, index, (int)value);
                return;
            }
            if (state != null) state.textures.remove(texture);
        }
        Textures.changed(texture);
    }

    static void samplerParameter(int sampler, int name, double value) {
        int index = index(name);
        if (index < 0) return;
        PackedSampling state = current();
        if (state != null) {
            if (valid(index, value)) state.update(state.samplers, sampler, index, (int)value);
            else state.samplers.remove(sampler);
        }
    }

    static void textureDeleted(int texture) {
        PackedSampling state = current();
        if (state != null) state.textures.remove(texture);
    }

    static void samplerDeleted(int sampler) {
        PackedSampling state = current();
        if (state != null) state.samplers.remove(sampler);
    }

    private void update(Map<Integer, Parameters> entries, int id, int index, int value) {
        Parameters parameters = entries.get(id);
        if (parameters != null && parameters.values[index] != value) {
            parameters.values[index] = value;
            parameters.revision = ++revision;
        }
    }

    long revision(int texture, int sampler) {
        Parameters parameters = sampler == 0 ? textures.get(texture) : samplers.get(sampler);
        return parameters == null ? 0 : parameters.revision;
    }

    /** Called with the original 2D texture bound on the scratch unit. */
    void observe(int texture, int sampler, int min, int mag, int wrapS, int wrapT) {
        Parameters parameters = sampler == 0
            ? textures.computeIfAbsent(texture, unused -> new Parameters())
            : samplers.computeIfAbsent(sampler, unused -> new Parameters());
        if (parameters.values[0] != min || parameters.values[1] != mag ||
            parameters.values[2] != wrapS || parameters.values[3] != wrapT) {
            parameters.values[0] = min;
            parameters.values[1] = mag;
            parameters.values[2] = wrapS;
            parameters.values[3] = wrapT;
            parameters.revision = ++revision;
        }
    }

    private static int index(int name) {
        return switch (name) {
            case GL11.GL_TEXTURE_MIN_FILTER -> 0;
            case GL11.GL_TEXTURE_MAG_FILTER -> 1;
            case GL11.GL_TEXTURE_WRAP_S -> 2;
            case GL11.GL_TEXTURE_WRAP_T -> 3;
            default -> -1;
        };
    }

    private static boolean valid(int index, double value) {
        int token = (int)value;
        if (token != value) return false;
        return switch (index) {
            case 0 -> token == GL11.GL_NEAREST || token == GL11.GL_LINEAR ||
                (token >= GL11.GL_NEAREST_MIPMAP_NEAREST && token <= GL11.GL_LINEAR_MIPMAP_LINEAR);
            case 1 -> token == GL11.GL_NEAREST || token == GL11.GL_LINEAR;
            default -> token == GL11.GL_REPEAT || token == GL14.GL_MIRRORED_REPEAT ||
                token == GL12.GL_CLAMP_TO_EDGE || token == GL13.GL_CLAMP_TO_BORDER;
        };
    }
}
