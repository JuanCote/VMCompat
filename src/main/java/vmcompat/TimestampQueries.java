package vmcompat;
import org.lwjgl.opengl.*;import java.util.concurrent.atomic.LongAdder;
/** Timestamp producers only. Occlusion query calls are never routed here. */
public final class TimestampQueries {
    static final LongAdder creates=new LongAdder(),issues=new LongAdder(),polls=new LongAdder(),results=new LongAdder(),deletes=new LongAdder();
    static volatile String status="GPU timestamp: not sampled";
    private static final StateStore<Boolean> SUPPORTED=new StateStore<>(()->{var c=GL.getCapabilities();boolean api=c.OpenGL33||c.GL_ARB_timer_query;int bits=api?GL15.glGetQueryi(GL33.GL_TIMESTAMP,GL15.GL_QUERY_COUNTER_BITS):0;boolean ok=bits>0;status=ok?"GPU timestamp: available ("+bits+" bits)":"GPU timestamp: unavailable (counter bits="+bits+")";return ok;});
    public static boolean supported(){boolean ok=SUPPORTED.get();if(!ok)status="GPU timestamp: unavailable (counter bits=0)";else status="GPU timestamp: available";return ok;}
    public static Boolean allowed(Boolean capability){return Boolean.TRUE.equals(capability)&&supported();}
    public static String status(){return status;}
    static void failure(RuntimeException e){status="GPU timestamp: unavailable ("+e.getClass().getSimpleName()+")";}
    public static String method(String name,String desc){if(name.endsWith("ARB"))name=name.substring(0,name.length()-3);return switch(name+desc){case "glGenQueries([I)V"->"generate";case "glQueryCounter(II)V"->"counter";case "glGetQueryObjecti(II)I"->"available";case "glGetQueryObjectui64(II)J"->"result";case "glGetQueryObjecti64(II)J"->"signedResult";case "glDeleteQueries([I)V"->"delete";default->null;};}
    public static void generate(int[] ids){if(supported()){creates.add(ids.length);GL15.glGenQueries(ids);}}
    public static void counter(int id,int target){if(target!=GL33.GL_TIMESTAMP)throw new IllegalArgumentException("Timestamp-only adapter");if(supported()){issues.increment();GL33.glQueryCounter(id,target);}}
    public static int available(int id,int parameter){if(!supported())return 0;polls.increment();return GL15.glGetQueryObjecti(id,parameter);}
    public static long result(int id,int parameter){if(!supported())return 0;results.increment();return GL33.glGetQueryObjectui64(id,parameter);}
    public static long signedResult(int id,int parameter){if(!supported())return 0;results.increment();return GL33.glGetQueryObjecti64(id,parameter);}
    public static void delete(int[] ids){if(supported()){deletes.add(ids.length);GL15.glDeleteQueries(ids);}}
}
