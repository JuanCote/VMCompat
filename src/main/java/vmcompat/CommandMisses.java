package vmcompat;
import java.nio.file.*;
import java.util.*;
/** Bounded heavy hitters. No per-read console/file output; counts use Space-Saving estimates. */
final class CommandMisses {
    private static final int LIMIT=32;
    private static final Map<Long,Entry> ENTRIES=new HashMap<>();
    private static final class Entry {int id;long generation,count,error,size,offset;int length;String writer,reason;}
    static synchronized void record(int id,long gen,long size,long offset,int length,String writer,String reason){
        if(!Counters.ENABLED)return;
        Entry e=ENTRIES.get(gen);if(e==null){long count=0;if(ENTRIES.size()>=LIMIT){Entry lowest=ENTRIES.values().stream().min(Comparator.comparingLong(x->x.count)).orElseThrow();count=lowest.count;ENTRIES.remove(lowest.generation);}e=new Entry();e.id=id;e.generation=gen;e.error=count;e.count=count;ENTRIES.put(gen,e);}
        e.count++;e.size=size;e.offset=offset;e.length=length;e.writer=writer;e.reason=reason;
    }
    static void write(Path path)throws Exception{
        String payload; synchronized(CommandMisses.class){StringJoiner rows=new StringJoiner(",");for(Entry e:ENTRIES.values().stream().sorted(Comparator.comparingLong((Entry x)->x.count).reversed()).toList())rows.add("{\"id\":"+e.id+",\"generation\":"+e.generation+",\"estimated_misses\":"+e.count+",\"error_bound\":"+e.error+",\"size\":"+e.size+",\"last_offset\":"+e.offset+",\"last_length\":"+e.length+",\"last_writer\":\""+e.writer+"\",\"last_reason\":\""+e.reason+"\"}");
        payload="{\"limit\":32,\"algorithm\":\"space-saving; estimated_misses minus error_bound is a lower bound\",\"buffers\":["+rows+"]}\n";}
        Path tmp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(tmp,payload);try{Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
}
