package vmcompat;
import java.nio.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.lang.reflect.*;import java.util.*;
import viewpoint.platform.GlProgram;import viewpoint.render.SceneData;

/** Public OpenGL build. No native library, Metal implementation or alternate renderer. */
public final class Opaque {
    private static Method planned;
    public static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    public static String nativeSha(){return "not-shipped";}
    public static String shaderKey(int program){String v=Shaders.vertexSource(program),f=Shaders.fragmentSource(program);return v==null||f==null?"unknown":hash((v+"\n"+f).getBytes(StandardCharsets.UTF_8));}
    public static boolean recording(){return false;}
    public static long nativePasses(){return 0;}
    public static boolean failed(){return false;}
    public static String detail(){return "Public OpenGL build; Metal not shipped";}
    public static String status(){return Options.PICKING.equals("original")?"OpenGL 4.1":"OpenGL 4.1 / depth staging";}
    public static boolean suppressWorld(){return false;}
    public static void worldBegin(){}
    public static void worldEnd(){}
    public static void cleared(int mask){}
    public static void deletedBuffer(int id){}
    public static void deletedTexture(int id){}
    public static void textureParameters(int id){}
    public static void drawn(){}
    public static void closeCurrent(){}
    public static void batch(int program,int vbo,ByteBuffer metadata,Runnable original){original.run();}
    public static void planned(SceneData scene,GlProgram shader,byte mask,boolean plants){
        try{if(planned==null){planned=Class.forName("viewpoint.render.Meshes").getDeclaredMethod("drawPlanned",SceneData.class,GlProgram.class,byte.class,boolean.class);planned.setAccessible(true);}planned.invoke(null,scene,shader,mask,plants);}
        catch(InvocationTargetException e){throw new IllegalStateException(e.getCause());}catch(Exception e){throw new IllegalStateException(e);}
    }
    static boolean direct(){return false;}
    static boolean capture(int mode,ByteBuffer commands,int count,int stride,boolean indexed){return false;}
    static void write(Path path)throws Exception{Files.writeString(path,"{\"requested\":\"opengl\",\"native_passes\":0,\"metal_shipped\":false}\n");}
}
