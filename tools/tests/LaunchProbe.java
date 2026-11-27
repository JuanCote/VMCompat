package workshoptests;
import java.lang.reflect.*;
import java.util.*;

/** Run with ZombieBuddy as the ONLY agent and no VMCompat on the application classpath. */
public final class LaunchProbe {
    public static void main(String[] args)throws Exception{
        boolean found;Class<?> c=null;
        try{c=Class.forName("vmcompat.WorkshopBootstrap",false,ClassLoader.getSystemClassLoader());found=true;}catch(ClassNotFoundException e){found=false;}
        boolean expect=Boolean.parseBoolean(args[0]);if(found!=expect)throw new AssertionError("VMCompat class visibility "+found+" expected "+expect);
        if(found){boolean active=(boolean)c.getMethod("active").invoke(null);String state=(String)c.getMethod("state").invoke(null);if(active)throw new AssertionError("negative bootstrap activated");if(!state.equals(args[1]))throw new AssertionError("state "+state+" expected "+args[1]);System.out.println("VMCompat state="+state);}
        if(System.getProperty("vmcompat.active")!=null)throw new AssertionError("Unexpected active property");
        if(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream().anyMatch(a->a.startsWith("-javaagent:")&&(a.contains("VMCompat")||a.contains("VMPerf"))))throw new AssertionError("Legacy agent masked test");
        Class<?> loader=Class.forName("me.zed_0xff.zombie_buddy.Loader");var field=loader.getDeclaredField("g_instrumentation");field.setAccessible(true);
        var inst=(java.lang.instrument.Instrumentation)field.get(null);
        System.out.println("Loaded graphics targets at probe: "+Arrays.stream(inst.getAllLoadedClasses()).map(Class::getName).filter(n->n.startsWith("org.lwjglx.")||n.startsWith("zombie.core.opengl.")||n.startsWith("viewpoint.")).sorted().toList());
        System.out.println("PASS isolated loader negative probe");
    }
}
