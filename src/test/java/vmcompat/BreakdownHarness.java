package vmcompat;
/** Drives the new CPU instrumentation around the existing GL fixture, not a game renderer. */
public final class BreakdownHarness {
    static final Object state=new Object(),drawer=new Object();static final viewpoint.core.Frame frame=new viewpoint.core.Frame();static long token,world,draw,step;static {Breakdown.drawerRegistered(drawer,frame);}
    public static void begin(){Breakdown.populate(state);long prep=Breakdown.open(Breakdown.Scope.MAIN_PREP.ordinal());frame.number++;frame.takenNanos=System.nanoTime();Breakdown.drawerQueued(drawer);Breakdown.close(prep);Breakdown.publish(state);long acquire=Breakdown.open(Breakdown.Scope.ACQUIRE.ordinal());Breakdown.consume(state);Breakdown.close(acquire);step=Breakdown.open(Breakdown.Scope.STEP_POST.ordinal());viewpoint.Hooks.gameRenderStarted();token=Breakdown.open(Breakdown.Scope.PZ_RENDER.ordinal());draw=Breakdown.drawerBegin(drawer,frame);world=Breakdown.open(Breakdown.Scope.VP_WORLD.ordinal());}
    public static void end(){Breakdown.close(world);Breakdown.close(draw);long ui=Breakdown.open(Breakdown.Scope.PZ_UI.ordinal());Breakdown.close(ui);Breakdown.close(token);viewpoint.Hooks.gameRenderEnded();Breakdown.close(step);Breakdown.drawerReleased(drawer);Breakdown.rendered(state);}
    public static void boundary(long window,long now){viewpoint.platform.FrameTimes.swapped();long p=Breakdown.profiler(now);Breakdown.boundary(window,now,Breakdown.cpu(),Telemetry.displayId(),true);Breakdown.close(p);}
}
