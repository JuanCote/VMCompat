package vmcompat.tests;

import java.nio.*;
import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import imgui.*;
import imgui.gl3.ImGuiImplGl3;
import vmcompat.Context;
import vmcompat.Samplers;
import vmcompat.Legacy;

public final class ImGuiTests {
    public static void main(String[] args){
        if(!Boolean.getBoolean("vmcompat.active"))throw new AssertionError("Requires live agent");
        GLFWErrorCallback callback=GLFWErrorCallback.createPrint(System.err);callback.set();if(!GLFW.glfwInit())throw new AssertionError("GLFW init");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=Context.createWindow(32,32,"Real ImGui fixture",0,0);if(window==0)throw new AssertionError("Window");Context.makeCurrent(window);Context.capabilities();
        ImGui.createContext();ImGuiImplGl3 backend=new ImGuiImplGl3();
        try{
            Samplers.glActiveTexture(GL13.GL_TEXTURE0+7);backend.init("#version 130");
            if(Legacy.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=GL13.GL_TEXTURE0+7)throw new AssertionError("ImGui init did not restore the virtual active texture unit");
            ImGui.getIO().setIniFilename(null);ImGui.getIO().setDisplaySize(32,32);ImGui.getIO().setDeltaTime(1f/60f);
            GL11.glViewport(0,0,32,32);GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            ImGui.newFrame();ImGui.getForegroundDrawList().addRectFilled(0,0,32,32,0xff0000ff);ImGui.render();backend.renderDrawData(ImGui.getDrawData());
            ByteBuffer pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            if((pixel.get(0)&255)<250||(pixel.get(1)&255)>3)throw new AssertionError("ImGui red pixel: "+(pixel.get(0)&255)+","+(pixel.get(1)&255)+","+(pixel.get(2)&255));
            if(GL11.glGetError()!=0)throw new AssertionError("ImGui GL error");
            if(Legacy.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=GL13.GL_TEXTURE0+7)throw new AssertionError("ImGui rendering did not restore the virtual active texture unit");
            System.out.println("IMGUI PIXEL PASS real GL3 backend initialized from GLSL 130 and rendered indexed geometry");
        }finally{backend.dispose();ImGui.destroyContext();GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();callback.free();}
    }
}
