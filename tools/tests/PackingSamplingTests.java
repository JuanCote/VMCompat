package vmcompat;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

/** Compare the production packer across repeated filters, real filter changes and pixel writes. */
public final class PackingSamplingTests {
    private static final int DRAWS = 48;
    private static final int SIDE = 512;
    private static final int IMAGES = 8;
    private static final FloatBuffer PIXEL = BufferUtils.createFloatBuffer(4);
    private static int sampleLocation;

    private static void check(boolean ok, String detail) {
        if (!ok) throw new AssertionError(detail + " / GL error " + GL11.glGetError());
    }

    private static void pixel(float expected) {
        GL11.glReadPixels(8, 8, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, PIXEL);
        check(Math.abs(PIXEL.get(0) - expected) < .006f, "expected red " + expected + ", got " + PIXEL.get(0));
        check(GL11.glGetError() == 0, "GL state");
    }

    private static void draw(float u, float v, float lod) {
        GL20.glUniform3f(sampleLocation, u, v, lod);
        Draws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static int bindImages(int program, int[] images) {
        for (int i = 0; i < images.length; i++) PackingResidencyTests.bind(i, images[i]);
        for (int i = 0; i < 16; i++) Samplers.glUniform1i(Shaders.glGetUniformLocation(program, "images[" + i + "]"), i % images.length);
        Samplers.glUniform1i(Shaders.glGetUniformLocation(program, "a"), 0);
        Samplers.glUniform1i(Shaders.glGetUniformLocation(program, "b"), images.length - 1);
        return Shaders.glGetUniformLocation(program, "sampleArgs");
    }

    private static long bytes() { return Packing.memoryStats()[3]; }

    private static void repeatFilters(int[] images, int count) {
        for (int i = 0; i < count; i++) {
            for (int unit = 0; unit < images.length; unit++) {
                PackingResidencyTests.bind(unit, images[unit]);
                Textures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                Textures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            }
            draw(.4f, .5f, 0);
        }
    }

    private static void performance(int program) throws Exception {
        int[] images = new int[IMAGES];
        for (int i = 0; i < IMAGES; i++) images[i] = PackingResidencyTests.texture(SIDE, GL11.GL_RGBA8, 64);
        sampleLocation = bindImages(program, images);
        repeatFilters(images, 8);
        GL11.glFinish();
        long before = bytes();
        long[] elapsed = new long[3];
        for (int i = 0; i < elapsed.length; i++) {
            long start = System.nanoTime();
            repeatFilters(images, DRAWS);
            GL11.glFinish();
            elapsed[i] = System.nanoTime() - start;
            pixel(64 / 255f);
        }
        long copied = bytes() - before;
        long expected = PackedSampling.ENABLED ? 0 : (long)elapsed.length * DRAWS * SIDE * SIDE * IMAGES * 4;
        check(copied == expected, "unexpected image transfer bytes " + copied + ", expected " + expected);
        Files.writeString(Path.of(System.getProperty("vmcompat.resultDir"), "sampling-transfer.json"),
            "{\"tracking\":" + PackedSampling.ENABLED + ",\"images\":" + IMAGES + ",\"draws_per_block\":" + DRAWS +
            ",\"copied_bytes\":" + copied + ",\"block_ns\":" + Arrays.toString(elapsed) + "}\n");
        Textures.glDeleteTextures(images);
    }

    private static void correctness(int program) {
        int texture = PackingResidencyTests.texture(2, GL11.GL_RGBA8, 0);
        int[] images = {texture};
        sampleLocation = bindImages(program, images);
        ByteBuffer columns = BufferUtils.createByteBuffer(8).putInt(0xff0000ff).putInt(0xff0000ff).flip();
        Textures.glTextureSubImage2D(texture, 0, 1, 0, 1, 2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, columns);
        draw(.4f, .5f, 0);
        pixel(0);

        long before = bytes();
        Textures.glTextureParameteri(texture, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        draw(.4f, .5f, 0);
        pixel(.3f);
        check(bytes() - before == (PackedSampling.ENABLED ? 0 : 16), "magnification updates sampling metadata");

        float[] min = {GL11.GL_LINEAR, 0, 0, 0};
        Textures.glTextureParameterfv(texture, GL11.GL_TEXTURE_MIN_FILTER, min);
        draw(.4f, .5f, 1);
        pixel(.3f);

        Textures.glTextureParameteri(texture, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        Textures.glTextureParameteriv(texture, GL11.GL_TEXTURE_WRAP_S, new int[]{GL11.GL_REPEAT, 0, 0, 0});
        draw(1.4f, .5f, 0);
        pixel(0);
        Textures.glTextureParameteri(texture, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        draw(1.4f, .5f, 0);
        pixel(1);

        int sampler = GL33.glGenSamplers();
        Samplers.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        Samplers.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        Samplers.glBindSampler(0, sampler);
        draw(.4f, .5f, 0);
        pixel(0);
        if (PackedSampling.ENABLED) {
            before = bytes();
            Samplers.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            draw(.4f, .5f, 0);
            pixel(.3f);
            check(bytes() == before, "same sampler object changed without reimporting image");
        }
        Samplers.glDeleteSamplers(new int[]{sampler});
        draw(1.4f, .5f, 0);
        pixel(1);

        before = bytes();
        ByteBuffer replacement = BufferUtils.createByteBuffer(4).putInt(0xff000040).flip();
        Textures.glTextureSubImage2D(texture, 0, 0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, replacement);
        draw(.1f, .1f, 0);
        pixel(64 / 255f);
        check(bytes() - before == 16, "real pixel write reimports texture");
        Textures.glDeleteTextures(texture);
    }

    public static void main(String[] args) throws Exception {
        check(GLFW.glfwInit(), "GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, 0);
        long window = Context.createWindow(16, 16, "Packed sampler invalidation", 0, 0);
        Context.makeCurrent(window);
        Context.capabilities();
        GL11.glViewport(0, 0, 16, 16);
        Draws.glBindVertexArray(GL30.glGenVertexArrays());
        int program = StorageBuffersTests.program("""
            #version 330
            uniform sampler2D images[16];
            uniform sampler2D a;
            uniform sampler2D b;
            uniform vec3 sampleArgs;
            out vec4 c;
            void main(){
                vec4 sum=vec4(0);
                for(int i=0;i<16;i++)sum+=textureLod(images[i],sampleArgs.xy,sampleArgs.z);
                c=(sum+textureLod(a,sampleArgs.xy,sampleArgs.z)+textureLod(b,sampleArgs.xy,sampleArgs.z))/18.;
            }
            """);
        Shaders.glUseProgram(program);
        performance(program);
        correctness(program);
        Shaders.glDeleteProgram(program);
        Context.destroyWindow(window);
        GLFW.glfwTerminate();
        System.out.println("PASS packed sampling: repeated filters, min/mag/wrap, sampler life and pixel mutation");
    }
}
