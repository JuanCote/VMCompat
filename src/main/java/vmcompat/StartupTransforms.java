package vmcompat;
import java.lang.instrument.*;
import java.util.*;

/** Actual callback coverage, separate from resource-only preflight rewrites. */
public final class StartupTransforms {
    private static final Map<ClassLoader,Set<String>> APPLIED=new WeakHashMap<>();
    public static synchronized void applied(ClassLoader loader,String name){APPLIED.computeIfAbsent(loader,k->new HashSet<>()).add(name.replace('/','.'));}
    public static synchronized boolean applied(Class<?> type){return APPLIED.getOrDefault(type.getClassLoader(),Set.of()).contains(type.getName());}
    public static int repair(Instrumentation inst)throws UnmodifiableClassException{
        int repaired=0;
        for(int pass=0;pass<4;pass++){
            var missing=Arrays.stream(inst.getAllLoadedClasses()).filter(WorkshopBootstrap::namedTarget).filter(c->!applied(c)).toArray(Class<?>[]::new);
            if(missing.length==0)return repaired;
            for(Class<?> c:missing)if(!inst.isModifiableClass(c))throw new UnmodifiableClassException(c.getName());
            Log.info("Startup transform gap pass "+pass+": "+Arrays.stream(missing).limit(24).map(Class::getName).toList()+" / total="+missing.length);
            inst.retransformClasses(missing);repaired+=missing.length;
        }
        throw new IllegalStateException("Unresolved recursive class-loading gaps; restart required");
    }
    private StartupTransforms(){}
}
