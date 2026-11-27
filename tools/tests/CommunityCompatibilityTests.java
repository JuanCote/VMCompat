package vmcompat;
import java.nio.*;import java.nio.file.*;import java.util.*;import org.lwjgl.*;import org.lwjgl.glfw.*;import org.lwjgl.opengl.*;import viewpoint.platform.*;
/** Targeted community reproductions using the existing runner and exact released artifact. */
public final class CommunityCompatibilityTests {
 static final Path OUT=Path.of(System.getProperty("vmcompat.resultDir"));static final List<String> rows=new ArrayList<>();
 static Throwable root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
 static void row(String... fields){String s=String.join("\t",fields).replace('\r',' ').replace('\n',' ');rows.add(s);System.out.println("AUDIT "+s);}
 static int[] pixels(int t,int target,int level,int n){ByteBuffer b=BufferUtils.createByteBuffer(n*4);Textures.bound(t,()->GL11.glGetTexImage(target,level,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b));int[] data=new int[n];for(int i=0;i<n;i++)data[i]=b.getInt(i*4);return data;}
 static void shaders()throws Exception{
  var load=Class.forName("viewpoint.platform.Shaders").getDeclaredMethod("load",String.class);load.setAccessible(true);
  for(String pack:System.getProperty("vmcompat.auditPacks","normal,default,somber,vivid,library").split(",")){
   ShaderPacks.useBuiltIn(pack);var presets=ShaderPacks.active().pack.presets;row("PRESETS",pack,presets.toString());
   for(PackPreset preset:presets){if(!System.getProperty("vmcompat.auditPreset",preset.id).equals(preset.id))continue;ShaderPacks.useBuiltIn(pack,preset.id);var active=ShaderPacks.active();var defs=active.getClass().getDeclaredField("defines");defs.setAccessible(true);Files.writeString(OUT.resolve(pack+"-"+preset.id+"-defines.txt"),(String)defs.get(active));
    for(String[] pair:new String[][]{{"far","far.vert","far.frag"},{"far shell","far_shell.vert","far_shell.frag"},{"far shell band","far_shell.vert","gbuffer_shell.frag"},{"far trees","far_tree.vert","far_tree.frag"},{"far prime","screen.vert","far_prime.frag"}}){
     String label=pack+"-"+preset.id+"-"+pair[0].replace(' ','-');
     for(String path:new String[]{"manual","production"}){int p=0;GlProgram program=null;List<Integer> shaders=new ArrayList<>();try{
      if(path.equals("production")){program=GlProgram.create("audit-"+label,pair[1],pair[2]);var id=GlProgram.class.getDeclaredField("id");id.setAccessible(true);p=id.getInt(program);if(p==0)throw new AssertionError("GlProgram created without linked id: "+GlProgram.lastError());}
      else{p=Shaders.glCreateProgram();for(int i=1;i<3;i++){Object source=load.invoke(null,pair[i]);var text=source.getClass().getDeclaredField("text");text.setAccessible(true);String original=(String)text.get(source);Files.writeString(OUT.resolve(label+"-"+(i==1?"vert":"frag")+".glsl"),original);int shader=GL20.glCreateShader(i==1?GL20.GL_VERTEX_SHADER:GL20.GL_FRAGMENT_SHADER);shaders.add(shader);Shaders.vpSource(shader,original);Shaders.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));Shaders.glAttachShader(p,shader);}Shaders.glLinkProgram(p);}
      if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(p));if(program!=null&&Boolean.getBoolean("vmcompat.auditAtlasContract")){Class<?> atlas=Class.forName("viewpoint.render.AtlasGroups");var size=atlas.getDeclaredMethod("size",GlProgram.class);size.setAccessible(true);int actualSize=(int)size.invoke(null,program);int expectedSize=program.has("uAtlases[0]")?(program.has("uAtlases[3]")?4:3):1;if(actualSize!=expectedSize)throw new AssertionError("CPU/shader atlas mismatch "+actualSize+" vs "+expectedSize);var bind=atlas.getDeclaredMethod("samplers",GlProgram.class);bind.setAccessible(true);bind.invoke(null,program);row("ATLAS_CONTRACT",pack,preset.id,pair[0],Integer.toString(actualSize));}row("SHADER",pack,preset.id,pair[0],path,"PASS");
     }catch(Throwable e){row("SHADER",pack,preset.id,pair[0],path,"FAIL",root(e).toString());}finally{if(program!=null)program.delete();else if(p!=0)Shaders.glDeleteProgram(p);for(int s:shaders)GL20.glDeleteShader(s);while(GL11.glGetError()!=0){}}}
    }
   }
  }
 }
 static void copies(){
  int[] formats={GL11.GL_RGBA8,GL30.GL_DEPTH_COMPONENT32F,GL11.GL_RGBA8,GL30.GL_R8,GL11.GL_RGB8,GL30.GL_RGBA16F,GL30.GL_R32I,GL30.GL_R32UI,0x83F1,0x83F3,GL21.GL_SRGB8_ALPHA8};String[] names={"rgba8","depth32f","rgba8-after-depth","r8","rgb8","rgba16f","r32i","r32ui","dxt1","dxt5","srgb8-alpha8"};
  for(int k=0;k<formats.length;k++)for(int target:new int[]{GL11.GL_TEXTURE_2D,GL30.GL_TEXTURE_2D_ARRAY})for(int level=0;level<2;level++){
   int source=0,dest=0;String phase="allocation";try{source=Textures.glCreateTextures(target);dest=Textures.glCreateTextures(target);for(int t:new int[]{source,dest}){if(target==GL11.GL_TEXTURE_2D)Textures.glTextureStorage2D(t,2,formats[k],8,8);else Textures.glTextureStorage3D(t,2,formats[k],8,8,2);Textures.glTextureParameteri(t,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);Textures.glTextureParameteri(t,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);}
    int error=GL11.glGetError();if(error!=0)throw new IllegalStateException("allocation GL error "+error);phase="copy";int width=8>>level,layer=target==GL11.GL_TEXTURE_2D?0:1;
    int saved=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),probe=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,probe);int attachment=formats[k]==GL30.GL_DEPTH_COMPONENT32F?GL30.GL_DEPTH_ATTACHMENT:GL30.GL_COLOR_ATTACHMENT0;if(target==GL11.GL_TEXTURE_2D)GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,attachment,target,source,level);else GL30.glFramebufferTextureLayer(GL30.GL_READ_FRAMEBUFFER,attachment,source,level,layer);GL11.glReadBuffer(attachment==GL30.GL_DEPTH_ATTACHMENT?GL11.GL_NONE:GL30.GL_COLOR_ATTACHMENT0);int status=GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,saved);GL30.glDeleteFramebuffers(probe);row("ATTACHMENT",names[k],Integer.toString(target),Integer.toString(level),"0x"+Integer.toHexString(status));
    Textures.glCopyImageSubData(source,target,level,0,0,layer,dest,target,level,0,0,layer,width,width,1);error=GL11.glGetError();if(error!=0)throw new IllegalStateException("copy GL error "+error);row("COPY",names[k],Integer.toString(target),Integer.toString(level),phase,"PASS");
   }catch(Throwable e){row("COPY",names[k],Integer.toString(target),Integer.toString(level),phase,"FAIL",root(e).toString());}finally{if(source!=0)Textures.glDeleteTextures(source);if(dest!=0)Textures.glDeleteTextures(dest);while(GL11.glGetError()!=0){}}
  }
 }
 public static void main(String[] args)throws Exception{
  Files.createDirectories(OUT);if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);long window=Context.createWindow(32,32,"Community compatibility component checks",0,0);Context.makeCurrent(window);Context.capabilities();
  row("ENV",Agent.VERSION,GL11.glGetString(GL11.GL_VERSION),GL11.glGetString(GL11.GL_RENDERER));
  try{if(args[0].equals("shaders"))shaders();else copies();}finally{Files.write(OUT.resolve("results.tsv"),rows);Context.destroyWindow(window);GLFW.glfwTerminate();}
 }
}
