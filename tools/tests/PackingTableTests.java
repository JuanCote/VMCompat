package vmcompat;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

/** Checks the pixels from queued draws across table eviction and source changes. */
public final class PackingTableTests {
    private static final int WIDTH=32;
    private static final String FRAGMENT="""
        #version 330
        uniform sampler2D images[16];
        uniform sampler2D a;
        uniform sampler2D b;
        out vec4 color;
        void main(){
            vec4 sum=vec4(0);
            for(int i=0;i<16;i++)sum+=textureLod(images[i],vec2(.5),0);
            color=(sum+textureLod(a,vec2(.5),0)+textureLod(b,vec2(.5),0))/18.;
        }
        """;

    private static void check(boolean ok,String detail){PackingResidencyTests.check(ok,detail);}

    private static void assign(int program){
        Shaders.glUseProgram(program);
        for(int i=0;i<16;i++)Samplers.glUniform1i(Shaders.glGetUniformLocation(program,"images["+i+"]"),0);
        Samplers.glUniform1i(Shaders.glGetUniformLocation(program,"a"),0);
        Samplers.glUniform1i(Shaders.glGetUniformLocation(program,"b"),0);
    }

    private static void draw(int cell,int texture){
        PackingResidencyTests.bind(0,texture);
        GL11.glViewport(cell,0,1,1);
        Draws.glDrawArrays(GL11.GL_TRIANGLES,0,3);
    }

    private static void update(int texture,int value){
        ByteBuffer pixels=BufferUtils.createByteBuffer(4).put(new byte[]{(byte)value,0,0,(byte)255}).flip();
        Textures.glTextureSubImage2D(texture,0,0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
    }

    private static void churn(int[] textures)throws Exception{
        for(int i=0;i<36;i++)draw(0,textures[i%textures.length]);
        GL11.glFinish();
        long[] durations=new long[3];
        for(int block=0;block<durations.length;block++){
            long uploads=Counters.value(Counters.Key.PACK_UPLOADS);
            long hits=Counters.value(Counters.Key.PACK_TABLE_REUSES);
            long start=System.nanoTime();
            for(int i=0;i<200;i++)draw(0,textures[(block*200+i)%textures.length]);
            GL11.glFinish();
            durations[block]=System.nanoTime()-start;
            check(Counters.value(Counters.Key.PACK_UPLOADS)-uploads==200&&Counters.value(Counters.Key.PACK_TABLE_REUSES)==hits,"churn must miss each time");
            ByteBuffer pixel=BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            check(Byte.toUnsignedInt(pixel.get(0))==8+16*((block*200+199)%textures.length),"churn final pixel");
        }
        Files.writeString(Path.of(System.getProperty("vmcompat.resultDir"),"packing-churn.json"),
            "{\"cache\":"+PackedTables.ENABLED+",\"draws_per_block\":200,\"completed_ns\":"+Arrays.toString(durations)+"}\n");
    }

    public static void main(String[] args)throws Exception{
        check(GLFW.glfwInit(),"GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        long window=Context.createWindow(WIDTH,16,"Packed tables queued draws",0,0);
        Context.makeCurrent(window);
        GLCapabilities capabilities=Context.capabilities();
        int vao=GL30.glGenVertexArrays();
        Draws.glBindVertexArray(vao);
        int program=StorageBuffersTests.program(FRAGMENT);
        assign(program);
        int fbo=GL30.glGenFramebuffers();
        int output=Textures.glCreateTextures(GL11.GL_TEXTURE_2D);
        Textures.glTextureStorage2D(output,1,GL11.GL_RGBA8,WIDTH,1);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        Gl41.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,output,0);
        check(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"output framebuffer");
        int[] textures=new int[12];
        for(int i=0;i<textures.length;i++)textures[i]=PackingResidencyTests.texture(1,GL11.GL_RGBA8,8+16*i);
        if(Boolean.getBoolean("vmcompat.packingChurn"))churn(textures);

        int[] expected=new int[WIDTH];
        int cell=0;
        long reused= Counters.value(Counters.Key.PACK_TABLE_REUSES);
        for(int index:new int[]{0,1,0,1}){
            expected[cell]=8+16*index;
            draw(cell++,textures[index]);
        }
        check(Counters.value(Counters.Key.PACK_TABLE_REUSES)-reused==(PackedTables.ENABLED?2:0),"A/B/A/B table reuse");

        expected[cell]=0;
        draw(cell++,0);
        for(int index=2;index<textures.length;index++){
            expected[cell]=8+16*index;
            draw(cell++,textures[index]);
        }
        check(!PackedTables.ENABLED||Counters.value(Counters.Key.PACK_TABLE_EVICTIONS)>0,"exercised replacement of queued GPU table");
        expected[cell]=8;
        draw(cell++,textures[0]);
        update(textures[0],219);
        expected[cell]=219;
        draw(cell++,textures[0]);
        Textures.glDeleteTextures(textures[1]);
        textures[1]=PackingResidencyTests.texture(1,GL11.GL_RGBA8,200);
        expected[cell]=200;
        draw(cell++,textures[1]);
        expected[cell]=219;
        draw(cell++,textures[0]);

        long other=Context.createWindow(16,16,"Packing share-group transition",0,window);
        check(other!=0,"shared window");
        GL11.glFinish();
        Context.makeCurrent(other);
        Context.capabilities();
        update(textures[0],150);
        GL11.glFinish();
        Context.destroyWindow(other);
        Context.makeCurrent(window);
        GL.setCapabilities(capabilities);
        assign(program);
        expected[cell]=150;
        draw(cell++,textures[0]);
        check(!PackedTables.ENABLED||GL20.glGetUniformi(program,GL20.glGetUniformLocation(program,"vmcompatTableBase"))==0,"shared context uses fallback row");

        ByteBuffer pixels=BufferUtils.createByteBuffer(cell*4);
        GL11.glReadPixels(0,0,cell,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        for(int i=0;i<cell;i++)check(Byte.toUnsignedInt(pixels.get(i*4))==expected[i],"queued pixel "+i+" expected "+expected[i]);
        check(GL11.glGetError()==0,"GL state after queued draws");
        Shaders.glDeleteProgram(program);
        Textures.glDeleteTextures(textures);
        Textures.glDeleteTextures(output);
        Context.destroyWindow(window);
        GLFW.glfwTerminate();
        check(Packing.memoryStats()[0]==0,"table lifetime is bounded by its program and context");
        System.out.println("PASS bounded packed-table reuse, queued pixels, missing texture, mutation, deletion and share group transition");
    }
}
