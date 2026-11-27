package vmcompat;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.lwjgl.glfw.GLFW;

/** CPU wall intervals only: no GL queries, readbacks or waits are added for profiling. */
public final class Telemetry {
    private static final boolean FULL=Options.TELEMETRY.equals("full"),OFF=Options.TELEMETRY.equals("off");
    private static final ArrayBlockingQueue<Row> QUEUE=new ArrayBlockingQueue<>(4096);
    private static final long[] INTERVALS=new long[2048];
    private static final Object LOCK=new Object();
    private static long count,lastNano;
    private static volatile long dropped;
    private static volatile long frameAt;
    private static volatile boolean running;
    private static Thread worker;
    private static volatile Path recording;
    private static final ThreadLocal<State> STATES=ThreadLocal.withInitial(State::new);
    private static final class State{
        double[] passes;int draws,meshDraws;long snapshotId,snapshotCount;
        long readNs,readBytes,reads,rangeNs,rangeBytes,rangeCopies,indirectRows,issued;
    }
    private record Row(long frame,long epoch,long interval,long swapNs,double[] passes,int draws,int meshDraws,
                       long readNs,long readBytes,long reads,long rangeNs,long rangeBytes,long rangeCopies,long indirectRows,long issued,long[] counters,long snapshotId,long snapshotCount){}
    public record Stats(long total,int count,double window,double fps,double mean,double median,double p95,double p99,double peak,double last,int over50,long age,long dropped){}
    public static synchronized void start(){
        if(worker!=null)return;
        running=true;worker=Thread.ofPlatform().daemon(true).name("VMCompat telemetry").start(Telemetry::work);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{running=false;try{worker.join(2000);}catch(InterruptedException e){Thread.currentThread().interrupt();}},"VMCompat telemetry shutdown"));
    }
    public static long displayId(){synchronized(LOCK){return count;}}
    public static long snapshotId(){return STATES.get().snapshotId;}
    public static void world(double[] cpuMs,int draws,int meshes){if(!FULL)return;State s=STATES.get();s.snapshotId++;s.snapshotCount++;Picking.snapshot(s.snapshotId);s.passes=cpuMs.clone();s.draws=draws;s.meshDraws=meshes;}
    static void read(long ns,long bytes){if(!FULL)return;State s=STATES.get();s.readNs+=ns;s.readBytes+=bytes;s.reads++;}
    static void ranges(long ns,long bytes,long copies){if(!FULL)return;State s=STATES.get();s.rangeNs+=ns;s.rangeBytes+=bytes;s.rangeCopies+=copies;}
    static void indirect(int rows,int issued){if(!FULL)return;State s=STATES.get();s.indirectRows+=rows;s.issued+=issued;}
    public static void swap(long window){
        if(OFF){HangTrace.step("swap-buffers");GLFW.glfwSwapBuffers(window);HangTrace.step("idle");StartupUiProbe.afterSwap(window);return;}
        long token=Breakdown.open(Breakdown.Scope.SWAP.ordinal()),started=System.nanoTime();try{HangTrace.step("swap-buffers");GLFW.glfwSwapBuffers(window);HangTrace.step("idle");StartupUiProbe.afterSwap(window);}catch(RuntimeException|Error e){Breakdown.exceptional(token);throw e;}long end=System.nanoTime(),cpuEnd=Breakdown.ENABLED?Breakdown.cpu():-1;
        Breakdown.closeAt(token,end,false);long overhead=Breakdown.profiler(end);int selection=Breakdown.primary(window);boolean primary=selection>0;
        if(selection==2){synchronized(LOCK){lastNano=0;}}
        if(primary)frame(end,System.currentTimeMillis(),end-started);
        Breakdown.boundary(window,end,cpuEnd,primary?displayId():-1,primary);Breakdown.close(overhead);
    }
    /** Public to exercise the same recorder using deterministic intervals in tests. */
    public static void frame(long now,long epoch,long swapNs){
        if(OFF)return;
        State s=STATES.get();long dt,id;
        synchronized(LOCK){dt=lastNano==0?0:Math.max(0,now-lastNano);lastNano=now;frameAt=now;if(dt>0)INTERVALS[(int)(count++%INTERVALS.length)]=dt;id=count;}
        if(FULL&&!ReleaseLogs.capped&&dt>0&&!QUEUE.offer(new Row(id,epoch,dt,swapNs,s.passes,s.draws,s.meshDraws,s.readNs,s.readBytes,s.reads,s.rangeNs,s.rangeBytes,s.rangeCopies,s.indirectRows,s.issued,Counters.take(),s.snapshotId,s.snapshotCount)))dropped++;
        s.snapshotCount=0;s.passes=null;s.draws=s.meshDraws=0;s.readNs=s.readBytes=s.reads=s.rangeNs=s.rangeBytes=s.rangeCopies=s.indirectRows=s.issued=0;
    }
    public static Stats snapshot(long now){
        long[] values=new long[INTERVALS.length];int n=0,over=0;long sum=0,total,at;
        synchronized(LOCK){total=count;at=frameAt;for(int age=0;age<Math.min(count,values.length);age++){long v=INTERVALS[(int)((count-1-age)%INTERVALS.length)];values[n++]=v;sum+=v;if(v>50_000_000)over++;if(sum>=2_000_000_000L)break;}}
        if(n==0)return new Stats(total,0,0,0,0,0,0,0,0,0,0,0,dropped);
        double last=values[0]/1e6;Arrays.sort(values,0,n);
        return new Stats(total,n,sum/1e6,n*1e9/sum,sum/1e6/n,values[(n-1)/2]/1e6,values[(int)Math.ceil(n*.95)-1]/1e6,values[(int)Math.ceil(n*.99)-1]/1e6,values[n-1]/1e6,last,over,Math.max(0,(now-at)/1_000_000),dropped);
    }
    private static void publish(Path path,String text)throws IOException{
        Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,text);
        try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
    private static void hud()throws IOException{
        Stats s=snapshot(System.nanoTime());
        String text="1\n"+System.currentTimeMillis()+"\n"+Agent.VERSION+"\n"+s.total+"\n"+s.count+"\n"+s.window+"\n"+s.fps+"\n"+s.mean+"\n"+s.median+"\n"+s.p95+"\n"+s.p99+"\n"+s.peak+"\n"+s.last+"\n"+s.over50+"\n"+(OFF?Opaque.status()+" / diagnostics off":Opaque.status()+(ReleaseLogs.capped?" / recording capped":""))+"\n"+s.age+"\n"+s.dropped+"\n";
        publish(Log.ROOT.resolve("performance-hud.txt"),text);
        publish(Path.of(System.getProperty("vmcompat.luaRoot",Log.ROOT.getParent().resolve("Lua").toString())).resolve("VMCompatBackendDetails.txt"),"1\n"+System.currentTimeMillis()+"\n"+(Opaque.detail()+" | "+TimestampQueries.status()).replace('\r',' ').replace('\n',' ')+"\n");
        publish(Path.of(System.getProperty("vmcompat.luaRoot",Log.ROOT.getParent().resolve("Lua").toString())).resolve("VMCompatPerformance.txt"),text);
    }
    private static void work(){
        try{
            Files.createDirectories(Log.ROOT);ReleaseLogs.trim();
            if(!FULL){while(running){hud();ReleaseLogs.tick();Thread.sleep(1000);}hud();return;}
            recording=Log.ROOT.resolve("frames-"+System.currentTimeMillis()+".csv");
            Metadata.write(recording.resolveSibling(recording.getFileName()+".metadata.json"));
            try(Breakdown.Sink diagnostic=Breakdown.sink(recording);BufferedWriter out=Files.newBufferedWriter(recording)){
                out.write("frame,epoch_ms,interval_ns,swap_ns,viewpoint_active,draws,mesh_draws,read_ns,read_bytes,reads,range_ns,range_bytes,range_copies,indirect_rows,indirect_issued,floors_ms,shadow_ms,gbuffer_ms,occlusion_ms,lighting_ms,far_ms,outlines_ms,glass_ms,weather_ms,air_ms,bloom_ms,post_ms"+Counters.columns()+",viewpoint_snapshot_id,viewpoint_snapshots_in_interval\n");
                long next=0;
                while(running||!QUEUE.isEmpty()||Breakdown.pending()){
                    Row r=QUEUE.poll(100,TimeUnit.MILLISECONDS);
                    if(r!=null&&!ReleaseLogs.capped){StringBuilder line=new StringBuilder(256);line.append(r.frame).append(',').append(r.epoch).append(',').append(r.interval).append(',').append(r.swapNs).append(',').append(r.passes==null?0:1).append(',').append(r.draws).append(',').append(r.meshDraws).append(',').append(r.readNs).append(',').append(r.readBytes).append(',').append(r.reads).append(',').append(r.rangeNs).append(',').append(r.rangeBytes).append(',').append(r.rangeCopies).append(',').append(r.indirectRows).append(',').append(r.issued);for(int i=0;i<12;i++)line.append(',').append(r.passes!=null&&i<r.passes.length?r.passes[i]:Double.NaN);for(long value:r.counters)line.append(',').append(value);line.append(',').append(r.snapshotId).append(',').append(r.snapshotCount);out.write(line.append('\n').toString());}
                    diagnostic.drain();
                    long now=System.nanoTime();if(now>=next){hud();ReleaseLogs.tick();FarStats.write(recording.resolveSibling(recording.getFileName()+".far.json"));CommandMisses.write(recording.resolveSibling(recording.getFileName()+".misses.json"));RangeUpdates.write(recording.resolveSibling(recording.getFileName()+".ranges.json"));Picking.write(recording.resolveSibling(recording.getFileName()+".picking.json"));Opaque.write(recording.resolveSibling(recording.getFileName()+".opaque.json"));out.flush();next=now+1_000_000_000L;}
                }
            }
            hud();if(recording!=null){FarStats.write(recording.resolveSibling(recording.getFileName()+".far.json"));CommandMisses.write(recording.resolveSibling(recording.getFileName()+".misses.json"));RangeUpdates.write(recording.resolveSibling(recording.getFileName()+".ranges.json"));Picking.write(recording.resolveSibling(recording.getFileName()+".picking.json"));Opaque.write(recording.resolveSibling(recording.getFileName()+".opaque.json"));}
        }catch(Exception error){Log.failure("Frame telemetry stopped",error);}
    }
    public static Path recording(){return recording;}
    public static void stopForTest()throws InterruptedException{running=false;if(worker!=null)worker.join(3000);}
}
