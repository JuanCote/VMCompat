package vmcompat;
import java.util.*;import java.util.regex.*;import org.lwjgl.opengl.*;
/** Read-only homogeneous std430 arrays used by the qualified Viewpoint 0.1.7.
 * Storage remains in the original GL buffer; texture-buffer aliases need no readback.
 * Writable/heterogeneous blocks are deliberately not translated.
 */
public final class StorageBuffers {
 static final int TARGET=0x90D2,BLOCK=0x92E6,UNIT=224;
 record Spec(String name,String type,int format,String sampler,String swizzle){}
 private static final Map<String,Spec> SPECS=Map.of(
  "ModelPalettes",new Spec("ModelPalettes","vec4",GL30.GL_RGBA32F,"samplerBuffer",""),
  "ModelDraws",new Spec("ModelDraws","vec4",GL30.GL_RGBA32F,"samplerBuffer",""),
  "ReliefWalls",new Spec("ReliefWalls","int",GL30.GL_R32I,"isamplerBuffer",".r"),
  "ReliefFlow",new Spec("ReliefFlow","int",GL30.GL_R32I,"isamplerBuffer",".r"),
  "LampLists",new Spec("LampLists","uvec2",GL30.GL_RG32UI,"usamplerBuffer",".rg"),
  "LookRecords",new Spec("LookRecords","uvec4",GL30.GL_RGBA32UI,"usamplerBuffer",""));
 static final Pattern DECL=Pattern.compile("layout\\s*\\(\\s*std430[^)]*\\)\\s+readonly\\s+buffer\\s+(\\w+)\\s*\\{([^}]+)\\}\\s*;");
 static final class Binding {int buffer;long generation;long offset,size;boolean range;}
 static final class Alias {int texture,buffer,format;long generation;boolean range;}
 static final class Program {final Map<Integer,Spec> specs=new HashMap<>();final Map<Integer,Integer> bindings=new HashMap<>();final Map<String,Integer> counts=new HashMap<>(),lastCounts=new HashMap<>(),offsets=new HashMap<>(),lastOffsets=new HashMap<>();int words=-1;final int[] textureDims={-1,-1,-1},textureUnits=new int[3],lastWidths={-1,-1,-1},lastHeights={-1,-1,-1};}
 static final class State {final Map<Integer,Binding> bindings=new HashMap<>();final Map<String,Alias> aliases=new HashMap<>();}
 private static final Map<ContextResources.Group,Map<Integer,Program>> PROGRAMS=Collections.synchronizedMap(new WeakHashMap<>());
 static Map<Integer,Program> programs(){synchronized(PROGRAMS){return PROGRAMS.computeIfAbsent(ContextResources.group(),k->new HashMap<>());}}
 static final StateStore<State> STATES=new StateStore<>(State::new);
 static int unit(Spec spec){return UNIT+List.of("ModelPalettes","ModelDraws","ReliefWalls","ReliefFlow","LampLists","LookRecords").indexOf(spec.name);}
 static String uniform(String block){return "vmStorage_"+block;}
 public static String convert(String source){
  Matcher m=DECL.matcher(source);StringBuffer lowered=new StringBuffer();Map<String,String> replacements=new LinkedHashMap<>();
  while(m.find()){
   Spec spec=SPECS.get(m.group(1));if(spec==null)throw new UnsupportedOperationException("Unqualified read-only storage block "+m.group(1));
   Matcher field=Pattern.compile("(\\w+)\\s+(\\w+)\\s*\\[([^]]*)\\]\\s*;").matcher(m.group(2));String base="0";int end=0;
   while(field.find()){
    if(!m.group(2).substring(end,field.start()).isBlank()||!field.group(1).equals(spec.type))throw new IllegalArgumentException("Changed storage layout: "+spec.name);
    replacements.put(field.group(2),uniform(spec.name)+"|"+base+"|"+spec.swizzle);String count=field.group(3).trim();base="("+base+")+("+count+")";end=field.end();
   }
   if(end==0||!m.group(2).substring(end).isBlank())throw new IllegalArgumentException("Changed storage members: "+spec.name);
   m.appendReplacement(lowered,Matcher.quoteReplacement("uniform "+spec.sampler+" "+uniform(spec.name)+";"));
  }
  m.appendTail(lowered);String result=lowered.toString();
  for(var e:replacements.entrySet()){
   Pattern p=Pattern.compile("\\b"+Pattern.quote(e.getKey())+"\\s*\\[");Matcher use=p.matcher(result);StringBuilder out=new StringBuilder();int from=0;
   while(use.find(from)) {int start=use.end(),at=start,depth=1;while(at<result.length()&&depth>0){char c=result.charAt(at++);if(c=='[')depth++;else if(c==']')depth--;}
    if(depth!=0)throw new IllegalArgumentException("Unclosed storage subscript");String[] parts=e.getValue().split("\\|",-1);out.append(result,from,use.start()).append("texelFetch(").append(parts[0]).append(",int(").append(parts[1]).append(") + int(").append(result,start,at-1).append("))").append(parts[2]);from=at;
   }
   result=out.append(result.substring(from)).toString();
  }
  if(!replacements.isEmpty())result=result.replaceAll("(?m)^\\s*#extension\\s+GL_ARB_(?:shader_storage_buffer_object|shader_image_load_store)\\s*:[^\\n]*","");
  return result;
 }
 public static void linked(int program){Program p=new Program();p.words=GL20.glGetUniformLocation(program,WordStorage.SAMPLER);for(int kind=0;kind<3;kind++)p.textureDims[kind]=WordStorage.dimension(program,kind);for(Spec spec:SPECS.values()){int location=GL20.glGetUniformLocation(program,uniform(spec.name));if(location>=0)p.specs.put(location,spec);else if(p.words>=0&&WordStorage.NAMES.contains(spec.name)){int off=GL20.glGetUniformLocation(program,WordStorage.offset(spec.name));if(off>=0){int slot=WordStorage.NAMES.indexOf(spec.name);p.specs.put(WordStorage.INDEX+slot,spec);p.counts.put(spec.name,GL20.glGetUniformLocation(program,WordStorage.count(spec.name)));p.offsets.put(spec.name,off);}}}programs().put(program,p);if(p.words>=0)for(Samplers.Input input:Samplers.state().programs.getOrDefault(program,new Samplers.Input[0]))if(input.location==p.words){input.logical=WordStorage.UNIT;GL41.glProgramUniform1i(program,input.location,input.physical);}}
 public static void deleted(int program){programs().remove(program);}
 public static int glGetProgramResourceIndex(int program,int iface,CharSequence name){
  if(iface!=BLOCK){if(GL.getCapabilities().OpenGL43)return GL43.glGetProgramResourceIndex(program,iface,name);throw new UnsupportedOperationException("Program resource interface "+iface);}
  Program p=programs().get(program);if(p==null)return -1;for(var e:p.specs.entrySet())if(e.getValue().name.contentEquals(name))return e.getKey();return -1;
 }
 public static void glShaderStorageBlockBinding(int program,int index,int binding){Program p=programs().get(program);if(p==null||!p.specs.containsKey(index)||binding<0||UNIT+binding>=256)throw new IllegalArgumentException("Unsupported storage binding");p.bindings.put(index,binding);if(index>=WordStorage.INDEX)return;Samplers.State samplers=Samplers.state();if(!samplers.programs.containsKey(program))Samplers.linked(program);for(Samplers.Input input:samplers.programs.get(program))if(input.location==index){input.logical=unit(p.specs.get(index));GL41.glProgramUniform1i(program,index,input.physical);return;}throw new IllegalStateException("Missing storage sampler after link");}
 public static void bindBase(int index,int buffer){bind(index,buffer,0,0,false);}
 public static void bindRange(int index,int buffer,long offset,long size){bind(index,buffer,offset,size,true);}
 static void bind(int index,int buffer,long offset,long size,boolean range){if(index<0||UNIT+index>=256)throw new IllegalArgumentException("Storage binding "+index);Binding b=new Binding();b.buffer=buffer;b.generation=buffer==0?0:Buffers.identity(buffer)[0];b.offset=offset;b.size=size;b.range=range;STATES.get().bindings.put(index,b);}
 public static void prepare(int program){State s=STATES.get();Program p=programs().get(program);if(p==null||(p.specs.isEmpty()&&!WordStorage.hasTextures(p)))return;Samplers.State samplers=Samplers.state();if(!samplers.programs.containsKey(program)||!Objects.equals(samplers.revisions.get(program),ContextResources.programRevision(program)))Samplers.linked(program);for(var e:p.specs.entrySet())for(Samplers.Input input:samplers.programs.get(program))if(input.location==e.getKey())input.logical=unit(e.getValue());int active=Samplers.activeUnit();try{for(var e:p.specs.entrySet()){
  if(e.getKey()>=WordStorage.INDEX)continue;Spec spec=e.getValue();int binding=p.bindings.getOrDefault(e.getKey(),0);Binding b=s.bindings.get(binding);int buffer=b==null?0:b.buffer;
  if(buffer!=0&&Buffers.identity(buffer)[0]!=b.generation)buffer=0;
  if(buffer==0){Samplers.glActiveTexture(GL13.GL_TEXTURE0+unit(spec));Samplers.glBindTexture(GL31.GL_TEXTURE_BUFFER,0);continue;}
  Alias a=s.aliases.computeIfAbsent(spec.name+":"+binding,k->new Alias());if(a.texture==0)a.texture=GL11.glGenTextures();Samplers.glActiveTexture(GL13.GL_TEXTURE0+unit(spec));Samplers.glBindTexture(GL31.GL_TEXTURE_BUFFER,a.texture);
  long generation=buffer==0?0:Buffers.identity(buffer)[0];if(b!=null&&b.range&&buffer!=0){Buffers.glTexBufferRange(GL31.GL_TEXTURE_BUFFER,spec.format,buffer,b.offset,b.size);a.range=true;}
  else if(a.buffer!=buffer||a.format!=spec.format||a.generation!=generation||a.range){Buffers.glTexBuffer(GL31.GL_TEXTURE_BUFFER,spec.format,buffer);a.buffer=buffer;a.format=spec.format;a.generation=generation;a.range=false;}
 }WordStorage.prepare(program,p,s);}finally{Samplers.glActiveTexture(GL13.GL_TEXTURE0+active);}}
 public static void closeCurrent(){State s=STATES.get();for(Alias a:s.aliases.values())if(a.texture!=0)Textures.glDeleteTextures(a.texture);s.aliases.clear();s.bindings.clear();}
 private StorageBuffers(){}
}
