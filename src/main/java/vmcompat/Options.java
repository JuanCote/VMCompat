package vmcompat;
import java.nio.file.*;
import java.util.*;
/** Immutable per-process comparison switches; JVM properties override the local file. */
public final class Options {
    private static final Properties FILE=new Properties();
    static {Path p=Path.of(System.getProperty("vmcompat.config",Path.of(System.getProperty("user.home"),"Zomboid/vmcompat.properties").toString()));if(Files.isRegularFile(p))try(var in=Files.newInputStream(p)){FILE.load(in);}catch(Exception e){throw new IllegalStateException("Cannot read "+p,e);}}
    private static final Properties DIAGNOSTICS=new Properties();
    static {Path p=Path.of(System.getProperty("user.home"),"Zomboid/Lua/VMCompatDiagnostics.properties");if(Files.isRegularFile(p))try(var in=Files.newInputStream(p)){DIAGNOSTICS.load(in);}catch(Exception e){throw new IllegalStateException("Cannot read diagnostic switch",e);}}
    static String value(String k,String fallback){String diagnostic=Set.of("telemetry","frameBreakdown","pickingDetailed").contains(k)?DIAGNOSTICS.getProperty(k,fallback):fallback;return System.getProperty("vmcompat."+k,FILE.getProperty(k,diagnostic));}
    static boolean flag(String k){String v=value(k,"true");if(!v.equals("true")&&!v.equals("false"))throw new IllegalArgumentException(k+" requires true/false");return Boolean.parseBoolean(v);}
    public static final boolean RANGE_STORAGE=flag("rangeStorageReuse"),RANGES=flag("rangeVersioning"),LOCATIONS=flag("locationCache"),BATCH=flag("batchState"),SCRATCH=flag("indirectScratch"),MIRROR=flag("indirectMirror"),ARRAYS=flag("samplerArrays"),SAMPLERS=flag("samplerInvalidation");
    public static final boolean TEX_BUFFER_BINDINGS=flag("textureBufferBindingCache"),COMMAND_RANGES=flag("commandRangeCache");
    public static final boolean RANGE_CPU=flag("rangeCpuUpload"),INSTANCE_GROUPS=flag("instanceGroups"),CONTEXT_FAST=Boolean.parseBoolean(value("contextFastPath","false"));
    public static final boolean SINGLE_INSTANCE=Boolean.parseBoolean(value("singleInstanceDraw","false"));
    private static boolean farFlag(String key){String v=value(key,key.equals("farMaskCache")?"true":"false");if(!v.equals("true")&&!v.equals("false"))throw new IllegalArgumentException(key+" requires true/false");return Boolean.parseBoolean(v);}
    public static final boolean FAR_MASK=farFlag("farMaskCache"),FAR_BATCH=farFlag("farShellBatch");
    public static final boolean FRAME_BREAKDOWN=Boolean.parseBoolean(value("frameBreakdown","false"));
    /** Start the detailed recording window at the first Viewpoint frame instead of process start, so a long load (hosted or multiplayer) does not use it up. */
    public static final boolean RECORD_FROM_VIEWPOINT=Boolean.parseBoolean(value("recordFromViewpoint","false"));
    public static final int PICK_EVERY=pickEvery();
    private static int pickEvery(){String v=value("pickEvery","3");if(!v.equals("1")&&!v.equals("3"))throw new IllegalArgumentException("pickEvery requires 1 or 3");return Integer.parseInt(v);}
    public static final String OPAQUE=value("opaqueBackend","opengl"),PICKING=value("pickingPath","depthStaging");
    public static final boolean PICK_DETAIL=Boolean.parseBoolean(value("pickingDetailed","false")),PICK_EARLY=Boolean.parseBoolean(value("pickingEarlySubmit","false"));
    static {if(!Set.of("opengl","metalOpaque").contains(OPAQUE)||!Set.of("original","stateReuse","depthStaging","pboRing").contains(PICKING))throw new IllegalArgumentException("Invalid backend/picking path");if(PICKING.equals("stateReuse")||PICKING.equals("pboRing"))throw new IllegalArgumentException("This picking experiment has not qualified; use original/depthStaging");}
    public static final String TELEMETRY=value("telemetry","hud");
    static {if(!Set.of("full","hud","off").contains(TELEMETRY))throw new IllegalArgumentException("telemetry requires full/hud/off");}
    static {if(!OPAQUE.equals("opengl")||PICK_EARLY||FAR_BATCH)throw new IllegalArgumentException("Public build requires OpenGL, early-submit=false, farShellBatch=false");}
    public static Map<String,Object> describe(){Map<String,Object> out=new TreeMap<>();out.put("rangeStorageReuse",RANGE_STORAGE);out.put("rangeVersioning",RANGES);out.put("locationCache",LOCATIONS);out.put("batchState",BATCH);out.put("indirectScratch",SCRATCH);out.put("indirectMirror",MIRROR);out.put("samplerArrays",ARRAYS);out.put("samplerInvalidation",SAMPLERS);out.put("textureBufferBindingCache",TEX_BUFFER_BINDINGS);out.put("commandRangeCache",COMMAND_RANGES);out.put("rangeCpuUpload",RANGE_CPU);out.put("instanceGroups",INSTANCE_GROUPS);out.put("contextFastPath",CONTEXT_FAST);out.put("singleInstanceDraw",SINGLE_INSTANCE);out.put("frameBreakdown",FRAME_BREAKDOWN);out.put("farMaskCache",FAR_MASK);out.put("farShellBatch",FAR_BATCH);out.put("pickEvery",PICK_EVERY);out.put("opaqueBackend",OPAQUE);out.put("pickingPath",PICKING);out.put("pickingDetailed",PICK_DETAIL);out.put("pickingEarlySubmit",PICK_EARLY);out.put("telemetry",TELEMETRY);out.put("nativeBatchSamplers",NativeSamplers.ENABLED);out.put("lightingSamplerFallback",LightingSamplerFallback.ENABLED);out.put("packingArrayOnly",Boolean.parseBoolean(value("packingArrayOnly","true")));out.put("packingParameterTracking",PackedSampling.ENABLED);out.put("packingTableCache",PackedTables.ENABLED);out.put("packingBreakdown",Packing.BREAKDOWN);out.put("hangTrace",HangTrace.ENABLED);out.put("modelSamplerSwitchCollapse",ModelPacking.ENABLED);return Collections.unmodifiableMap(out);}
}
