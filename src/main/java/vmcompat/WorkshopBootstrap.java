package vmcompat;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/** Pinned ZombieBuddy 2.3.4 preload integration. No agents, files or approval policies are installed here. */
public final class WorkshopBootstrap {
    private static boolean attempted, active;
    private static Instrumentation instrumentation;
    private static boolean audited;
    private static String state = "NOT_STARTED";
    public static String packingFailure(){return Packing.failure();}
    public static synchronized boolean active() { return active; }
    public static synchronized String state() { return state; }
    public static synchronized boolean auditLoading(){
        if(!active||audited)return active;
        try{int count=StartupTransforms.repair(instrumentation);audited=true;Log.info("Menu class-loading audit complete; repaired="+count);return true;}
        catch(Throwable e){notice("FAILED","Class-loading integrity check failed: "+e);active=false;return false;}
    }
    static Path zomboid() { return Path.of(System.getProperty("user.home"), "Zomboid"); }
    public static synchronized void notice(String status, String message) {
        state = status;
        System.err.println("[VMCompat Workshop] " + status + ": " + message);
        try {
            Path p=zomboid().resolve("Lua/VMCompatStartup.txt"); Files.createDirectories(p.getParent());
            Files.writeString(p,"1\n"+status+"\n"+message.replace('\n',' ') + "\n");
        } catch (Exception e) { System.err.println("[VMCompat Workshop] Could not write startup status: " + e); }
    }
    static Set<String> selected(Path path) throws Exception {
        String text=Files.readString(path); Set<String> ids=new HashSet<>();
        var m=Pattern.compile("(?m)^\\s*mod\\s*=\\s*([^,\\r\\n]+)\\s*,?").matcher(text);
        while(m.find()) ids.add(m.group(1).trim()); return ids;
    }
    static String conflict(List<String> args, Properties properties) {
        for(String key:List.of("vmcompat.active","vmperf.active","vpmac.active"))
            if(properties.containsKey(key)) return "Legacy or duplicate agent detected: "+key+". See MIGRATION.md; no files were changed.";
        for(String arg:args) {
            String lower=arg.toLowerCase(Locale.ROOT);
            if((lower.startsWith("-javaagent:")||lower.startsWith("-agentpath:"))
                && (lower.contains("vmcompat")||lower.contains("vmperf")||lower.contains("vpmac")))
                return "Conflicting early agent: "+arg+". Remove only that legacy VMCompat/VMPerf/VPMac option using MIGRATION.md.";
        }
        return null;
    }
    static Properties info(Path file) throws Exception {
        Properties p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}return p;
    }
    static void version(String actual, String expected, String name) {
        if(!expected.equals(actual)) throw new IllegalStateException(name+" "+actual+" is unsupported; requires "+expected);
    }
    static Path self() throws Exception { return Path.of(PreMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath(); }
    static String expectedViewpoint(String gameHash) {
        return switch(gameHash) {
            case "e1a69eb743ede60b213a0fe7f8b83d4fcab773036d256cc4543a336f3b058a33" -> "0.1.6.1";
            case "3c4a7c5f149d784758c6fb5f8fa85f3a21d572e38bc081400e89035f8120d287" -> "0.1.6.2";
            default -> throw new IllegalStateException("Unsupported PZ binary. Compatibility must be checked before activation; supported game builds: 42.21 and 42.22.2.");
        };
    }
    static Path viewpoint(Path self,String expectedVersion) throws Exception {
        List<Path> roots=new ArrayList<>(); roots.add(zomboid().resolve("mods"));
        // Workshop libraries may be on another disk. Derive the library from this mod, not an author's path.
        for(Path p=self.getParent();p!=null;p=p.getParent()) if(p.getFileName()!=null&&p.getFileName().toString().equals("108600")){roots.add(p);break;}
        String cp=System.getProperty("java.class.path","");
        for(String entry:cp.split(Pattern.quote(System.getProperty("path.separator")))) {
            Path p=Path.of(entry).toAbsolutePath();for(Path q=p.getParent();q!=null;q=q.getParent())
                if(q.getFileName()!=null&&q.getFileName().toString().equals("steamapps")){roots.add(q.resolve("workshop/content/108600"));break;}
        }
        Set<Path> candidates=new HashSet<>(),own=new HashSet<>();
        for(Path root:new LinkedHashSet<>(roots)) if(Files.isDirectory(root)) try(var paths=Files.find(root,6,(p,a)->a.isRegularFile()&&p.getFileName().toString().equals("mod.info"))) {
            for(Path p:paths.toList()) {
                Properties props=info(p);String id=props.getProperty("id");
                if("VMCompat".equals(id)) own.add(p.getParent().getParent().toRealPath());
                if("Viewpoint".equals(id)) candidates.add(p.getParent().getParent().toRealPath());
            }
        }
        if(own.size()>1) throw new IllegalStateException("Multiple VMCompat installations found; disable/remove the duplicate using MIGRATION.md");
        if(candidates.size()!=1) throw new IllegalStateException("Expected one Viewpoint installation; found "+candidates.size());
        Path root=candidates.iterator().next();Properties merged=new Properties();
        for(Path p:List.of(root.resolve("common/mod.info"),root.resolve("42/mod.info"))) if(Files.isRegularFile(p)) merged.putAll(info(p));
        Path jar=root.resolve("42/media/java/client/Viewpoint.jar");
        validateViewpoint(expectedVersion,merged.getProperty("modversion"),Opaque.hash(Files.readAllBytes(jar)));
        return jar;
    }
    static void validateViewpoint(String baseline,String actual,String sha) {
        if("0.1.6.2".equals(baseline)&&"0.1.7".equals(actual)) {
            if(!"8d0d5a73227d3ba0724cab076f621435ebaed8ed2c73bfaf7d32a90b0a249e1f".equals(sha))
                throw new IllegalStateException("Viewpoint 0.1.7 binary has changed; this update needs compatibility validation before activation.");
            return;
        }
        version(actual,baseline,"Viewpoint for the installed PZ build (42.22.2 also supports qualified 0.1.7)");
    }
    static boolean target(String name) { return name.equals(ImguiBootstrap.OWNED.replace('/','.'))||name.equals(OldPointCompat.TARGET.replace('/','.'))||name.startsWith("zombie.")||name.startsWith("viewpoint.")||name.startsWith("org.lwjglx.")||name.startsWith("imgui.gl3."); }
    static boolean namedTarget(Class<?> type) {
        // LambdaMetafactory adapters are hidden VM-generated classes, not classfile targets.
        // Their implementation bodies live in the named host class, which remains checked.
        return !type.isHidden()&&!type.isArray()&&!type.isPrimitive()&&target(type.getName());
    }
    static void validateJar(Path jar,ClassFile verifier) throws Exception {
        try(JarFile z=new JarFile(jar.toFile())) {
            var entries=z.entries();while(entries.hasMoreElements()){
                var e=entries.nextElement();if(!e.getName().endsWith(".class")||!target(e.getName().replace('/','.'))) continue;
                byte[] rewritten=Transformer.rewrite(e.getName().substring(0,e.getName().length()-6),z.getInputStream(e).readAllBytes());
                var errors=verifier.verify(rewritten);if(!errors.isEmpty())throw new IllegalStateException("Preflight bytecode failed: "+e.getName()+" "+errors.getFirst());
            }
        }
    }
    public static synchronized void start() {
        if(attempted) return; attempted=true;
        Instrumentation inst=null; Transformer transformer=null; Class<?>[] loaded=null; boolean registered=false;
        try {
            Set<String> ids=selected(zomboid().resolve("mods/default.txt"));
            if(!ids.contains("VMCompat")){notice("DISABLED","VMCompat is disabled in the default mod selection; no transformations installed.");return;}
            if(!ids.containsAll(Set.of("Viewpoint","ZombieBuddy"))) throw new IllegalStateException("Enable Viewpoint and ZombieBuddy before VMCompat");
            String conflict=conflict(ManagementFactory.getRuntimeMXBean().getInputArguments(),System.getProperties());
            if(conflict!=null) throw new IllegalStateException(conflict);
            version(System.getProperty("os.arch"),"aarch64","CPU architecture");
            if(!System.getProperty("os.name").contains("Mac"))throw new IllegalStateException("Only macOS Apple Silicon is qualified");
            if(Runtime.version().feature()!=25)throw new IllegalStateException("Requires the game's bundled Java 25");
            Class<?> loader=Class.forName("me.zed_0xff.zombie_buddy.Loader");
            Path buddy=Path.of(loader.getProtectionDomain().getCodeSource().getLocation().toURI());
            try(JarFile z=new JarFile(buddy.toFile())) { version(z.getManifest().getMainAttributes().getValue("Implementation-Version"),"2.3.4","ZombieBuddy"); }
            // 2.3.4 has no public getInstrumentation API. This pinned field is checked before touching targets.
            var field=loader.getDeclaredField("g_instrumentation");field.setAccessible(true); inst=(Instrumentation)field.get(null);
            if(inst==null||!inst.isRetransformClassesSupported())throw new IllegalStateException("ZombieBuddy instrumentation/retransformation unavailable");
            boolean early=StackWalker.getInstance().walk(s->s.anyMatch(f->f.getClassName().equals(loader.getName())&&f.getMethodName().equals("try_call_main")));
            if(!early)throw new IllegalStateException("Not invoked by ZombieBuddy preload; refusing late activation");
            Path game=Path.of(System.getProperty("java.home")).resolve("../../../../Java/projectzomboid.jar").normalize();
            // macOS game JRE is Contents/PlugIns/jre-aarch64/Contents/Home.
            String expectedVp=expectedViewpoint(Opaque.hash(Files.readAllBytes(game)));
            Path vp=viewpoint(self(),expectedVp);
            if(!Options.OPAQUE.equals("opengl")||Options.PICK_EARLY||Options.FAR_BATCH)throw new IllegalStateException("Unqualified experimental configuration; restore the documented release options");
            // Viewpoint is not on the application classpath during approved VMCompat preload.
            // Read dependency class metadata without defining/executing its Java classes or bypassing its approval.
            try(var resources=new URLClassLoader(new java.net.URL[]{vp.toUri().toURL(),game.toUri().toURL()},WorkshopBootstrap.class.getClassLoader())) {
                ClassFile verifier=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofResourceParsing(resources)));
                try(var scope=ClassFileSupport.resources(resources)){validateJar(game,verifier);validateJar(vp,verifier);validateJar(buddy,verifier);}
            }
            loaded=Arrays.stream(inst.getAllLoadedClasses()).filter(WorkshopBootstrap::namedTarget).toArray(Class<?>[]::new);
            for(Class<?> c:loaded) {
                if(!inst.isModifiableClass(c))throw new IllegalStateException("Already loaded unmodifiable target: "+c.getName());
                if(c.getName().equals("zombie.core.opengl.ShaderUnit"))throw new IllegalStateException("ShaderUnit loaded before preload; safe static initialization cannot be proved");
            }
            transformer=new Transformer();inst.addTransformer(transformer,true);registered=true;
            if(loaded.length>0) inst.retransformClasses(loaded);
            instrumentation=inst;
            active=true;System.setProperty("vmcompat.active","workshop");
            Breakdown.warmup();Telemetry.start();NativeTrace.start();
            notice("ACTIVE","VMCompat "+Agent.VERSION+" / OpenGL / pickEvery="+Options.PICK_EVERY+" / "+Options.PICKING);
        } catch(Throwable error) {
            active=false;
            if("workshop".equals(System.getProperty("vmcompat.active")))System.clearProperty("vmcompat.active");
            if(registered&&inst!=null) {
                inst.removeTransformer(transformer);
                try{if(loaded!=null&&loaded.length>0)inst.retransformClasses(loaded);}catch(Throwable rollback){notice("FAILED","Could not restore original classes; restart required: "+rollback);return;}
            }
            notice("INCOMPATIBLE",error.toString());
        }
    }
}
