package vmcompat;
import java.lang.classfile.*;import java.lang.classfile.instruction.*;import org.lwjgl.glfw.*;import org.lwjgl.opengl.*;import java.nio.file.*;import java.util.*;import java.lang.reflect.*;
/** Existing resource/picking runner extension; real unsupported GL plus deterministic supported protocol. */
public final class TimestampTests {
 static void check(boolean x,String msg){if(!x)throw new AssertionError(msg);}
 static Object field(String name)throws Exception{var f=viewpoint.platform.FrameTimes.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 static class Fake implements FrameTimesGpu.Driver {
  boolean supports=true,ready=false,fail;int created,issued,polled,result,deleted,id=1;long tick;Map<Integer,Long> times=new HashMap<>();
  public boolean supported(){return supports;}public void generate(int[] x){created+=x.length;for(int i=0;i<x.length;i++)x[i]=id++;}public void issue(int x){if(fail)throw new IllegalStateException("fixture error");issued++;times.put(x,++tick*1000);}public boolean ready(int x){polled++;return ready;}public long result(int x){result++;return times.get(x);}public void delete(int[] x){deleted+=x.length;}
 }
 static void supportedProtocol(){
  Fake d=new Fake();var c=new FrameTimesGpu.Clock(d);float[] h=new float[4096];for(int i=0;i<6;i++){check(c.begin(i,h),"supported begin");check(c.end(i),"supported end");}check(d.created==12&&d.issued==12&&d.result==0,"six slots and nonblocking busy result");c.begin(6,h);c.end(6);check(d.issued==12,"busy ring never overwritten");d.ready=true;c.begin(7,h);c.end(7);check(d.result==12&&h[0]>0&&d.issued==14,"completed queries consumed then reused");
  Fake other=new Fake();var b=new FrameTimesGpu.Clock(other);b.begin(8,new float[4096]);b.end(8);check(d.created==12&&other.created==12,"context clocks independent");c.close();check(d.deleted==12&&other.deleted==0,"owner cleanup only");b.close();
  Fake bad=new Fake();bad.fail=true;var failed=new FrameTimesGpu.Clock(bad);check(!failed.begin(0,h)&&!failed.end(0),"runtime error disables clock");int issued=bad.issued;check(!failed.begin(1,h)&&issued==bad.issued,"failed clock has no retries");failed.close();
  Fake unsupported=new Fake();unsupported.supports=false;var no=new FrameTimesGpu.Clock(unsupported);no.begin(1,h);no.end(1);check(unsupported.created+unsupported.issued+unsupported.polled+unsupported.result==0&&h[0]==-1,"unavailable sentinel and no driver traffic");
 }
 static void bytecodeGuard()throws Exception {
  for(String name:List.of("viewpoint/platform/FrameTimes","viewpoint/platform/Profile","viewpoint/platform/GpuParts")){
   byte[] raw;try(var in=TimestampTests.class.getClassLoader().getResourceAsStream(name+".class")){raw=in.readAllBytes();}byte[] fixed=Transformer.rewrite(name,raw);check(ClassFile.of().verify(fixed).isEmpty(),"timestamp bytecode verifier "+name);
   for(var method:ClassFile.of().parse(fixed).methods())if(method.code().isPresent())for(var e:method.code().get())if(e instanceof InvokeInstruction i&&i.owner().asInternalName().startsWith("org/lwjgl/opengl/")&&TimestampQueries.method(i.name().stringValue(),i.type().stringValue())!=null)throw new AssertionError("unguarded query API "+name+" "+i.name());
  }
 }
 public static void main(String[] args)throws Exception{bytecodeGuard();
  supportedProtocol();check(TimestampQueries.method("glGetQueryObjecti64","(II)J").equals("signedResult"),"signed dispatch");check(TimestampQueries.method("glGetQueryObjectui64","(II)J").equals("result"),"unsigned dispatch");
  check(GLFW.glfwInit(),"GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);long a=Context.createWindow(32,32,"timestamp guards",0,0);Context.makeCurrent(a);Context.capabilities();int bits=GL15.glGetQueryi(GL33.GL_TIMESTAMP,GL15.GL_QUERY_COUNTER_BITS);check(bits==0,"this hardware fixture qualifies unsupported target only");
  for(int i=0;i<20;i++){viewpoint.platform.FrameTimes.renderStarted();viewpoint.platform.FrameTimes.renderEnded();viewpoint.platform.FrameTimes.swapped();}
  var prepare=viewpoint.platform.FrameTimes.class.getDeclaredMethod("prepare");prepare.setAccessible(true);check(prepare.invoke(null).equals(false),"original prepare also enforces counter bits");check(field("starts")==null&&field("ends")==null,"unsupported prepare never allocates query pairs");check(Boolean.FALSE.equals(field("gpuTimed")),"FrameTimes unavailable flag");check(((Number)field("count")).longValue()>=19,"CPU swapped history retained");float[] cpu=(float[])field("intervalMs"),gpu=(float[])field("gpuMs");check(cpu[0]>0&&gpu[0]==-1,"CPU interval valid, GPU missing not zero");
  // Exercise both result adapters directly: unsupported path must not call the driver even with an arbitrary ID.
  TimestampQueries.generate(new int[2]);TimestampQueries.counter(123,GL33.GL_TIMESTAMP);TimestampQueries.available(123,GL15.GL_QUERY_RESULT_AVAILABLE);TimestampQueries.result(123,GL15.GL_QUERY_RESULT);TimestampQueries.signedResult(123,GL15.GL_QUERY_RESULT);
  long b=Context.createWindow(32,32,"other context",0,a);Context.makeCurrent(b);Context.capabilities();viewpoint.platform.FrameTimes.renderStarted();viewpoint.platform.FrameTimes.renderEnded();Context.destroyWindow(b);Context.makeCurrent(a);Context.capabilities();viewpoint.platform.FrameTimes.renderStarted();viewpoint.platform.FrameTimes.renderEnded();
  check(TimestampQueries.creates.sum()+TimestampQueries.issues.sum()+TimestampQueries.polls.sum()+TimestampQueries.results.sum()==0,"zero native timestamp create/issue/poll/result");
  int occlusion=GL15.glGenQueries();GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED,occlusion);GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);check(GL15.glIsQuery(occlusion),"occlusion unaffected");GL15.glDeleteQueries(occlusion);check(GL11.glGetError()==0,"no GL errors");Context.destroyWindow(a);GLFW.glfwTerminate();
  var p=Path.of(System.getProperty("vmcompat.resultDir"));Files.createDirectories(p);Files.writeString(p.resolve("timestamp-tests.json"),"{\"passed\":true,\"timestamp_counter_bits\":0,\"native_create\":0,\"native_issue\":0,\"native_poll\":0,\"native_result\":0,\"cpu_history_preserved\":true,\"occlusion_preserved\":true,\"supported_protocol_simulated\":true,\"supported_hardware_tested\":false}\n");System.out.println("TIMESTAMP TESTS PASS");
 }
}
