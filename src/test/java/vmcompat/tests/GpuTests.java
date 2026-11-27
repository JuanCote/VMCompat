package vmcompat.tests;

import java.nio.*;
import java.util.*;
import java.nio.file.*;
import java.util.regex.*;
import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import vmcompat.*;

public final class GpuTests {
    private static int scenarios;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void done(String name){int error=GL11.glGetError();check(error==0,name+": OpenGL error 0x"+Integer.toHexString(error));scenarios++;System.out.println("GPU PASS "+name);}
    public static void main(String[] args){
        GLFWErrorCallback error=GLFWErrorCallback.createPrint(System.err);error.set();check(GLFW.glfwInit(),"GLFW initialization");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);long window=Context.createWindow(64,64,"VMPerf GPU tests",0,0);check(window!=0,"Core context creation");Context.makeCurrent(window);Context.capabilities();
        try{
            GL11.glViewport(0,0,64,64);GL11.glDisable(GL11.GL_DEPTH_TEST);done("OpenGL 4.1 core initialization");
            testTypedDummies();testTextures();testBuffers();testVirtualSamplers();testImmediate();testDepthView();testOverflow();testCombinedUnits();testIndirect();testIntegerClear();testOriginalShaders();testFailedShaderContract();if(args.length>0)testGameShaders(Path.of(args[0],"media/shaders"));
            System.out.println("GPU scenarios passed: "+scenarios);
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();error.free();}
    }
    private static void testTypedDummies(){
        int p=program("#version 410 core\nuniform isampler2D signedImage;uniform usampler2D unsignedImage;uniform sampler2DShadow shadowImage;out vec4 colour;void main(){colour=vec4(float(texture(signedImage,vec2(.5)).x),float(texture(unsignedImage,vec2(.5)).x),texture(shadowImage,vec3(.5,.5,.5)),1);}");
        Shaders.glUseProgram(p);for(String name:new String[]{"signedImage","unsignedImage","shadowImage"})Samplers.glUniform1i(Shaders.glGetUniformLocation(p,name),222);
        Samplers.glActiveTexture(GL13.GL_TEXTURE0+222);Samplers.glBindTexture(GL11.GL_TEXTURE_2D,0);Draws.glDrawArrays(GL11.GL_TRIANGLES,0,3);check((pixel().get(3)&255)==255,"Typed dummy samplers render without type mismatch");Shaders.glDeleteProgram(p);done("typed signed/unsigned/shadow dummy textures");
    }
    private static void testTextures(){
        int src=Textures.glCreateTextures(GL11.GL_TEXTURE_2D),dst=Textures.glCreateTextures(GL11.GL_TEXTURE_2D);
        Textures.glTextureStorage2D(src,1,GL11.GL_RGBA8,2,2);Textures.glTextureStorage2D(dst,1,GL11.GL_RGBA8,2,2);
        ByteBuffer pixels=BufferUtils.createByteBuffer(16);for(int i=0;i<16;i++)pixels.put(i,(byte)(i*13));Textures.glTextureSubImage2D(src,0,0,0,2,2,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        Textures.glCopyImageSubData(src,GL11.GL_TEXTURE_2D,0,0,0,0,dst,GL11.GL_TEXTURE_2D,0,0,0,0,2,2,1);
        ByteBuffer actual=BufferUtils.createByteBuffer(16);Textures.bound(dst,()->GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,actual));for(int i=0;i<16;i++)check(pixels.get(i)==actual.get(i),"Texture copy pixel "+i);
        Textures.glDeleteTextures(src);Textures.glDeleteTextures(dst);done("texture storage, upload and GPU image copy");
    }
    private static void testBuffers(){
        int a=Gl41.glCreateBuffers(),b=Gl41.glCreateBuffers();Gl41.glNamedBufferStorage(a,64,256);Gl41.glNamedBufferStorage(b,64,256);GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,a);
        ByteBuffer input=BufferUtils.createByteBuffer(64);for(int i=0;i<64;i++)input.put(i,(byte)(i+1));Buffers.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER,0,input);Gl41.glCopyNamedBufferSubData(a,b,0,0,64);
        ByteBuffer output=BufferUtils.createByteBuffer(64);Gl41.glGetNamedBufferSubData(b,0,output);for(int i=0;i<64;i++)check(input.get(i)==output.get(i),"Buffer GPU copy "+i);GL15.glDeleteBuffers(a);GL15.glDeleteBuffers(b);done("buffer storage and GPU copy");
    }
    private static int program(String fragment){
        String vertex="#version 410 core\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0,1);}";
        int v=Shaders.compileRaw(GL20.GL_VERTEX_SHADER,vertex),f=Shaders.compileRaw(GL20.GL_FRAGMENT_SHADER,fragment),p=GL20.glCreateProgram();Shaders.glAttachShader(p,v);Shaders.glAttachShader(p,f);Shaders.glLinkProgram(p);check(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,"Fixture program link: "+GL20.glGetProgramInfoLog(p));GL20.glDeleteShader(v);GL20.glDeleteShader(f);return p;
    }
    private static ByteBuffer pixel(){ByteBuffer p=BufferUtils.createByteBuffer(4);GL11.glReadPixels(32,32,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);return p;}
    private static void testVirtualSamplers(){
        int texture=Textures.glCreateTextures(GL11.GL_TEXTURE_2D);Textures.glTextureStorage2D(texture,1,GL11.GL_RGBA8,1,1);Textures.glTextureParameteri(texture,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);Textures.glTextureParameteri(texture,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        ByteBuffer red=BufferUtils.createByteBuffer(4).put(new byte[]{(byte)255,0,0,(byte)255});red.flip();Textures.glTextureSubImage2D(texture,0,0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,red);
        Samplers.glActiveTexture(GL13.GL_TEXTURE0+200);Samplers.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        int p=program("#version 410 core\nuniform sampler2D image;out vec4 colour;void main(){colour=texture(image,vec2(0.5));}");Shaders.glUseProgram(p);Samplers.glUniform1i(GL20.glGetUniformLocation(p,"image"),200);Draws.glDrawArrays(GL11.GL_TRIANGLES,0,3);ByteBuffer actual=pixel();check((actual.get(0)&255)>250&&(actual.get(1)&255)<3,"Virtual sampler 200 routed to physical unit");
        Shaders.glDeleteProgram(p);Textures.glDeleteTextures(texture);done("virtual texture units produce correct pixels");
    }
    private static void testImmediate(){
        GL20.glUseProgram(0);Legacy.glDisable(GL11.GL_TEXTURE_2D);Legacy.glMatrixMode(5889);Legacy.glLoadIdentity();Legacy.glMatrixMode(5888);Legacy.glLoadIdentity();Legacy.glColor4f(0,1,0,1);Legacy.glBegin(7);Legacy.glVertex2f(-1,-1);Legacy.glVertex2f(1,-1);Legacy.glVertex2f(1,1);Legacy.glVertex2f(-1,1);Legacy.glEnd();ByteBuffer p=pixel();check((p.get(1)&255)>250&&(p.get(0)&255)<3,"Legacy quad green pixels");done("legacy matrices, immediate quads and colour");
    }
    private static void testDepthView(){
        int array=Textures.glCreateTextures(GL30.GL_TEXTURE_2D_ARRAY),view=GL11.glGenTextures();Textures.glTextureStorage3D(array,1,GL30.GL_DEPTH_COMPONENT32F,4,4,2);Textures.glTextureView(view,GL11.GL_TEXTURE_2D,array,GL30.GL_DEPTH_COMPONENT32F,0,1,1,1);
        ByteBuffer value=BufferUtils.createByteBuffer(4).putFloat(.42f);value.flip();Textures.glClearTexImage(view,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,value);
        Textures.attachedDepth(0,view);Textures.markDraw();Textures.synchronizeViews();FloatBuffer depth=BufferUtils.createFloatBuffer(32);Textures.bound(array,()->GL11.glGetTexImage(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth));for(int i=16;i<32;i++)check(Math.abs(depth.get(i)-.42f)<1e-5,"Depth view shared layer "+i);Textures.glDeleteTextures(array);Textures.glDeleteTextures(view);done("depth texture views copy back to array layers");
    }
    private static void testOverflow(){
        int p=GL20.glCreateProgram();String vs="#version 410 core\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0,1);}";
        String fs="#version 410 core\nuniform sampler2D images[17];out vec4 colour;void main(){colour=vec4(0);for(int i=0;i<17;i++)colour+=texture(images[i],vec2(0.5))/17.0;}";
        int[] shaders={GL20.glCreateShader(GL20.GL_VERTEX_SHADER),GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER)};for(int i=0;i<2;i++){Shaders.vpSource(shaders[i],i==0?vs:fs);GL20.glCompileShader(shaders[i]);check(GL20.glGetShaderi(shaders[i],GL20.GL_COMPILE_STATUS)!=0,"Overflow shader compilation");Shaders.glAttachShader(p,shaders[i]);}Shaders.glLinkProgram(p);check(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,"Fixture program link: "+GL20.glGetProgramInfoLog(p));Shaders.glUseProgram(p);
        int[] textures=new int[17];for(int i=0;i<17;i++){int tex=Textures.glCreateTextures(GL11.GL_TEXTURE_2D);textures[i]=tex;Textures.glTextureStorage2D(tex,1,GL11.GL_RGBA8,1,1);Textures.glTextureParameteri(tex,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);Textures.glTextureParameteri(tex,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);ByteBuffer colour=BufferUtils.createByteBuffer(4).put(new byte[]{(byte)(i*10),0,0,(byte)255});colour.flip();Textures.glTextureSubImage2D(tex,0,0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,colour);Samplers.glActiveTexture(GL13.GL_TEXTURE0+i);Samplers.glBindTexture(GL11.GL_TEXTURE_2D,tex);Samplers.glUniform1i(Shaders.glGetUniformLocation(p,"images["+i+"]"),i);}
        Draws.glDrawArrays(GL11.GL_TRIANGLES,0,3);ByteBuffer pixel=pixel();check(Math.abs((pixel.get(0)&255)-80)<=1,"Overflow packing pixels: "+(pixel.get(0)&255));Shaders.glDeleteProgram(p);for(int s:shaders)GL20.glDeleteShader(s);Textures.glDeleteTextures(textures);done("17 samplers packed beyond the hardware fragment limit");
    }
    private static void testCombinedUnits(){
        String vs="#version 410 core\nuniform samplerBuffer buffers[3];flat out vec4 base;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2-1,0,1);base=texelFetch(buffers[0],0)+texelFetch(buffers[1],0)+texelFetch(buffers[2],0);}";
        String fs="#version 410 core\nuniform sampler2D images[16];flat in vec4 base;out vec4 colour;void main(){colour=base;for(int i=0;i<16;i++)colour+=texture(images[i],vec2(.5))/16.0;}";
        int v=Shaders.compileRaw(GL20.GL_VERTEX_SHADER,vs),f=Shaders.compileRaw(GL20.GL_FRAGMENT_SHADER,fs),p=GL20.glCreateProgram();Shaders.glAttachShader(p,v);Shaders.glAttachShader(p,f);Shaders.glLinkProgram(p);check(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,"Combined stage sampler link");Shaders.glUseProgram(p);
        int[] textures=new int[19],buffers=new int[3];
        for(int i=0;i<3;i++){buffers[i]=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffers[i]);ByteBuffer zero=BufferUtils.createByteBuffer(16);Buffers.glBufferData(GL15.GL_ARRAY_BUFFER,zero,GL15.GL_STATIC_DRAW);textures[i]=GL11.glGenTextures();Samplers.glActiveTexture(GL13.GL_TEXTURE0+100+i);Samplers.glBindTexture(GL31.GL_TEXTURE_BUFFER,textures[i]);GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER,GL30.GL_RGBA32F,buffers[i]);Samplers.glUniform1i(GL20.glGetUniformLocation(p,"buffers["+i+"]"),100+i);}
        for(int i=0;i<16;i++){int tex=Textures.glCreateTextures(GL11.GL_TEXTURE_2D);textures[i+3]=tex;Textures.glTextureStorage2D(tex,1,GL11.GL_RGBA8,1,1);Textures.glTextureParameteri(tex,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);Textures.glTextureParameteri(tex,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);ByteBuffer colour=BufferUtils.createByteBuffer(4).put(new byte[]{(byte)(i*10),0,0,(byte)255});colour.flip();Textures.glTextureSubImage2D(tex,0,0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,colour);Samplers.glActiveTexture(GL13.GL_TEXTURE0+i);Samplers.glBindTexture(GL11.GL_TEXTURE_2D,tex);Samplers.glUniform1i(GL20.glGetUniformLocation(p,"images["+i+"]"),i);}
        Draws.glDrawArrays(GL11.GL_TRIANGLES,0,3);check(Math.abs((pixel().get(0)&255)-75)<=1,"Nineteen combined samplers retain distinct bindings");Textures.glDeleteTextures(textures);for(int buffer:buffers)Buffers.glDeleteBuffers(buffer);Shaders.glDeleteProgram(p);GL20.glDeleteShader(v);GL20.glDeleteShader(f);done("19 combined vertex/fragment samplers keep TBOs on safe units and leave staging units reserved");
    }
    private static void testOriginalShaders(){
        try{
            Class<?> packs=Class.forName("viewpoint.platform.ShaderPacks");
            Class<?> loader=Class.forName("viewpoint.platform.Shaders");var load=loader.getDeclaredMethod("load",String.class);load.setAccessible(true);
            for(String pack:new String[]{System.getProperty("vmcompat.testPack","normal")}){packs.getMethod("useBuiltIn",String.class,String.class).invoke(null,pack,"low");
            for(String[] pair:new String[][]{{"mesh.vert","gbuffer.frag"},{"model.vert","model_gbuffer.frag"},{"screen.vert","light.frag"},{"screen.vert","post.frag"},{"sky.vert","sky.frag"},{"far_shell.vert","far_shadow_shell.frag"},{"far_shell.vert","far_shell.frag"},{"far_shell.vert","gbuffer_shell.frag"}}){
                int program=GL20.glCreateProgram();for(int i=0;i<2;i++){Object source=load.invoke(null,pair[i]);var text=source.getClass().getDeclaredField("text");text.setAccessible(true);int shader=GL20.glCreateShader(i==0?GL20.GL_VERTEX_SHADER:GL20.GL_FRAGMENT_SHADER);Shaders.vpSource(shader,(String)text.get(source));GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0){try{Files.writeString(Path.of("build/shader-failure.glsl"),Shaders.convert((String)text.get(source),i==0,false));}catch(Exception ignored){}throw new AssertionError(pack+"/"+pair[i]+": "+GL20.glGetShaderInfoLog(shader));}Shaders.glAttachShader(program,shader);GL20.glDeleteShader(shader);}Shaders.glLinkProgram(program);check(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0,"Original program link: "+GL20.glGetProgramInfoLog(program));Shaders.glDeleteProgram(program);done("original "+pack+" shader pair "+Arrays.toString(pair));
            }}
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot use installed shader assets as test inputs",e);}
    }
    private static void testIntegerClear(){
        int tex=Textures.glCreateTextures(GL11.GL_TEXTURE_2D);Textures.glTextureStorage2D(tex,1,GL30.GL_R32UI,2,2);
        ByteBuffer value=BufferUtils.createByteBuffer(4).putInt(12345);value.flip();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(0,0,0,0);GL11.glColorMask(false,false,false,false);
        Textures.glClearTexImage(tex,0,GL30.GL_RED_INTEGER,GL11.GL_UNSIGNED_INT,value);
        check(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),"Clear preserves scissor enable");GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);
        IntBuffer pixels=BufferUtils.createIntBuffer(4);Textures.bound(tex,()->GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL30.GL_RED_INTEGER,GL11.GL_UNSIGNED_INT,pixels));for(int i=0;i<4;i++)check(pixels.get(i)==12345,"Integer clear entire texture");Textures.glDeleteTextures(tex);done("integer storage and clear ignore then restore scissor and masks");
    }
    private static void testFailedShaderContract(){
        int shader=GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER),p=GL20.glCreateProgram();
        Shaders.gameSource(shader,"#version 330\nvoid main(){gl_FragColor=missingFixtureFunction();}");Shaders.glCompileShader(shader);
        check(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0,"Deliberately invalid fixture must fail compilation");
        Shaders.glAttachShader(p,shader);Shaders.glLinkProgram(p);
        check(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0,"Native failure status is returned to caller");
        Shaders.glDeleteProgram(p);GL20.glDeleteShader(shader);done("invalid shader returns failure status without escaping the caller's recovery path (expected diagnostic)");
    }
    private static void testIndirect(){
        int p=program("#version 410 core\nout vec4 colour;void main(){colour=vec4(0,0,1,1);}");Shaders.glUseProgram(p);
        int indirect=GL15.glGenBuffers();GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,indirect);Buffers.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER,16,GL15.GL_DYNAMIC_DRAW);
        IntBuffer command=BufferUtils.createIntBuffer(4).put(new int[]{3,1,0,0});command.flip();Buffers.glBufferSubData(GL40.GL_DRAW_INDIRECT_BUFFER,0,command);
        Draws.glMultiDrawArraysIndirect(GL11.GL_TRIANGLES,0L,1,0);check((pixel().get(2)&255)>250,"Indirect command blue pixels");
        GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,0);Buffers.glDeleteBuffers(indirect);Shaders.glDeleteProgram(p);done("indirect command dispatch produces correct pixels");
    }
    private static String include(Path path,Set<Path> seen)throws Exception{
        path=path.normalize();if(!seen.add(path))return "";String source=Files.readString(path).replaceAll("(?m)^\\s*#version[^\\n]*","");
        Matcher matcher=Pattern.compile("(?m)^\\s*#include\\s+\"([^\"]+)\"[^\\n]*").matcher(source);StringBuffer expanded=new StringBuffer();
        while(matcher.find()){Path child=path.getParent().resolve(matcher.group(1)+".glsl"),header=path.getParent().resolve(matcher.group(1)+".h");String text=(Files.exists(header)?include(header,seen)+"\n":"")+include(child,seen);matcher.appendReplacement(expanded,Matcher.quoteReplacement(text));}matcher.appendTail(expanded);return expanded.toString();
    }
    private static void testGameShaders(Path root){
        try(var files=Files.list(root)){
            int count=0,ssbo=0;for(Path file:files.filter(p->p.toString().endsWith(".vert")||p.toString().endsWith(".frag")).sorted().toList()){
                String source="#version 410 core\n"+include(file,new HashSet<>());if(source.contains("std430")){ssbo++;continue;}int shader=GL20.glCreateShader(file.toString().endsWith(".vert")?GL20.GL_VERTEX_SHADER:GL20.GL_FRAGMENT_SHADER);Shaders.gameSource(shader,source);GL20.glCompileShader(shader);
                if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(file.getFileName()+": "+GL20.glGetShaderInfoLog(shader));GL20.glDeleteShader(shader);count++;
            }done("compilation of "+count+" installed game shaders (expanded includes; "+ssbo+" OpenGL 4.3 SSBO shaders excluded)");
        }catch(Exception e){throw new IllegalStateException("Game shader corpus",e);}
    }
}
