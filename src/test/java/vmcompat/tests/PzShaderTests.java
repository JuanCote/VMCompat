package vmcompat.tests;

import java.nio.file.*;
import java.util.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import vmcompat.*;
import zombie.ZomboidFileSystem;
import zombie.core.opengl.ShaderProgram;

/** Exercises the real PZ shader loader without starting the game or loading a world. */
public final class PzShaderTests {
    public static void main(String[] args)throws Exception{
        if(!Boolean.getBoolean("vmcompat.active"))throw new AssertionError("Requires live agent");
        Path cache=Path.of(System.getProperty("vmcompat.root"),"pz-shader-cache");Files.createDirectories(cache);
        ZomboidFileSystem.instance.setCacheDir(cache.toString());
        ZomboidFileSystem.instance.init();
        zombie.debug.DebugLog.setLogEnabled(zombie.debug.DebugType.Shader,true);
        GLFWErrorCallback callback=GLFWErrorCallback.createPrint(System.err);callback.set();
        if(!GLFW.glfwInit())throw new AssertionError("GLFW init");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=Context.createWindow(32,32,"PZ shader loader fixture",0,0);if(window==0)throw new AssertionError("Context");
        Context.makeCurrent(window);org.lwjglx.opengl.Display.capabilities=Context.capabilities();
        try{
            record Variant(String name,boolean isStatic){}
            LinkedHashSet<Variant> names=new LinkedHashSet<>();for(String name:List.of("puddles_hq","puddles_mq","puddles_lq","default","screen","basicEffect","water","water_hq"))names.add(new Variant(name,false));
            try(var files=Files.list(Path.of("media/shaders"))){for(Path file:files.filter(p->p.toString().endsWith(".vert")).sorted().toList()){
                String name=file.getFileName().toString().replaceFirst("\\.vert$","");
                if(name.contains("instanced"))continue;
                if(Files.exists(file.resolveSibling(name+".frag")))names.add(new Variant(name,false));
                if(name.endsWith("_static")){String base=name.substring(0,name.length()-7);if(Files.exists(file.resolveSibling(base+".frag")))names.add(new Variant(base,true));}
            }}
            int count=0;
            for(Variant variant:names){
                String name=variant.name();ShaderProgram program=ShaderProgram.createShaderProgram(name,variant.isStatic(),false,false);
                program.compile();
                if(!program.isCompiled())throw new AssertionError("Real PZ shader loader: "+name);
                System.out.println("PZ LOADER PASS "+name+(variant.isStatic()?" (static)":""));program.destroy();count++;
            }
            System.out.println("PZ loader programs passed: "+count+" (real preprocessing, compilation, linking and validation)");
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();callback.free();}
    }
}
