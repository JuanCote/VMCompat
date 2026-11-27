package vmcompat;
import java.util.*;import org.lwjgl.opengl.*;
/** A context-owned raw-word alias for the three integer SSBO inputs.
 * GPU copies preserve source bits. No texture pixels or CPU readbacks involved.
 */
public final class WordStorage {
 static final boolean ENABLED=NativeSamplers.ENABLED&&Boolean.parseBoolean(Options.value("nativeStorageWords","true"));
 static final String SAMPLER="vmStorageWords";static final int UNIT=230,INDEX=2_000_000,MAP_BYTES=65536+2*1024*1024,CELL=3_000_000;
 static final List<String> NAMES=List.of("ReliefWalls","ReliefFlow","LampLists");
 private record Source(int id,long generation,long storage,long version,long offset,long size){}
 private static final String[] TEX={"uCellTable","uMask","uShellMask"},DIM={"vmCellSize","vmMaskSize","vmShellSize"};
 private static final class Image{int texture=-1,width,height;long generation,version;}
 static boolean hasTextures(StorageBuffers.Program p){for(int dim:p.textureDims)if(dim>=0)return true;return false;}
 static int textureOffset(int kind){return kind==0?0:65536+(kind-1)*1024*1024;}
 static int dimension(int program,int kind){return GL20.glGetUniformLocation(program,DIM[kind]);}
 private static final java.util.concurrent.atomic.AtomicLong WORD_BYTES=new java.util.concurrent.atomic.AtomicLong(),WORD_COPIES=new java.util.concurrent.atomic.AtomicLong(),IMAGE_BYTES=new java.util.concurrent.atomic.AtomicLong(),IMAGE_COPIES=new java.util.concurrent.atomic.AtomicLong();
 static long[] diagnostics(){return new long[]{WORD_BYTES.get(),WORD_COPIES.get(),IMAGE_BYTES.get(),IMAGE_COPIES.get()};}
 private static final class State {long reserved;int capacity;final long[] capacities=new long[3],bases=new long[3];int buffer,texture,zero;boolean attached;final Image[] images={new Image(),new Image(),new Image()};final Source[] sources=new Source[3];long allocations,copies,bytes;}
 private static final StateStore<State> STATES=new StateStore<>(State::new);
 static String offset(String name){return "vmWordBase_"+name;}
 static String count(String name){return "vmWordCount_"+name;}
 public static String convert(String source){if(!ENABLED)return source;int count=0;for(String name:NAMES)if(source.contains("uniform "+(name.equals("LampLists")?"usamplerBuffer":"isamplerBuffer")+" "+StorageBuffers.uniform(name)+";"))count++;boolean cell=source.contains("uniform isampler2D uCellTable;"),world=source.contains("uMaskGrid")||source.contains("uShellGrid");int textures=cell?1:0;if(world)for(int k=1;k<3;k++)if(source.contains("uniform sampler2D "+TEX[k]+";"))textures++;if(count+textures<2)return source;
  boolean added=false;for(String name:NAMES){String type=name.equals("LampLists")?"usamplerBuffer":"isamplerBuffer",decl="uniform "+type+" "+StorageBuffers.uniform(name)+";";if(!source.contains(decl))continue;String call="vmWordRead_"+name;
   String helper=name.equals("LampLists")?"uvec4 "+call+"(int i){if(i<0||i>="+count(name)+")return uvec4(0);int at="+offset(name)+"+i*2;return uvec4(texelFetch("+SAMPLER+",at).r,texelFetch("+SAMPLER+",at+1).r,0u,0u);}":"ivec4 "+call+"(int i){if(i<0||i>="+count(name)+")return ivec4(0);return ivec4(int(texelFetch("+SAMPLER+","+offset(name)+"+i).r),0,0,0);}";
   source=source.replace(decl,"uniform int "+offset(name)+";uniform int "+count(name)+";\n"+helper+"\n");added=true;source=source.replace("texelFetch("+StorageBuffers.uniform(name)+",",call+"(");
  }
  for(int kind=0;kind<3;kind++){String name=TEX[kind],decl="uniform "+(kind==0?"isampler2D ":"sampler2D ")+name+";";if((kind!=0&&!world)||!source.contains(decl))continue;String call="vmRead_"+name;String helper=kind==0?"ivec4 "+call+"(ivec2 p,int lod){if(any(lessThan(p,ivec2(0)))||any(greaterThanEqual(p,"+DIM[kind]+")))return ivec4(0);return ivec4(int(texelFetch("+SAMPLER+","+(textureOffset(kind)/4)+"+p.y*"+DIM[kind]+".x+p.x).r),0,0,0);}":"vec4 "+call+"(ivec2 p,int lod){if(any(lessThan(p,ivec2(0)))||any(greaterThanEqual(p,"+DIM[kind]+")))return vec4(0);uint v=texelFetch("+SAMPLER+","+(textureOffset(kind)/4)+"+p.y*"+DIM[kind]+".x+p.x).r;return vec4(v&255u,(v>>8)&255u,(v>>16)&255u,v>>24)/255.0;}";
   if(source.contains("texture("+name+",")||source.contains("textureLod("+name+",")||source.contains("textureGrad("+name+",")||source.contains("textureSize("+name+","))throw new UnsupportedOperationException("Unqualified native mask access: "+name);
   source=source.replace(decl,"uniform ivec2 "+DIM[kind]+";\n"+helper).replace("texelFetch("+name+",",call+"(");added=true;
  }
  return added?Shaders.insertDeclarations(source,"uniform usamplerBuffer "+SAMPLER+";\n"):source;
 }
 static int location(int program,String name){StorageBuffers.Program p=StorageBuffers.programs().get(program);if(p!=null)for(int k=0;k<3;k++)if(p.textureDims[k]>=0&&name.equals(TEX[k]))return CELL+k;return -1;}
 static boolean assign(int program,int location,int unit){if(location<CELL||location>=CELL+3)return false;int kind=location-CELL;StorageBuffers.Program p=StorageBuffers.programs().get(program);if(p==null||p.textureDims[kind]<0||unit<0||unit>=256)throw new IllegalArgumentException("Native mask sampler binding");p.textureUnits[kind]=unit;return true;}
 private static final class Transfer implements AutoCloseable{final int active=Samplers.activeUnit();boolean touched;int read,write;BufferTextureGuard guard;void touch(){if(!touched){read=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);write=GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);touched=true;guard=new BufferTextureGuard();}}public void close(){if(touched){if(guard!=null)guard.close();GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,read);GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,write);Samplers.glActiveTexture(GL13.GL_TEXTURE0+active);}}}
 private static void image(int program,StorageBuffers.Program p,State state,int kind,Transfer transfer){if(p.textureDims[kind]<0)return;Samplers.State samplers=Samplers.state();int texture=samplers.texture(p.textureUnits[kind],0);long generation=texture==0?0:Buffers.textureLifetime(texture),version=Textures.version(texture);Image image=state.images[kind];
  if(image.texture!=texture||image.generation!=generation||image.version!=version||(texture!=0&&version==0)||!ContextResources.group().trusted||ContextResources.group().contexts!=1){transfer.touch();int width=1,height=1;long bytes=4;if(texture==0){GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,state.zero);GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,state.buffer);GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER,GL31.GL_COPY_WRITE_BUFFER,0,textureOffset(kind),4);}else{
   GL13.glActiveTexture(GL13.GL_TEXTURE0+samplers.scratch);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);samplers.live[samplers.scratch][0]=texture;width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH);height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);int format=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);bytes=(long)width*height*4;boolean valid=kind==0?Set.of(GL30.GL_R8I,GL30.GL_R16I,GL30.GL_R32I).contains(format):Set.of(GL30.GL_R8,GL30.GL_RG8,GL11.GL_RGB8,GL11.GL_RGBA8,GL11.GL_RED,GL30.GL_RG,GL11.GL_RGB,GL11.GL_RGBA).contains(format);
   if(width<=0||height<=0||bytes>(kind==0?65536:1024*1024)||!valid)throw new IllegalStateException("Unqualified "+TEX[kind]+": "+width+"x"+height+" format="+format);
   int old=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);int[] names={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SKIP_ROWS,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST},saved=new int[names.length];for(int i=0;i<names.length;i++){saved[i]=GL11.glGetInteger(names[i]);GL11.glPixelStorei(names[i],i==0?1:0);}try{GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,state.buffer);GL11.nglGetTexImage(GL11.GL_TEXTURE_2D,0,kind==0?GL30.GL_RED_INTEGER:GL11.GL_RGBA,kind==0?GL11.GL_INT:GL11.GL_UNSIGNED_BYTE,textureOffset(kind));Buffers.pixelPackWrite();}finally{GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,old);for(int i=0;i<names.length;i++)GL11.glPixelStorei(names[i],saved[i]);}
   }image.texture=texture;image.generation=generation;image.version=version;image.width=width;image.height=height;state.copies++;state.bytes+=bytes;IMAGE_BYTES.addAndGet(bytes);IMAGE_COPIES.incrementAndGet();
  }if(p.lastWidths[kind]!=image.width||p.lastHeights[kind]!=image.height||!ContextResources.group().trusted||ContextResources.group().contexts!=1){GL41.glProgramUniform2i(program,p.textureDims[kind],image.width,image.height);p.lastWidths[kind]=image.width;p.lastHeights[kind]=image.height;}
 }
 static Source source(StorageBuffers.Program p,StorageBuffers.State bindings,String name,Transfer transfer){
  int slot=NAMES.indexOf(name);StorageBuffers.Binding b=bindings.bindings.get(p.bindings.getOrDefault(INDEX+slot,0));int id=b==null?0:b.buffer;long[] identity=id==0?new long[]{0,0,0,64}:Buffers.identity(id);
  if(b!=null&&identity[0]!=b.generation){id=0;identity=new long[]{0,0,0,64};}
  long offset=id!=0&&b.range?b.offset:0,size=id!=0&&b.range?b.size:identity[3];if(id!=0&&size<0){transfer.touch();GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,id);size=GL32.glGetBufferParameteri64(GL31.GL_COPY_READ_BUFFER,GL15.GL_BUFFER_SIZE);}
  if(offset<0||size<0||size>Integer.MAX_VALUE||(offset&3)!=0||(size&3)!=0||(id!=0&&identity[3]>=0&&(offset>identity[3]||size>identity[3]-offset)))throw new IllegalStateException("Unqualified native word range: "+name+" bytes="+size+" offset="+offset);
  return new Source(id,identity[0],identity[1],identity[2],offset,size);
 }
 static void storage(State state,Source[] wanted,Transfer transfer){
  long[] capacities=state.capacities.clone();boolean grow=state.buffer==0;
  for(int slot=0;slot<3;slot++)if(wanted[slot]!=null&&wanted[slot].size>capacities[slot]){long bytes=wanted[slot].size;capacities[slot]=Math.max(64,(bytes+1048575L)&~1048575L);grow=true;}
  if(!grow)return;
  long total=MAP_BYTES;for(long bytes:capacities)total+=bytes;
  // Validate the entire new layout before allocation; never truncate or overlap inputs.
  transfer.touch();long limit=(long)GL11.glGetInteger(GL31.GL_MAX_TEXTURE_BUFFER_SIZE)*4;
  if(total>limit||total>Integer.MAX_VALUE)throw new IllegalStateException("Native word layout exceeds GL limit: need="+total+" limit="+limit+" sources="+Arrays.toString(capacities));
  long reservation=total+(state.zero==0?64:0);Packing.reserveAux(reservation);int next=0,newZero=0;
  try(var guard=new BufferTextureGuard()){
   next=Buffers.glGenBuffers();GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,next);Buffers.glBufferData(GL31.GL_COPY_WRITE_BUFFER,total,GL15.GL_DYNAMIC_DRAW);
   if(GL32.glGetBufferParameteri64(GL31.GL_COPY_WRITE_BUFFER,GL15.GL_BUFFER_SIZE)!=total)throw new IllegalStateException("Native word arena allocation failed");
   if(state.zero==0){newZero=Buffers.glGenBuffers();GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,newZero);Buffers.glBufferData(GL31.GL_COPY_READ_BUFFER,org.lwjgl.BufferUtils.createIntBuffer(16),GL15.GL_STATIC_DRAW);}
   if(state.texture==0)state.texture=Textures.glGenTextures();
  }catch(RuntimeException|Error e){if(next!=0)Buffers.glDeleteBuffers(next);if(newZero!=0)Buffers.glDeleteBuffers(newZero);Packing.retireAux(reservation);throw e;}
  int old=state.buffer;long retired=state.capacity;state.buffer=next;state.capacity=(int)total;state.reserved+=reservation;if(newZero!=0)state.zero=newZero;
  long at=MAP_BYTES;for(int slot=0;slot<3;slot++){state.capacities[slot]=capacities[slot];state.bases[slot]=at;at+=capacities[slot];}Arrays.fill(state.sources,null);for(Image image:state.images)image.texture=-1;state.attached=false;state.allocations++;
  if(old!=0){Buffers.glDeleteBuffers(old);Packing.retireAux(retired);state.reserved-=retired;}
 }
 static void prepare(int program,StorageBuffers.Program p,StorageBuffers.State bindings){if(p.words<0)return;State state=STATES.get();int active=Samplers.activeUnit();
  try(Transfer transfer=new Transfer()){
   Source[] wanted=new Source[3];for(var entry:p.specs.entrySet())if(entry.getKey()>=INDEX){String name=entry.getValue().name();wanted[NAMES.indexOf(name)]=source(p,bindings,name,transfer);}
   storage(state,wanted,transfer);
   for(int kind=0;kind<3;kind++)image(program,p,state,kind,transfer);
   for(int slot=0;slot<3;slot++){Source source=wanted[slot];if(source==null)continue;String name=NAMES.get(slot);int base=(int)(state.bases[slot]/4);Integer off=p.offsets.get(name);
    if(off!=null&&(!Objects.equals(p.lastOffsets.get(name),base)||!ContextResources.group().trusted||ContextResources.group().contexts!=1)){GL41.glProgramUniform1i(program,off,base);p.lastOffsets.put(name,base);}
    Integer countLocation=p.counts.get(name);int elements=(int)(source.size/(name.equals("LampLists")?8:4));if(countLocation!=null&&(!Objects.equals(p.lastCounts.get(name),elements)||!ContextResources.group().trusted||ContextResources.group().contexts!=1)){GL41.glProgramUniform1i(program,countLocation,elements);p.lastCounts.put(name,elements);}
    if(!source.equals(state.sources[slot])||!ContextResources.group().trusted||ContextResources.group().contexts!=1){transfer.touch();GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,source.id==0?state.zero:source.id);GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,state.buffer);if(source.size>0)GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER,GL31.GL_COPY_WRITE_BUFFER,source.offset,state.bases[slot],source.size);state.sources[slot]=source;state.copies++;state.bytes+=source.size;WORD_BYTES.addAndGet(source.size);WORD_COPIES.incrementAndGet();}
   }
  }
  if(Samplers.state().texture(UNIT,4)!=state.texture||!state.attached){Samplers.glActiveTexture(GL13.GL_TEXTURE0+UNIT);Samplers.glBindTexture(GL31.GL_TEXTURE_BUFFER,state.texture);if(!state.attached){Buffers.glTexBuffer(GL31.GL_TEXTURE_BUFFER,GL30.GL_R32UI,state.buffer);state.attached=true;}Samplers.glActiveTexture(GL13.GL_TEXTURE0+active);}
 }
 static long[] stats(){State s=STATES.get();return new long[]{s.allocations,s.copies,s.bytes,s.capacity};}
 static void closeCurrent(){State s=STATES.get();if(s.texture!=0)Textures.glDeleteTextures(s.texture);if(s.buffer!=0)Buffers.glDeleteBuffers(s.buffer);if(s.zero!=0)Buffers.glDeleteBuffers(s.zero);s.texture=s.buffer=s.zero=s.capacity=0;s.attached=false;Arrays.fill(s.capacities,0);Arrays.fill(s.sources,null);for(Image image:s.images)image.texture=-1;if(s.reserved!=0){Packing.retireAux(s.reserved);s.reserved=0;}}
 private WordStorage(){}
}
