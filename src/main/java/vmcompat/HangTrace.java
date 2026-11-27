package vmcompat;
import java.nio.file.*;
/** One bounded progress snapshot, written only by the existing background writer.
 * No GL queries, stack sampling or file writes on the render thread.
 */
public final class HangTrace {
 static final boolean ENABLED=!Options.TELEMETRY.equals("off")&&Boolean.parseBoolean(Options.value("hangTrace",Agent.VERSION.contains("-local")?"true":"false"));
 static volatile String stage="idle";static volatile long since;static volatile Thread thread;static volatile int program,mode,count,instances,texture,width,height;static volatile long bytes;
 public static void step(String next){if(!ENABLED)return;thread=Thread.currentThread();stage=next;since=System.nanoTime();}
 public static void program(int value){if(ENABLED)program=value;}
 public static void draw(int m,int n,int copies){if(!ENABLED)return;mode=m;count=n;instances=copies;step("native-draw");}
 public static void table(long value){if(ENABLED)bytes=value;step("packing-storage");}
 public static void image(int id,int w,int h,boolean depth){if(!ENABLED)return;texture=id;width=w;height=h;step(depth?"packing-depth-read":"packing-image-read");}
 public static void flush(){if(!ENABLED)return;try{String phase=stage;long at=since,age=at==0?0:Math.max(0,System.nanoTime()-at);Thread owner=thread;StringBuilder out=new StringBuilder("version="+Agent.VERSION+"\nstage="+phase+"\nstage_age_ms="+age/1e6+"\nthread="+(owner==null?"unavailable":owner.getName())+"\nprogram="+program+"\nmode="+mode+"\ncount="+count+"\ninstances="+instances+"\npacking_bytes="+bytes+"\ntexture="+texture+"\nsize="+width+"x"+height+"\n");if(owner!=null&&age>500_000_000L&&(!phase.equals("idle")||Telemetry.snapshot(System.nanoTime()).age()>1000)){out.append("java_stack_at_background_sample:\n");var trace=owner.getStackTrace();for(int i=0;i<Math.min(24,trace.length);i++)out.append("  ").append(trace[i]).append('\n');}Path target=Log.ROOT.resolve("render-progress.txt"),tmp=target.resolveSibling("render-progress.tmp");Files.writeString(tmp,out);Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}catch(Throwable e){Log.failure("Render progress diagnostic",e);}}
 private HangTrace(){}
}
