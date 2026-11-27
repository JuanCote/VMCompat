package vmcompat;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;

/** Keep OldPoint's cutaway buffer in the same logical texture state as Viewpoint. */
public final class OldPointCompat {
    static final String TARGET = "oldpoint/viewpoint/CutawayData";
    private static final ClassDesc OWNER = ClassDesc.of("vmcompat.OldPointCompat");

    static byte[] rewrite(byte[] bytes) {
        ClassFile cf = ClassFileSupport.create();
        return cf.transformClass(cf.parse(bytes), ClassTransform.transformingMethodBodies((builder, element) -> {
            if (element instanceof InvokeInstruction call && call.opcode() == Opcode.INVOKESTATIC) {
                String replacement = switch (call.owner().asInternalName() + "." + call.name().stringValue() + call.type().stringValue()) {
                    case "org/lwjgl/opengl/GL11.glGetInteger(I)I" -> "getInteger";
                    case "org/lwjgl/opengl/GL20.glGetUniformi(II)I" -> "getUniform";
                    case "org/lwjgl/opengl/GL13.glActiveTexture(I)V" -> "activeTexture";
                    case "org/lwjgl/opengl/GL11.glBindTexture(II)V" -> "bindTexture";
                    case "org/lwjgl/opengl/GL11.glGenTextures()I" -> "createTexture";
                    case "org/lwjgl/opengl/GL31.glTexBuffer(III)V" -> "attachBuffer";
                    case "org/lwjgl/opengl/GL15.glBindBuffer(II)V" -> "bindBuffer";
                    case "org/lwjgl/opengl/GL15.glGenBuffers()I" -> "createBuffer";
                    case "org/lwjgl/opengl/GL15.glBufferData(I[FI)V" -> "bufferData";
                    default -> null;
                };
                if (replacement != null) {
                    builder.invokestatic(OWNER, replacement, call.typeSymbol());
                    return;
                }
            }
            builder.with(element);
        }));
    }

    public static int getInteger(int parameter) {
        return switch (parameter) {
            case GL13.GL_ACTIVE_TEXTURE -> GL13.GL_TEXTURE0 + Samplers.activeUnit();
            case GL31.GL_TEXTURE_BINDING_BUFFER -> Samplers.boundTexture(Samplers.activeUnit(), GL31.GL_TEXTURE_BUFFER);
            case GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS -> Samplers.state().sampler.length;
            // CutawayData uses TEXTURE_BUFFER as its upload target. Use a regular copy-write binding for it.
            case GL31.GL_TEXTURE_BUFFER -> GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);
            default -> GL11.glGetInteger(parameter);
        };
    }

    public static int getUniform(int program, int location) {
        int unit = Samplers.logicalUnit(program, location);
        return unit >= 0 ? unit : GL20.glGetUniformi(program, location);
    }

    public static void activeTexture(int unit) { Samplers.glActiveTexture(unit); }
    public static void bindTexture(int target, int texture) { Samplers.glBindTexture(target, texture); }
    public static int createTexture() { return Textures.glGenTextures(); }
    public static void attachBuffer(int target, int format, int buffer) { Buffers.glTexBuffer(target, format, buffer); }
    public static int createBuffer() { return Buffers.glGenBuffers(); }

    public static void bindBuffer(int target, int buffer) {
        GL15.glBindBuffer(uploadTarget(target), buffer);
    }

    public static void bufferData(int target, float[] data, int usage) {
        Buffers.uploadFloatArray(uploadTarget(target), data, usage);
    }

    private static int uploadTarget(int target) {
        return target == GL31.GL_TEXTURE_BUFFER ? GL31.GL_COPY_WRITE_BUFFER : target;
    }

    private OldPointCompat() {}
}
