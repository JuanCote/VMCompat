package vmcompat;
import java.nio.file.*;import java.util.concurrent.*;
public final class HangTraceTests {
 public static void main(String[]args)throws Exception{
  Files.createDirectories(Log.ROOT);if(!HangTrace.ENABLED){HangTrace.step("disabled");HangTrace.flush();if(Files.exists(Log.ROOT.resolve("render-progress.txt")))throw new AssertionError("disabled trace wrote a file");System.out.println("PASS disabled progress trace");return;}
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);Thread producer=new Thread(()->{HangTrace.program(77);HangTrace.draw(4,300,1);entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}},"fixture render producer");producer.start();entered.await();Thread.sleep(550);HangTrace.flush();String text=Files.readString(Log.ROOT.resolve("render-progress.txt"));if(!text.contains("stage=native-draw")||!text.contains("program=77")||!text.contains("java_stack_at_background_sample:")||text.length()>8192)throw new AssertionError(text);release.countDown();producer.join();
  for(int i=0;i<30000;i++)HangTrace.step("warmup");long at=System.nanoTime();for(int i=0;i<200000;i++){HangTrace.step("measurement");}double ns=(System.nanoTime()-at)/200000.;HangTrace.step("idle");HangTrace.flush();System.out.println("PASS bounded background progress + stalled-thread stack; measured_ns_per_mark="+ns+" (not frame time)");
 }
}
