package vmcompat.tests;
import vmcompat.*;
import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import java.nio.*;
import static vmcompat.Counters.Key.*;
public final class BatchStateTests {
    static void check(boolean b,String msg){if(!b)throw new AssertionError(msg+" GL="+GL11.glGetError());}
    static long n(Counters.Key key){return Counters.snapshot()[key.ordinal()];}
    static int make(boolean uniforms){int p=Shaders.glCreateProgram();attach(p,uniforms);return p;}
    static void attach(int p,boolean uniforms){String decl=uniforms?"uniform int vmcompatBaseInstance;uniform int vmcompatDrawId;":"";String value=uniforms?"vec2(vmcompatBaseInstance,vmcompatDrawId)":"vec2(8,9)";
        int v=Shaders.compileRaw(GL20.GL_VERTEX_SHADER,"#version 410 core\nlayout(location=0)in vec2 position;layout(location=1)in vec2 delta;"+decl+"flat out vec2 checkValue;void main(){gl_Position=vec4(position+delta,0,1);checkValue="+value+";}");int f=Shaders.compileRaw(GL20.GL_FRAGMENT_SHADER,"#version 410 core\nflat in vec2 checkValue;out vec4 color;void main(){color=vec4(checkValue/255.,0,1);}");Shaders.glAttachShader(p,v);Shaders.glAttachShader(p,f);Shaders.glLinkProgram(p);check(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,"link");GL20.glDeleteShader(v);GL20.glDeleteShader(f);}
    static void draw(int p,int r,int g){Shaders.glUseProgram(p);Draws.glMultiDrawArraysIndirect(GL11.GL_TRIANGLES,0,3,0);ByteBuffer bytes=BufferUtils.createByteBuffer(4);GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);check((bytes.get(0)&255)==r&&(bytes.get(1)&255)==g,"BaseInstance/DrawID/order pixels: "+(bytes.get(0)&255)+","+(bytes.get(1)&255));}
    public static void main(String[] args)throws Exception{
        GLFWErrorCallback cb=GLFWErrorCallback.createPrint(System.err);cb.set();check(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);long w=Context.createWindow(32,32,"Batch state fixture",0,0);Context.makeCurrent(w);Context.capabilities();GL11.glViewport(0,0,32,32);
        int vao=GL30.glGenVertexArrays();Draws.glBindVertexArray(vao);int vertices=ResourceMutationTests.buffer(Float.floatToIntBits(-1),Float.floatToIntBits(-1),Float.floatToIntBits(3),Float.floatToIntBits(-1),Float.floatToIntBits(-1),Float.floatToIntBits(3));Draws.glVertexAttribBinding(0,0);Draws.glVertexAttribFormat(0,2,GL11.GL_FLOAT,false,0);Draws.glBindVertexBuffer(0,vertices,0,8);GL20.glEnableVertexAttribArray(0);
        int instance=ResourceMutationTests.buffer(0,0,0,0,0,0,0,0);Draws.glVertexAttribBinding(1,1);Draws.glVertexAttribFormat(1,2,GL11.GL_FLOAT,false,0);Draws.glBindVertexBuffer(1,instance,0,8);Draws.glVertexBindingDivisor(1,1);GL20.glEnableVertexAttribArray(1);
        int commands=ResourceMutationTests.buffer(3,1,0,3, 0,0,0,99, 3,1,0,3);GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,commands);int p=make(true);
        try{
            draw(p,3,2);long calls=n(LOCATION_CALLS),pointers=n(POINTER_UPDATES);draw(p,3,2);
            if(Options.LOCATIONS)check(n(LOCATION_CALLS)==calls,"warmed locations including reset");if(Options.BATCH)check(n(POINTER_UPDATES)-pointers<=2,"repeated base avoids pointers");
            check(GL20.glGetVertexAttribPointer(1,GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER)==0,"base pointers restored");check(GL20.glGetVertexAttribi(1,GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR)==1,"divisor preserved");
            int missing=make(false);draw(missing,8,9);calls=n(LOCATION_CALLS);draw(missing,8,9);if(Options.LOCATIONS)check(n(LOCATION_CALLS)==calls,"minus-one locations cached");
            IntBuffer attached=BufferUtils.createIntBuffer(8),count=BufferUtils.createIntBuffer(1);GL20.glGetAttachedShaders(p,count,attached);for(int i=0;i<count.get(0);i++)GL20.glDetachShader(p,attached.get(i));attach(p,false);calls=n(LOCATION_CALLS);draw(p,8,9);if(Counters.ENABLED)check(n(LOCATION_CALLS)>calls,"relink invalidates locations");
            Shaders.glDeleteProgram(p);p=make(true);draw(p,3,2);
            // Legacy pointer mutation is reflected in the next batch's binding model.
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,instance);Draws.glVertexAttribPointer(1,2,GL11.GL_FLOAT,false,8,0L);Draws.glVertexAttribDivisor(1,1);draw(p,3,2);check(GL20.glGetVertexAttribPointer(1,GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER)==0,"external pointer restored");
            Draws.divisorsUnknown();draw(p,3,2);Shaders.glDeleteProgram(missing);
            layoutChecks();
            lifecycle(w);
            if(args.length>0&&args[0].equals("--batch-bench"))bench();
            check(GL11.glGetError()==0,"No driver errors");System.out.println("BATCH STATE PASS BaseInstance/DrawID/inactive rows/negative locations/relink/delete/new program/pointer-divisor restoration");
        }finally{Shaders.glDeleteProgram(p);Buffers.glDeleteBuffers(new int[]{vertices,instance,commands});Draws.glBindVertexArray(0);Draws.glDeleteVertexArrays(vao);Context.destroyWindow(w);GLFW.glfwTerminate();cb.free();}
    }
    static void layoutChecks(){
        int savedVao=Draws.vertexArrayBinding(),v=GL30.glGenVertexArrays();Draws.glBindVertexArray(v);
        int floats=ResourceMutationTests.buffer(Float.floatToIntBits(1),Float.floatToIntBits(1),Float.floatToIntBits(1),Float.floatToIntBits(1),Float.floatToIntBits(1),Float.floatToIntBits(1),Float.floatToIntBits(1),Float.floatToIntBits(1));
        int ints=ResourceMutationTests.buffer(37,37,37,37,37,37,37,37);
        Draws.glVertexAttribBinding(6,1);Draws.glVertexAttribFormat(6,1,GL11.GL_FLOAT,false,0);Draws.glBindVertexBuffer(1,floats,0,4);Draws.glVertexBindingDivisor(1,1);Draws.glEnableVertexAttribArray(6);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,ints);Draws.glVertexAttribIPointer(7,1,GL11.GL_INT,0,0L);Draws.glVertexAttribDivisor(7,2);Draws.glEnableVertexAttribArray(7);
        int p=Shaders.glCreateProgram(),vs=Shaders.compileRaw(GL20.GL_VERTEX_SHADER,"#version 410 core\nlayout(location=6)in float x;layout(location=7)in int y;uniform int vmcompatBaseInstance;uniform int vmcompatDrawId;flat out vec2 value;void main(){int id=gl_VertexID%3;vec2 q=vec2((id<<1)&2,id&2);gl_Position=vec4(q*2-1,0,1);value=vec2(float(y)+x+vmcompatBaseInstance,vmcompatDrawId+gl_VertexID);}"),fs=Shaders.compileRaw(GL20.GL_FRAGMENT_SHADER,"#version 410 core\nflat in vec2 value;out vec4 color;void main(){color=vec4(value/255.,0,1);}");Shaders.glAttachShader(p,vs);Shaders.glAttachShader(p,fs);Shaders.glLinkProgram(p);check(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,"mixed link");GL20.glDeleteShader(vs);GL20.glDeleteShader(fs);Shaders.glUseProgram(p);
        int elements=ResourceMutationTests.buffer(0,1,2);GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,elements);int command=ResourceMutationTests.buffer(3,2,0,3,3, 0,0,0,0,0, 3,2,0,3,3);GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,command);
        for(int round=0;round<3;round++){
            Draws.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES,GL11.GL_UNSIGNED_INT,0,3,0);ByteBuffer pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);check((pixel.get(0)&255)==41&&(pixel.get(1)&255)==7,"mixed integer/float, stride0, divisor2, indexed baseVertex/DrawID");
            check(GL20.glGetVertexAttribPointer(7,GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER)==0,"raw pointer restored");check(GL20.glGetVertexAttribi(7,GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR)==2,"divisor2 restored");
            Draws.glDisableVertexAttribArray(6);Draws.glEnableVertexAttribArray(6);Draws.glVertexAttribFormat(6,1,GL11.GL_FLOAT,false,0);Draws.glBindVertexBuffer(1,floats,0,4);
        }
        Shaders.glDeleteProgram(p);Buffers.glDeleteBuffers(new int[]{floats,ints,elements,command});Draws.glBindVertexArray(savedVao);Draws.glDeleteVertexArrays(v);
    }
    static void lifecycle(long main)throws Exception{
        GLCapabilities caps=GL.getCapabilities();var original=ContextResources.current();Context.makeCurrent(0);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread other=new Thread(()->{try{Context.makeCurrent(main);GL.setCapabilities(caps);check(ContextResources.current()==original,"same lifetime after transfer");check(ContextResources.current()==original,"thread cache identity");Context.makeCurrent(0);GL.setCapabilities(null);}catch(Throwable e){failure.set(e);}},"GL context transfer fixture");other.start();other.join();if(failure.get()!=null)throw new AssertionError(failure.get());
        Context.makeCurrent(main);GL.setCapabilities(caps);check(ContextResources.current()==original,"transfer back");
        long independent=Context.createWindow(16,16,"Context cache lifetime",0,0);Context.makeCurrent(independent);Context.capabilities();var different=ContextResources.current();check(different!=original,"independent identity");Context.makeCurrent(main);GL.setCapabilities(caps);Context.destroyWindow(independent);check(ContextResources.current()==original,"destroy on other current context");
        long replacement=Context.createWindow(16,16,"Replacement context",0,0);Context.makeCurrent(replacement);Context.capabilities();check(ContextResources.current().generation()!=different.generation(),"replacement generation");Context.makeCurrent(main);GL.setCapabilities(caps);Context.destroyWindow(replacement);
        // Explicit raw lifecycle boundary must remain authoritative until a managed makeCurrent.
        ContextResources.externalLifecycle();check(ContextResources.current()==original,"foreign lifecycle fallback");Context.makeCurrent(main);GL.setCapabilities(caps);
    }
    static void bench()throws Exception{
        boolean real=Boolean.parseBoolean(System.getProperty("vmcompat.realShader","false"));
        int vao=GL30.glGenVertexArrays();Draws.glBindVertexArray(vao);
        int instance=Buffers.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,instance);FloatBuffer bytes=BufferUtils.createFloatBuffer(8*12);for(int i=0;i<8;i++){bytes.put(i*12,1).put(i*12+4,2).put(i*12+8,3);}Buffers.glBufferData(GL15.GL_ARRAY_BUFFER,bytes,GL15.GL_DYNAMIC_DRAW);
        for(int i=0;i<3;i++){Draws.glVertexAttribBinding(6+i,1);Draws.glVertexAttribFormat(6+i,4,GL11.GL_FLOAT,false,i*16);Draws.glEnableVertexAttribArray(6+i);}Draws.glBindVertexBuffer(1,instance,0,48);Draws.glVertexBindingDivisor(1,1);
        int p=Shaders.glCreateProgram();int v=Shaders.compileRaw(GL20.GL_VERTEX_SHADER,"#version 410 core\nlayout(location=6)in vec4 a;layout(location=7)in vec4 b;layout(location=8)in vec4 c;uniform int vmcompatBaseInstance;uniform int vmcompatDrawId;uniform float turn;flat out vec2 value;void main(){vec2 q=vec2((gl_VertexID<<1)&2,gl_VertexID&2)*2-1;gl_Position=vec4(mat2(cos(turn),sin(turn),-sin(turn),cos(turn))*q,0,1);value=vec2(a.x+b.x+c.x+vmcompatBaseInstance,vmcompatDrawId);}");int f=Shaders.compileRaw(GL20.GL_FRAGMENT_SHADER,"#version 410 core\nflat in vec2 value;out vec4 color;void main(){color=vec4(value/255.,0,1);}");Shaders.glAttachShader(p,v);Shaders.glAttachShader(p,f);Shaders.glLinkProgram(p);check(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,"benchmark link");GL20.glDeleteShader(v);GL20.glDeleteShader(f);Shaders.glUseProgram(p);
        IntBuffer commands=BufferUtils.createIntBuffer(8*4);boolean single=Boolean.parseBoolean(System.getProperty("vmcompat.onlySingle","false"));for(int i=0;i<8;i++)commands.put(new int[]{i==4?0:3,single?1:1+(i&1),0,i%6});commands.flip();int indirect=Buffers.glGenBuffers();GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,indirect);Buffers.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER,commands,GL15.GL_DYNAMIC_DRAW);
        int positions=0,white=0;
        if(real){Shaders.glDeleteProgram(p);p=originalMesh();Shaders.glUseProgram(p);positions=ResourceMutationTests.buffer(Float.floatToIntBits(-1),Float.floatToIntBits(-1),0,Float.floatToIntBits(3),Float.floatToIntBits(-1),0,Float.floatToIntBits(-1),Float.floatToIntBits(3),0);Draws.glVertexAttribBinding(0,0);Draws.glVertexAttribFormat(0,3,GL11.GL_FLOAT,false,0);Draws.glBindVertexBuffer(0,positions,0,12);Draws.glEnableVertexAttribArray(0);
            GL20.glVertexAttrib2f(1,.5f,.5f);GL20.glVertexAttrib4f(2,0,0,1,1);GL20.glVertexAttrib3f(3,0,0,1);GL20.glVertexAttrib1f(4,0);GL30.glVertexAttribI1i(5,0);
            bytes.clear();for(int i=0;i<bytes.limit();i++)bytes.put(i,0);for(int i=0;i<8;i++)bytes.put(i*12+5,2);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,instance);Buffers.glBufferSubData(GL15.GL_ARRAY_BUFFER,0,bytes);
            white=Textures.glCreateTextures(GL11.GL_TEXTURE_2D);Textures.glTextureStorage2D(white,1,GL11.GL_RGBA8,1,1);Textures.glTextureParameteri(white,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);Textures.glTextureParameteri(white,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);ByteBuffer pixel=BufferUtils.createByteBuffer(4).putInt(-1).flip();Textures.glTextureSubImage2D(white,0,0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);Samplers.glActiveTexture(GL13.GL_TEXTURE0+80);Samplers.glBindTexture(GL11.GL_TEXTURE_2D,white);Samplers.glUniform1i(Shaders.glGetUniformLocation(p,"uTexture"),80);GL20.glUniform2f(Shaders.glGetUniformLocation(p,"uLightAtlasSize"),64,64);
        }
        int turn=Shaders.glGetUniformLocation(p,"turn"),matrix=Shaders.glGetUniformLocation(p,"uViewProjection");FloatBuffer rotation=BufferUtils.createFloatBuffer(16);Telemetry.start();Telemetry.frame(System.nanoTime(),System.currentTimeMillis(),0);double[] passes=new double[12];
        for(int block=0;block<30;block++){camera(turn,matrix,rotation,block);for(int i=0;i<400;i++)Draws.glMultiDrawArraysIndirect(GL11.GL_TRIANGLES,0,8,0);Telemetry.world(passes,2800,2800);Telemetry.frame(System.nanoTime(),System.currentTimeMillis(),0);}GL11.glFinish();long[] totals=new long[Counters.Key.values().length];long start=System.nanoTime();
        for(int block=0;block<200;block++){camera(turn,matrix,rotation,block);for(int i=0;i<400;i++)Draws.glMultiDrawArraysIndirect(GL11.GL_TRIANGLES,0,8,0);TextureCommandTests.sum(totals);Telemetry.world(passes,2800,2800);Telemetry.frame(System.nanoTime(),System.currentTimeMillis(),0);}GL11.glFinish();double ms=(System.nanoTime()-start)/1e6/200;
        ByteBuffer pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);check(real?(pixel.get(0)&255)==255&&(pixel.get(1)&255)==255:(pixel.get(0)&255)==7&&(pixel.get(1)&255)==7,"batch benchmark final pixels real="+real+" pixel="+(pixel.get(0)&255)+","+(pixel.get(1)&255));Telemetry.stopForTest();TextureCommandTests.writeResult(ms,totals,(real?"Original installed mesh.vert + gbuffer.frag normal/low; ":"Diagnostic shader; ")+"400 batches 8 rows (one empty), 3 vec4 attributes at stride 48, changing camera uniform",2800);
        Shaders.glDeleteProgram(p);if(positions!=0)Buffers.glDeleteBuffers(positions);if(white!=0)Textures.glDeleteTextures(white);Buffers.glDeleteBuffers(new int[]{instance,indirect});Draws.glBindVertexArray(0);Draws.glDeleteVertexArrays(vao);
    }

    static void camera(int turn,int matrix,FloatBuffer values,int block){float a=(block&1)*.02f;GL20.glUniform1f(turn,a);if(matrix>=0){for(int i=0;i<16;i++)values.put(i,0);values.put(0,(float)Math.cos(a)).put(1,(float)Math.sin(a)).put(4,-(float)Math.sin(a)).put(5,(float)Math.cos(a)).put(10,1).put(15,1);GL20.glUniformMatrix4fv(matrix,false,values);}}
    static int originalMesh()throws Exception{
        Class.forName("viewpoint.platform.ShaderPacks").getMethod("useBuiltIn",String.class,String.class).invoke(null,"normal","low");Class<?> loader=Class.forName("viewpoint.platform.Shaders");var load=loader.getDeclaredMethod("load",String.class);load.setAccessible(true);int p=Shaders.glCreateProgram();String[] names={"mesh.vert","gbuffer.frag"};
        for(int i=0;i<2;i++){Object src=load.invoke(null,names[i]);var text=src.getClass().getDeclaredField("text");text.setAccessible(true);int shader=GL20.glCreateShader(i==0?GL20.GL_VERTEX_SHADER:GL20.GL_FRAGMENT_SHADER);Shaders.vpSource(shader,(String)text.get(src));Shaders.glCompileShader(shader);check(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)!=0,"original mesh shader");Shaders.glAttachShader(p,shader);GL20.glDeleteShader(shader);}Shaders.glLinkProgram(p);check(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,"original mesh shader link");return p;
    }

}
