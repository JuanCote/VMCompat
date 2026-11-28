package vmcompat;
import java.nio.file.*;
import java.util.*;

/** Invoked only from the existing background telemetry writer. Never deletes outside its own log directory. */
public final class ReleaseLogs {
    static volatile boolean capped;
    private static final long START=System.nanoTime();
    private static boolean reported;
    public static void tick() {
        Log.flush();HangTrace.flush();NativeTrace.tick();
        try{long[] m=Packing.memoryStats(),w=WordStorage.diagnostics(),c=CompressedCopies.diagnostics();long heap=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();Files.createDirectories(Log.ROOT);Files.writeString(Log.ROOT.resolve("packing-memory.txt"),"version="+Agent.VERSION+"\nreserved_gpu_buffer_bytes="+m[0]+"\npeak_reserved_bytes="+m[1]+"\nallocations="+m[2]+"\ncopied_bytes="+m[3]+"\ncopied_images="+m[4]+"\nreused_images="+m[5]+"\nevictions="+m[6]+"\nrebuilds="+m[7]+"\nnative_word_copy_bytes="+w[0]+"\nnative_word_copies="+w[1]+"\nnative_mask_copy_bytes="+w[2]+"\nnative_mask_copies="+w[3]+"\ncompressed_source_imports="+c[0]+"\ncompressed_source_bytes="+c[1]+"\ncompressed_copy_calls="+c[2]+"\ncompressed_cpu_cache_bytes="+c[4]+"\ncompressed_copy_bytes="+c[3]+"\njava_heap_used="+heap+"\nfailure="+Packing.failure()+"\n");}catch(Exception e){System.err.println("[VMCompat] packing memory report: "+e);}
        if(!reported)try{
            Files.createDirectories(Log.ROOT);
            Files.writeString(Log.ROOT.resolve("error-report.txt"),"VMCompat "+Agent.VERSION+"\nJAR SHA256: "+Metadata.artifactSha()+"\nJava: "+Runtime.version()+"\nOS: "+System.getProperty("os.name")+" / "+System.getProperty("os.arch")+"\nOptions: "+Options.describe()+"\nBackend: "+Opaque.status()+"\nNo game/world files or automatic uploads are included. Attach runtime.log and a description of the issue.\n");
            reported=true;
        }catch(Exception e){System.err.println("[VMCompat] Error report: "+e);}
        Path current=Telemetry.recording();
        if(current!=null&&!capped)try(var files=Files.list(Log.ROOT)) {
            long size=0;for(Path p:files.filter(p->p.getFileName().toString().startsWith(current.getFileName().toString())).toList())size+=Files.size(p);
            long opened=Options.RECORD_FROM_VIEWPOINT?Telemetry.armedAt:START;
            if(size>=32L*1024*1024||opened!=0&&System.nanoTime()-opened>120_000_000_000L) {capped=true;Log.info("Detailed recording capped (120 seconds / approximately 32 MiB). HUD continues; restart to record again.");}
        } catch(Exception e){System.err.println("[VMCompat] Recording quota: "+e);}
    }
    public static void trim() {
        try {
            Path root=Log.ROOT;if(!Files.isDirectory(root))return;
            List<Path> files;
            try(var stream=Files.list(root)) {files=stream.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                .filter(p->p.getFileName().toString().startsWith("frames-")||p.getFileName().toString().equals("runtime.log"))
                .sorted(Comparator.comparingLong(ReleaseLogs::modified).reversed()).toList();}
            long total=0;long age=System.currentTimeMillis()-7L*86400000;
            for(Path p:files) {
                total+=Files.size(p);
                if(modified(p)<age||total>64L*1024*1024) {
                    // Do not delete the current session. The writer stops a full recording at its cap.
                    Path current=Telemetry.recording();if(current!=null&&p.getFileName().toString().startsWith(current.getFileName().toString()))continue;
                    Files.deleteIfExists(p);
                }
            }
        } catch(Exception e) {System.err.println("[VMCompat] Log retention: "+e);}
    }
    private static long modified(Path p){try{return Files.getLastModifiedTime(p).toMillis();}catch(Exception e){return Long.MAX_VALUE;}}
}
