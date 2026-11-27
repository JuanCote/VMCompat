package vmcompat;
import java.nio.file.*;
import java.util.concurrent.ArrayBlockingQueue;
/** Disk writes happen only when drained by the existing telemetry worker. */
public final class Log {
    public static final Path ROOT=Path.of(System.getProperty("vmcompat.root",Path.of(System.getProperty("user.home"),"Zomboid","vmcompat-workshop").toString()));
    private static final ArrayBlockingQueue<String> QUEUE=new ArrayBlockingQueue<>(128);
    private static final ArrayBlockingQueue<Runnable> TASKS=new ArrayBlockingQueue<>(4);
    static boolean background(Runnable task){return TASKS.offer(task);}
    public static void info(String text){String line="[VMCompat "+Agent.VERSION+"] "+text;System.out.println(line);QUEUE.offer(line.substring(0,Math.min(8192,line.length())));}
    public static void failure(String text,Throwable error){info(text+": "+error);}
    static void flush(){try{Files.createDirectories(ROOT);Path p=ROOT.resolve("runtime.log");if(Files.exists(p)&&Files.size(p)>1024*1024)Files.move(p,ROOT.resolve("runtime.previous.log"),StandardCopyOption.REPLACE_EXISTING);String line;while((line=QUEUE.poll())!=null)Files.writeString(p,line+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);Runnable task;while((task=TASKS.poll())!=null)task.run();}catch(Exception e){System.err.println("[VMCompat] Log writer failed: "+e);}}
}
