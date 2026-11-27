package vmcompat;
import java.nio.*;import java.nio.file.*;import org.lwjgl.*;import org.lwjgl.glfw.*;import org.lwjgl.opengl.*;
/** Bounded regression: real 2048x2048 atlas switches on the production GL path.
 * It is not a scene replay or a claim about whole-game FPS.
 */
public final class PackingResidencyTests {
 static void check(boolean x,String s){if(!x)throw new AssertionError(s+" / GL="+GL11.glGetError());}
 static void bind(int unit,int texture){Samplers.glActiveTexture(GL13.GL_TEXTURE0+unit);Samplers.glBindTexture(GL11.GL_TEXTURE_2D,texture);}
 static int texture(int side,int internal,int value){int t=Textures.glCreateTextures(GL11.GL_TEXTURE_2D);Textures.glTextureStorage2D(t,1,internal,side,side);Textures.glTextureParameteri(t,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);Textures.glTextureParameteri(t,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);ByteBuffer pixels=BufferUtils.createByteBuffer(side*side*4);for(int i=0;i<side*side;i++)pixels.put(new byte[]{(byte)value,0,0,(byte)255});pixels.flip();Textures.glTextureSubImage2D(t,0,0,0,side,side,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);return t;}
 static void draw(int p,int[] textures,int step){Shaders.glUseProgram(p);for(int i=0;i<16;i++){int unit=(i+step)%textures.length;Samplers.glUniform1i(Shaders.glGetUniformLocation(p,"images["+i+"]"),unit);}Draws.glDrawArrays(GL11.GL_TRIANGLES,0,3);}
 static float pixel(){FloatBuffer b=BufferUtils.createFloatBuffer(4);GL11.glReadPixels(8,8,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,b);return b.get(0);}
 public static void main(String[] args)throws Exception{check(GLFW.glfwInit(),"init");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);long w=Context.createWindow(16,16,"Resident atlas regression",0,0);Context.makeCurrent(w);Context.capabilities();GL11.glViewport(0,0,16,16);
  int p=StorageBuffersTests.program("#version 330\nuniform sampler2D images[16];uniform sampler2D a;uniform sampler2D b;out vec4 c;void main(){vec4 sum=vec4(0);for(int i=0;i<16;i++)sum+=texture(images[i],vec2(.5));c=(sum+texture(a,vec2(.5))+texture(b,vec2(.5)))/18.;}");Shaders.glUseProgram(p);
  int[] images=new int[6];for(int i=0;i<6;i++){images[i]=texture(2048,GL11.GL_RGBA8,32+i*32);bind(i,images[i]);}Samplers.glUniform1i(Shaders.glGetUniformLocation(p,"a"),0);Samplers.glUniform1i(Shaders.glGetUniformLocation(p,"b"),0);int vao=GL30.glGenVertexArrays();Draws.glBindVertexArray(vao);
  draw(p,images,0);GL11.glFinish();long[] warm=Packing.memoryStats();check(warm[0]<160L*1024*1024,"compact resident GPU capacity bounded");check(warm[3]==6L*2048*2048*4,"RGBA8 remains four bytes per texel");
  int switches=Boolean.getBoolean("vmcompat.asyncPackingBurst")?200:90;for(int i=0;i<switches;i++){draw(p,images,i);if(switches==90?i%15==0:i==switches-1){float expected=2*32;for(int k=0;k<16;k++)expected+=32+32*((k+i)%6);expected/=18*255f;check(Math.abs(pixel()-expected)<.008,"atlas switch pixels");}}GL11.glFinish();long[] stable=Packing.memoryStats();check(stable[2]==warm[2],"no new storage during atlas switching");check(stable[3]==warm[3],"no atlas readback during atlas switching");
  if(Boolean.getBoolean("vmcompat.measurePackingPhases"))measurePhases(p,images);
  ByteBuffer red=BufferUtils.createByteBuffer(4).put(new byte[]{(byte)224,0,0,(byte)255});red.flip();Textures.glTextureSubImage2D(images[0],0,1024,1024,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,red);draw(p,images,0);GL11.glFinish();long[] mutation=Packing.memoryStats();check(mutation[3]-stable[3]==2048L*2048*4,"mutation only copies one atlas");float expected=(224*5+64*3+96*3+128*3+160*2+192*2)/(18*255f);check(Math.abs(pixel()-expected)<.008,"mutated atlas pixel");
  int extra=texture(2048,GL11.GL_RGBA8,255);bind(0,extra);draw(p,images,0);GL11.glFinish();float grown=(255*5+64*3+96*3+128*3+160*2+192*2)/(18*255f);check(Math.abs(pixel()-grown)<.008,"new resident after buffer growth");bind(0,images[0]);draw(p,images,0);check(Math.abs(pixel()-expected)<.008,"old resident data survives growth copy");Textures.glDeleteTextures(extra);
  // Storage replacement reuses a freed resident range and does not trust old pixels.
  Textures.glTextureStorage2D(images[1],1,GL11.GL_RGBA8,2,2);ByteBuffer fill=BufferUtils.createByteBuffer(16);for(int i=0;i<4;i++)fill.put(new byte[]{64,0,0,(byte)255});fill.flip();Textures.glTextureSubImage2D(images[1],0,0,0,2,2,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,fill);draw(p,images,1);GL11.glFinish();check(GL11.glGetError()==0,"realloc and draw");
  Textures.glDeleteTextures(images[2]);images[2]=texture(2,GL11.GL_RGBA8,96);bind(2,images[2]);draw(p,images,2);GL11.glFinish();check(GL11.glGetError()==0,"delete/recreate");
  long[] end=Packing.memoryStats();Path result=Path.of(System.getProperty("vmcompat.resultDir"),"packing-residency.json");Files.writeString(result,"{\"atlas_size\":2048,\"atlases\":6,\"switch_draws\":"+switches+",\"warm_capacity\":"+warm[0]+",\"peak_reserved\":"+end[1]+",\"allocations_after_warm\":"+(stable[2]-warm[2])+",\"atlas_bytes_during_switches\":"+(stable[3]-warm[3])+",\"mutation_bytes\":"+(mutation[3]-stable[3])+",\"game_fps\":null}\n");
  var plans=Packing.class.getDeclaredField("PLANS");plans.setAccessible(true);Object plan=((java.util.Map<?,?>)plans.get(null)).get(p);var grow=Packing.class.getDeclaredMethod("capacity",plan.getClass(),long.class);grow.setAccessible(true);long count=Packing.memoryStats()[2];boolean stopped=false;try{grow.invoke(null,plan,Packing.BUDGET+16);}catch(java.lang.reflect.InvocationTargetException e){stopped=e.getCause() instanceof IllegalStateException;}check(stopped&&Packing.failure()!=null,"over-budget request stops before allocation");for(int i=0;i<30;i++){try{draw(p,images,i);throw new AssertionError("failure must latch");}catch(IllegalStateException expectedFailure){}}check(Packing.memoryStats()[2]==count,"no allocations on failure/retries");
  Shaders.glDeleteProgram(p);Textures.glDeleteTextures(images);Context.destroyWindow(w);GLFW.glfwTerminate();check(Packing.memoryStats()[0]==0,"context destruction releases accounting and pending retirements");System.out.println("PASS 2048 atlas residency, switches, mutation, realloc, delete/recreate, bounded capacity and cleanup");
 }
 private static void measurePhases(int program,int[] images)throws Exception{
  StringBuilder data=new StringBuilder("{\"enabled\":"+Packing.BREAKDOWN+",\"draws\":200,\"blocks\":[");
  boolean reuse=Boolean.getBoolean("vmcompat.expectPackingReuse");
  for(int block=0;block<3;block++){
   long[] before=Counters.snapshot();
   long start=System.nanoTime();
   for(int i=0;i<200;i++)draw(program,images,i);
   GL11.glFinish();
   long elapsed=System.nanoTime()-start;
   long[] after=Counters.snapshot();
   long pixels=after[Counters.Key.PACK_IMAGE_BYTES.ordinal()]-before[Counters.Key.PACK_IMAGE_BYTES.ordinal()];
   long bytes=after[Counters.Key.PACK_BYTES.ordinal()]-before[Counters.Key.PACK_BYTES.ordinal()];
   long headers=after[Counters.Key.PACK_HEADER_BYTES.ordinal()]-before[Counters.Key.PACK_HEADER_BYTES.ordinal()];
   long selections=after[Counters.Key.PACK_TABLE_REUSES.ordinal()]-before[Counters.Key.PACK_TABLE_REUSES.ordinal()];
   check(pixels==0&&headers==(Packing.BREAKDOWN?bytes:0),"resident switching transfers headers only");
   check(reuse?bytes==0&&selections>=199:bytes>0&&selections==0,"resident table reuse must remove header transfers");
   if(block>0)data.append(',');
   data.append("{\"completed_ns\":").append(elapsed);
   long phases=0;
   for(Counters.Key key:Counters.Key.values()){
    if(!key.name().startsWith("PACK_"))continue;
    long delta=after[key.ordinal()]-before[key.ordinal()];
    if(key.name().endsWith("_NS")&&key!=Counters.Key.PACK_NS)phases+=delta;
    data.append(",\"").append(key.name().toLowerCase(java.util.Locale.ROOT)).append("\":").append(delta);
   }
   long pack=after[Counters.Key.PACK_NS.ordinal()]-before[Counters.Key.PACK_NS.ordinal()];
   check(Packing.BREAKDOWN&&!reuse?phases>0&&phases<=pack:phases==0,"phase timing bounds");
   data.append('}');
   float expected=2*32;for(int k=0;k<16;k++)expected+=32+32*((k+199)%6);
   check(Math.abs(pixel()-expected/(18*255f))<.008,"measured atlas switch pixels");
  }
  Files.writeString(Path.of(System.getProperty("vmcompat.resultDir"),"packing-phases.json"),data.append("]}\n"));
 }
}
