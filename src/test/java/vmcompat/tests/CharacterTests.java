package vmcompat.tests;

import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import vmcompat.*;
import zombie.ZomboidFileSystem;
import zombie.core.opengl.*;
import zombie.core.textures.*;

/** Real game compositing and skinned shaders, without game startup or a world. */
public final class CharacterTests {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Texture solid(int r,int g,int b,int a){
        Texture t=new Texture(32,32,0);t.bind();
        ByteBuffer pixels=BufferUtils.createByteBuffer(32*32*4);for(int i=0;i<32*32;i++)pixels.put((byte)r).put((byte)g).put((byte)b).put((byte)a);pixels.flip();
        // Fixture setup deliberately bypasses the game's cached texture bind.
        vmcompat.Samplers.glActiveTexture(GL13.GL_TEXTURE0);vmcompat.Samplers.glBindTexture(GL11.GL_TEXTURE_2D,t.getID());
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        return t;
    }
    private static void skinned(Texture texture){
        ShaderProgram shader=ShaderProgram.createShaderProgram("basicEffect",false,false,false);shader.compile();check(shader.isCompiled(),"Skinned program compile");shader.Start();int p=shader.getShaderID();
        int vao=GL30.glGenVertexArrays();Draws.glBindVertexArray(vao);int vbo=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
        FloatBuffer vertices=BufferUtils.createFloatBuffer(6*2*16);int[] order={0,1,2,0,2,3};
        for(int bone=0;bone<2;bone++){float lo=bone==0?-.9f:.1f,hi=bone==0?-.1f:.9f;float[][] xy={{lo,-.4f},{hi,-.4f},{hi,.4f},{lo,.4f}};for(int k:order)vertices.put(new float[]{xy[k][0],xy[k][1],0, 0,0,1, .25f,.5f, 1,0,0,0, bone,0,0,0});}vertices.flip();Buffers.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STATIC_DRAW);
        int[] sizes={3,3,4,4,2},offsets={0,12,32,48,24};for(int i=0;i<5;i++){GL20.glVertexAttribPointer(i,sizes[i],GL11.GL_FLOAT,false,64,offsets[i]);GL20.glEnableVertexAttribArray(i);}
        float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(p,"ModelViewProjection"),false,identity);
        for(int i=0;i<5;i++)GL20.glUniform3f(GL20.glGetUniformLocation(p,"Light"+i+"Direction"),0,0,1);
        GL20.glUniform3f(GL20.glGetUniformLocation(p,"TintColour"),1,1,1);GL20.glUniform3f(GL20.glGetUniformLocation(p,"AmbientColour"),1,1,1);GL20.glUniform1f(GL20.glGetUniformLocation(p,"Alpha"),1);GL20.glUniform1f(GL20.glGetUniformLocation(p,"FinalScale"),1);
        Samplers.glActiveTexture(GL13.GL_TEXTURE0);Samplers.glBindTexture(GL11.GL_TEXTURE_2D,texture.getID());Samplers.glUniform1i(GL20.glGetUniformLocation(p,"Texture"),0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);GL11.glViewport(0,0,32,32);Legacy.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);GL11.glClearColor(0,0,0,0);
        for(int pose=0;pose<2;pose++){
            FloatBuffer palette=BufferUtils.createFloatBuffer(32).put(identity).put(identity);palette.put(16+13,pose*.8f);palette.flip();GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(p,"MatrixPalette[0]"),false,palette);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            Draws.glDrawArrays(GL11.GL_TRIANGLES,0,12);
            ByteBuffer pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(8,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);check((pixel.get(0)&255)>=195&&(pixel.get(3)&255)>250,"First skinned bone and body texture visible: RGBA="+(pixel.get(0)&255)+","+(pixel.get(1)&255)+","+(pixel.get(2)&255)+","+(pixel.get(3)&255)+" GL="+GL11.glGetError());
            GL11.glReadPixels(24,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);check(pose==0?(pixel.get(3)&255)>250:(pixel.get(3)&255)==0,"Second bone moves out of original position");
            if(pose==1){GL11.glReadPixels(24,28,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);check((pixel.get(0)&255)>=195&&(pixel.get(3)&255)>250,"Second bone visible in changed pose");}
        }
        check(GL11.glGetError()==0,"No GL error in skinned model");shader.End();shader.destroy();Buffers.glDeleteBuffers(vbo);Draws.glBindVertexArray(0);GL30.glDeleteVertexArrays(vao);System.out.println("CHARACTER PASS real basicEffect skinning, two bones and two poses with composed texture");
    }
    public static void main(String[] args)throws Exception{
        Path cache=Path.of(System.getProperty("vmcompat.root"),"character-cache");Files.createDirectories(cache);ZomboidFileSystem.instance.setCacheDir(cache.toString());ZomboidFileSystem.instance.init();
        GLFWErrorCallback cb=GLFWErrorCallback.createPrint(System.err);cb.set();check(GLFW.glfwInit(),"GLFW init");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=Context.createWindow(32,32,"Character rendering regression",0,0);check(window!=0,"Window");Context.makeCurrent(window);org.lwjglx.opengl.Display.capabilities=Context.capabilities();RenderThread.renderThread=Thread.currentThread();zombie.core.VBO.GLVertexBufferObject.init();
        try{
            Texture skin=solid(200,80,40,255),mask=solid(255,255,255,255);SmartShader shader=new SmartShader("bodyMask");
            ArrayList<TextureCombinerCommand> commands=new ArrayList<>();commands.add(new TextureCombinerCommand().init(skin,shader,mask,GL11.GL_ONE,GL11.GL_ZERO));
            Texture result=TextureCombiner.instance.combine(commands);
            vmcompat.Samplers.glActiveTexture(GL13.GL_TEXTURE0);vmcompat.Samplers.glBindTexture(GL11.GL_TEXTURE_2D,result.getID());ByteBuffer actual=BufferUtils.createByteBuffer(32*32*4);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,actual);
            int at=(16*32+16)*4;check((actual.get(at+3)&255)>250,"Real TextureCombiner body texture is transparent: alpha="+(actual.get(at+3)&255)+" GL="+GL11.glGetError());check(Math.abs((actual.get(at)&255)-200)<=1,"Body colour lost: "+(actual.get(at)&255));
            System.out.println("CHARACTER PASS real TextureCombiner + bodyMask produce opaque skin");
            skinned(result);
            // Half of the clothing mask is transparent. Preserve skin there.
            Texture clothes=solid(0,0,255,128);vmcompat.Samplers.glActiveTexture(GL13.GL_TEXTURE0);vmcompat.Samplers.glBindTexture(GL11.GL_TEXTURE_2D,mask.getID());ByteBuffer halves=BufferUtils.createByteBuffer(32*32*4);for(int y=0;y<32;y++)for(int x=0;x<32;x++)halves.put((byte)255).put((byte)255).put((byte)255).put((byte)(x<16?0:255));halves.flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,32,32,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,halves);
            Texture fullMask=solid(255,255,255,255);commands.clear();commands.add(new TextureCombinerCommand().init(skin,shader,fullMask,GL11.GL_ONE,GL11.GL_ZERO));commands.add(new TextureCombinerCommand().init(clothes,shader,mask,GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA));
            result=TextureCombiner.instance.combine(commands);vmcompat.Samplers.glActiveTexture(GL13.GL_TEXTURE0);vmcompat.Samplers.glBindTexture(GL11.GL_TEXTURE_2D,result.getID());actual.clear();GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,actual);
            int left=(16*32+8)*4,right=(16*32+24)*4;check(Math.abs((actual.get(left)&255)-200)<=1&&(actual.get(left+3)&255)>250,"Masked clothing preserves skin and alpha");check(Math.abs((actual.get(right)&255)-100)<=2&&Math.abs((actual.get(right+2)&255)-148)<=2&&(actual.get(right+3)&255)>250,"Clothing alpha blends over skin");check(GL11.glGetError()==0,"No GL error in character compositing");
            System.out.println("CHARACTER PASS clothing mask and alpha composition preserve visible skin");
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();cb.free();}
    }
}
