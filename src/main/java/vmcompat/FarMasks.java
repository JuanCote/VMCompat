package vmcompat;
import java.nio.*;import java.util.*;import org.lwjgl.opengl.*;
/** Only replaces pixel transfer, leaving the owning uploadMask's GL-state transitions intact. */
public final class FarMasks {
 static final StateStore<WeakHashMap<Object,Map<Integer,Entry>>> CACHE=new StateStore<>(WeakHashMap::new);
 record Entry(long generation,long version,int w,int h,int format,byte[] data){}
 public static void upload(int target,int level,int x,int y,int w,int h,int format,int type,ByteBuffer pixels,Object owner){long at=System.nanoTime();try{
  var group=Options.FAR_MASK?ContextResources.group():null;long generation=-1,version=0;int texture=0,n=0;Map<Integer,Entry> entries=null;boolean supported=Options.FAR_MASK&&ContextResources.opaqueExclusive()&&group.trusted&&group.contexts==1&&target==GL11.GL_TEXTURE_2D&&level==0&&x==0&&y==0&&type==GL11.GL_UNSIGNED_BYTE&&((format==GL11.GL_RGB&&w==53&&h==53)||(format==GL11.GL_RED&&w>0&&h>0&&w<=19&&h<=19))&&GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)==0&&GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT)==1&&GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH)==0&&GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS)==0&&GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS)==0;
  if(supported){texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);generation=Buffers.textureLifetime(texture);version=Textures.version(texture);var image=Textures.description(texture);supported=generation>0&&image!=null&&image.format()==(format==GL11.GL_RGB?GL11.GL_RGB8:GL30.GL_R8)&&image.width()>=w&&image.height()>=h;}
  if(supported){n=w*h*(format==GL11.GL_RGB?3:1);supported=pixels!=null&&pixels.remaining()>=n;if(supported){var owners=CACHE.get();entries=owners.get(owner);if(entries==null&&owners.size()<64){entries=new HashMap<>();owners.put(owner,entries);}if(entries!=null){Entry e=entries.get(texture);boolean same=e!=null&&e.generation==generation&&e.version==version&&e.w==w&&e.h==h&&e.format==format&&e.data.length==n;if(same)for(int i=0;i<n;i++)if(e.data[i]!=pixels.get(pixels.position()+i)){same=false;break;}if(same){FarStats.add(FarStats.K.MASK_SKIPS,1);return;}}}}
  Textures.glTexSubImage2D(target,level,x,y,w,h,format,type,pixels);FarStats.add(FarStats.K.MASK_UPLOADS,1);FarStats.add(FarStats.K.MASK_BYTES,(long)w*h*(format==GL11.GL_RGB?3:1));
  if(supported&&entries!=null){byte[] bytes=new byte[n];pixels.duplicate().get(bytes);entries.entrySet().removeIf(e->e.getValue().format==format); // two formats, at most two masks
   entries.put(texture,new Entry(generation,Textures.version(texture),w,h,format,bytes));}
 }finally{FarStats.add(FarStats.K.MASK_NS,System.nanoTime()-at);}}
}
