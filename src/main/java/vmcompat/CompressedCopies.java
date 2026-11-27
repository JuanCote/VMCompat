package vmcompat;
import java.nio.*;import java.util.*;import org.lwjgl.system.MemoryUtil;import java.util.concurrent.atomic.AtomicLong;import org.lwjgl.opengl.*;
/** Exact S3TC block copies through a bounded raw-block cache; no RGBA expansion. */
public final class CompressedCopies {
 static final long SOURCE_LIMIT=64L*1024*1024;static final int STRIP_LIMIT=4*1024*1024;
 private record Image(int texture,int target,int level,int face,int format,int width,int height,int layers,int bytes,long generation,long version){}
 private static final class State{ByteBuffer source,strip;long sourceCapacity,stripCapacity,reserved;Image cached;}
 private static final java.util.concurrent.ConcurrentHashMap<ContextResources.Identity,State> OWNERS=new java.util.concurrent.ConcurrentHashMap<>();
 private static final StateStore<State> STATES=new StateStore<>(State::new);
 private static final AtomicLong IMPORTS=new AtomicLong(),IMPORT_BYTES=new AtomicLong(),COPIES=new AtomicLong(),COPY_BYTES=new AtomicLong(),CACHE_BYTES=new AtomicLong();
 static long[] diagnostics(){return new long[]{IMPORTS.get(),IMPORT_BYTES.get(),COPIES.get(),COPY_BYTES.get(),CACHE_BYTES.get()};}
 private static int blockBytes(int format){return switch(format){case 0x83F0,0x83F1,0x8C4C,0x8C4D->8;case 0x83F2,0x83F3,0x8C4E,0x8C4F->16;default->0;};}
 private static boolean maybeCompressed(Textures.Image image){if(image==null)return true;int f=image.format();return blockBytes(f)>0||f==GL13.GL_COMPRESSED_RGB||f==GL13.GL_COMPRESSED_RGBA||f==GL21.GL_COMPRESSED_SRGB||f==GL21.GL_COMPRESSED_SRGB_ALPHA;}
 private static int queryTarget(int target,int face){return target==GL13.GL_TEXTURE_CUBE_MAP?GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+face:target;}
 private static Image image(int texture,int target,int level,int face){
  if(target!=GL11.GL_TEXTURE_2D&&target!=GL30.GL_TEXTURE_2D_ARRAY&&target!=GL13.GL_TEXTURE_CUBE_MAP)throw new UnsupportedOperationException("Compressed copy target "+target);
  if(level<0||face<0||(target==GL13.GL_TEXTURE_CUBE_MAP&&face>=6))throw new IllegalArgumentException("Compressed copy level/face");
  final int[] a=new int[6];Textures.target(texture,target);Textures.bound(texture,()->{int t=queryTarget(target,face);a[0]=GL11.glGetTexLevelParameteri(t,level,GL11.GL_TEXTURE_INTERNAL_FORMAT);a[1]=GL11.glGetTexLevelParameteri(t,level,GL11.GL_TEXTURE_WIDTH);a[2]=GL11.glGetTexLevelParameteri(t,level,GL11.GL_TEXTURE_HEIGHT);a[3]=target==GL30.GL_TEXTURE_2D_ARRAY?GL11.glGetTexLevelParameteri(t,level,GL12.GL_TEXTURE_DEPTH):target==GL13.GL_TEXTURE_CUBE_MAP?6:1;a[4]=GL11.glGetTexLevelParameteri(t,level,GL13.GL_TEXTURE_COMPRESSED);if(a[4]!=0)a[5]=GL11.glGetTexLevelParameteri(t,level,GL13.GL_TEXTURE_COMPRESSED_IMAGE_SIZE);});
  return new Image(texture,target,level,face,a[0],a[1],a[2],a[3],a[4]==0?0:a[5],Buffers.textureLifetime(texture),Textures.version(texture));
 }
 private static void bounds(Image a,int x,int y,int z,int width,int height,int depth){if(x<0||y<0||z<0||width<0||height<0||depth<0||x>a.width||width>a.width-x||y>a.height||height>a.height-y||z>a.layers||depth>a.layers-z||(x&3)!=0||(y&3)!=0||((width&3)!=0&&x+width!=a.width)||((height&3)!=0&&y+height!=a.height))throw new IllegalArgumentException("Compressed copy block alignment/bounds: texture="+a.texture+" size="+a.width+"x"+a.height+"x"+a.layers+" region="+x+","+y+","+z+" "+width+"x"+height+"x"+depth);}
 private static void capacity(State s,long bytes,int stripBytes){
  long sourceCapacity=Math.max(s.sourceCapacity,bytes),stripCapacity=Math.max(s.stripCapacity,stripBytes);if(sourceCapacity==s.sourceCapacity&&stripCapacity==s.stripCapacity)return;
  long added=sourceCapacity+stripCapacity;Packing.reserveAux(added);ByteBuffer source=null,strip=null;
  try{source=MemoryUtil.memAlloc(Math.toIntExact(sourceCapacity));strip=MemoryUtil.memAlloc(Math.toIntExact(stripCapacity));}catch(RuntimeException|Error e){if(source!=null)MemoryUtil.memFree(source);if(strip!=null)MemoryUtil.memFree(strip);Packing.releaseCpu(added);throw e;}
  if(s.source!=null)MemoryUtil.memFree(s.source);if(s.strip!=null)MemoryUtil.memFree(s.strip);if(s.reserved!=0)Packing.releaseCpu(s.reserved);CACHE_BYTES.addAndGet(added-s.reserved);s.source=source;s.strip=strip;s.sourceCapacity=sourceCapacity;s.stripCapacity=stripCapacity;s.reserved=added;s.cached=null;
 }
 public static boolean copy(int source,int sourceTarget,int sourceLevel,int sx,int sy,int sz,int destination,int destinationTarget,int destinationLevel,int dx,int dy,int dz,int width,int height,int depth){
  if(!maybeCompressed(Textures.description(source))&&!maybeCompressed(Textures.description(destination)))return false;
  Image src=image(source,sourceTarget,sourceLevel,sourceTarget==GL13.GL_TEXTURE_CUBE_MAP?sz:0),dst=image(destination,destinationTarget,destinationLevel,destinationTarget==GL13.GL_TEXTURE_CUBE_MAP?dz:0);
  if(src.bytes==0&&dst.bytes==0)return false;
  int block=blockBytes(src.format);if(src.bytes<=0||dst.bytes<=0||block==0||src.format!=dst.format)throw new UnsupportedOperationException("Unqualified compressed copy formats: "+src.format+" -> "+dst.format);
  bounds(src,sx,sy,sz,width,height,depth);bounds(dst,dx,dy,dz,width,height,depth);if(destinationTarget==GL13.GL_TEXTURE_CUBE_MAP)for(int i=1;i<depth;i++){Image face=image(destination,destinationTarget,destinationLevel,dz+i);if(face.format!=dst.format||face.width!=dst.width||face.height!=dst.height||face.bytes!=dst.bytes)throw new UnsupportedOperationException("Mismatched compressed destination cube face");}if(width==0||height==0||depth==0)return true;
  // Query and bound every source face before enqueuing writes. Arrays read their entire mip.
  Image[] sources=new Image[sourceTarget==GL13.GL_TEXTURE_CUBE_MAP?depth:1];long needed=0;for(int i=0;i<sources.length;i++){Image face=i==0?src:image(source,sourceTarget,sourceLevel,sz+i);if(face.format!=src.format||face.width!=src.width||face.height!=src.height||face.bytes<=0||face.bytes>SOURCE_LIMIT)throw new IllegalStateException("Compressed source exceeds qualified 64 MiB mip limit or face layout: texture="+source+" bytes="+face.bytes);sources[i]=face;needed=Math.max(needed,face.bytes);}
  long sourceStride=((long)src.width+3)/4*block,sourceLayer=sourceStride*((src.height+3L)/4);long expected=sourceLayer*(sourceTarget==GL30.GL_TEXTURE_2D_ARRAY?src.layers:1);if(expected!=src.bytes)throw new UnsupportedOperationException("Unexpected compressed mip layout: expected="+expected+" actual="+src.bytes);
  int rowBytes=Math.toIntExact(((long)width+3)/4*block);if(rowBytes>STRIP_LIMIT)throw new IllegalStateException("Compressed block row exceeds strip bound");int rows=(height+3)/4,stripRows=Math.max(1,Math.min(rows,STRIP_LIMIT/rowBytes)),stripBytes=stripRows*rowBytes;
  State state=STATES.get();OWNERS.put(ContextResources.current(),state);int oldPack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),oldUnpack=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
  int[] names={GL11.GL_UNPACK_ALIGNMENT,GL11.GL_UNPACK_ROW_LENGTH,GL11.GL_UNPACK_SKIP_PIXELS,GL11.GL_UNPACK_SKIP_ROWS,GL12.GL_UNPACK_IMAGE_HEIGHT,GL12.GL_UNPACK_SKIP_IMAGES},saved=new int[names.length];for(int i=0;i<names.length;i++){saved[i]=GL11.glGetInteger(names[i]);GL11.glPixelStorei(names[i],i==0?1:0);}
  try{
   capacity(state,needed,stripBytes);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
   for(int layer=0;layer<depth;layer++){
    Image input=sources[sourceTarget==GL13.GL_TEXTURE_CUBE_MAP?layer:0];boolean trusted=ContextResources.group().trusted&&ContextResources.group().contexts==1&&input.version!=0;
    if(!input.equals(state.cached)||!trusted){state.source.clear().limit(input.bytes);Textures.bound(source,()->GL13.glGetCompressedTexImage(queryTarget(sourceTarget,input.face),sourceLevel,state.source));state.cached=input;IMPORTS.incrementAndGet();IMPORT_BYTES.addAndGet(input.bytes);}
    long start=(sourceTarget==GL30.GL_TEXTURE_2D_ARRAY?(long)(sz+layer)*sourceLayer:0)+(sy/4L)*sourceStride+(sx/4L)*block;
    for(int at=0;at<rows;at+=stripRows){int n=Math.min(stripRows,rows-at);long offset=start+(long)at*sourceStride;state.strip.clear();for(int row=0;row<n;row++){int position=state.strip.position();state.strip.put(position,state.source,Math.toIntExact(offset+row*sourceStride),rowBytes);state.strip.position(position+rowBytes);}state.strip.flip();
     int y=dy+at*4,h=Math.min(height-at*4,n*4),bytes=n*rowBytes,face=dz+layer;Textures.bound(destination,()->{if(destinationTarget==GL30.GL_TEXTURE_2D_ARRAY)GL13.glCompressedTexSubImage3D(destinationTarget,destinationLevel,dx,y,face,width,h,1,dst.format,state.strip);else GL13.glCompressedTexSubImage2D(queryTarget(destinationTarget,face),destinationLevel,dx,y,width,h,dst.format,state.strip);});COPY_BYTES.addAndGet(bytes);
    }
   }
  }finally{GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,oldPack);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,oldUnpack);for(int i=0;i<names.length;i++)GL11.glPixelStorei(names[i],saved[i]);}
  for(int layer=0;layer<depth;layer++)Textures.pixelsChanged(destination,destinationLevel,dz+layer,dx,dy,width,height);COPIES.incrementAndGet();if(source==destination)state.cached=null;return true;
 }
 static long[] memory(){State s=STATES.get();return new long[]{s.reserved,s.sourceCapacity,s.stripCapacity};}
 private static synchronized void free(State s){if(s.source!=null)MemoryUtil.memFree(s.source);if(s.strip!=null)MemoryUtil.memFree(s.strip);if(s.reserved!=0)Packing.releaseCpu(s.reserved);CACHE_BYTES.addAndGet(-s.reserved);s.source=s.strip=null;s.reserved=s.sourceCapacity=s.stripCapacity=0;s.cached=null;}
 static void closeCurrent(){State s=OWNERS.remove(ContextResources.current());if(s!=null)free(s);}
 static void closeWindow(long window){for(var entry:OWNERS.entrySet())if(entry.getKey().handle()==window&&OWNERS.remove(entry.getKey(),entry.getValue()))free(entry.getValue());}
 private CompressedCopies(){}
}
