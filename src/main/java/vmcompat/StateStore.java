package vmcompat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
/** Context generations prevent stale state when native window handles are reused. */
public final class StateStore<T>{
    private static final List<StateStore<?>> STORES=new ArrayList<>();
    private final ConcurrentHashMap<ContextResources.Identity,T> contexts=new ConcurrentHashMap<>();
    private final Supplier<T> factory;
    public StateStore(Supplier<T> factory){this.factory=factory;synchronized(STORES){STORES.add(this);}}
    private final class Local {ContextResources.Identity id;T value;}
    private final ThreadLocal<Local> local=ThreadLocal.withInitial(Local::new);
    public T get(){ContextResources.Identity id=ContextResources.current();if(Options.CONTEXT_FAST){Local hit=local.get();if(hit.id==id)return hit.value;hit.id=id;return hit.value=contexts.computeIfAbsent(id,k->factory.get());}return contexts.computeIfAbsent(id,k->factory.get());}
    static void release(ContextResources.Identity id){synchronized(STORES){for(StateStore<?> store:STORES)store.contexts.remove(id);}}
}
