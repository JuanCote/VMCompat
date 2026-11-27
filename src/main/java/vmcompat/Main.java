package vmcompat;

/** Late mod loading can explain a missing preload, but must not install compatibility. */
public final class Main {
    public static void main(String[] args) {
        try { Class.forName("me.zed_0xff.zombie_buddy.Exposer").getMethod("exposeClass", Class.class).invoke(null, WorkshopBootstrap.class); }
        catch (ReflectiveOperationException e) { System.err.println("[VMCompat] Startup status exposure unavailable: " + e); }
        if (!WorkshopBootstrap.active() && WorkshopBootstrap.state().equals("NOT_STARTED")) WorkshopBootstrap.notice("INACTIVE",
            "VMCompat early compatibility is inactive. Requires macOS Apple Silicon, compatible Build 42 Viewpoint and ZombieBuddy 2.3.4. "
            + "A valid author signature and ZombieBuddy preload approval are required. Restart after approval. "
            + "This private preview still requires manual installation and gameplay validation.");
    }
}
