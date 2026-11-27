package vmcompat;
import java.nio.file.*;
import java.util.*;
/** Bounded cost-heavy sources; approximate cumulative CPU wall, never individual upload logging. */
final class RangeUpdates {
    private static final class Entry {int id;long generation,size,offset,length,version,count,nanos,error;String origin;}
    private static final Map<Long,Entry> ENTRIES=new HashMap<>();
    static synchronized void record(Buffers.ObjectState o,long offset,long size,Counters.Key origin,long ns){
        if(!Counters.ENABLED)return;Entry e=ENTRIES.get(o.generation);
        if(e==null){long cost=0;if(ENTRIES.size()>=32){Entry low=ENTRIES.values().stream().min(Comparator.comparingLong(x->x.nanos)).orElseThrow();cost=low.nanos;ENTRIES.remove(low.generation);}e=new Entry();e.id=o.name;e.generation=o.generation;e.nanos=e.error=cost;ENTRIES.put(o.generation,e);}
        e.size=o.size;e.offset=offset;e.length=size;e.version=o.version;e.count++;e.nanos+=ns;e.origin=origin.name();
    }
    static void write(Path path)throws Exception{
        String payload;synchronized(RangeUpdates.class){StringJoiner rows=new StringJoiner(",");for(Entry e:ENTRIES.values().stream().sorted(Comparator.comparingLong((Entry x)->x.nanos).reversed()).toList())rows.add("{\"id\":"+e.id+",\"generation\":"+e.generation+",\"source_size\":"+e.size+",\"offset\":"+e.offset+",\"range_size\":"+e.length+",\"version\":"+e.version+",\"observed_updates\":"+e.count+",\"estimated_ns\":"+e.nanos+",\"error_bound_ns\":"+e.error+",\"origin\":\""+e.origin+"\"}");payload="{\"limit\":32,\"scope\":\"range updates CPU wall incl guard; Space-Saving cost estimates\",\"resources\":["+rows+"]}\n";}
        Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,payload);try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
}
