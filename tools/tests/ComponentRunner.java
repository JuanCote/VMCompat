package workshoptests;

/** Existing GL fixtures only. Not a claim that subscription/approval bootstrap passed. */
public final class ComponentRunner {
    public static void main(String[] args)throws Exception{
        Class<?> loader=Class.forName("me.zed_0xff.zombie_buddy.Loader");var field=loader.getDeclaredField("g_instrumentation");field.setAccessible(true);
        var inst=(java.lang.instrument.Instrumentation)field.get(null);
        inst.addTransformer(new vmcompat.Transformer(),true);
        java.util.List<Class<?>> loaded=new java.util.ArrayList<>();
        for(Class<?> c:inst.getAllLoadedClasses())if((c.getName().startsWith("zombie.")||c.getName().startsWith("org.lwjglx.")||c.getName().startsWith("viewpoint."))&&inst.isModifiableClass(c))loaded.add(c);
        if(!loaded.isEmpty())inst.retransformClasses(loaded.toArray(Class<?>[]::new));
        System.setProperty("vmcompat.active","true");
        java.nio.file.Files.createDirectories(vmcompat.Log.ROOT);
        if(java.util.Arrays.asList(args).contains("--picking-rate")) {
            // JVM calls incapable transformers before capable ones regardless of registration time.
            // Keep the existing probe after the capable public transformer in the same group.
            var proxy=(java.lang.instrument.Instrumentation)java.lang.reflect.Proxy.newProxyInstance(ComponentRunner.class.getClassLoader(),new Class<?>[]{java.lang.instrument.Instrumentation.class},(o,m,a)->{
                if(m.getName().equals("addTransformer")&&a.length==1){inst.addTransformer((java.lang.instrument.ClassFileTransformer)a[0],true);return null;}return m.invoke(inst,a);
            });
            vmcompat.tests.PickingTestAgent.premain(null,proxy);
        }
        if(System.getProperty("vmcompat.fixtureViewpointJar")!=null){
            Class<?> phase=Class.forName(loader.getName()+"$Phase");
            var method=loader.getDeclaredMethod("loadJar",java.nio.file.Path.class,String.class,String.class,phase);method.setAccessible(true);
            Object before=java.util.Arrays.stream(phase.getEnumConstants()).filter(x->x.toString().equals("PREMAIN")).findFirst().orElseThrow();
            var vp=java.nio.file.Path.of(System.getProperty("vmcompat.fixtureViewpointJar"));
            if(!(boolean)method.invoke(null,vp,"viewpoint",vmcompat.Opaque.hash(java.nio.file.Files.readAllBytes(vp)),before))throw new AssertionError("Fixture Viewpoint patch composition");
        }
        String target=args[0].contains(".")?args[0]:"vmcompat.tests."+args[0];
        if(target.equals("vmcompat.CompositionInspect"))vmcompat.CompositionInspect.premain(null,inst);
        Class.forName(target).getMethod("main",String[].class).invoke(null,(Object)java.util.Arrays.copyOfRange(args,1,args.length));
    }
}
