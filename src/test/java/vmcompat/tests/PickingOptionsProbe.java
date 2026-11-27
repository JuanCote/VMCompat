package vmcompat.tests;
/** Process-isolated configuration parsing check, no OpenGL or game loop. */
public final class PickingOptionsProbe {public static void main(String[] args){System.out.println(args.length>0?vmcompat.Options.PICKING:vmcompat.Options.PICK_EVERY);}}
