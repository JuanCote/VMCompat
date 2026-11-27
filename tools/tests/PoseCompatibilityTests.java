package vmcompat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import viewpoint.platform.LiveSettings;
import viewpoint.render.ModelDraws;

/** Runs the real Viewpoint pose selectors and GL palette upload without starting a world. */
public final class PoseCompatibilityTests {
    private static Method method(String owner, String name, Class<?>... parameters) throws ReflectiveOperationException {
        var result = Class.forName(owner).getDeclaredMethod(name, parameters);
        result.setAccessible(true);
        return result;
    }

    private static void enable(String owner) throws ReflectiveOperationException {
        var field = Class.forName(owner).getDeclaredField("ON");
        field.setAccessible(true);
        ((LiveSettings.Toggle) field.get(null)).set(true);
    }

    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = Context.createWindow(16, 16, "Viewpoint pose fixture", 0, 0);
        Context.makeCurrent(window);
        Context.capabilities();
        try {
            String gpu = "viewpoint.render.BakedPoseGpu";
            Method receive = method(gpu, "receive");
            if (args.length > 0 && args[0].equals("baseline")) {
                method(gpu, "upload", int.class, int.class, float[].class).invoke(null, 1, 0, new float[12]);
                try {
                    receive.invoke(null);
                } catch (InvocationTargetException error) {
                    if (error.getCause() instanceof UnsupportedOperationException
                            && "Buffer binding 37074".equals(error.getCause().getMessage())) {
                        System.out.println("Baseline reproduced: BakedPoseGpu.receive -> Buffer binding 37074");
                        return;
                    }
                    throw error;
                }
                throw new AssertionError("Expected the reported SSBO failure");
            }
            enable("viewpoint.models.LivePoses");
            enable("viewpoint.models.BakedPoses");
            var live = method("viewpoint.models.ModelCapture", "live",
                    Class.forName("viewpoint.core.Frame"), Class.forName("zombie.core.skinnedmodel.model.ModelSlotRenderData"),
                    Class.forName("zombie.characters.IsoGameCharacter"), boolean.class, Class.forName("viewpoint.models.ModelCapture$Pose"));
            if ((int) live.invoke(null, null, null, null, false, null) != -1) throw new AssertionError("Expected CPU palette sentinel");
            var baked = method("viewpoint.models.CharacterRate", "baked", Class.forName("viewpoint.models.CharacterRate$Entry"),
                    Class.forName("zombie.characters.IsoGameCharacter"), float.class, boolean.class);
            if ((boolean) baked.invoke(null, null, null, 64f, true)) throw new AssertionError("Unexpected baked GPU pose");
            receive.invoke(null);
            StorageBuffersTests.clean("pose receive with GL 4.1");

            // Use the production CPU palette, FrameStream and ModelPalettes binding.
            Class<?> uploads = Class.forName("viewpoint.render.ModelUploads");
            var constructor = uploads.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object uploader = constructor.newInstance();
            Method put = method(uploads.getName(), "put", ModelDraws.class);
            Method attach = method(uploads.getName(), "attach", boolean.class);
            int program = StorageBuffersTests.program("#version 330\nlayout(std430) readonly buffer ModelPalettes {vec4 uBones[];};out vec4 c;void main(){c=uBones[0];}");
            int block = StorageBuffers.glGetProgramResourceIndex(program, StorageBuffers.BLOCK, "ModelPalettes");
            StorageBuffers.glShaderStorageBlockBinding(program, block, 8);
            Shaders.glUseProgram(program);
            GL11.glViewport(0, 0, 16, 16);
            for (float value : new float[]{.25f, .75f}) {
                ModelDraws draws = new ModelDraws();
                draws.palette(new float[]{value, 0, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0}, 1);
                put.invoke(uploader, draws);
                attach.invoke(uploader, true);
                StorageBuffersTests.near(value);
            }
            GL20.glUseProgram(0);
            Shaders.glDeleteProgram(program);
            System.out.println("Both GPU settings enabled: CPU pose selectors, clean receive and two CPU palette pixels passed.");
        } finally {
            Context.destroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
