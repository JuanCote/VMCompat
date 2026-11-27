package vmcompat;

import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import viewpoint.platform.Gl;
import viewpoint.platform.ShaderPacks;

public final class WallAndMaskTests {
    private static final int ARRAY = GL30.GL_TEXTURE_2D_ARRAY;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " GL=" + GL11.glGetError());
    }
    private static Object newInstance(Class<?> type, Class<?>[] params, Object... args) throws Exception {
        var constructor = type.getDeclaredConstructor(params);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }
    private static int id(Object object, String fieldName, int index) throws Exception {
        var field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((int[])field.get(object))[index];
    }
    private static void wall() throws Exception {
        ShaderPacks.useBuiltIn("normal","low");
        var vao = Gl.class.getDeclaredField("emptyVao");
        vao.setAccessible(true);
        vao.setInt(null,GL30.glGenVertexArrays());
        Class<?> arrayClass = Class.forName("viewpoint.render.WallArray");
        Object array = newInstance(arrayClass, new Class<?>[]{int.class}, 0);
        Class<?> bakerClass = Class.forName("viewpoint.render.WallBaker");
        Object baker = newInstance(bakerClass, new Class<?>[0]);
        var init = bakerClass.getDeclaredMethod("init",arrayClass);
        init.setAccessible(true);
        init.invoke(baker,array);
        int source = id(baker,"premultiplied",0), output = id(baker,"straight",0);
        ByteBuffer color=BufferUtils.createByteBuffer(16).putFloat(.25f).putFloat(.125f).putFloat(.0625f).putFloat(.5f).flip();
        Textures.glClearTexImage(source,0,GL11.GL_RGBA,GL11.GL_FLOAT,color);
        GL11.glViewport(0,0,64,224);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        Samplers.glActiveTexture(GL13.GL_TEXTURE0);
        var resolve = bakerClass.getDeclaredMethod("resolved",int.class);
        resolve.setAccessible(true);
        resolve.invoke(baker,0);
        int[] rgba = {128,64,32,128};
        for (int mip : new int[]{0,4}) {
            ByteBuffer pixels=BufferUtils.createByteBuffer((64>>mip)*(224>>mip)*4);
            Textures.bound(output,()->GL11.glGetTexImage(GL11.GL_TEXTURE_2D,mip,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels));
            check(GL11.glGetError()==0,"Wall resolve error");
            for(int channel=0;channel<4;channel++) {
                int actual=pixels.get(channel)&255;
                check(Math.abs(actual-rgba[channel])<=2,"Wall mip "+mip+" channel "+channel+" was "+actual);
            }
        }
        var make = arrayClass.getDeclaredMethod("make",int.class);
        make.setAccessible(true);
        int wallArray = (int)make.invoke(array,2);
        var texture = arrayClass.getDeclaredField("texture");
        texture.setAccessible(true);
        texture.setInt(array,wallArray);
        var copy = bakerClass.getDeclaredMethod("copy",arrayClass,int.class);
        copy.setAccessible(true);
        copy.invoke(baker,array,1);
        ByteBuffer layers=BufferUtils.createByteBuffer(4*14*4*2);
        Textures.bound(wallArray,()->GL11.glGetTexImage(ARRAY,4,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,layers));
        check(GL11.glGetError()==0,"Wall layer upload GL error");
        for(int channel=0;channel<4;channel++)
            check(Math.abs((layers.get(4*14*4+channel)&255)-rgba[channel])<=2,"Wall array mip color");
        Textures.glDeleteTextures(new int[]{source,output,wallArray});
        GL30.glDeleteFramebuffers(id(baker,"premultipliedFbo",0));
        GL30.glDeleteFramebuffers(id(baker,"straightFbo",0));
        System.out.println("PASS real WallBaker: premultiplied RGBA -> resolved color and mip 4");
    }

    private static int mask(int layers) {
        int texture=Textures.glCreateTextures(ARRAY);
        Textures.glTextureStorage3D(texture,1,GL30.GL_R16UI,256,256,layers);
        Textures.glTextureParameteri(texture,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        Textures.glTextureParameteri(texture,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        return texture;
    }
    private static void masks(boolean forceFallback) {
        int source=mask(4), target=mask(8);
        ByteBuffer values=BufferUtils.createByteBuffer(256*256*4*2);
        for(int i=0;i<256*256*4;i++)values.putShort((short)(i*31+(i>>>16)*17));
        values.flip();
        Textures.glTextureSubImage3D(source,0,0,0,0,256,256,4,GL30.GL_RED_INTEGER,GL11.GL_UNSIGNED_SHORT,values);
        if(!forceFallback) {
            Textures.glCopyImageSubData(source,ARRAY,0,0,0,0,target,ARRAY,0,0,0,0,256,256,4);
        } else {
            int pack=GL15.glGenBuffers(), unpack=GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpack);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,3);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,11);
            check(IntegerImageCopy.copy(source,ARRAY,0,17,19,1,target,ARRAY,0,3,5,4,31,37,2),"R16UI fallback not selected");
            check(GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)==pack
                    &&GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)==unpack
                    &&GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS)==3
                    &&GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH)==11,"R16UI state not restored");
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);
            GL15.glDeleteBuffers(pack);
            GL15.glDeleteBuffers(unpack);
        }
        ByteBuffer result=BufferUtils.createByteBuffer(256*256*8*2);
        Textures.bound(target,()->GL11.glGetTexImage(ARRAY,0,GL30.GL_RED_INTEGER,GL11.GL_UNSIGNED_SHORT,result));
        check(GL11.glGetError()==0,"Mask copy/read GL error");
        if(forceFallback) {
            for(int z=0;z<2;z++)for(int y=0;y<37;y++)for(int x=0;x<31;x++){
                int from=2*((1+z)*256*256+(19+y)*256+17+x);
                int to=2*((4+z)*256*256+(5+y)*256+3+x);
                if(result.getShort(to)!=values.getShort(from))throw new AssertionError("Cropped R16UI copy mismatch");
            }
        } else {
            for(int i=0;i<values.limit();i+=2)
                if(result.getShort(i)!=values.getShort(i))throw new AssertionError("R16UI growth copy mismatch at byte "+i);
        }
        Textures.glDeleteTextures(new int[]{source,target});
        check(Packing.memoryStats()[0]==0,"R16UI allocation not released");
        System.out.println("PASS exact unsigned R16UI "+(forceFallback?"fallback crop, layers and restored state":"array growth"));
    }

    private static void compressedGrowth() {
        int format = 0x83F2; // S3TC DXT3
        int source = Textures.glCreateTextures(ARRAY), target = Textures.glCreateTextures(ARRAY);
        Textures.glTextureStorage3D(source,1,format,64,64,3);
        Textures.glTextureStorage3D(target,1,format,64,64,6);
        for (int layer=0;layer<3;layer++)
            CompressedCopyTests.upload(source,ARRAY,0,layer,64,format,new int[]{0xf800,0x07e0,0x001f}[layer]);
        Textures.glCopyImageSubData(source,ARRAY,0,0,0,0,target,ARRAY,0,0,0,0,64,64,3);
        ByteBuffer expected = CompressedCopyTests.read(source,ARRAY,0,0);
        CompressedCopyTests.same(expected,0,CompressedCopyTests.read(target,ARRAY,0,0),0,expected.capacity(),"DXT3 LookMasks growth");
        Textures.glDeleteTextures(new int[]{source,target});
        System.out.println("PASS DXT3 array growth with three populated layers");
    }

    public static void main(String[] args) throws Exception {
        check(GLFW.glfwInit(),"GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);
        long window=Context.createWindow(64,224,"Viewpoint wall and mask regression",0,0);
        Context.makeCurrent(window);
        Context.capabilities();
        try {
            if (Boolean.getBoolean("vmcompat.wallAndMaskProbeBaseline")) {
                try {wall();} finally {masks(false);}
            } else {
                wall();
                masks(false);
                masks(true);
                compressedGrowth();
            }
        } finally {
            Context.destroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
