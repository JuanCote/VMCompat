package vmcompat;

/** Called only by ZombieBuddy's approved preload phase, never by Lua. */
public final class PreMain {
    public static void premain(String[] args) { WorkshopBootstrap.start(); }
}
