package vmcompat;
import java.nio.file.*;import org.lwjgl.glfw.*;import org.lwjgl.opengl.*;
public class NativeTraceTests {
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("fatal")){
   if(!NativeTrace.start())throw new AssertionError("Native trace could not start");
   if(!GLFW.glfwInit())throw new AssertionError();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);long window=Context.createWindow(32,32,"Native fatal stack capture fixture",0,0);Context.makeCurrent(window);Context.capabilities();
   GL42.glMemoryBarrier(0);throw new AssertionError("Unsupported native entry unexpectedly returned");
  }
  if(!NativeTrace.start())throw new AssertionError("Native trace unavailable");
  System.out.println("NATIVE_TRACE_STDOUT");System.err.println("NATIVE_TRACE_STDERR");
  Path log=Log.ROOT.resolve("native-console.log");String s=Files.readString(log);if(!s.contains("NATIVE_TRACE_STDOUT")||!s.contains("NATIVE_TRACE_STDERR"))throw new AssertionError("Descriptor output not captured");
  System.out.print("x".repeat(4*1024*1024+1024));System.out.flush();NativeTrace.tick();System.out.println("AFTER_NATIVE_TRACE_TRIM");
  if(Files.size(log)>4*1024*1024||!Files.readString(log).contains("AFTER_NATIVE_TRACE_TRIM"))throw new AssertionError("Native trace trimming failed");
  NativeTrace.restore();System.out.println("PASS native stdout/stderr capture, bounded maintenance and descriptor restoration");
 }
}
