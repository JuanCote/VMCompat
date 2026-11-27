package vmcompat;
import java.util.*;import java.util.concurrent.atomic.*;import java.nio.file.*;import java.io.*;
/** Bounded aggregate counters, serialized only by the existing background writer. */
public final class FarStats {
 enum K {MASK_UPLOADS,MASK_SKIPS,MASK_BYTES,MASK_NS,ELIGIBLE,BATCHED,FALLBACK,ISSUE_FALLBACKS,REJECTED_PAGES,COMMANDS,NATIVE_BATCHES,OWNER_HITS,OWNER_NEW,VERTICES,LOOKUP_BYTES,PREPARE_NS,UPLOAD_NS,SUBMIT_NS}
 static final LongAdder[] C=Arrays.stream(K.values()).map(x->new LongAdder()).toArray(LongAdder[]::new);
 static final Map<String,LongAdder> reasons=new java.util.concurrent.ConcurrentHashMap<>();
 static volatile long ownerBytes,auxBytes,ownerPeak,auxPeak;
 static void add(K k,long n){if(Counters.ENABLED)C[k.ordinal()].add(n);}
 static void issueFallback(String reason,int spanInts){add(K.ISSUE_FALLBACKS,1);fallback(reason,Math.max(0,spanInts/5));}
 static void fallback(String reason){fallback(reason,1);}
 static void fallback(String reason,long count){add(K.FALLBACK,count);if(Counters.ENABLED)reasons.computeIfAbsent(reason,k->new LongAdder()).add(count);}
 static void memory(long owner,long aux){ownerBytes=owner;auxBytes=aux;ownerPeak=Math.max(ownerPeak,owner);auxPeak=Math.max(auxPeak,aux);}
 static Map<String,Object> snapshot(){var m=new LinkedHashMap<String,Object>();m.put("schema",1);m.put("jar_version",Agent.VERSION);for(K k:K.values())m.put(k.name().toLowerCase(Locale.ROOT),C[k.ordinal()].sum());var r=new TreeMap<String,Long>();reasons.forEach((k,v)->r.put(k,v.sum()));m.put("fallback_reasons",r);m.put("owner_gpu_bytes",ownerBytes);m.put("owner_gpu_peak",ownerPeak);m.put("auxiliary_cpu_gpu_bytes",auxBytes);m.put("auxiliary_peak",auxPeak);m.put("timing","CPU wall; nested within original passes; do not add to frame time");return m;}
 static void write(Path path)throws IOException{Files.writeString(path,Breakdown.json(snapshot())+"\n");}
}
