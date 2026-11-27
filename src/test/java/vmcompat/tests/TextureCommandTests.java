package vmcompat.tests;
import vmcompat.*;
import java.nio.*;
import java.util.*;
import java.nio.file.*;
import java.lang.reflect.*;
import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import static vmcompat.Counters.Key.*;
/** Original FrameStream plus real GL pixels: small commands in an 8 MiB buffer. */
public final class TextureCommandTests {
    static void check(boolean b,String s){ResourceMutationTests.check(b,s);}
    static long n(Counters.Key k){return ResourceMutationTests.n(k);}
    static void bind(int tex,int source,int at,int bytes){Samplers.glActiveTexture(GL13.GL_TEXTURE0+80);Samplers.glBindTexture(GL31.GL_TEXTURE_BUFFER,tex);Buffers.glTexBufferRange(GL31.GL_TEXTURE_BUFFER,GL30.GL_R32I,source,at,bytes);}
    public static void main(String[] args)throws Exception{
        GLFWErrorCallback cb=GLFWErrorCallback.createPrint(System.err);cb.set();check(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);long w=Context.createWindow(32,32,"Texture buffer and command regions",0,0);Context.makeCurrent(w);GLCapabilities caps=Context.capabilities();GL11.glViewport(0,0,32,32);
        int p=ResourceMutationTests.program("i"),src=ResourceMutationTests.buffer(11,12,13,14),tex=ResourceMutationTests.range(src,0,4);
        try{
            long calls=n(TEX_BUFFER_BINDS),skips=n(TEX_BUFFER_BIND_SKIPS);bind(tex,src,0,4);ResourceMutationTests.draw(p,tex,11);if(Options.TEX_BUFFER_BINDINGS){check(n(TEX_BUFFER_BINDS)==calls,"repeat association skipped");check(n(TEX_BUFFER_BIND_SKIPS)>skips,"skip counter");}
            ResourceMutationTests.write(src,0,21);bind(tex,src,0,4);ResourceMutationTests.draw(p,tex,21);if(Options.TEX_BUFFER_BINDINGS)check(n(TEX_BUFFER_BINDS)==calls,"content update does not rebind");
            bind(tex,src,4,4);ResourceMutationTests.draw(p,tex,12);int other=ResourceMutationTests.buffer(31,32,33,34);bind(tex,other,0,8);ResourceMutationTests.draw(p,tex,31);int sizedTexture=tex;Textures.bound(sizedTexture,()->check(GL11.glGetTexLevelParameteri(GL31.GL_TEXTURE_BUFFER,0,GL11.GL_TEXTURE_WIDTH)==2,"backing resize updates texture size"));
            int up=ResourceMutationTests.program("u");Samplers.glBindTexture(GL31.GL_TEXTURE_BUFFER,tex);calls=n(TEX_BUFFER_BINDS);Buffers.glTexBufferRange(GL31.GL_TEXTURE_BUFFER,GL30.GL_R32UI,other,0,8);ResourceMutationTests.draw(up,tex,31);check(!Counters.ENABLED||n(TEX_BUFFER_BINDS)>calls,"format rebind");Shaders.glDeleteProgram(up);
            Buffers.glTexBuffer(GL31.GL_TEXTURE_BUFFER,GL30.GL_R32I,src);ResourceMutationTests.draw(p,tex,21);bind(tex,other,4,4);ResourceMutationTests.draw(p,tex,32);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER,GL30.GL_R32I,src);Buffers.textureBindingUnknown(tex);bind(tex,other,4,4);ResourceMutationTests.draw(p,tex,32);
            GL11.glFinish();long second=Context.createWindow(16,16,"Shared association",0,w);Context.makeCurrent(second);Context.capabilities();bind(tex,other,8,4);GL11.glFinish();Context.makeCurrent(w);GL.setCapabilities(caps);bind(tex,other,8,4);ResourceMutationTests.draw(p,tex,33);Context.destroyWindow(second);
            Textures.glDeleteTextures(tex);tex=ResourceMutationTests.range(src,0,4);ResourceMutationTests.draw(p,tex,21);Buffers.glDeleteBuffers(src);ResourceMutationTests.draw(p,tex,21);int fresh=ResourceMutationTests.buffer(41,42);bind(tex,fresh,0,4);ResourceMutationTests.draw(p,tex,41);Buffers.glDeleteBuffers(fresh);Buffers.glDeleteBuffers(other);
            if(args.length>0&&args[0].equals("--range-bench"))rangeBench();
            else frameStream(args.length>0&&args[0].equals("--bench"));
            check(GL11.glGetError()==0,"fixture driver errors");System.out.println("TEXTURE COMMAND PASS associations/content/format/offset/size/source/alternate/delete/shared contexts; actual Viewpoint FrameStream and partial command updates");
        }finally{Shaders.glDeleteProgram(p);Textures.glDeleteTextures(tex);Context.destroyWindow(w);GLFW.glfwTerminate();cb.free();}
    }
    static void rangeBench()throws Exception{
        Class<?> type=Class.forName("viewpoint.render.FrameStream");Method put=method(type,"put",IntBuffer.class,int.class),get=method(type,"buffer"),startedFrame=method(type,"frameStarted");
        IntBuffer values=BufferUtils.createIntBuffer(2304);for(int i=0;i<values.limit();i++)values.put(i,37);
        int[] textures=new int[6];long[] offsets=new long[6];int shader=ResourceMutationTests.program("i");
        startedFrame.invoke(null);for(int i=0;i<6;i++){offsets[i]=(long)put.invoke(null,values,256);textures[i]=ResourceMutationTests.range((int)get.invoke(null),(int)offsets[i],9216);}
        Shaders.glUseProgram(shader);Samplers.glUniform1i(Shaders.glGetUniformLocation(shader,"pixels"),80);
        boolean dynamic=Boolean.parseBoolean(System.getProperty("vmcompat.dynamic","true"));
        Telemetry.start();Telemetry.frame(System.nanoTime(),System.currentTimeMillis(),0);double[] passes=new double[12];
        for(int block=0;block<30;block++){rangeBlock(put,get,startedFrame,values,textures,offsets,dynamic,block);Telemetry.world(passes,12,12);Telemetry.frame(System.nanoTime(),System.currentTimeMillis(),0);}GL11.glFinish();
        long[] totals=new long[Counters.Key.values().length];long at=System.nanoTime();
        for(int block=0;block<200;block++){rangeBlock(put,get,startedFrame,values,textures,offsets,dynamic,block);sum(totals);Telemetry.world(passes,12,12);Telemetry.frame(System.nanoTime(),System.currentTimeMillis(),0);}GL11.glFinish();double ms=(System.nanoTime()-at)/1e6/200;
        ByteBuffer pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);check((pixel.get(0)&255)==(dynamic?38:37),"range benchmark final pixels");check(Buffers.mirrorBytesForTest()<=16*1024*1024,"ranges compete within shared bounded cache");Telemetry.stopForTest();
        writeResult(ms,totals,"6 real FrameStream 9216-byte range updates, each consumed twice",12);
        for(int texture:textures)Textures.glDeleteTextures(texture);Shaders.glDeleteProgram(shader);
    }
    static void rangeBlock(Method put,Method get,Method startedFrame,IntBuffer values,int[] textures,long[] offsets,boolean dynamic,int block)throws Exception{
        startedFrame.invoke(null);if(dynamic)values.put(0,37+(block&1));
        for(int i=0;i<textures.length;i++){if(dynamic)offsets[i]=(long)put.invoke(null,values,256);bind(textures[i],(int)get.invoke(null),(int)offsets[i],9216);Draws.glDrawArrays(GL11.GL_TRIANGLES,0,3);Draws.glDrawArrays(GL11.GL_TRIANGLES,0,3);}
    }
    static void sum(long[] totals){long[] now=Counters.snapshot();for(Counters.Key k:Counters.Key.values()){int i=k.ordinal();if(k==COMMAND_CACHE_PEAK_BYTES)totals[i]=Math.max(totals[i],now[i]);else if(k==COMMAND_CACHE_BYTES)totals[i]=now[i];else totals[i]+=now[i];}}
    static void writeResult(double ms,long[] totals,String scope,int draws)throws Exception{StringJoiner c=new StringJoiner(",");for(Counters.Key key:Counters.Key.values())c.add("\""+key.name()+"\":"+totals[key.ordinal()]);Files.writeString(Path.of(System.getProperty("vmcompat.result")),"{\"completed_block_ms\":"+ms+",\"blocks\":200,\"draws_per_block\":"+draws+",\"scope\":\""+scope+"\",\"telemetry\":\""+Options.TELEMETRY+"\",\"counters\":{"+c+"},\"game_launched\":false}\n");}
    static Method method(Class<?> type,String name,Class<?>... args)throws Exception{Method m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    static void frameStream(boolean measure)throws Exception{
        Class<?> type=Class.forName("viewpoint.render.FrameStream");method(type,"frameStarted").invoke(null);Method put=method(type,"put",IntBuffer.class,int.class),get=method(type,"buffer");
        IntBuffer words=BufferUtils.createIntBuffer(4).put(new int[]{3,1,0,0}).flip();long offset=(long)put.invoke(null,words,4);int buffer=(int)get.invoke(null);GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,buffer);long size=GL32.glGetBufferParameteri64(GL40.GL_DRAW_INDIRECT_BUFFER,GL15.GL_BUFFER_SIZE);check(size>=8*1024*1024,"real FrameStream size");
        long hits=n(MIRROR_HITS);ByteBuffer first=Buffers.commands(GL40.GL_DRAW_INDIRECT_BUFFER,offset,16);check(first.getInt(0)==3&&words.position()==0,"original upload retains values/position");if(Counters.ENABLED&&Options.MIRROR&&Options.COMMAND_RANGES)check(n(MIRROR_HITS)==hits+1,"large FrameStream commands hit sparse CPU ownership");
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,buffer);Buffers.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER,offset+12,BufferUtils.createIntBuffer(1).put(0,7));ByteBuffer updated=Buffers.commands(GL40.GL_DRAW_INDIRECT_BUFFER,offset,16);check(updated.getInt(0)==3&&updated.getInt(12)==7,"partial write preserves remaining command bytes");
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);ByteBuffer mapped=Buffers.glMapBufferRange(GL15.GL_ARRAY_BUFFER,offset,16,GL30.GL_MAP_WRITE_BIT);mapped.order(ByteOrder.nativeOrder()).putInt(0,6);check(Buffers.glUnmapBuffer(GL15.GL_ARRAY_BUFFER),"large source small map");check(Buffers.commands(GL40.GL_DRAW_INDIRECT_BUFFER,offset,16).getInt(0)==6,"mapped sparse bytes");
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);mapped=Buffers.glMapBufferRange(GL15.GL_ARRAY_BUFFER,offset,4,GL30.GL_MAP_WRITE_BIT|GL30.GL_MAP_FLUSH_EXPLICIT_BIT);mapped.order(ByteOrder.nativeOrder()).putInt(0,3);Buffers.glFlushMappedBufferRange(GL15.GL_ARRAY_BUFFER,0,4);check(Buffers.glUnmapBuffer(GL15.GL_ARRAY_BUFFER),"sparse explicit flush");check(Buffers.commands(GL40.GL_DRAW_INDIRECT_BUFFER,offset,16).getInt(12)==7,"flush preserves other command bytes");
        // Fresh CPU commands in the same large source still produce the expected image.
        Buffers.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER,offset+12,BufferUtils.createIntBuffer(1).put(0,0));int shader=ResourceMutationTests.program("i");int source=ResourceMutationTests.buffer(51);int texture=ResourceMutationTests.range(source,0,4);Shaders.glUseProgram(shader);Samplers.glActiveTexture(GL13.GL_TEXTURE0+80);Samplers.glBindTexture(GL31.GL_TEXTURE_BUFFER,texture);Samplers.glUniform1i(Shaders.glGetUniformLocation(shader,"pixels"),80);Draws.glMultiDrawArraysIndirect(GL11.GL_TRIANGLES,offset,1,0);ByteBuffer pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);check((pixel.get(0)&255)==51,"actual FrameStream indirect draw pixels");if(measure)bench(put,get,shader,texture,source);Shaders.glDeleteProgram(shader);Textures.glDeleteTextures(texture);Buffers.glDeleteBuffers(source);
        long currentOffset=(long)put.invoke(null,BufferUtils.createIntBuffer(4).put(new int[]{3,1,0,0}).flip(),4);
        GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,buffer);Buffers.commands(GL40.GL_DRAW_INDIRECT_BUFFER,currentOffset,16);
        ByteBuffer broad=BufferUtils.createByteBuffer(2*1024*1024).order(ByteOrder.nativeOrder());broad.putInt((int)currentOffset,6).putInt((int)currentOffset+4,1);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,buffer);Buffers.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER,0,broad);
        check(Buffers.commands(GL40.GL_DRAW_INDIRECT_BUFFER,currentOffset,16).getInt(0)==6,"large overlapping CPU update patches retained command bytes");
        // GPU/unknown ownership remains on the real readback path.
        Buffers.unknownWrite(buffer);hits=n(MIRROR_HITS);Buffers.commands(GL40.GL_DRAW_INDIRECT_BUFFER,offset,16);Buffers.commands(GL40.GL_DRAW_INDIRECT_BUFFER,offset,16);check(n(MIRROR_HITS)==hits,"unknown writer is never legitimized by readback");
        Path out=Path.of(System.getProperty("vmcompat.root"),"command-misses-test.json");Class<?> diag=Class.forName("vmcompat.CommandMisses");method(diag,"write",Path.class).invoke(null,out);String text=Files.readString(out);check(text.contains("external_unknown")&&text.contains("last_length"),"bounded refusal diagnostics");
        System.out.println("FrameStream observed bytes="+size+" command_bytes=16 new_cache="+Options.COMMAND_RANGES+" mirror="+Options.MIRROR);
        GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,0);
    }
    static void bench(Method put,Method get,int shader,int texture,int source)throws Exception{
        IntBuffer words=BufferUtils.createIntBuffer(4).put(new int[]{3,1,0,0}).flip();int stream=(int)get.invoke(null);GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,stream);
        Shaders.glUseProgram(shader);Samplers.glActiveTexture(GL13.GL_TEXTURE0+80);Samplers.glBindTexture(GL31.GL_TEXTURE_BUFFER,texture);Samplers.glUniform1i(Shaders.glGetUniformLocation(shader,"pixels"),80);
        double[] passes=new double[13];Telemetry.start();Telemetry.frame(System.nanoTime(),System.currentTimeMillis(),0);
        for(int block=0;block<30;block++){for(int i=0;i<100;i++){long at=(long)put.invoke(null,words,4);Buffers.glTexBufferRange(GL31.GL_TEXTURE_BUFFER,GL30.GL_R32I,source,0,4);Draws.glMultiDrawArraysIndirect(GL11.GL_TRIANGLES,at,1,0);}Telemetry.world(passes,100,100);Telemetry.frame(System.nanoTime(),System.currentTimeMillis(),0);}GL11.glFinish();
        long[] totals=new long[Counters.Key.values().length];long started=System.nanoTime();for(int block=0;block<200;block++){for(int i=0;i<100;i++){long at=(long)put.invoke(null,words,4);Buffers.glTexBufferRange(GL31.GL_TEXTURE_BUFFER,GL30.GL_R32I,source,0,4);Draws.glMultiDrawArraysIndirect(GL11.GL_TRIANGLES,at,1,0);}long[] values=Counters.snapshot();for(int i=0;i<values.length;i++)if(i==COMMAND_CACHE_PEAK_BYTES.ordinal())totals[i]=Math.max(totals[i],values[i]);else if(i==COMMAND_CACHE_BYTES.ordinal())totals[i]=values[i];else totals[i]+=values[i];Telemetry.world(passes,100,100);Telemetry.frame(System.nanoTime(),System.currentTimeMillis(),0);}GL11.glFinish();double elapsed=(System.nanoTime()-started)/1e6;Telemetry.stopForTest();
        StringJoiner c=new StringJoiner(",");for(Counters.Key key:Counters.Key.values())c.add("\""+key.name()+"\":"+totals[key.ordinal()]);Path out=Path.of(System.getProperty("vmcompat.result"));Files.writeString(out,"{\"completed_100_call_block_ms\":"+(elapsed/200)+",\"blocks\":200,\"counters\":{"+c+"},\"game_launched\":false}\n");
    }

}
