package vmcompat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
/** Shared resources use share-group identity; vertex/sampler state uses context lifetime identity. */
public final class ContextResources {
    static final AtomicLong SERIAL=new AtomicLong();
    public static final class Group {final Buffers.Shared buffers=new Buffers.Shared();final Map<Integer,Long> programs=new HashMap<>();boolean trusted;volatile boolean everShared;int contexts;Group(boolean trusted){this.trusted=trusted;}}
    public record Identity(long handle,long generation,Group group){}
    private static volatile boolean multipleSeen;
    static boolean opaqueExclusive(){return !multipleSeen;}
    private static final Map<Long,Identity> CONTEXTS=new HashMap<>();
    static synchronized void created(long handle,long share){if(handle==0)return;Group group=share==0?new Group(true):identity(share).group();group.contexts++;if(share!=0)group.everShared=true;CONTEXTS.put(handle,new Identity(handle,SERIAL.incrementAndGet(),group));if(CONTEXTS.size()>1)multipleSeen=true;}
    private static Identity identity(long handle){Identity id=CONTEXTS.get(handle);if(id==null){Group g=new Group(false);g.contexts=1;id=new Identity(handle,SERIAL.incrementAndGet(),g);CONTEXTS.put(handle,id);if(CONTEXTS.size()>1)multipleSeen=true;}return id;}
    private static final AtomicLong EPOCH=new AtomicLong();
    private static final class Owner {Identity id;long epoch;Object capabilities;boolean tracked;}
    private static final ThreadLocal<Owner> OWNER=ThreadLocal.withInitial(Owner::new);
    /** Called only after successful managed make-current; foreign lifecycle remains authoritative. */
    static synchronized void madeCurrent(long handle){
        Owner o=OWNER.get();o.id=handle==0?null:identity(handle);o.epoch=EPOCH.incrementAndGet();o.capabilities=null;o.tracked=handle!=0&&o.id.group.trusted;
    }
    public static Identity current(){
        Owner o=OWNER.get();Object caps=GL.getCapabilities();
        if(Options.CONTEXT_FAST&&o.tracked&&o.id!=null&&o.epoch==EPOCH.get()&&o.capabilities==caps){Counters.add(Counters.Key.CONTEXT_FAST_HITS,1);return o.id;}
        Counters.add(Counters.Key.CONTEXT_AUTHORITATIVE,1);
        synchronized(ContextResources.class){long handle=GLFW.glfwGetCurrentContext();if(handle==0)throw new IllegalStateException("No current GL context");Identity id=identity(handle);
            // Only lifecycle explicitly claimed on this thread may take the shortcut.
            if(o.tracked&&o.id==id){o.epoch=EPOCH.get();o.capabilities=caps;}else{o.tracked=false;o.id=id;}
            return id;}
    }
    public static void externalLifecycle(){Owner o=OWNER.get();o.tracked=false;EPOCH.incrementAndGet();}
    static Group group(){return current().group();}
    static synchronized void destroyed(long handle){EPOCH.incrementAndGet();Identity id=CONTEXTS.remove(handle);if(id!=null){id.group.contexts--;StateStore.release(id);if(id.group.contexts==0){id.group.buffers.clear();id.group.programs.clear();}}}
    static long programRevision(int id){Group g=group();synchronized(g){return g.programs.computeIfAbsent(id,k->SERIAL.incrementAndGet());}}
    static void programChanged(int id){Group g=group();synchronized(g){g.programs.put(id,SERIAL.incrementAndGet());}}
}
