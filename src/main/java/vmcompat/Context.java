package vmcompat;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.PointerBuffer;
import java.util.function.IntFunction;
public final class Context{
    public static void destroyWindow(long window){FarShell.closeWindow(window);CompressedCopies.closeWindow(window);if(GLFW.glfwGetCurrentContext()==window){FeedbackSnapshots.closeCurrent();StorageBuffers.closeCurrent();WordStorage.closeCurrent();Packing.closeCurrent();Opaque.closeCurrent();DepthStaging.closeCurrent();FrameTimesGpu.closeCurrent();}GLFW.glfwDestroyWindow(window);ContextResources.destroyed(window);Breakdown.windowDestroyed(window);}
    public static void makeCurrent(long window){GLFW.glfwMakeContextCurrent(window);if(GLFW.glfwGetCurrentContext()!=window)throw new IllegalStateException("makeCurrent failed");ContextResources.madeCurrent(window);Breakdown.currentWindow(window);}
    public static long createWindow(int w,int h,CharSequence title,long monitor,long share){
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,1);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT,GLFW.GLFW_TRUE);
        long window=GLFW.glfwCreateWindow(w,h,title,monitor,share);ContextResources.created(window,share);Breakdown.windowCreated(window);return window;
    }
    public static GLCapabilities capabilities(){return ready(GL.createCapabilities());}
    public static GLCapabilities capabilities(boolean f){return ready(GL.createCapabilities(f));}
    public static GLCapabilities capabilities(IntFunction<PointerBuffer> a){return ready(GL.createCapabilities(a));}
    public static GLCapabilities capabilities(boolean f,IntFunction<PointerBuffer> a){return ready(GL.createCapabilities(f,a));}
    private static GLCapabilities ready(GLCapabilities caps){
        if(!caps.OpenGL41)throw new IllegalStateException("OpenGL 4.1 unavailable");
        Legacy.initialize();Draws.initialize();
        Log.info("OpenGL ready: "+GL11.glGetString(GL11.GL_VERSION)+" / "+GL11.glGetString(GL11.GL_RENDERER));
        return caps;
    }
}
