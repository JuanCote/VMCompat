package vmcompat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;
import viewpoint.platform.GlProgram;

/** Uses the installed OldPoint's upload/bind/end methods and the real VMCompat word arena. */
public final class OldPointCompatibilityTests {
    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws Exception {
        Method m = owner.getDeclaredMethod(name, parameters);
        m.setAccessible(true);
        return m;
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static GlProgram reference(int linked) throws Exception {
        Constructor<GlProgram> constructor = GlProgram.class.getDeclaredConstructor(String.class, String.class, String.class,
                String.class, String.class, String.class, String.class);
        constructor.setAccessible(true);
        GlProgram program = constructor.newInstance("OldPoint test", null, null, null, null, null, null);
        field(GlProgram.class, "id").setInt(program, linked);
        return program;
    }

    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = Context.createWindow(16, 16, "OldPoint cutaway storage fixture", 0, 0);
        Context.makeCurrent(window);
        Context.capabilities();
        try {
            var cutaway = Class.forName("oldpoint.viewpoint.CutawayData");
            var data = Class.forName(cutaway.getName() + "$Data");
            var values = data.getDeclaredConstructor(float[].class, int.class, int.class, int.class,
                    int.class, int.class, int.class, int.class, int.class, int.class);
            values.setAccessible(true);
            Method upload = method(cutaway, "upload", Object.class, data);
            Method bind = method(cutaway, "bind", GlProgram.class);
            Method end = method(cutaway, "end");

            int program = StorageBuffersTests.program("""
                    #version 330
                    layout(std430) readonly buffer ReliefWalls { int walls[]; };
                    layout(std430) readonly buffer ReliefFlow { int flow[]; };
                    uniform samplerBuffer uOPData;
                    out vec4 c;
                    void main() {
                      c = vec4(float(walls[0] + flow[0]) / 100.0 + texelFetch(uOPData, 0).x, 0, 0, 1);
                    }
                    """);
            GlProgram vp = reference(program);
            int wall = WordStorageTests.buffer(5), flow = WordStorageTests.buffer(5);
            for (int i = 0; i < 2; i++) {
                int index = StorageBuffers.glGetProgramResourceIndex(program, StorageBuffers.BLOCK, i == 0 ? "ReliefWalls" : "ReliefFlow");
                StorageBuffers.glShaderStorageBlockBinding(program, index, i);
                Buffers.glBindBufferBase(StorageBuffers.TARGET, i, i == 0 ? wall : flow);
            }
            // The pre-existing unit and buffer binding must survive OldPoint's helpers.
            int saved = WordStorageTests.buffer(0);
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, saved);
            Samplers.glActiveTexture(GL13.GL_TEXTURE0 + 11);
            Shaders.glUseProgram(program);
            GL11.glViewport(0, 0, 16, 16);
            int location = Shaders.glGetUniformLocation(program, "uOPData");
            for (int i = 0; i < 3; i++) {
                float[] rows = new float[i == 0 ? 4 : 8192];
                rows[0] = .2f + .1f * i;
                upload.invoke(null, new Object(), values.newInstance(rows, 0, 0, 0, 0, 0, 0, 0, 0, 0));
                bind.invoke(null, vp);
                int unit = Samplers.logicalUnit(program, location);
                int texture = field(cutaway, "texture").getInt(null);
                if (args.length == 1 && args[0].equals("baseline")) {
                    int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                    GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                    boolean outsideGuard = unit > Samplers.state().scratch &&
                            GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER) == texture &&
                            Samplers.boundTexture(unit, GL31.GL_TEXTURE_BUFFER) == 0;
                    GL13.glActiveTexture(active);
                    if (!outsideGuard) throw new AssertionError("Baseline did not bind outside the buffer guard");
                    System.out.println("Baseline reproduced: raw OldPoint texture " + texture + " on untracked physical unit " + unit);
                    return;
                }
                if (unit < 0 || texture == 0 || Samplers.boundTexture(unit, GL31.GL_TEXTURE_BUFFER) != texture) {
                    throw new AssertionError("Cutaway texture is missing from logical sampler state");
                }
                if (Samplers.activeUnit() != 11 || GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER) != saved) {
                    throw new AssertionError("OldPoint did not restore caller state");
                }
                if (i == 1) {
                    // Force the same WordStorage allocation path again with the cutaway texture live.
                    GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, wall);
                    Buffers.glBufferData(GL31.GL_COPY_WRITE_BUFFER, 2L * 1024 * 1024, GL15.GL_DYNAMIC_DRAW);
                    ModernGl.glNamedBufferSubData(wall, 0, new int[]{5});
                    GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, saved);
                }
                StorageBuffersTests.near(rows[0] + .1f);
                end.invoke(null);
                if (Samplers.boundTexture(unit, GL31.GL_TEXTURE_BUFFER) != 0 || Samplers.activeUnit() != 11) {
                    throw new AssertionError("Cutaway unbind did not restore the prior sampler state");
                }
            }
            if (WordStorage.stats()[0] < 2) throw new AssertionError("Expected native word arena allocation and growth");
            System.out.println("PASS OldPoint cutaway pixels, resized uploads, native word arena growth and binding restoration");
        } finally {
            Context.destroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
