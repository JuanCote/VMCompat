package vmcompat;
import java.lang.foreign.*;import java.lang.invoke.MethodHandle;import java.nio.file.*;

/** Private diagnostic: retain native/JNI fatal output otherwise sent to /dev/null.
 * Uses the game's already-enabled Java native access; no installed library or JVM flag.
 */
public final class NativeTrace {
    private static MethodHandle DUP,DUP2,CLOSE,TRUNCATE;
    private static int output=-1,savedOut=-1,savedErr=-1;
    private static boolean started,tickFailed;
    private static Path path;
    static synchronized boolean start(){
        if(started)return output>=0;started=true;
        if(!Boolean.parseBoolean(Options.value("nativeCrashTrace",Boolean.getBoolean("vmcompat.testMode")?"false":"true")))return false;
        try{
            Files.createDirectories(Log.ROOT);path=Log.ROOT.resolve("native-console.log");
            if(Files.isSymbolicLink(path))throw new IllegalStateException("Native trace target is a symlink");
            if(Files.exists(path))Files.move(path,Log.ROOT.resolve("native-console.previous.log"),StandardCopyOption.REPLACE_EXISTING);
            Linker linker=Linker.nativeLinker();SymbolLookup libc=linker.defaultLookup();
            DUP=linker.downcallHandle(libc.find("dup").orElseThrow(),FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
            DUP2=linker.downcallHandle(libc.find("dup2").orElseThrow(),FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
            CLOSE=linker.downcallHandle(libc.find("close").orElseThrow(),FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
            TRUNCATE=linker.downcallHandle(libc.find("ftruncate").orElseThrow(),FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
            var open=linker.downcallHandle(libc.find("open").orElseThrow(),FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT),Linker.Option.firstVariadicArg(2));
            try(Arena arena=Arena.ofConfined()){
                // Darwin O_WRONLY | O_CREAT | O_TRUNC | O_APPEND, owner-only new file.
                output=(int)open.invokeExact(arena.allocateFrom(path.toString()),1|0x0200|0x0400|0x0008,0600);
            }
            if(output<0)throw new IllegalStateException("Cannot open native trace");
            savedOut=(int)DUP.invokeExact(1);savedErr=(int)DUP.invokeExact(2);
            if(savedOut<0||savedErr<0)throw new IllegalStateException("Cannot preserve native output descriptors");
            if((int)DUP2.invokeExact(output,1)<0||(int)DUP2.invokeExact(output,2)<0)throw new IllegalStateException("Cannot capture native output");
            Runtime.getRuntime().addShutdownHook(new Thread(NativeTrace::restore,"VMCompat native output restore"));
            Log.info("Native crash trace active: native-console.log / pid="+ProcessHandle.current().pid());return true;
        }catch(Throwable e){restore();Log.failure("Native crash trace unavailable",e);return false;}
    }
    static synchronized void tick(){
        if(output<0||tickFailed)return;
        try{if(Files.size(path)>4L*1024*1024){int rc=(int)TRUNCATE.invokeExact(output,0L);if(rc!=0)throw new IllegalStateException("Cannot trim native trace");}}
        catch(Throwable e){tickFailed=true;Log.failure("Native trace trimming stopped",e);}
    }
    static synchronized void restore(){
        try{if(savedOut>=0){int ignored=(int)DUP2.invokeExact(savedOut,1);}}catch(Throwable ignored){}
        try{if(savedErr>=0){int ignored=(int)DUP2.invokeExact(savedErr,2);}}catch(Throwable ignored){}
        for(int fd:new int[]{output,savedOut,savedErr})if(fd>=0)try{int ignored=(int)CLOSE.invokeExact(fd);}catch(Throwable ignored){}
        output=savedOut=savedErr=-1;
    }
    private NativeTrace(){}
}
