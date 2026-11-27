package zombie.compatfixture;
/** Must be rewritten before invocation; GL42 entry is unavailable on the test Mac. */
public class RecursiveChild {public static void barrier(){org.lwjgl.opengl.GL42.glMemoryBarrier(0);}}
