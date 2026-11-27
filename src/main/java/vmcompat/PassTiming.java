package vmcompat;
import org.lwjgl.opengl.GL32;
/** CPU wait scope only, not GPU timing. Poll is nested in MousePick.collect. */
public final class PassTiming {
    private static final ThreadLocal<long[]> RESOLVE=ThreadLocal.withInitial(()->new long[2]);
    public static void resetResolve(){long[] s=RESOLVE.get();if(s[0]!=0)scope((int)s[1]);s[0]=0;}
    public static void beginResolve(){RESOLVE.get()[1]=scope(7);RESOLVE.get()[0]=Counters.now();}
    public static void endResolve(){long at=RESOLVE.get()[0];if(at!=0)Counters.elapsed(Counters.Key.GLASS_RESOLVE_NS,at);scope((int)RESOLVE.get()[1]);RESOLVE.get()[0]=0;}
    private static final ThreadLocal<int[]> SCOPE=ThreadLocal.withInitial(()->new int[1]);
    private static final Counters.Key[] SUBMIT={null,Counters.Key.GL_SURFACE_SUBMIT_NS,Counters.Key.GL_MODEL_SUBMIT_NS,Counters.Key.GL_FAR_BAND_SUBMIT_NS,Counters.Key.GL_GLASS_SURFACE_SUBMIT_NS,Counters.Key.GL_GLASS_MODEL_SUBMIT_NS,Counters.Key.GL_FLAME_SUBMIT_NS,Counters.Key.GL_RESOLVE_SUBMIT_NS,Counters.Key.GL_FAR_SUBMIT_NS};
    private static final Counters.Key[] DRAWS={null,Counters.Key.GL_SURFACE_DRAWS,Counters.Key.GL_MODEL_DRAWS,Counters.Key.GL_FAR_BAND_DRAWS,Counters.Key.GL_GLASS_SURFACE_DRAWS,Counters.Key.GL_GLASS_MODEL_DRAWS,Counters.Key.GL_FLAME_DRAWS,Counters.Key.GL_RESOLVE_DRAWS,Counters.Key.GL_FAR_DRAWS};
    public static int scope(int value){if(!Counters.ENABLED)return 0;int[] s=SCOPE.get();int old=s[0];s[0]=value;return old;}
    public static void submit(long started){if(Counters.ENABLED){Counters.Key k=SUBMIT[SCOPE.get()[0]];if(k!=null)Counters.elapsed(k,started);}}
    public static void nativeDraw(){if(Counters.ENABLED){Counters.Key k=DRAWS[SCOPE.get()[0]];if(k!=null)Counters.add(k,1);}}
    public static long collected(){return Counters.value(Counters.Key.PICK_COLLECT_NS);}
    public static void finishRead(long at,long collectedBefore){if(!Counters.ENABLED)return;long total=System.nanoTime()-at;Counters.add(Counters.Key.PICK_READ_NS,total);Counters.add(Counters.Key.PICK_ENQUEUE_NS,Math.max(0,total-(collected()-collectedBefore)));}
    public static int poll(long fence,int flags,long timeout){long at=Counters.now();try{return GL32.glClientWaitSync(fence,flags,timeout);}finally{Counters.elapsed(Counters.Key.PICK_POLL_NS,at);}}
}
