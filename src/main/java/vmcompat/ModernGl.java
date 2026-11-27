package vmcompat;
import java.nio.*;import org.lwjgl.*;import org.lwjgl.opengl.*;
/** DSA operations newly used by Viewpoint 0.1.7, lowered to existing tracked 4.1 adapters. */
public final class ModernGl {
 public static void glNamedBufferStorage(int buffer,float[] data,int flags){FloatBuffer b=BufferUtils.createFloatBuffer(data.length);b.put(data).flip();Gl41.glNamedBufferStorage(buffer,b,flags);}
 public static void glNamedBufferStorage(int buffer,int[] data,int flags){IntBuffer b=BufferUtils.createIntBuffer(data.length);b.put(data).flip();Gl41.glNamedBufferStorage(buffer,b,flags);}
 public static void glNamedBufferSubData(int buffer,long offset,float[] data){FloatBuffer b=BufferUtils.createFloatBuffer(data.length);b.put(data).flip();Gl41.glNamedBufferSubData(buffer,offset,b);}
 public static void glNamedBufferSubData(int buffer,long offset,int[] data){IntBuffer b=BufferUtils.createIntBuffer(data.length);b.put(data).flip();Gl41.glNamedBufferSubData(buffer,offset,b);}
 public static void glClearNamedBufferData(int buffer,int internal,int format,int type,ByteBuffer value){int saved=GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,buffer);try{Gl41.glClearBufferData(GL31.GL_COPY_WRITE_BUFFER,internal,format,type,value);}finally{GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,saved);}}
 public static void glBindTextureUnit(int unit,int texture){int old=Samplers.activeUnit();try{Samplers.glActiveTexture(GL13.GL_TEXTURE0+unit);if(texture==0){for(int target:new int[]{3553,32879,34067,35866,35882,3552})Samplers.glBindTexture(target,0);}else Samplers.glBindTexture(Textures.target(texture),texture);}finally{Samplers.glActiveTexture(GL13.GL_TEXTURE0+old);}}
 public static void glNamedFramebufferTexture(int framebuffer,int attachment,int texture,int level){int saved=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer);try{GL32.glFramebufferTexture(GL30.GL_DRAW_FRAMEBUFFER,attachment,texture,level);Textures.attachedLayer(framebuffer,attachment,texture,level,-1);if(attachment==GL30.GL_DEPTH_ATTACHMENT||attachment==GL30.GL_DEPTH_STENCIL_ATTACHMENT)Textures.attachedDepth(framebuffer,texture);}finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,saved);}}
 public static int glCreateVertexArrays(){return GL30.glGenVertexArrays();}
 static void vao(int vao,Runnable action){int saved=Draws.logicalVao();Draws.glBindVertexArray(vao);try{action.run();}finally{Draws.glBindVertexArray(saved);}}
 public static void glEnableVertexArrayAttrib(int vao,int index){vao(vao,()->Draws.glEnableVertexAttribArray(index));}
 public static void glVertexArrayAttribFormat(int vao,int index,int size,int type,boolean normalized,int offset){vao(vao,()->Draws.glVertexAttribFormat(index,size,type,normalized,offset));}
 public static void glVertexArrayAttribBinding(int vao,int index,int binding){vao(vao,()->Draws.glVertexAttribBinding(index,binding));}
 public static void glVertexArrayVertexBuffer(int vao,int binding,int buffer,long offset,int stride){vao(vao,()->Draws.glBindVertexBuffer(binding,buffer,offset,stride));}
 public static void glTextureSubImage3D(int texture,int level,int x,int y,int z,int w,int h,int d,int format,int type,ShortBuffer data){Textures.bound(texture,()->GL12.glTexSubImage3D(Textures.target(texture),level,x,y,z,w,h,d,format,type,data));Textures.pixelsChanged(texture);}
 public static void glTextureBarrier(){FeedbackSnapshots.barrier();}
 public static void glDispatchComputeIndirect(long offset){throw new UnsupportedOperationException("Compute shader packs require OpenGL 4.3 and are unavailable on macOS OpenGL 4.1");}
 private ModernGl(){}
}
