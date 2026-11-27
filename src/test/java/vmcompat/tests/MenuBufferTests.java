package vmcompat.tests;

import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import vmcompat.*;
import zombie.core.VBO.GLVertexBufferObject;


/** Real PZ menu-ring VBO API, including the failing 65536-byte index mapping.
 * No menu/world/main-loop launch. Raw VAO zero also exercises external GL restore.
 */
public final class MenuBufferTests {
    private static void cycle(int target,int frames){
        GLVertexBufferObject buffer=new GLVertexBufferObject(65536,target,GL15.GL_STREAM_DRAW);buffer.create();
        try{for(int frame=0;frame<frames;frame++){
            buffer.bind();ByteBuffer data=buffer.map();check(data!=null&&data.remaining()==65536,"Real engine mapping size");data.putInt(0,0x12340000+frame);check(buffer.unmap(),"Real engine unmap");
            ByteBuffer result=BufferUtils.createByteBuffer(4).order(ByteOrder.nativeOrder());GL15.glGetBufferSubData(target,0,result);check(result.getInt(0)==0x12340000+frame,"Mapped menu-ring bytes survived");check(GL11.glGetError()==0,"No GL error in menu buffer cycle");
        }}finally{GL15.glDeleteBuffers(buffer.getID());}
    }
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[1]);Files.createDirectories(out);
        GLFWErrorCallback callback=GLFWErrorCallback.createPrint(System.err);callback.set();check(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);long window=Context.createWindow(32,32,"Real menu buffer regression",0,0);Context.makeCurrent(window);Context.capabilities();GLVertexBufferObject.init();
        long start=System.nanoTime();
        try{
            Draws.glBindVertexArray(0);cycle(GL15.GL_ARRAY_BUFFER,64);cycle(GL15.GL_ELEMENT_ARRAY_BUFFER,64);
            // This call deliberately bypasses our alias, like an external renderer.
            GL30.glBindVertexArray(0);cycle(GL15.GL_ARRAY_BUFFER,16);
            cycle(GL15.GL_ELEMENT_ARRAY_BUFFER,256);
            while(GL11.glGetError()!=0){}
            int custom=GL30.glGenVertexArrays();Draws.glBindVertexArray(custom);cycle(GL15.GL_ELEMENT_ARRAY_BUFFER,16);check(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)==custom,"Existing custom VAO must stay bound");Draws.glBindVertexArray(0);GL30.glDeleteVertexArrays(custom);

            System.out.println("MENU BUFFER PASS isolated real engine vertex/index mappings, including raw VAO zero and custom VAO");
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();callback.free();}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private MenuBufferTests(){}
}
