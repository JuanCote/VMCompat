package vmcompat;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.GlProgram;
import viewpoint.platform.ShaderPacks;

/** Reproduce the published lighting pass failure with the recorded scene dimensions. */
public final class LightingPresetTests {
    private static final int WIDTH = 1920, HEIGHT = 1200;
    private static final int RELIEF_WIDTH = 4993, RELIEF_HEIGHT = 4033;

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static int texture(int format, int width, int height, int levels) {
        int texture = Textures.glCreateTextures(GL11.GL_TEXTURE_2D);
        Textures.glTextureStorage2D(texture, levels, format, width, height);
        Textures.glTextureParameteri(texture, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        Textures.glTextureParameteri(texture, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return texture;
    }

    private static ByteBuffer color(float r, float g, float b, float a) {
        return BufferUtils.createByteBuffer(16).putFloat(r).putFloat(g).putFloat(b).putFloat(a).flip();
    }

    private static void clear(int texture, int mip, float r, float g, float b, float a) {
        Textures.glClearTexImage(texture, mip, GL11.GL_RGBA, GL11.GL_FLOAT, color(r,g,b,a));
    }

    private static void use(GlProgram program, String name, int unit, int texture) {
        Samplers.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        Samplers.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        program.sampler(name, unit);
    }

    private static FloatBuffer draw() {
        Draws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        FloatBuffer pixel = BufferUtils.createFloatBuffer(4);
        GL11.glReadPixels(1, 1, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixel);
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "Lighting draw/readback failed");
        return pixel;
    }

    private static void closeTo(FloatBuffer pixel, float... expected) {
        for (int i = 0; i < expected.length; i++)
            check(Math.abs(pixel.get(i) - expected[i]) < .005f, "Lighting component " + i + ": " + pixel.get(i));
    }

    private static void probePackedInputs(int relief, int reflectionDepth) {
        int program = Shaders.glCreateProgram();
        int vertex = GL20.glCreateShader(GL20.GL_VERTEX_SHADER), fragment = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        Shaders.vpSource(vertex, "#version 410\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0.0,1.0);}");
        String source= """
                #version 410
                uniform sampler2D uAlbedo;
                uniform sampler2D uNormal;
                uniform sampler2D uEngineLight;
                uniform sampler2D uRelief;
                uniform sampler2D uReflectionDepth;
                vec3 lightShare(){return vec3(0);}
                out vec4 result;
                void main(){
                  result=vec4(texelFetch(uRelief,ivec2(4991,4032),0).r,
                              texelFetch(uRelief,ivec2(4992,4032),0).r,
                              texelFetch(uReflectionDepth,ivec2(959,599),1).r,1);
                }
                """;
        Shaders.glCompileShader(vertex);
        Shaders.glAttachShader(program, vertex);
        Shaders.glAttachShader(program, fragment);
        // The tiny probe fits natively, so explicitly exercise the production recipe.
        var converted=Packing.lowerAll(program, Map.of(fragment,Shaders.convert(source,false,false)), Map.of(fragment,false));
        GL20.glShaderSource(fragment, converted.get(fragment));
        GL20.glCompileShader(fragment);
        GL20.glLinkProgram(program);
        check(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS)!=0, GL20.glGetProgramInfoLog(program));
        Samplers.linked(program);
        Packing.linked(program);
        GL20.glUseProgram(program);
        Samplers.glUniform1i(Shaders.glGetUniformLocation(program,"uRelief"),44);
        Samplers.glUniform1i(Shaders.glGetUniformLocation(program,"uReflectionDepth"),120);
        closeTo(draw(), .25f, .75f, .375f, 1);
        long stable=Packing.memoryStats()[3];
        closeTo(draw(), .25f, .75f, .375f, 1);
        check(Packing.memoryStats()[3]==stable,"Unchanged lighting images were reimported");
        clear(reflectionDepth,1,.625f,0,0,1);
        closeTo(draw(), .25f, .75f, .625f, 1);
        long byteDelta=Packing.memoryStats()[3]-stable;
        check(byteDelta>=4L*WIDTH*HEIGHT&&byteDelta<16L*1024*1024,"Reflection mip chain import size "+byteDelta);
        Shaders.glDeleteProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
    }

    public static void main(String[] args) throws Exception {
        check(GLFW.glfwInit(),"GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        long window=Context.createWindow(32,32,"Lighting preset regression",0,0);
        Context.makeCurrent(window);
        Context.capabilities();
        GL11.glViewport(0,0,32,32);
        Draws.glBindVertexArray(GL30.glGenVertexArrays());
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        List<Integer> textures=new ArrayList<>();
        ShaderPacks.useBuiltIn("normal","low");
        GlProgram program=GlProgram.create("light","screen.vert","light.frag");
        try {
            int albedo=texture(GL11.GL_RGBA8,WIDTH,HEIGHT,1), normal=texture(GL11.GL_RGB10_A2,WIDTH,HEIGHT,1);
            int engine=texture(GL11.GL_RGBA8,WIDTH,HEIGHT,1), depth=texture(GL30.GL_DEPTH_COMPONENT32F,WIDTH,HEIGHT,1);
            int reflection=texture(GL30.GL_RGBA16F,WIDTH,HEIGHT,1), reflectionDepth=texture(GL30.GL_R32F,WIDTH,HEIGHT,8);
            int relief=texture(GL30.GL_R16,RELIEF_WIDTH,RELIEF_HEIGHT,1);
            int cloudShadow=texture(GL30.GL_R16F,512,512,1);
            int lampFields=texture(GL30.GL_R8,672,504,1);
            int bounce=texture(GL11.GL_RGBA8,128,512,1);
            int bounceOpen=texture(GL30.GL_RGBA32F,128,384,1);
            int beds=texture(GL11.GL_RGBA8,128,128,1);
            int occlusion=texture(GL30.GL_RGBA16F,32,32,1);
            clear(occlusion,0,.5f,.5f,.5f,.75f);
            textures.addAll(List.of(cloudShadow,lampFields,bounce,bounceOpen,beds,occlusion));
            textures.addAll(List.of(albedo,normal,engine,depth,reflection,reflectionDepth,relief));
            clear(albedo,0,.25f,.5f,.75f,1);
            clear(normal,0,.5f,1,.5f,1);
            clear(engine,0,.7f,.6f,.5f,1);
            Textures.glClearTexImage(depth,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,color(.5f,0,0,0));
            clear(reflectionDepth,1,.375f,0,0,1);
            clear(relief,0,0,0,0,1);
            Textures.glTextureSubImage2D(relief,0,4991,4032,2,1,GL11.GL_RED,GL11.GL_FLOAT,
                BufferUtils.createFloatBuffer(2).put(.25f).put(.75f).flip());
            use(program,"uAlbedo",9,albedo);
            use(program,"uNormal",10,normal);
            use(program,"uEngineLight",11,engine);
            use(program,"uDepth",12,depth);
            use(program,"uRelief",44,relief);
            use(program,"uReflection",119,reflection);
            use(program,"uReflectionDepth",120,reflectionDepth);
            use(program,"uCloudShadow",121,cloudShadow);
            use(program,"uLampFields",122,lampFields);
            use(program,"uBounce",123,bounce);
            use(program,"uBounceOpen",124,bounceOpen);
            use(program,"uBedColours",129,beds);
            use(program,"uOcclusion",130,occlusion);
            program.use();
            program.setInt("uDebugView",1);
            closeTo(draw(),.25f,.5f,.75f,1);
            for(String preset:List.of("low","default","high","ultra")){
                ShaderPacks.useBuiltIn("default",preset);
                String error=GlProgram.relinkAll(ShaderPacks.active());
                check(error==null,"Relink "+preset+": "+error);
                program.use();
                program.setInt("uDebugView",1);
                check(program.has("uOcclusion")&&program.has("uSpotShadow"),
                    "Ambient occlusion and spot shadows must stay linked at "+preset);
                check(program.has("uReflection")==!preset.equals("ultra"),"Unexpected reflections at "+preset);
                long before=Packing.memoryStats()[3];
                closeTo(draw(),.25f,.5f,.75f,1);
                long imported=Packing.memoryStats()[3]-before;
                check(imported>=2L*RELIEF_WIDTH*RELIEF_HEIGHT&&imported<64L*1024*1024,"First-frame copy bytes for "+preset+": "+imported);
                long reused=Packing.memoryStats()[3];
                closeTo(draw(),.25f,.5f,.75f,1);
                check(Packing.memoryStats()[3]==reused,"Stable textures were reimported: "+preset);
                check(Packing.memoryStats()[0] <= Packing.BUDGET,"Budget exceeded");
                program.setInt("uDebugView",4);
                closeTo(draw(),.75f,.75f,.75f,1);
                System.out.println("LIGHTING "+preset+" imported_bytes="+imported+" steady_bytes=0");
            }
            probePackedInputs(relief,reflectionDepth);
            ShaderPacks.useBuiltIn("normal","low");
            check(GlProgram.relinkAll(ShaderPacks.active())==null,"Return to Vanilla relink");
            program.use();
            program.setInt("uDebugView",1);
            closeTo(draw(),.25f,.5f,.75f,1);
            System.out.println("PASS real lighting switch, full-size resources, pixels, R16 odd rows, float mip mutation, return to Vanilla");
        } finally {
            program.delete();
            for(int texture:textures)Textures.glDeleteTextures(texture);
            Context.destroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
