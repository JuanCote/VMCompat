package vmcompat.tests;
import java.nio.*;import org.lwjgl.*;import org.lwjgl.glfw.*;import org.lwjgl.opengl.*;import imgui.*;import imgui.gl3.*;import vmcompat.*;
/** Actual Viewpoint settings backend initialization, without starting the game. */
public class ViewpointImGuiTests{
 public static void main(String[] args)throws Exception{
  if(!GLFW.glfwInit())throw new AssertionError();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);long w=Context.createWindow(32,32,"Viewpoint settings initialization",0,0);Context.makeCurrent(w);Context.capabilities();ImGuiImplGl3 renderer=null;var display=Class.forName("org.lwjglx.opengl.Display$Window").getDeclaredField("handle");display.setAccessible(true);display.setLong(null,w);
  try{
   Class<?> c=Class.forName("viewpoint.platform.ImGuiFrame");var create=c.getDeclaredMethod("create");create.setAccessible(true);create.invoke(null);var backend=c.getDeclaredField("backend");backend.setAccessible(true);renderer=(ImGuiImplGl3)backend.get(null);
   var version=ImGuiImplGl3.class.getDeclaredField("glslVersion");version.setAccessible(true);if(!"#version 410 core".equals(version.get(renderer)))throw new AssertionError("Viewpoint backend GLSL mismatch");
   ImGui.getIO().setDisplaySize(32,32);ImGui.getIO().setDeltaTime(1f/60);GL11.glViewport(0,0,32,32);GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);ImGui.newFrame();ImGui.getForegroundDrawList().addRectFilled(0,0,32,32,0xff0000ff);ImGui.render();var data=ImGui.getDrawData();try{renderer.renderDrawData(data);}finally{ImGui.freeDrawData(data);}
   ByteBuffer pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);if((pixel.get(0)&255)<250||(pixel.get(1)&255)>3||GL11.glGetError()!=0)throw new AssertionError("Viewpoint settings pixels/GL error");System.out.println("PASS actual Viewpoint settings create and core shader pixels");
  }finally{if(renderer!=null)renderer.dispose();if(ImGui.getCurrentContext().isValidPtr())ImGui.destroyContext();Context.destroyWindow(w);GLFW.glfwTerminate();}
 }
}
