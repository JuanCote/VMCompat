package vmcompat;
import java.nio.*;
import org.lwjgl.opengl.*;
/** Pixel-pack destinations are GPU-produced: never retain their former CPU command bytes. */
public final class GpuWrites {
    public static void glReadPixels(int x,int y,int w,int h,int format,int type,long offset){GL11.glReadPixels(x,y,w,h,format,type,offset);Buffers.pixelPackWrite();}
    public static void glReadPixels(int x,int y,int w,int h,int format,int type,ByteBuffer out){GL11.glReadPixels(x,y,w,h,format,type,out);Buffers.pixelPackWrite();}
    public static void glReadPixels(int x,int y,int w,int h,int format,int type,FloatBuffer out){GL11.glReadPixels(x,y,w,h,format,type,out);Buffers.pixelPackWrite();}
    public static void glReadPixels(int x,int y,int w,int h,int format,int type,IntBuffer out){GL11.glReadPixels(x,y,w,h,format,type,out);Buffers.pixelPackWrite();}
    public static void glGetTexImage(int target,int level,int format,int type,long offset){GL11.glGetTexImage(target,level,format,type,offset);Buffers.pixelPackWrite();}
    public static void nglGetTexImage(int target,int level,int format,int type,long offset){GL11.nglGetTexImage(target,level,format,type,offset);Buffers.pixelPackWrite();}
    public static void glGetTexImage(int target,int level,int format,int type,ByteBuffer out){GL11.glGetTexImage(target,level,format,type,out);Buffers.pixelPackWrite();}
    public static void glGetTexImage(int target,int level,int format,int type,FloatBuffer out){GL11.glGetTexImage(target,level,format,type,out);Buffers.pixelPackWrite();}
    public static void glGetTexImage(int target,int level,int format,int type,IntBuffer out){GL11.glGetTexImage(target,level,format,type,out);Buffers.pixelPackWrite();}
}
