package vmcompat.tests;
import vmcompat.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.glfw.*;
import viewpoint.platform.Profile;

/** Checks rolling interval math, async CSV, actual Profile hook, and published HUD protocol. */
public final class TelemetryTests{
    public static void main(String[] args)throws Exception{
        GLFWErrorCallback callback=GLFWErrorCallback.createPrint(System.err);callback.set();
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=Context.createWindow(32,32,"Telemetry fixture",0,0);Context.makeCurrent(window);Context.capabilities();
        try{
            if(args.length>0&&args[0].equals("--swap-streams")){swapStreams(window);return;}
            var field=Profile.class.getDeclaredField("frameCpuMs");field.setAccessible(true);
            double[] cpu=(double[])field.get(null);Arrays.fill(cpu,0);cpu[0]=2.5;cpu[2]=7.25;
            Profile.draws=321;Profile.meshDraws=99;
            Telemetry.start();long now=System.nanoTime(),epoch=System.currentTimeMillis();
            Telemetry.frame(now,epoch,0);
            Class<?> pick=Class.forName("viewpoint.render.MousePick");var read=pick.getDeclaredMethod("read",Class.forName("viewpoint.render.FrameContext"),int.class,org.joml.Matrix4f.class);read.setAccessible(true);read.invoke(null,null,0,new org.joml.Matrix4f());
            long fence=org.lwjgl.opengl.GL32.glFenceSync(org.lwjgl.opengl.GL32.GL_SYNC_GPU_COMMANDS_COMPLETE,0);PassTiming.poll(fence,0,0);org.lwjgl.opengl.GL32.glDeleteSync(fence);
            if(Counters.snapshot()[Counters.Key.PICK_ENQUEUE_NS.ordinal()]<=0||Counters.snapshot()[Counters.Key.PICK_COLLECT_NS.ordinal()]<=0||Counters.snapshot()[Counters.Key.PICK_POLL_NS.ordinal()]<=0)throw new AssertionError("actual picking instrumentation missing");
            Profile.endFrame(); // Must snapshot before Viewpoint resets its arrays/counters.
            now+=10_000_000;Telemetry.frame(now,epoch+10,100);
            for(int i=2;i<=200;i++){now+=10_000_000;Telemetry.frame(now,epoch+i*10,100);}
            var stats=Telemetry.snapshot(now);
            if(stats.count()!=200||stats.total()!=200||stats.mean()!=10||stats.fps()!=100||stats.p99()!=10||stats.over50()!=0||stats.dropped()!=0)throw new AssertionError(stats);
            Telemetry.stopForTest();
            List<String> lines=Files.readAllLines(Telemetry.recording());
            if(lines.size()!=201)throw new AssertionError("Missing frame rows "+lines.size());
            String[] first=lines.get(1).split(","),second=lines.get(2).split(",");
            if(!first[4].equals("1")||!first[5].equals("321")||!first[6].equals("99")||!first[15].equals("2.5")||!first[17].equals("7.25")||!second[4].equals("0"))throw new AssertionError("Profile hook or next-frame reset: "+lines.get(1));
            String[] header=lines.getFirst().split(",");if(header.length!=first.length||!first[Arrays.asList(header).indexOf("viewpoint_snapshot_id")].equals("1")||!first[Arrays.asList(header).indexOf("viewpoint_snapshots_in_interval")].equals("1"))throw new AssertionError("display/snapshot association");
            if(!Files.readString(Telemetry.recording().resolveSibling(Telemetry.recording().getFileName()+".ranges.json")).contains("resources"))throw new AssertionError("range diagnostics sidecar missing");
            List<String> hud=Files.readAllLines(Path.of(System.getProperty("vmcompat.luaRoot")).resolve("VMCompatPerformance.txt"));
            if(hud.size()!=17||!hud.get(2).equals(Agent.VERSION)||!hud.get(14).equals(Options.PICKING.equals("original")?"OpenGL 4.1":"OpenGL 4.1 / depth staging")||Double.parseDouble(hud.get(6))!=100)throw new AssertionError(hud);
            System.out.println("TELEMETRY PASS 200 complete intervals; actual Viewpoint Profile snapshot; HUD schema; CSV and queue delivery");
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();callback.free();}
    }
    static void swapStreams(long window)throws Exception{
        Telemetry.start();Telemetry.swap(window);Telemetry.swap(window);
        var caps=org.lwjgl.opengl.GL.getCapabilities();long other=Context.createWindow(16,16,"Secondary diagnostic fixture",0,0);Context.makeCurrent(other);Context.capabilities();Telemetry.swap(other);Telemetry.swap(other);Context.destroyWindow(other);Context.makeCurrent(window);org.lwjgl.opengl.GL.setCapabilities(caps);Telemetry.swap(window);Telemetry.stopForTest();
        List<String> lines=Files.readAllLines(Telemetry.recording());if(lines.size()!=3)throw new AssertionError("primary CSV must contain exactly 2 complete intervals: "+lines.size());
        Files.writeString(Path.of(System.getProperty("vmcompat.resultDir")).resolve("swap-status.json"),"{\"passed\":true,\"primary_intervals\":2,\"output_streams\":2}\n");
    }
}
