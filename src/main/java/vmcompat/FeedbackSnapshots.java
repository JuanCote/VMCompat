package vmcompat;
import java.util.*;import org.lwjgl.opengl.*;
/** Exact GPU snapshots for GL4.5 texture-barrier feedback, used by 0.1.7 snow.
 * The original textures remain the write targets; only overlapping sampler reads
 * use copies. No native timestamp/compute emulation and no CPU pixel transfers.
 */
public final class FeedbackSnapshots {
 static final long LIMIT=64L*1024*1024;
 static final class Copy {int texture;long generation,bytes;Textures.Image image;}
 static final class State {boolean pending;long bytes;final Map<Integer,Copy> copies=new LinkedHashMap<>(16,.75f,true);}
 static final StateStore<State> STATES=new StateStore<>(State::new);
 public static void barrier(){STATES.get().pending=true;}
 public static void prepare(int program){State s=STATES.get();if(!s.pending)return;s.pending=false;int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);Set<Integer> outputs=Textures.feedbackAttachments(fbo);if(outputs.isEmpty())return;Map<Integer,Copy> current=new HashMap<>();
  for(Samplers.Input input:Samplers.state().programs.getOrDefault(program,new Samplers.Input[0])){int source=Samplers.boundTexture(input.logical,input.target);if(source==0||!outputs.contains(source))continue;
   Copy c=current.get(source);if(c==null){Textures.Image image=Textures.description(source);if(image==null||!(image.target()==GL11.GL_TEXTURE_2D||image.target()==GL30.GL_TEXTURE_2D_ARRAY))throw new UnsupportedOperationException("Texture feedback format/target is not qualified: "+source);
    long generation=Buffers.textureLifetime(source);c=s.copies.computeIfAbsent(source,k->new Copy());if(c.texture!=0&&(c.generation!=generation||!image.equals(c.image))){Textures.glDeleteTextures(c.texture);s.bytes-=c.bytes;c.texture=0;}
    if(c.texture==0){long bytes=(long)image.width()*image.height()*image.depth()*16*2;if(s.bytes+bytes>LIMIT){for(var it=s.copies.entrySet().iterator();it.hasNext()&&s.bytes+bytes>LIMIT;){var entry=it.next();if(entry.getKey()==source||outputs.contains(entry.getKey()))continue;Copy old=entry.getValue();if(old.texture!=0)Textures.glDeleteTextures(old.texture);s.bytes-=old.bytes;it.remove();}}if(s.bytes+bytes>LIMIT)throw new IllegalStateException("Texture feedback staging exceeds 64 MiB");c.texture=Textures.glCreateTextures(image.target());if(image.target()==GL11.GL_TEXTURE_2D)Textures.glTextureStorage2D(c.texture,image.levels(),image.format(),image.width(),image.height());else Textures.glTextureStorage3D(c.texture,image.levels(),image.format(),image.width(),image.height(),image.depth());c.generation=generation;c.image=image;c.bytes=bytes;s.bytes+=bytes;}
    final Copy copy=c;Textures.bound(source,()->{for(int name:new int[]{GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_TEXTURE_WRAP_S,GL11.GL_TEXTURE_WRAP_T,GL12.GL_TEXTURE_WRAP_R,GL12.GL_TEXTURE_BASE_LEVEL,GL12.GL_TEXTURE_MAX_LEVEL,GL14.GL_TEXTURE_COMPARE_MODE,GL14.GL_TEXTURE_COMPARE_FUNC,GL33.GL_TEXTURE_SWIZZLE_R,GL33.GL_TEXTURE_SWIZZLE_G,GL33.GL_TEXTURE_SWIZZLE_B,GL33.GL_TEXTURE_SWIZZLE_A}){int value=GL11.glGetTexParameteri(image.target(),name);Textures.glTextureParameteri(copy.texture,name,value);}});
    // Storage matches the original complete image, including all mip levels and layers.
    for(int level=0;level<image.levels();level++)Textures.glCopyImageSubData(source,image.target(),level,0,0,0,c.texture,image.target(),level,0,0,0,Math.max(1,image.width()>>level),Math.max(1,image.height()>>level),image.depth());current.put(source,c);
   }
   Samplers.restoreInput(program,input.location,c.texture);
  }
 }
 public static void closeCurrent(){State s=STATES.get();for(Copy c:s.copies.values())if(c.texture!=0)Textures.glDeleteTextures(c.texture);s.copies.clear();s.bytes=0;s.pending=false;}
 private FeedbackSnapshots(){}
}
